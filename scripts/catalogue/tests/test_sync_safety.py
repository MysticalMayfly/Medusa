import importlib.util
import pathlib
import sqlite3
import unittest

path = pathlib.Path(__file__).resolve().parents[1] / 'supabase_mirror.py'
spec = importlib.util.spec_from_file_location('mirror_safety', path)
mirror = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mirror)


class SyncSafetyTest(unittest.TestCase):
    def test_cloud_profile_changes_are_never_pushed_from_desktop_mirror(self):
        db = sqlite3.connect(':memory:')
        self.addCleanup(db.close)
        schemas = {'profiles': {'id': 'string', 'name': 'string'}}
        mirror.initialize(db, schemas)
        # Simulate a malformed or manually edited queue. No network request should be made.
        db.execute("INSERT INTO _local_changes VALUES ('profiles', 'p1', 'upsert', 1)")
        db.commit()
        calls = []
        with self.assertRaises(ValueError):
            mirror.publish_pending(db, schemas, lambda *args: calls.append(args))
        self.assertEqual([], calls)
        self.assertEqual(1, len(mirror.pending_changes(db)))


if __name__ == '__main__':
    unittest.main()

class MasterIdentityTest(unittest.TestCase):
    def test_legacy_identity_moves_without_losing_history(self):
        import importlib.util
        p=pathlib.Path(__file__).resolve().parents[1]/'build_catalogue.py'
        spec=importlib.util.spec_from_file_location('master_sync_test',p)
        m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
        c=sqlite3.connect(':memory:');self.addCleanup(c.close)
        c.executescript("create table profiles(id text primary key);create table profile_titles(profile_id text,title_id integer);create table applied_events(id integer,profile_id text);insert into profiles values('p1');insert into profile_titles values('p1',1);insert into applied_events values(3,'p1');")
        m.canonicalize_master_profile(c,'p1','g123')
        self.assertEqual('g123',c.execute('select id from profiles').fetchone()[0])
        self.assertEqual('g123',c.execute('select profile_id from profile_titles').fetchone()[0])
        self.assertEqual('g123',c.execute('select profile_id from applied_events').fetchone()[0])
