import importlib.util
import pathlib
import sqlite3
import tempfile
import unittest

path = pathlib.Path(__file__).resolve().parents[1] / 'supabase_mirror.py'
spec = importlib.util.spec_from_file_location('supabase_mirror', path)
mirror = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mirror)


class MirrorTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.db = sqlite3.connect(str(pathlib.Path(self.tmp.name) / 'test.db'))
        self.addCleanup(self.db.close)
        mirror.initialize(self.db, {'titles': {'id': 'integer', 'title': 'text'},
                                    'profiles': {'id': 'text', 'name': 'text'}})
        mirror.apply_snapshot(self.db, {'titles': [{'id': 1, 'title': 'Original'}],
                                        'profiles': [{'id': 'p', 'name': 'Person'}]})

    def test_cloud_refresh_does_not_mark_rows_as_local_edits(self):
        self.assertEqual([], mirror.pending_changes(self.db))

    def test_dirty_catalogue_change_survives_cloud_refresh(self):
        self.db.execute("UPDATE titles SET title='Local edit' WHERE id=1")
        self.db.commit()
        mirror.apply_snapshot(self.db, {'titles': [{'id': 1, 'title': 'Old cloud'}],
                                        'profiles': [{'id': 'p', 'name': 'Updated'}]})
        self.assertEqual('Local edit', self.db.execute('SELECT title FROM titles').fetchone()[0])
        self.assertEqual('Updated', self.db.execute('SELECT name FROM profiles').fetchone()[0])
        self.assertEqual(1, len(mirror.pending_changes(self.db)))

    def test_older_acknowledgment_does_not_erase_newer_edit(self):
        self.db.execute("UPDATE titles SET title='First' WHERE id=1"); self.db.commit()
        pending = mirror.pending_changes(self.db)
        self.db.execute("UPDATE titles SET title='Second' WHERE id=1"); self.db.commit()
        mirror.acknowledge(self.db, pending[0])
        self.assertEqual(1, len(mirror.pending_changes(self.db)))

    def test_local_profile_edits_are_rejected(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("UPDATE profiles SET name='Changed' WHERE id='p'")

    def test_pending_deletion_is_not_restored_by_cloud_refresh(self):
        self.db.execute('DELETE FROM titles WHERE id=1'); self.db.commit()
        mirror.apply_snapshot(self.db, {'titles': [{'id': 1, 'title': 'Old cloud'}]})
        self.assertEqual(0, self.db.execute('SELECT count(*) FROM titles').fetchone()[0])
        self.assertEqual('delete', mirror.pending_changes(self.db)[0][2])

    def test_import_from_master_stages_only_actual_catalogue_changes(self):
        source = {'titles': [{'id': 1, 'title': 'Original'}]}
        self.assertEqual(0, mirror.stage_master(self.db, source))
        self.assertEqual([], mirror.pending_changes(self.db))
        source['titles'][0]['title'] = 'Revised'
        self.assertEqual(1, mirror.stage_master(self.db, source))
        self.assertEqual('Revised', self.db.execute('SELECT title FROM titles').fetchone()[0])
        self.assertEqual(1, len(mirror.pending_changes(self.db)))

    def test_empty_master_does_not_delete_the_cloud_catalogue(self):
        with self.assertRaises(ValueError): mirror.stage_master(self.db, {'titles': []})
        self.assertEqual(1, self.db.execute('SELECT count(*) FROM titles').fetchone()[0])


class BasicUsersTest(unittest.TestCase):
    def test_excludes_authentication_tokens_and_private_metadata(self):
        users = [{'id': '123', 'email': 'person@example.test', 'encrypted_password': 'secret',
                  'user_metadata': {'name': 'Person', 'access_token': 'secret'}}]
        result = mirror.basic_users(users)
        self.assertEqual('Person', result[0]['display_name'])
        self.assertNotIn('secret', repr(result))

    def test_profile_refresh_does_not_download_the_catalogue(self):
        paths=[]
        result=mirror.download_snapshot(lambda path: paths.append(path) or [],lambda: [],
                                        tables=('profiles','profile_titles','users'))
        self.assertEqual({'profiles','profile_titles','users'},set(result))
        self.assertFalse(any(path.startswith('titles?') for path in paths))

class PublishTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.addCleanup(self.db.close)
        self.schema = {'titles': {'id': 'integer', 'title': 'string', 'genres': 'array', 'horror': 'boolean'}}
        mirror.initialize(self.db, self.schema)
        mirror.apply_snapshot(self.db, {'titles': [{'id': 1, 'title': 'Original', 'genres': ['Drama'], 'horror': False}]})

    def test_upload_converts_sqlite_values_and_acknowledges_success(self):
        self.db.execute("UPDATE titles SET title='New', genres='[\"Horror\"]', horror=1 WHERE id=1")
        self.db.commit()
        sent = []
        mirror.publish_pending(self.db, self.schema, lambda *args: sent.append(args))
        self.assertEqual(['Horror'], sent[0][2][0]['genres'])
        self.assertIs(sent[0][2][0]['horror'], True)
        self.assertEqual([], mirror.pending_changes(self.db))

    def test_failed_upload_remains_queued(self):
        self.db.execute("UPDATE titles SET title='New' WHERE id=1"); self.db.commit()
        def failed(*args): raise RuntimeError('offline')
        with self.assertRaises(RuntimeError): mirror.publish_pending(self.db, self.schema, failed)
        self.assertEqual(1, len(mirror.pending_changes(self.db)))

    def test_postgres_jsonb_is_sent_as_json(self):
        doc = {'definitions': {table: {'properties': {'x': {'format': 'jsonb'}}} for table in
               ('services','platforms','titles','app_titles','profiles','profile_titles','profile_events','account_profile_links')}}
        self.assertEqual('json', mirror.schema_from_openapi(doc)['titles']['x'])

    def test_uploads_multiple_catalogue_rows_in_one_batch(self):
        self.db.execute("INSERT INTO titles VALUES (2,'New','[]',0)")
        self.db.execute("UPDATE titles SET title='Changed' WHERE id=1"); self.db.commit()
        sent = []
        mirror.publish_pending(self.db, self.schema, lambda *args: sent.append(args))
        self.assertEqual(1, len(sent))
        self.assertEqual(2, len(sent[0][2]))


if __name__ == '__main__':
    unittest.main()
