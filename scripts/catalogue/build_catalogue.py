#!/usr/bin/env python3
"""Medusa's upkeeper: keeps the master database on the Mac and pushes the maintained view to Supabase for the TV.

The master database is ~/Desktop/Medusa Master/medusa-master.db: the single source of truth and the only copy that is
ever updated. Supabase only receives what this script pushes; the TV only reads Supabase and keeps its own cache.

    python3 scripts/catalogue/build_catalogue.py seed      # once: titles from ~/Desktop/Films (medusa.fixed.csv,
                                                          #   tv_shows.csv) into the master; existing titles are kept
    python3 scripts/catalogue/build_catalogue.py fetch     # TMDB details for every master title (cached, resumable)
    python3 scripts/catalogue/build_catalogue.py build     # refresh the catalogue table in the master from source + TMDB
    python3 scripts/catalogue/build_catalogue.py publish   # push the catalogue to Supabase (service role key)
    python3 scripts/catalogue/build_catalogue.py upkeep    # fetch + build + publish

Master tables: sources (one row per title, the CSV row it came from: edit here), catalogue (built, what Supabase gets),
services, platforms. TMDB token: ~/Desktop/Films/.tmdb_key. Cache: ~/Desktop/Medusa Master/tmdb_cache/.
Supabase settings: ~/Desktop/Medusa Master/.supabase.env (never in this repo or the app).
Personal data (what you watched) stays in sources and is NOT published: the view is readable with the app's public key.
Standard library only.
"""
import csv
import json
import os
import sqlite3
import sys
import threading
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone

FILMS_DIR = os.path.expanduser(os.environ.get('MEDUSA_FILMS_DIR', '~/Desktop/Films'))
FILMS_CSV = os.path.join(FILMS_DIR, 'medusa.fixed.csv')
SHOWS_CSV = os.path.join(FILMS_DIR, 'tv_shows.csv')
MASTER_DIR = os.path.expanduser(os.environ.get('MEDUSA_MASTER_DIR', '~/Desktop/Medusa Master'))
MASTER = os.path.join(MASTER_DIR, 'medusa-master.db')
CACHE = os.path.join(MASTER_DIR, 'tmdb_cache')
SCHEMA = os.path.join(os.path.dirname(__file__), 'schema.sql')


def master():
    os.makedirs(MASTER_DIR, exist_ok=True)
    c = sqlite3.connect(MASTER)
    c.executescript(
        'CREATE TABLE IF NOT EXISTS sources (id INTEGER PRIMARY KEY, kind TEXT NOT NULL, tmdb_id INTEGER NOT NULL, row TEXT NOT NULL,'
        ' added_at TEXT NOT NULL, updated_at TEXT NOT NULL);'
        'CREATE TABLE IF NOT EXISTS catalogue (id INTEGER PRIMARY KEY, data TEXT NOT NULL, built_at TEXT NOT NULL);'
        'CREATE TABLE IF NOT EXISTS services (code TEXT PRIMARY KEY, name TEXT, colour TEXT, mine INTEGER, free INTEGER, sort INTEGER);'
        'CREATE TABLE IF NOT EXISTS platforms (slug TEXT PRIMARY KEY, name TEXT, colour TEXT, sort INTEGER);'
        'CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT);')
    return c

IMG = 'https://image.tmdb.org/t/p/'

# Indian services the app knows (the Mac app's services table, plus Plex). mine = on Prince's plans.
SERVICES = [
    # code, name, colour, mine, free, sort
    ('NF', 'Netflix', '#ff5a4e', True, False, 0),
    ('JH', 'JioHotstar', '#ff5fae', True, False, 1),
    ('PV', 'Prime Video', '#3fd0d4', True, False, 2),
    ('SL', 'SonyLIV', '#ffd23f', True, False, 3),
    ('AP', 'Apple TV', '#ecebf2', True, False, 4),
    ('Z5', 'ZEE5', '#b0a2ff', True, False, 5),
    ('LP', 'Lionsgate Play', '#b6e34a', True, False, 6),
    ('MU', 'MUBI', '#8fe0c4', False, False, 7),
    ('PX', 'Plex', '#e5a00d', False, True, 8),
]
# streaming_on names in the CSVs -> service code. "(paid add-on)" channels are left out, as on the Mac.
SERVICE_NAMES = {
    'Netflix': 'NF', 'Netflix Kids': 'NF', 'JioHotstar': 'JH', 'Amazon Prime Video': 'PV',
    'Amazon Prime Video with Ads': 'PV', 'Amazon Prime Video Free with Ads': 'PV', 'Sony Liv': 'SL', 'Apple TV': 'AP',
    'Zee5': 'Z5', 'Lionsgate Play': 'LP', 'MUBI': 'MU', 'Plex': 'PX',
}
# Networks of shows -> the platform they premiered on (the Mac app's platforms table).
PLATFORMS = [
    ('apple', 'Apple TV+', '#F2F2F7', 0), ('netflix', 'Netflix', '#E50914', 1), ('prime', 'Prime Video', '#1FA3FF', 2),
    ('hbo', 'HBO & Max', '#A06BFF', 3), ('crunchy', 'Crunchyroll', '#F78B24', 4), ('disney', 'Disney+', '#2F7BFF', 5),
    ('hulu', 'Hulu', '#1CE783', 6), ('paramount', 'Paramount+', '#0064FF', 7), ('peacock', 'Peacock', '#F5C518', 8),
]
NETWORK_PLATFORM = {
    'Apple TV+': 'apple', 'Apple TV': 'apple', 'Netflix': 'netflix', 'Prime Video': 'prime', 'Amazon Prime Video': 'prime',
    'HBO': 'hbo', 'HBO Max': 'hbo', 'Max': 'hbo', 'Crunchyroll': 'crunchy', 'Disney+': 'disney', 'Hulu': 'hulu',
    'Paramount+': 'paramount', 'Peacock': 'peacock',
}


# ---------------------------------------------------------------- input

def read_csv(path):
    with open(path, encoding='utf-8-sig', newline='') as f:
        return list(csv.DictReader(f))


def now():
    return datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')


def seed():
    """Titles from the two ~/Desktop/Films files into the master. Titles already in the master are left as they are."""
    c = master()
    added = 0
    for kind, path in (('movie', FILMS_CSV), ('show', SHOWS_CSV)):
        for r in read_csv(path):
            tid = (r.get('tmdb_id') or '').strip()
            if not tid.isdigit():
                continue
            r = {k.lstrip('\ufeff'): v for k, v in r.items()}
            cur = c.execute('INSERT OR IGNORE INTO sources VALUES (?,?,?,?,?,?)',
                            (stable_id(kind, tid), kind, int(tid), json.dumps(r, ensure_ascii=False), now(), now()))
            added += cur.rowcount
    for s in SERVICES:
        c.execute('INSERT OR IGNORE INTO services VALUES (?,?,?,?,?,?)', s)
    for p in PLATFORMS:
        c.execute('INSERT OR IGNORE INTO platforms VALUES (?,?,?,?)', p)
    c.commit()
    total = c.execute('SELECT COUNT(*) FROM sources').fetchone()[0]
    print('%d new titles; the master now has %d (%s)' % (added, total, MASTER))


def titles():
    """(kind, row) for every title in the master."""
    c = master()
    return [(k, json.loads(r)) for k, r in c.execute('SELECT kind, row FROM sources ORDER BY id')]


def stable_id(kind, tmdb_id):
    """One number per title, stable across rebuilds: TMDB ids are per type, so films are even and shows odd."""
    return int(tmdb_id) * 2 + (1 if kind == 'show' else 0)


# ---------------------------------------------------------------- fetch

def token():
    t = os.environ.get('TMDB_TOKEN') or open(os.path.join(FILMS_DIR, '.tmdb_key')).read().strip()
    return t


def cache_path(kind, tmdb_id):
    return os.path.join(CACHE, '%s_%s.json' % (kind, tmdb_id))


def fetch():
    os.makedirs(CACHE, exist_ok=True)
    tok = token()
    auth = {'Authorization': 'Bearer ' + tok} if tok.startswith('eyJ') else {}
    key = '' if auth else '&api_key=' + tok
    todo = [(k, r['tmdb_id'].strip()) for k, r in titles() if not os.path.exists(cache_path(k, r['tmdb_id'].strip()))]
    todo = list(dict.fromkeys(todo))
    print('%d to fetch (%d already cached)' % (len(todo), len(titles()) - len(todo)))
    lock = threading.Lock()
    done = [0]
    gate = threading.Semaphore(16)

    def one(item):
        kind, tid = item
        path = ('movie' if kind == 'movie' else 'tv') + '/' + tid
        url = 'https://api.themoviedb.org/3/%s?append_to_response=videos,watch/providers&language=en-US%s' % (path, key)
        for attempt in range(8):
            with gate:
                try:
                    req = urllib.request.Request(url, headers=dict(auth, Accept='application/json'))
                    body = urllib.request.urlopen(req, timeout=20).read()
                    with open(cache_path(kind, tid), 'wb') as f:
                        f.write(body)
                    break
                except urllib.error.HTTPError as e:
                    if e.code == 404:
                        with open(cache_path(kind, tid), 'w') as f:
                            f.write('{"missing": true}')
                        break
                    time.sleep(0.5 * (attempt + 1))
                except Exception:
                    time.sleep(0.5 * (attempt + 1))
        with lock:
            done[0] += 1
            if done[0] % 250 == 0:
                print('  %d / %d' % (done[0], len(todo)))

    with ThreadPoolExecutor(16) as ex:
        list(ex.map(one, todo))
    print('fetch done')


# ---------------------------------------------------------------- build

def num(v, f=float):
    try:
        return f(v) if v not in (None, '') else None
    except ValueError:
        return None


def split(v):
    return [x.strip() for x in (v or '').split('|') if x.strip()]


def services_of(streaming_on):
    """(codes, free_codes, names): services you can stream on, from the CSV's streaming_on."""
    codes, names = [], []
    for raw in split(streaming_on):
        if '(paid add-on)' in raw:
            continue
        name = raw.replace('(free with ads)', '').strip()
        names.append(raw)
        c = SERVICE_NAMES.get(name)
        if c and c not in codes:
            codes.append(c)
    return codes, names


def trailer(tm, csv_key):
    if csv_key:
        return csv_key
    vids = [v for v in ((tm.get('videos') or {}).get('results') or []) if v.get('site') == 'YouTube']
    vids.sort(key=lambda v: (v.get('type') != 'Trailer', not v.get('official'), v.get('published_at') or ''))
    return vids[0]['key'] if vids else None


def episode(e):
    return {'season': e.get('season_number'), 'number': e.get('episode_number'), 'airdate': e.get('air_date')} if e else None


def build():
    rows = []
    for kind, r in titles():
        tid = r['tmdb_id'].strip()
        try:
            tm = json.load(open(cache_path(kind, tid)))
        except (OSError, ValueError):
            tm = {}
        if tm.get('missing'):
            tm = {}
        codes, names = services_of(r.get('streaming_on'))
        status = (r.get('india_status') or '').strip()
        rent = split(r.get('rent_buy'))
        watch = json.loads(r['provider_links']) if (r.get('provider_links') or '').startswith('[') else []
        prov = ((tm.get('watch/providers') or {}).get('results') or {}).get('IN') or {}
        genres = [g['name'] for g in tm.get('genres') or []]
        horror = 'Horror' in genres or num(r.get('horror_sources'), int) and int(r['horror_sources']) >= 1
        seasons = [s.get('episode_count') or 0 for s in sorted(tm.get('seasons') or [], key=lambda s: s.get('season_number') or 0)
                   if (s.get('season_number') or 0) > 0]
        networks = [n['name'] for n in tm.get('networks') or []]
        coll = (tm.get('belongs_to_collection') or {}).get('name')
        franchise = (r.get('franchise') or '').strip() or (coll.replace(' Collection', '') if coll else None)
        poster = r.get('poster_path') or tm.get('poster_path')
        backdrop = r.get('backdrop_path') or tm.get('backdrop_path')
        year = num((r.get('year') or '')[:4], int) or num((tm.get('release_date') or tm.get('first_air_date') or '')[:4], int)
        runtime = num(r.get('runtime_min'), int) or tm.get('runtime') or ((tm.get('episode_run_time') or [None])[0])
        rows.append({
            'id': stable_id(kind, tid),
            'kind': kind,
            'title': r.get('title') or r.get('﻿title') or tm.get('title') or tm.get('name'),
            'year': year,
            'original_title': r.get('original_title') or None,
            'tmdb_id': int(tid),
            'imdb_id': r.get('imdb_id') or None,
            'runtime': runtime,
            'countries': [c.strip() for c in (r.get('countries') or '').split(',') if c.strip()],
            'language': r.get('original_language') or None,
            'imdb_rating': num(r.get('imdb_rating')),
            'imdb_votes': num(r.get('imdb_votes'), int),
            'tmdb_rating': num(r.get('tmdb_rating')),
            'tmdb_votes': num(r.get('tmdb_votes'), int),
            'rt_score': num(r.get('rotten_tomatoes'), int),
            'horror': bool(horror),
            'genres': genres,
            'franchise': franchise,
            'overview': tm.get('overview') or None,
            'tagline': tm.get('tagline') or None,
            'poster': IMG + 'w342' + poster if poster else None,
            'backdrop': IMG + 'w1280' + backdrop if backdrop else None,
            'trailer_key': trailer(tm, r.get('trailer_key')),
            'india_status': {'Streaming in India': 'streaming', 'Rent or buy only in India': 'rent',
                             'Not available in India': 'not_yet'}.get(status, 'unknown'),
            'services': codes,
            'service_names': names,
            'rent_buy': rent,
            'watch': watch,
            'watch_page': r.get('watch_page') or prov.get('link'),
            'seasons': seasons,
            'networks': networks,
            'platform': next((NETWORK_PLATFORM[n] for n in networks if n in NETWORK_PLATFORM), None) if kind == 'show' else None,
            'tmdb_status': tm.get('status'),
            'next_ep': episode(tm.get('next_episode_to_air')),
            'prev_ep': episode(tm.get('last_episode_to_air')),
        })
    c = master()
    stamp = now()
    c.execute('DELETE FROM catalogue')
    c.executemany('INSERT INTO catalogue VALUES (?,?,?)', [(t['id'], json.dumps(t, ensure_ascii=False), stamp) for t in rows])
    c.execute("INSERT OR REPLACE INTO meta VALUES ('built_at', ?)", (stamp,))
    c.commit()
    print('%d titles built into %s' % (len(rows), MASTER))


def catalogue():
    """What publish pushes: the built catalogue plus services and platforms, from the master."""
    c = master()
    c.row_factory = sqlite3.Row
    return {
        'built_at': c.execute("SELECT value FROM meta WHERE key='built_at'").fetchone()[0],
        'services': [dict(r, mine=bool(r['mine']), free=bool(r['free'])) for r in c.execute('SELECT * FROM services ORDER BY sort')],
        'platforms': [dict(r) for r in c.execute('SELECT * FROM platforms ORDER BY sort')],
        'titles': [json.loads(r['data']) for r in c.execute('SELECT data FROM catalogue ORDER BY id')],
    }


# ---------------------------------------------------------------- publish

def supabase_env():
    env = {}
    path = os.path.join(MASTER_DIR, '.supabase.env')
    for line in open(path):
        if '=' in line and not line.lstrip().startswith('#'):
            k, v = line.strip().split('=', 1)
            env[k.strip()] = v.strip()
    return env


def publish():
    env = supabase_env()
    url, key = env['SUPABASE_URL'].rstrip('/'), env['SUPABASE_SERVICE_ROLE_KEY']
    data = catalogue()
    headers = {'apikey': key, 'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json',
               'Prefer': 'resolution=merge-duplicates,return=minimal'}

    def post(table, rows):
        for i in range(0, len(rows), 500):
            req = urllib.request.Request('%s/rest/v1/%s?on_conflict=%s' % (url, table, 'code' if table == 'services' else 'slug' if table == 'platforms' else 'id'),
                                         data=json.dumps(rows[i:i + 500]).encode(), headers=headers, method='POST')
            urllib.request.urlopen(req, timeout=60).read()
        print('%s: %d rows' % (table, len(rows)))

    post('services', data['services'])
    post('platforms', data['platforms'])
    stamp = data['built_at']
    post('titles', [dict(t, published_at=stamp) for t in data['titles']])
    # titles no longer in the CSVs leave the catalogue
    req = urllib.request.Request('%s/rest/v1/titles?published_at=lt.%s' % (url, stamp), headers=headers, method='DELETE')
    urllib.request.urlopen(req, timeout=60).read()
    print('published')


if __name__ == '__main__':
    cmd = sys.argv[1] if len(sys.argv) > 1 else 'build'
    def upkeep():
        fetch(); build(); publish()
    {'seed': seed, 'fetch': fetch, 'build': build, 'publish': publish, 'upkeep': upkeep}[cmd]()
