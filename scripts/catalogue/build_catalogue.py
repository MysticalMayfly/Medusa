#!/usr/bin/env python3
"""Medusa's upkeeper: stages the Mac catalogue in the desktop mirror and syncs it with Supabase.

The catalogue master is ~/Desktop/Medusa/Medusa.db. Catalogue changes are staged in
~/Desktop/Medusa/supabase.db before being sent to Supabase. Profile data is cloud-owned.

    python3 scripts/catalogue/build_catalogue.py seed      # the master's titles = exactly the titles in ~/Desktop/Films
                                                          #   (films.csv, tv_shows.csv); titles not in them are removed
    python3 scripts/catalogue/build_catalogue.py build     # fill title_data in the master: TMDB (fresh), the Mac app, IMDb
    python3 scripts/catalogue/build_catalogue.py publish   # stage the catalogue in the mirror, then sync it
    python3 scripts/catalogue/build_catalogue.py sync      # refresh the mirror; never push old master profiles
    python3 scripts/catalogue/build_catalogue.py upkeep    # seed + build + publish

Master tables, plain columns (no JSON blobs):
  titles        one row per title: kind, tmdb_id, imdb_id, title, year, RT score, horror. Only these come from the two CSVs
                (~/Desktop/Medusa/films.csv and tv_shows.csv).
  title_data    everything the app shows, fetched: TMDB (details, genres, art, trailers, Indian streaming), the Mac app's
                database ~/Desktop/Medusa Mac/data/medusa.db where it has the title (IMDb and RT scores, trailer,
                direct watch links, franchise), and IMDb's public ratings file for the IMDb score of the rest.
  seasons, watch_offers, services, platforms, meta, profiles, profile_titles, applied_events.
TMDB token: ~/Desktop/Films/.tmdb_key. Nothing is cached: every build asks TMDB afresh, so streaming info stays current.
Supabase settings: ~/Desktop/Medusa/.supabase.env (never in this repo or the app).
Lists in title_data (genres, countries, services, ...) are text joined with ' | '.
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
MASTER_DIR = os.path.expanduser(os.environ.get('MEDUSA_MASTER_DIR', '~/Desktop/Medusa'))
# The two title lists live in the Medusa folder (older copies in ~/Desktop/Films are used only if they are missing).
CSV_DIR = MASTER_DIR if os.path.exists(os.path.join(MASTER_DIR, 'films.csv')) else FILMS_DIR
FILMS_CSV = os.path.join(CSV_DIR, 'films.csv')
SHOWS_CSV = os.path.join(CSV_DIR, 'tv_shows.csv')
MASTER = os.path.join(MASTER_DIR, 'Medusa.db')
MIRROR = os.path.join(MASTER_DIR, 'supabase.db')
MAC_DB = os.path.expanduser(os.environ.get('MEDUSA_MAC_DB', '~/Desktop/Medusa Mac/data/medusa.db'))
IMDB_RATINGS = 'https://datasets.imdbws.com/title.ratings.tsv.gz'
SCHEMA = os.path.join(os.path.dirname(__file__), 'schema.sql')


def master():
    os.makedirs(MASTER_DIR, exist_ok=True)
    c = sqlite3.connect(MASTER)
    c.execute('PRAGMA foreign_keys = ON')
    c.executescript(
        'CREATE TABLE IF NOT EXISTS titles (id INTEGER PRIMARY KEY, kind TEXT NOT NULL, tmdb_id INTEGER NOT NULL,'
        ' imdb_id TEXT, title TEXT NOT NULL, year INTEGER, rt_score INTEGER, added_at TEXT NOT NULL, horror INTEGER NOT NULL DEFAULT 0);'
        'CREATE TABLE IF NOT EXISTS title_data (id INTEGER PRIMARY KEY REFERENCES titles(id) ON DELETE CASCADE,'
        ' original_title TEXT, runtime INTEGER, countries TEXT, language TEXT,'
        ' imdb_rating REAL, imdb_votes INTEGER, tmdb_rating REAL, tmdb_votes INTEGER, rt_score INTEGER,'
        ' horror INTEGER NOT NULL DEFAULT 0, genres TEXT, franchise TEXT, overview TEXT, tagline TEXT,'
        ' poster TEXT, backdrop TEXT, trailer_key TEXT,'
        ' india_status TEXT, services TEXT, service_names TEXT, rent_buy TEXT, watch_page TEXT,'
        ' networks TEXT, platform TEXT, tmdb_status TEXT,'
        ' next_season INTEGER, next_episode INTEGER, next_airdate TEXT, prev_season INTEGER, prev_episode INTEGER, prev_airdate TEXT,'
        ' built_at TEXT NOT NULL);'
        'CREATE TABLE IF NOT EXISTS seasons (title_id INTEGER NOT NULL REFERENCES titles(id) ON DELETE CASCADE,'
        ' season INTEGER NOT NULL, episodes INTEGER NOT NULL, PRIMARY KEY (title_id, season));'
        'CREATE TABLE IF NOT EXISTS watch_offers (title_id INTEGER NOT NULL REFERENCES titles(id) ON DELETE CASCADE,'
        ' code TEXT, pkg TEXT, name TEXT, type TEXT, url TEXT);'
        'CREATE TABLE IF NOT EXISTS services (code TEXT PRIMARY KEY, name TEXT, colour TEXT, free INTEGER, sort INTEGER);'
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

# Indian services the app knows (the Mac app's services table, plus Plex). free = free with ads.
SERVICES = [
    # code, name, colour, free, sort
    ('NF', 'Netflix', '#ff5a4e', False, 0),
    ('JH', 'JioHotstar', '#ff5fae', False, 1),
    ('PV', 'Prime Video', '#3fd0d4', False, 2),
    ('SL', 'SonyLIV', '#ffd23f', False, 3),
    ('AP', 'Apple TV', '#ecebf2', False, 4),
    ('Z5', 'ZEE5', '#b0a2ff', False, 5),
    ('LP', 'Lionsgate Play', '#b6e34a', False, 6),
    ('MU', 'MUBI', '#8fe0c4', False, 7),
    ('PX', 'Plex', '#e5a00d', True, 8),
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
    """The master's titles become exactly the titles in the two ~/Desktop/Films files (title, year, ids only: the other
    CSV columns are not used). Titles no longer in the files are removed with their data."""
    c = master()
    want = {}
    for kind, path in (('movie', FILMS_CSV), ('show', SHOWS_CSV)):
        for r in read_csv(path):
            r = {k.lstrip('\ufeff'): v for k, v in r.items()}
            tid = (r.get('tmdb_id') or '').strip()
            if not tid.isdigit():
                continue
            want[stable_id(kind, tid)] = (kind, int(tid), (r.get('imdb_id') or '').strip() or None,
                                         (r.get('title') or '').strip(), num((r.get('year') or '')[:4], int),
                                         num(r.get('rotten_tomatoes'), int),
                                         # films.csv is Prince's hand-compiled horror list: every film in it is horror.
                                         # A show is horror when at least one horror source marks it.
                                         1 if kind == 'movie' or (num(r.get('horror_sources'), int) or 0) > 0 else 0)
    have = {i for (i,) in c.execute('SELECT id FROM titles')}
    gone = have - set(want)
    c.executemany('DELETE FROM titles WHERE id=?', [(i,) for i in gone])
    stamp = now()
    for i, (kind, tid, imdb, title, year, rt, horror) in want.items():
        c.execute('INSERT INTO titles (id, kind, tmdb_id, imdb_id, title, year, rt_score, horror, added_at) VALUES (?,?,?,?,?,?,?,?,?)'
                  ' ON CONFLICT(id) DO UPDATE SET imdb_id=excluded.imdb_id, title=excluded.title, year=excluded.year,'
                  ' rt_score=excluded.rt_score, horror=excluded.horror', (i, kind, tid, imdb, title, year, rt, horror, stamp))
    for s in SERVICES:
        c.execute('INSERT OR IGNORE INTO services (code, name, colour, free, sort) VALUES (?,?,?,?,?)', s)
    for p in PLATFORMS:
        c.execute('INSERT OR IGNORE INTO platforms VALUES (?,?,?,?)', p)
    c.commit()
    print('%d new titles, %d removed; the master now has %d (%s)' % (len(set(want) - have), len(gone), len(want), MASTER))


def titles():
    """(kind, tmdb_id) for every title in the master."""
    return [(k, str(t)) for k, t in master().execute('SELECT kind, tmdb_id FROM titles ORDER BY id')]


def stable_id(kind, tmdb_id):
    """One number per title, stable across rebuilds: TMDB ids are per type, so films are even and shows odd."""
    return int(tmdb_id) * 2 + (1 if kind == 'show' else 0)


# ---------------------------------------------------------------- fetch

def token():
    t = os.environ.get('TMDB_TOKEN') or open(os.path.join(FILMS_DIR, '.tmdb_key')).read().strip()
    return t


def fetch():
    """TMDB details for every master title, fresh (in memory, nothing saved): {(kind, tmdb_id): data}.
    A title TMDB no longer knows maps to {}; a title that could not be fetched is left out, so build keeps its old data."""
    tok = token()
    auth = {'Authorization': 'Bearer ' + tok} if tok.startswith('eyJ') else {}
    key = '' if auth else '&api_key=' + tok
    todo = list(dict.fromkeys(titles()))
    print('fetching %d titles from TMDB' % len(todo))
    out = {}
    lock = threading.Lock()
    done = [0]

    def one(item):
        kind, tid = item
        path = ('movie' if kind == 'movie' else 'tv') + '/' + tid
        url = 'https://api.themoviedb.org/3/%s?append_to_response=videos,watch/providers&language=en-US%s' % (path, key)
        got = None
        for attempt in range(8):
            try:
                req = urllib.request.Request(url, headers=dict(auth, Accept='application/json'))
                got = json.loads(urllib.request.urlopen(req, timeout=20).read())
                break
            except urllib.error.HTTPError as e:
                if e.code == 404:
                    got = {}
                    break
                time.sleep(0.5 * (attempt + 1))
            except Exception:
                time.sleep(0.5 * (attempt + 1))
        with lock:
            if got is not None:
                out[item] = got
            done[0] += 1
            if done[0] % 1000 == 0:
                print('  %d / %d' % (done[0], len(todo)))

    with ThreadPoolExecutor(16) as ex:
        list(ex.map(one, todo))
    print('fetched %d, %d failed (their old data is kept)' % (len(out), len(todo) - len(out)))
    return out


# ---------------------------------------------------------------- build

def num(v, f=float):
    try:
        return f(v) if v not in (None, '') else None
    except ValueError:
        return None


# Shown in names but not counted as a service you can stream on: paid channels inside another service.
def services_of(provider_names):
    """(codes, names): services you can stream on in India, from TMDB's provider names (JustWatch data)."""
    codes, names = [], []
    for name in provider_names:
        if name in names:
            continue
        names.append(name)
        if 'Channel' in name:
            continue
        c = SERVICE_NAMES.get(name)
        if c and c not in codes:
            codes.append(c)
    return codes, names


def trailer(tm, mac_key):
    if mac_key:
        return mac_key
    vids = [v for v in ((tm.get('videos') or {}).get('results') or []) if v.get('site') == 'YouTube']
    vids.sort(key=lambda v: (v.get('type') != 'Trailer', not v.get('official'), v.get('published_at') or ''))
    return vids[0]['key'] if vids else None


def mac_titles():
    """What the Mac app's database knows, by (kind, tmdb_id): scores, trailer, direct watch links, franchise."""
    if not os.path.exists(MAC_DB):
        print('note: %s not found, building without it' % MAC_DB)
        return {}
    d = sqlite3.connect('file:%s?mode=ro' % MAC_DB, uri=True)
    d.row_factory = sqlite3.Row
    fr = {}
    for tid, name in d.execute("SELECT tt.title_id, t.name FROM title_tags tt JOIN tags t ON t.id = tt.tag_id WHERE t.kind = 'franchise'"):
        fr.setdefault(tid, name)
    out = {}
    for r in d.execute('SELECT * FROM titles WHERE tmdb_id IS NOT NULL'):
        out[(r['type'] or r['tmdb_type'] or '', str(r['tmdb_id']))] = dict(r, franchise=fr.get(r['id']))
    return out


def imdb_ratings(ids):
    """IMDb rating and votes from IMDb's public ratings file, for the given ids (streamed, nothing is saved)."""
    import gzip
    out = {}
    try:
        with urllib.request.urlopen(IMDB_RATINGS, timeout=120) as resp, gzip.open(resp, 'rt', encoding='utf-8') as f:
            next(f)
            for line in f:
                tconst, rating, votes = line.rstrip('\n').split('\t')
                if tconst in ids:
                    out[tconst] = (float(rating), int(votes))
    except Exception as e:
        print('note: IMDb ratings not loaded (%s)' % e)
    return out


def joined(xs):
    return ' | '.join(x for x in xs if x) or None


def build():
    c = master()
    rows = c.execute('SELECT id, kind, tmdb_id, imdb_id, rt_score, horror FROM titles ORDER BY id').fetchall()
    mac = mac_titles()
    imdb = imdb_ratings({r[3] for r in rows if r[3]})
    tmdb = fetch()
    stamp = now()
    built = 0
    for tid_, kind, tmdb_id, imdb_id, csv_rt, csv_horror in rows:
        tm = tmdb.get((kind, str(tmdb_id)))
        if tm is None and c.execute('SELECT 1 FROM title_data WHERE id=?', (tid_,)).fetchone():
            continue   # TMDB did not answer this time: keep what the master has
        tm = tm or {}
        built += 1
        for table, col in (('title_data', 'id'), ('seasons', 'title_id'), ('watch_offers', 'title_id')):
            c.execute('DELETE FROM %s WHERE %s=?' % (table, col), (tid_,))
        m = mac.get((kind, str(tmdb_id))) or {}
        prov = ((tm.get('watch/providers') or {}).get('results') or {}).get('IN') or {}
        stream = [p['provider_name'] for t in ('flatrate', 'ads', 'free') for p in prov.get(t) or []]
        rent = list(dict.fromkeys(p['provider_name'] for t in ('rent', 'buy') for p in prov.get(t) or []))
        codes, names = services_of(stream)
        try:
            offers = json.loads(m.get('watch') or '[]') or []
        except ValueError:
            offers = []
        status = 'streaming' if stream else 'rent' if rent else 'not_yet' if tm else 'unknown'
        genres = [g['name'] for g in tm.get('genres') or []]
        coll = (tm.get('belongs_to_collection') or {}).get('name')
        franchise = m.get('franchise') or (coll.replace(' Collection', '') if coll else None)
        poster, backdrop = tm.get('poster_path'), tm.get('backdrop_path')
        networks = [n['name'] for n in tm.get('networks') or []]
        ir = imdb.get(imdb_id) if imdb_id else None
        nxt, prv = tm.get('next_episode_to_air') or {}, tm.get('last_episode_to_air') or {}
        c.execute('INSERT INTO title_data VALUES (%s)' % ','.join('?' * 33), (
            tid_,
            tm.get('original_title') or tm.get('original_name'),
            tm.get('runtime') or ((tm.get('episode_run_time') or [None])[0]),
            joined(x.get('iso_3166_1') for x in tm.get('production_countries') or []) or joined(tm.get('origin_country') or []),
            tm.get('original_language'),
            m.get('imdb_rating') or (ir[0] if ir else None), m.get('imdb_votes') or (ir[1] if ir else None),
            tm.get('vote_average') or None, tm.get('vote_count') or None,
            csv_rt if csv_rt is not None else m.get('rt_score'),
            csv_horror,
            joined(genres), franchise, tm.get('overview') or None, tm.get('tagline') or None,
            IMG + 'w342' + poster if poster else None, IMG + 'w1280' + backdrop if backdrop else None,
            trailer(tm, m.get('trailer_key')),
            status, joined(codes), joined(names), joined(rent), prov.get('link'),
            joined(networks),
            next((NETWORK_PLATFORM[n] for n in networks if n in NETWORK_PLATFORM), None) if kind == 'show' else None,
            tm.get('status'),
            nxt.get('season_number'), nxt.get('episode_number'), nxt.get('air_date'),
            prv.get('season_number'), prv.get('episode_number'), prv.get('air_date'),
            stamp))
        for s in tm.get('seasons') or []:
            if (s.get('season_number') or 0) > 0:
                c.execute('INSERT OR REPLACE INTO seasons VALUES (?,?,?)', (tid_, s['season_number'], s.get('episode_count') or 0))
        for o in offers:
            c.execute('INSERT INTO watch_offers VALUES (?,?,?,?,?,?)',
                      (tid_, o.get('code'), o.get('pkg'), o.get('name'), o.get('type'), o.get('url')))
    c.execute("INSERT OR REPLACE INTO meta VALUES ('built_at', ?)", (stamp,))
    c.commit()
    print('%d of %d titles built into %s (%d matched in the Mac app, %d IMDb scores from IMDb)' % (
        built, len(rows), MASTER, sum(1 for r in rows if (r[1], str(r[2])) in mac), len(imdb)))


def catalogue():
    """What publish pushes, assembled from the master's tables in the shape of Supabase's titles table."""
    c = master()
    c.row_factory = sqlite3.Row
    seasons, offers = {}, {}
    for r in c.execute('SELECT * FROM seasons ORDER BY title_id, season'):
        seasons.setdefault(r['title_id'], []).append(r['episodes'])
    for r in c.execute('SELECT * FROM watch_offers'):
        offers.setdefault(r['title_id'], []).append({k: r[k] for k in ('code', 'pkg', 'name', 'type', 'url')})
    lst = lambda v: [x for x in (v or '').split(' | ') if x]
    ep = lambda s, n, a: {'season': s, 'number': n, 'airdate': a} if s is not None else None
    out = []
    for r in c.execute('SELECT titles.kind, titles.tmdb_id, titles.imdb_id, titles.title, titles.year, title_data.* FROM titles JOIN title_data ON title_data.id = titles.id ORDER BY titles.id'):
        out.append({
            'id': r['id'], 'kind': r['kind'], 'title': r['title'], 'year': r['year'], 'original_title': r['original_title'],
            'tmdb_id': r['tmdb_id'], 'imdb_id': r['imdb_id'], 'runtime': r['runtime'], 'countries': lst(r['countries']),
            'language': r['language'], 'imdb_rating': r['imdb_rating'], 'imdb_votes': r['imdb_votes'],
            'tmdb_rating': r['tmdb_rating'], 'tmdb_votes': r['tmdb_votes'], 'rt_score': r['rt_score'],
            'horror': bool(r['horror']), 'genres': lst(r['genres']), 'franchise': r['franchise'],
            'overview': r['overview'], 'tagline': r['tagline'], 'poster': r['poster'], 'backdrop': r['backdrop'],
            'trailer_key': r['trailer_key'], 'india_status': r['india_status'], 'services': lst(r['services']),
            'service_names': lst(r['service_names']), 'rent_buy': lst(r['rent_buy']), 'watch': offers.get(r['id'], []),
            'watch_page': r['watch_page'], 'seasons': seasons.get(r['id'], []), 'networks': lst(r['networks']),
            'platform': r['platform'], 'tmdb_status': r['tmdb_status'],
            'next_ep': ep(r['next_season'], r['next_episode'], r['next_airdate']),
            'prev_ep': ep(r['prev_season'], r['prev_episode'], r['prev_airdate']),
        })
    return {
        'built_at': c.execute("SELECT value FROM meta WHERE key='built_at'").fetchone()[0],
        'services': [dict(r, free=bool(r['free'])) for r in c.execute('SELECT code, name, colour, free, sort FROM services ORDER BY sort')],
        'platforms': [dict(r) for r in c.execute('SELECT * FROM platforms ORDER BY sort')],
        'titles': out,
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
    """Stage the master catalogue in the desktop mirror, then send its pending changes to Supabase."""
    import supabase_mirror
    if not os.path.isfile(MASTER) or not os.path.isfile(MIRROR):
        raise FileNotFoundError('Medusa.db or supabase.db is missing; nothing was published')
    data = catalogue()
    with sqlite3.connect(MIRROR, timeout=30) as db:
        staged = supabase_mirror.stage_master(db, data)
    counts = supabase_mirror.sync_mirror(MIRROR)
    print('catalogue: %d rows staged; %d titles in Supabase mirror' % (staged, counts.get('titles', 0)))


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
    """Refresh the desktop mirror from Supabase without pushing old master profile data."""
    import supabase_mirror
    if not os.path.isfile(MIRROR):
        raise FileNotFoundError('supabase.db is missing; no profiles were changed')
    counts = supabase_mirror.sync_mirror(MIRROR, profiles_only=True)
    print('mirror: %d profiles and %d profile title rows' %
          (counts.get('profiles', 0), counts.get('profile_titles', 0)))


def canonicalize_master_profile(c, old_id, new_id):
    """Rename a legacy master profile without deleting its stored history."""
    with c:
        if not c.execute('SELECT 1 FROM profiles WHERE id=?', (old_id,)).fetchone():
            raise ValueError('Legacy master profile is missing')
        if c.execute('SELECT 1 FROM profiles WHERE id=?', (new_id,)).fetchone():
            raise ValueError('Destination master profile already exists')
        c.execute('UPDATE profiles SET id=? WHERE id=?', (new_id, old_id))
        c.execute('UPDATE profile_titles SET profile_id=? WHERE profile_id=?', (new_id, old_id))
        c.execute('UPDATE applied_events SET profile_id=? WHERE profile_id=?', (new_id, old_id))


if __name__ == '__main__':
    cmd = sys.argv[1] if len(sys.argv) > 1 else 'build'
    def upkeep():
        seed(); build(); publish()
    {'seed': seed, 'build': build, 'publish': publish, 'sync': sync, 'upkeep': upkeep}[cmd]()
