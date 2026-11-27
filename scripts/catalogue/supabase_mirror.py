#!/usr/bin/env python3
"""Local Medusa data mirror. No auth secrets are stored in this database.

This module does not start automatically. Catalogue changes are tracked locally;
profile data is cloud-owned and read-only here.
"""
import json
import sqlite3

CATALOGUE_KEYS = {'titles': 'id', 'services': 'code', 'platforms': 'slug'}
CLOUD_KEYS = {'profiles': 'id', 'profile_titles': None, 'profile_events': 'id',
              'account_profile_links': 'user_id', 'app_titles': 'id', 'users': 'id'}


def quote(name):
    return '"' + name.replace('"', '""') + '"'


def initialize(db, schemas):
    db.executescript('''
      CREATE TABLE IF NOT EXISTS _sync_control (id INTEGER PRIMARY KEY CHECK(id=1), importing INTEGER NOT NULL);
      INSERT OR IGNORE INTO _sync_control VALUES (1,0);
      CREATE TABLE IF NOT EXISTS _local_changes (
        table_name TEXT NOT NULL, row_key TEXT NOT NULL, operation TEXT NOT NULL,
        revision INTEGER NOT NULL, PRIMARY KEY(table_name,row_key));
    ''')
    allowed = set(CATALOGUE_KEYS) | set(CLOUD_KEYS)
    for table, columns in schemas.items():
        if table not in allowed:
            raise ValueError('Unexpected table: ' + table)
        key = CATALOGUE_KEYS.get(table) or CLOUD_KEYS.get(table)
        column_sql = ', '.join(quote(c) + ' ' + ('INTEGER' if typ in ('integer', 'boolean') else 'REAL' if typ == 'number' else 'TEXT') for c, typ in columns.items())
        if table == 'profile_titles':
            pk = ', PRIMARY KEY (profile_id,title_id)'
        elif key:
            pk = ', PRIMARY KEY (' + quote(key) + ')'
        else:
            pk = ''
        db.execute('CREATE TABLE IF NOT EXISTS ' + quote(table) + ' (' + column_sql + pk + ')')
        existing = {row[1] for row in db.execute('PRAGMA table_info(' + quote(table) + ')')}
        for col, typ in columns.items():
            if col not in existing:
                db.execute('ALTER TABLE ' + quote(table) + ' ADD COLUMN ' + quote(col) + ' ' + ('INTEGER' if typ in ('integer', 'boolean') else 'REAL' if typ == 'number' else 'TEXT'))
        for action in ('INSERT', 'UPDATE', 'DELETE'):
            trig = quote('_mirror_' + table + '_' + action.lower())
            ref = 'OLD' if action == 'DELETE' else 'NEW'
            if table in CATALOGUE_KEYS:
                op = 'delete' if action == 'DELETE' else 'upsert'
                db.execute('CREATE TRIGGER IF NOT EXISTS ' + trig + ' AFTER ' + action + ' ON ' + quote(table) +
                           ' WHEN (SELECT importing FROM _sync_control WHERE id=1)=0 BEGIN '
                           "INSERT INTO _local_changes VALUES ('" + table + "',CAST(" + ref + '.' + quote(key) + " AS TEXT),'" + op + "',1) "
                           'ON CONFLICT(table_name,row_key) DO UPDATE SET operation=excluded.operation, revision=revision+1; END')
            else:
                db.execute('CREATE TRIGGER IF NOT EXISTS ' + trig + ' BEFORE ' + action + ' ON ' + quote(table) +
                           " WHEN (SELECT importing FROM _sync_control WHERE id=1)=0 BEGIN SELECT RAISE(ABORT,'Cloud-owned table is read-only'); END")
        if table in CATALOGUE_KEYS:
            db.execute('CREATE TRIGGER IF NOT EXISTS ' + quote('_mirror_' + table + '_key') + ' BEFORE UPDATE OF ' + quote(key) +
                       ' ON ' + quote(table) + ' WHEN OLD.' + quote(key) + ' IS NOT NEW.' + quote(key) +
                       " BEGIN SELECT RAISE(ABORT,'Catalogue keys cannot be changed'); END")
    db.commit()


def sql_value(value):
    if isinstance(value, (dict, list)):
        return json.dumps(value, ensure_ascii=False, separators=(',', ':'))
    return value


def apply_snapshot(db, snapshot):
    with db:
        db.execute('UPDATE _sync_control SET importing=1 WHERE id=1')
        for table, rows in snapshot.items():
            if table not in CATALOGUE_KEYS and table not in CLOUD_KEYS:
                raise ValueError('Unexpected table: ' + table)
            cols = [r[1] for r in db.execute('PRAGMA table_info(' + quote(table) + ')')]
            if not cols:
                raise ValueError('Uninitialized table: ' + table)
            key = CATALOGUE_KEYS.get(table)
            if key:
                # Preserve local edits (including pending deletions) while refreshing cloud rows.
                db.execute('DELETE FROM ' + quote(table) + ' WHERE CAST(' + quote(key) + ' AS TEXT) NOT IN '
                           '(SELECT row_key FROM _local_changes WHERE table_name=?)', (table,))
                dirty = {r[0] for r in db.execute('SELECT row_key FROM _local_changes WHERE table_name=?', (table,))}
                rows = [r for r in rows if str(r[key]) not in dirty]
            else:
                db.execute('DELETE FROM ' + quote(table))
            placeholders = ','.join('?' for _ in cols)
            query = 'INSERT INTO ' + quote(table) + ' (' + ','.join(map(quote, cols)) + ') VALUES (' + placeholders + ')'
            db.executemany(query, ([sql_value(row.get(c)) for c in cols] for row in rows))
        db.execute('UPDATE _sync_control SET importing=0 WHERE id=1')


def pending_changes(db):
    return list(db.execute('SELECT table_name,row_key,operation,revision FROM _local_changes ORDER BY table_name,row_key'))


def acknowledge(db, change):
    with db:
        db.execute('DELETE FROM _local_changes WHERE table_name=? AND row_key=? AND operation=? AND revision=?', change)


def stage_master(db, source):
    """Stage changed catalogue rows only. A missing row is never treated as a deletion."""
    import math
    if not source.get('titles'):
        raise ValueError('Refusing to import an empty master catalogue')
    count = 0
    with db:
        for table in ('services', 'platforms', 'titles'):
            if table not in source:
                continue
            key = CATALOGUE_KEYS[table]
            available = {r[1] for r in db.execute('PRAGMA table_info(' + quote(table) + ')')}
            if key not in available:
                raise ValueError('Mirror is missing table ' + table)
            for item in source[table]:
                row = dict(item)
                if table == 'titles' and source.get('built_at'):
                    row['published_at'] = source['built_at']
                if not set(row).issubset(available) or key not in row:
                    raise ValueError('Master row does not match mirror schema for ' + table)
                columns = list(row)
                old = db.execute('SELECT ' + ','.join(map(quote, columns)) + ' FROM ' + quote(table) +
                                 ' WHERE ' + quote(key) + '=?', (row[key],)).fetchone()
                def equal(a, b):
                    if isinstance(b, (list, dict)):
                        try: return json.loads(a) == b
                        except (ValueError, TypeError): return False
                    if isinstance(a, float) and isinstance(b, float):
                        return math.isclose(a, b, rel_tol=1e-6, abs_tol=1e-6)
                    return a == b
                changed = old is None or any(name != 'published_at' and not equal(value, row[name])
                                              for name, value in zip(columns, old))
                if not changed:
                    continue
                setters = ','.join(quote(name) + '=excluded.' + quote(name) for name in columns if name != key)
                query = ('INSERT INTO ' + quote(table) + ' (' + ','.join(map(quote, columns)) + ') VALUES (' +
                         ','.join('?' for _ in columns) + ') ON CONFLICT(' + quote(key) + ') DO UPDATE SET ' + setters)
                db.execute(query, [sql_value(row[name]) for name in columns])
                count += 1
    return count


def publish_pending(db, schemas, send):
    from urllib.parse import quote as urlquote
    changes = pending_changes(db)
    for table in sorted({change[0] for change in changes}):
        if table not in CATALOGUE_KEYS or table not in schemas:
            raise ValueError('Unexpected local change: ' + table)
        key = CATALOGUE_KEYS[table]
        entries = [change for change in changes if change[0] == table]
        for change in (entry for entry in entries if entry[2] == 'delete'):
            send('DELETE', table + '?' + key + '=eq.' + urlquote(change[1], safe=''), None, None)
            acknowledge(db, change)
        upserts = [entry for entry in entries if entry[2] == 'upsert']
        if len(upserts) + sum(entry[2] == 'delete' for entry in entries) != len(entries):
            raise ValueError('Unexpected local operation')
        columns = list(schemas[table])
        for offset in range(0, len(upserts), 200):
            batch = upserts[offset:offset + 200]
            payload = []
            for change in batch:
                row = db.execute('SELECT ' + ','.join(map(quote, columns)) + ' FROM ' + quote(table) +
                                 ' WHERE CAST(' + quote(key) + ' AS TEXT)=?', (change[1],)).fetchone()
                if row is None:
                    raise ValueError('Pending catalogue row is missing')
                data = dict(zip(columns, row))
                for name, typ in schemas[table].items():
                    value = data[name]
                    if value is not None and typ in ('array', 'object', 'json'):
                        data[name] = json.loads(value)
                    elif value is not None and typ == 'boolean':
                        data[name] = bool(value)
                payload.append(data)
            send('POST', table + '?on_conflict=' + key, payload, 'resolution=merge-duplicates,return=minimal')
            for change in batch:
                acknowledge(db, change)


def download_snapshot(rest_get, get_users, tables=None):
    if tables is None:
        tables = ('services', 'platforms', 'titles', 'app_titles', 'profiles', 'profile_titles',
                  'profile_events', 'account_profile_links', 'users')
    order = {'services': 'code', 'platforms': 'slug', 'titles': 'id', 'app_titles': 'id',
             'profiles': 'id', 'profile_titles': 'profile_id,title_id',
             'profile_events': 'id', 'account_profile_links': 'user_id'}
    result = {}
    for table in tables:
        if table == 'users':
            result['users'] = get_users()
            continue
        rows = []
        while True:
            page = rest_get('%s?select=*&order=%s&limit=1000&offset=%d' % (table, order[table], len(rows)))
            if not isinstance(page, list):
                raise ValueError('Unexpected response for ' + table)
            rows.extend(page)
            if len(page) < 1000:
                break
        result[table] = rows
    return result


def basic_users(users):
    result = []
    for user in users:
        meta = user.get('user_metadata') or {}
        result.append({'id': user['id'], 'email': user.get('email'),
                       'display_name': meta.get('full_name') or meta.get('name'),
                       'avatar_url': meta.get('avatar_url') or meta.get('picture'),
                       'created_at': user.get('created_at'), 'last_sign_in_at': user.get('last_sign_in_at')})
    return result


def schema_from_openapi(document):
    schemas = {}
    for table in ('services', 'platforms', 'titles', 'app_titles', 'profiles', 'profile_titles',
                  'profile_events', 'account_profile_links'):
        fields = document.get('definitions', {}).get(table, {}).get('properties')
        if not fields:
            raise ValueError('No API schema for ' + table)
        schemas[table] = {name: ('json' if spec.get('format') in ('json', 'jsonb') else spec.get('type', 'string'))
                          for name, spec in fields.items()}
    schemas['users'] = {name: 'string' for name in ('id', 'email', 'display_name', 'avatar_url', 'created_at', 'last_sign_in_at')}
    return schemas


def fetch_api_schema(env):
    import urllib.request
    key = env['SUPABASE_SERVICE_ROLE_KEY']
    req = urllib.request.Request(env['SUPABASE_URL'].rstrip('/') + '/rest/v1/', headers={
        'apikey': key, 'Authorization': 'Bearer ' + key, 'Accept': 'application/openapi+json'})
    with urllib.request.urlopen(req, timeout=30) as response:
        return json.load(response)


def fetch_basic_users(env):
    import urllib.request
    key = env['SUPABASE_SERVICE_ROLE_KEY']
    all_users = []
    for page in range(1, 1001):
        url = env['SUPABASE_URL'].rstrip('/') + '/auth/v1/admin/users?page=%d&per_page=100' % page
        req = urllib.request.Request(url, headers={'apikey': key, 'Authorization': 'Bearer ' + key})
        with urllib.request.urlopen(req, timeout=30) as response:
            data = json.load(response)
        users = data.get('users')
        if not isinstance(users, list):
            raise ValueError('Unexpected authentication user response')
        all_users.extend(basic_users(users))
        if len(users) < 100:
            return all_users
    raise ValueError('Too many users to mirror safely')


def create_initial_mirror(destination):
    import os
    import pathlib
    import tempfile
    import build_catalogue as catalogue
    destination = pathlib.Path(destination).expanduser()
    if destination.exists():
        raise FileExistsError('Refusing to replace existing mirror: ' + str(destination))
    env = catalogue.supabase_env()
    schemas = schema_from_openapi(fetch_api_schema(env))
    snapshot = download_snapshot(lambda path: catalogue.rest('GET', path), lambda: fetch_basic_users(env))
    fd, tmp = tempfile.mkstemp(prefix='.supabase-initial-', suffix='.db', dir=str(destination.parent))
    os.close(fd)
    try:
        os.chmod(tmp, 0o600)
        db = sqlite3.connect(tmp)
        try:
            initialize(db, schemas)
            apply_snapshot(db, snapshot)
            if db.execute('PRAGMA integrity_check').fetchone()[0] != 'ok':
                raise ValueError('Mirror failed integrity check')
        finally:
            db.close()
        # A hard link fails if destination was created while the snapshot was being downloaded.
        os.link(tmp, destination)
    finally:
        os.unlink(tmp)
    return {table: len(rows) for table, rows in snapshot.items()}


def sync_mirror(destination, profiles_only=False):
    import fcntl
    import os
    import pathlib
    import build_catalogue as catalogue
    destination = pathlib.Path(destination).expanduser()
    if not destination.is_file():
        raise FileNotFoundError('Mirror does not exist: ' + str(destination))
    # Avoid overlapping runs, which could acknowledge a later local edit too early.
    lock_path = str(destination) + ('.profiles.sync.lock' if profiles_only else '.catalogue.sync.lock')
    fd = os.open(lock_path, os.O_CREAT | os.O_RDWR, 0o600)
    try:
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        env = catalogue.supabase_env()
        schemas = schema_from_openapi(fetch_api_schema(env))
        db = sqlite3.connect(str(destination), timeout=30)
        try:
            initialize(db, schemas)
            if not profiles_only:
                publish_pending(db, schemas, catalogue.rest)
            tables = ('profiles', 'profile_titles', 'account_profile_links', 'users') if profiles_only else (
                'services', 'platforms', 'titles', 'app_titles', 'profile_events')
            snapshot = download_snapshot(lambda path: catalogue.rest('GET', path), lambda: fetch_basic_users(env), tables)
            apply_snapshot(db, snapshot)
            if db.execute('PRAGMA quick_check').fetchone()[0] != 'ok':
                raise ValueError('Mirror integrity check failed')
            return {table: len(rows) for table, rows in snapshot.items()}
        finally:
            db.close()
    finally:
        os.close(fd)


if __name__ == '__main__':
    import sys
    if len(sys.argv) != 3 or sys.argv[1] not in ('--initialize', '--sync', '--sync-profiles', '--import-master'):
        raise SystemExit('Usage: supabase_mirror.py (--initialize|--sync|--sync-profiles|--import-master) PATH')
    if sys.argv[1] == '--import-master':
        import os
        import build_catalogue as catalogue
        if not os.path.isfile(catalogue.MASTER) or not os.path.isfile(sys.argv[2]):
            raise SystemExit('Master or mirror is missing; nothing was changed')
        with sqlite3.connect(sys.argv[2], timeout=30) as db:
            print('Master catalogue rows staged:', stage_master(db, catalogue.catalogue()))
    else:
        counts = create_initial_mirror(sys.argv[2]) if sys.argv[1] == '--initialize' else sync_mirror(sys.argv[2], profiles_only=sys.argv[1] == '--sync-profiles')
        print('Mirror updated:', ', '.join('%s=%d' % item for item in sorted(counts.items())))
