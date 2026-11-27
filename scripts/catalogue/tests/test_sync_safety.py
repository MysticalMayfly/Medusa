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
