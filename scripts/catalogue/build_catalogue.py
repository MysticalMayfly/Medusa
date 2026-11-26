#!/usr/bin/env python3
"""Medusa's upkeeper: keeps the master database on the Mac and pushes the maintained view to Supabase for the TV.

The master database is ~/Desktop/Medusa/medusa-master.db: the single source of truth and the only copy that is
ever updated. Supabase only receives what this script pushes; the TV only reads Supabase and keeps its own cache.

    python3 scripts/catalogue/build_catalogue.py seed      # once: titles from ~/Desktop/Films (medusa.fixed.csv,
                                                          #   tv_shows.csv) into the master; existing titles are kept
    python3 scripts/catalogue/build_catalogue.py fetch     # TMDB details for every master title (cached, resumable)
    python3 scripts/catalogue/build_catalogue.py build     # refresh the catalogue table in the master from source + TMDB
    python3 scripts/catalogue/build_catalogue.py publish   # push the catalogue to Supabase (service role key)
    python3 scripts/catalogue/build_catalogue.py sync      # profiles: devices' changes into the Master, then back out
    python3 scripts/catalogue/build_catalogue.py upkeep    # fetch + build + publish + sync

Master tables: sources (one row per title, the CSV row it came from: edit here), catalogue (built, what Supabase gets),
services, platforms. TMDB token: ~/Desktop/Films/.tmdb_key. Cache: ~/Desktop/Medusa/tmdb_cache/.
Supabase settings: ~/Desktop/Medusa/.supabase.env (never in this repo or the app).
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
MASTER_DIR = os.path.expanduser(os.environ.get('MEDUSA_MASTER_DIR', '~/Desktop/Medusa'))
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
        'CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT);'
        'CREATE TABLE IF NOT EXISTS profiles (id TEXT PRIMARY KEY, name TEXT NOT NULL, avatar INTEGER NOT NULL DEFAULT 0,'
        ' photo_url TEXT, updated_at TEXT NOT NULL);'
        'CREATE TABLE IF NOT EXISTS profile_titles (profile_id TEXT NOT NULL, title_id INTEGER NOT NULL,'
        ' watched INTEGER NOT NULL DEFAULT 0, watched_at TEXT, interested INTEGER NOT NULL DEFAULT 0,'
        ' prog_season INTEGER, prog_episode INTEGER, prog_at TEXT, updated_at TEXT NOT NULL, PRIMARY KEY (profile_id, title_id));'
        'CREATE TABLE IF NOT EXISTS applied_events (id INTEGER PRIMARY KEY, profile_id TEXT, kind TEXT, title_id INTEGER,'
        ' value TEXT, device TEXT, created_at TEXT, applied_at TEXT);')
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
    return datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%S.%fZ')


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
    # The default profile, "Me", starts with what the Films files mark as watched.
    if c.execute("INSERT OR IGNORE INTO profiles VALUES ('p1', 'Me', 0, NULL, ?)", (now(),)).rowcount:
        for k, r in c.execute('SELECT kind, row FROM sources').fetchall():
            row = json.loads(r)
            if (row.get('medusa_watched') or '').strip().upper() == 'Y':
                c.execute('INSERT OR IGNORE INTO profile_titles (profile_id, title_id, watched, updated_at) VALUES (?,?,1,?)',
                          ('p1', stable_id(k, row['tmdb_id']), now()))
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
        # Horror when TMDB says so, or when at least 3 of the 5 horror lists include it (1 list let in thrillers like
        # The Girl with the Dragon Tattoo).
        horror = 'Horror' in genres or (num(r.get('horror_sources'), int) or 0) >= 3
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


# ---------------------------------------------------------------- profiles

def rest(method, path, body=None, prefer=None):
    env = supabase_env()
    url, key = env['SUPABASE_URL'].rstrip('/'), env['SUPABASE_SERVICE_ROLE_KEY']
    h = {'apikey': key, 'Authorization': 'Bearer ' + key, 'Content-Type': 'application/json', 'Accept': 'application/json'}
    if prefer:
        h['Prefer'] = prefer
    req = urllib.request.Request('%s/rest/v1/%s' % (url, path), data=json.dumps(body).encode() if body is not None else None,
                                 headers=h, method=method)
    out = urllib.request.urlopen(req, timeout=60).read()
    return json.loads(out) if out else None


def norm(ts):
    """Any ISO timestamp -> UTC 'YYYY-MM-DDTHH:MM:SS.ffffffZ', so timestamps compare as text."""
    import re
    ts = ts.replace('Z', '+00:00')
    # Older Pythons only read 0, 3 or 6 fraction digits: pad or trim to 6.
    ts = re.sub(r'\.(\d+)', lambda m: '.' + (m.group(1) + '000000')[:6], ts)
    d = datetime.fromisoformat(ts)
    return d.astimezone(timezone.utc).strftime('%Y-%m-%dT%H:%M:%S.%fZ')


def sync():
    """Profiles: copy the devices' events into the Master, then push the Master's profiles back to Supabase.
    Newest change wins per title; events are kept in applied_events and removed from Supabase once applied."""
    c = master()
    events = rest('GET', 'profile_events?select=*&order=id&limit=10000') or []
    for e in events:
        pid, kind, tid, v, at = e['profile_id'], e['kind'], e['title_id'], e.get('value') or {}, norm(e['created_at'])
        if kind == 'profile':
            c.execute('INSERT INTO profiles (id, name, avatar, photo_url, updated_at) VALUES (?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET '
                      'name=excluded.name, avatar=excluded.avatar, photo_url=excluded.photo_url, updated_at=excluded.updated_at '
                      'WHERE excluded.updated_at > profiles.updated_at',
                      (pid, (v.get('name') or 'Profile')[:20], int(v.get('avatar') or 0), v.get('photo_url'), at))
        elif kind == 'delete_profile':
            c.execute('DELETE FROM profiles WHERE id=?', (pid,))
            c.execute('DELETE FROM profile_titles WHERE profile_id=?', (pid,))
        elif tid is not None and c.execute('SELECT 1 FROM profiles WHERE id=?', (pid,)).fetchone():
            cur = c.execute('SELECT updated_at FROM profile_titles WHERE profile_id=? AND title_id=?', (pid, tid)).fetchone()
            if cur and cur[0] > at:
                pass   # a newer change is already in the Master
            else:
                c.execute('INSERT OR IGNORE INTO profile_titles (profile_id, title_id, updated_at) VALUES (?,?,?)', (pid, tid, at))
                if kind == 'watched':
                    c.execute('UPDATE profile_titles SET watched=?, watched_at=?, updated_at=? WHERE profile_id=? AND title_id=?',
                              (1 if v.get('on') else 0, v.get('at') if v.get('on') else None, at, pid, tid))
                elif kind == 'interested':
                    c.execute('UPDATE profile_titles SET interested=?, updated_at=? WHERE profile_id=? AND title_id=?',
                              (1 if v.get('on') else 0, at, pid, tid))
                elif kind == 'progress':
                    c.execute('UPDATE profile_titles SET prog_season=?, prog_episode=?, prog_at=?, updated_at=? WHERE profile_id=? AND title_id=?',
                              (v.get('season'), v.get('episode'), v.get('at'), at, pid, tid))
        c.execute('INSERT OR IGNORE INTO applied_events VALUES (?,?,?,?,?,?,?,?)',
                  (e['id'], pid, kind, tid, json.dumps(v), e.get('device'), at, now()))
    c.commit()
    if events:
        rest('DELETE', 'profile_events?id=lte.%d' % events[-1]['id'])
    c.row_factory = sqlite3.Row
    profiles = [dict(r) for r in c.execute('SELECT id, name, avatar, photo_url, updated_at FROM profiles')]
    rows = [dict(r, watched=bool(r['watched']), interested=bool(r['interested'])) for r in c.execute('SELECT * FROM profile_titles')]
    rest('POST', 'profiles?on_conflict=id', profiles, 'resolution=merge-duplicates,return=minimal')
    keep = ','.join('"%s"' % p['id'] for p in profiles) or '""'
    rest('DELETE', 'profiles?id=not.in.(%s)' % urllib.request.quote(keep, safe='",()'))
    for i in range(0, len(rows), 500):
        rest('POST', 'profile_titles?on_conflict=profile_id,title_id', rows[i:i + 500], 'resolution=merge-duplicates,return=minimal')
    print('profiles: %d events applied, %d profiles, %d rows pushed' % (len(events), len(profiles), len(rows)))


if __name__ == '__main__':
    cmd = sys.argv[1] if len(sys.argv) > 1 else 'build'
    def upkeep():
        fetch(); build(); publish(); sync()
    {'seed': seed, 'fetch': fetch, 'build': build, 'publish': publish, 'sync': sync, 'upkeep': upkeep}[cmd]()
