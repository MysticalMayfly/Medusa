#!/usr/bin/env python3
"""Export the Mac Medusa library (~/Desktop/Medusa/data/medusa.db) to one JSON file the TV app imports.

    python3 scripts/export_library.py [path/to/medusa.db] [-o dist/medusa-library.json] [--serve [MINUTES]]

--serve: after writing, offer the file on the home network for a few minutes (default 10), announced over Bonjour as
_medusa._tcp, so the TV's Settings > "Import library from Mac" finds it with no address to type. Ctrl-C stops it.

Read-only: opens the database immutable, never writes to it. Standard library only.
The output holds Prince's collection and watch history: it goes to dist/ (git-ignored) and must never be committed.
It mirrors the Mac app's /api/bootstrap (app/server.py + app/db.py list_titles), plus the detail fields
(overview, tagline, providers, networks) and the TMDB image URLs instead of the image blobs.
"""
import argparse
import json
import os
import sqlite3
import subprocess
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from datetime import datetime

DEFAULT_DB = os.path.expanduser('~/Desktop/Medusa/data/medusa.db')
TAG_KINDS = ('franchise', 'genre', 'studio')

# Same as the Mac app's app/db.py OTT_CODES: TMDB India provider names -> service codes.
OTT_CODES = {'Netflix': 'NF', 'Netflix Kids': 'NF', 'JioHotstar': 'JH', 'Amazon Prime Video': 'PV',
             'Amazon Prime Video with Ads': 'PV', 'Sony Liv': 'SL', 'Apple TV': 'AP', 'Zee5': 'Z5',
             'Lionsgate Play': 'LP', 'MUBI': 'MU'}

JSON_COLS = ('tmdb_genres', 'networks', 'providers', 'next_ep', 'prev_ep', 'collection', 'india', 'watch')


def ott_codes(providers):
    out = []
    for k in ('flatrate', 'free', 'ads'):
        for name in (providers or {}).get(k) or []:
            code = OTT_CODES.get(name)
            if code and code not in out:
                out.append(code)
    return out


def score(r):
    """app/db.py score(): the hidden 0-100 sort score. Never shown."""
    votes = r['imdb_votes'] or 0
    parts = [(r['rt_score'], 4),
             (r['imdb_rating'] and r['imdb_rating'] * 10, 4 * min(1, votes / 500) if votes else 2),
             (r['tmdb_rating'] and r['tmdb_rating'] * 10, 1),
             (r['trakt_rating'] and r['trakt_rating'] * 10, 1)]
    parts = [(v, w) for v, w in parts if v is not None and w > 0]
    if not parts:
        return None
    parts.append((60, 1))
    total = sum(w for _, w in parts)
    return round(sum(v * w for v, w in parts) / total, 1) if total else None


def available(watch, india, providers):
    """app/db.py available(): streamable, free, rentable or buyable in India by any source."""
    if watch:
        return True
    if india and ((india.get('status') == 'streaming' and india.get('subs')) or india.get('rent')):
        return True
    return any((providers or {}).get(k) for k in ('flatrate', 'free', 'ads', 'rent', 'buy'))


def image_url(url, size):
    """TMDB image URL at another size (w500 -> w342 for posters, w1280 for backdrops)."""
    if not url:
        return None
    marker = '/t/p/'
    i = url.find(marker)
    if i < 0:
        return url
    rest = url[i + len(marker):]
    j = rest.find('/')
    return url[:i + len(marker)] + size + rest[j:] if j >= 0 else url


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('db', nargs='?', default=DEFAULT_DB)
    ap.add_argument('-o', '--out', default=os.path.join(os.path.dirname(__file__), '..', 'dist', 'medusa-library.json'))
    ap.add_argument('--serve', nargs='?', const=10, type=float, metavar='MINUTES')
    a = ap.parse_args()

    c = sqlite3.connect('file:%s?immutable=1' % os.path.abspath(a.db), uri=True)
    c.row_factory = sqlite3.Row

    images = {r['id']: r['source_url'] for r in c.execute('SELECT id, source_url FROM images')}
    tags = {}
    for r in c.execute('SELECT tt.title_id, t.kind, t.name FROM title_tags tt JOIN tags t ON t.id = tt.tag_id ORDER BY t.name'):
        tags.setdefault(r['title_id'], {k: [] for k in TAG_KINDS})[r['kind']].append(r['name'])

    titles = []
    for r in c.execute('SELECT * FROM titles ORDER BY created_at DESC, id'):
        d = {k: (json.loads(r[k]) if r[k] else None) for k in JSON_COLS}
        eps = json.loads(r['episodes'] or '[]')
        t = {
            'id': r['id'], 'title': r['title'], 'type': r['type'], 'platform': r['platform'], 'label': r['label'],
            'years': r['years'], 'episodes': eps, 'notes': r['notes'],
            'watched': bool(r['watched']), 'watched_at': r['watched_at'], 'interested': bool(r['interested']),
            'placeholder': bool(r['placeholder']), 'created_at': r['created_at'],
            'airing_date': r['airing_date'], 'airing_note': r['airing_note'],
            'tmdb_id': r['tmdb_id'], 'tmdb_type': r['tmdb_type'], 'imdb_id': r['imdb_id'], 'tvmaze_id': r['tvmaze_id'],
            'trakt_slug': r['trakt_slug'], 'tmdb_title': r['tmdb_title'], 'overview': r['overview'], 'tagline': r['tagline'],
            'runtime': r['runtime'], 'tmdb_rating': r['tmdb_rating'], 'trakt_rating': r['trakt_rating'],
            'trakt_votes': r['trakt_votes'], 'imdb_rating': r['imdb_rating'], 'imdb_votes': r['imdb_votes'],
            'rt_score': r['rt_score'], 'tmdb_status': r['tmdb_status'],
            'prog_season': r['prog_season'], 'prog_episode': r['prog_episode'], 'prog_at': r['prog_at'],
            'trailer_key': r['trailer_key'], 'trailer_ok': r['trailer_ok'],
            'poster': image_url(images.get(r['poster_id']), 'w342'),
            'backdrop': image_url(images.get(r['backdrop_id']), 'w1280'),
            'tags': tags.get(r['id'], {k: [] for k in TAG_KINDS}),
            'ott': ott_codes(d['providers']),
            'score': score(r),
            'avail': available(d['watch'], d['india'], d['providers']),
            'source': r['source'], 'match_status': r['match_status'],
        }
        t.update(d)
        titles.append(t)

    out = {
        'format': 1,
        'exported_at': datetime.now().strftime('%Y-%m-%dT%H:%M:%S'),
        'today': datetime.now().strftime('%Y-%m-%d'),
        'platforms': [dict(r) for r in c.execute('SELECT * FROM platforms ORDER BY sort, name')],
        'services': [dict(r) for r in c.execute('SELECT * FROM services ORDER BY sort, name')],
        'tags': [dict(r) for r in c.execute(
            'SELECT t.id, t.kind, t.name, t.colour, COUNT(tt.title_id) n FROM tags t LEFT JOIN title_tags tt ON tt.tag_id=t.id '
            'GROUP BY t.id ORDER BY t.kind, t.name')],
        'lists': [{'id': r['id'], 'name': r['name'], 'kind': r['kind'], 'year': r['year'], 'genre': r['genre']}
                  for r in c.execute('SELECT * FROM lists ORDER BY year, name')],
        'list_items': [list(r) for r in c.execute('SELECT list_id, title_id, position FROM list_items ORDER BY list_id, position')],
        'titles': titles,
    }
    os.makedirs(os.path.dirname(os.path.abspath(a.out)), exist_ok=True)
    with open(a.out, 'w', encoding='utf-8') as f:
        json.dump(out, f, ensure_ascii=False, separators=(',', ':'))
    print('%d titles -> %s (%d KB)' % (len(titles), os.path.abspath(a.out), os.path.getsize(a.out) // 1024))
    if a.serve:
        serve(os.path.abspath(a.out), a.serve)


PORT = 4322


def serve(path, minutes):
    """Offer only this one file at /medusa-library.json until the TV has it, or the time runs out."""
    body = open(path, 'rb').read()

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path != '/medusa-library.json':
                self.send_error(404)
                return
            self.send_response(200)
            self.send_header('Content-Type', 'application/json; charset=utf-8')
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)
            print('Sent the library to %s' % self.client_address[0])

        def log_message(self, *args):
            pass

    httpd = ThreadingHTTPServer(('0.0.0.0', PORT), Handler)
    # Bonjour announcement with macOS's own dns-sd, so the TV finds the Mac without an address.
    bonjour = subprocess.Popen(['dns-sd', '-R', 'Medusa library', '_medusa._tcp', 'local', str(PORT)],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    timer = threading.Timer(minutes * 60, httpd.shutdown)
    timer.start()
    print('Serving for %g minutes. On the TV: Settings > Import library from Mac. Ctrl-C to stop.' % minutes)
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        timer.cancel()
        bonjour.terminate()
        httpd.server_close()


if __name__ == '__main__':
    sys.exit(main())
