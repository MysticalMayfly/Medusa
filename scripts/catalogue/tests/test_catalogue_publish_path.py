import importlib.util
import pathlib
import sqlite3
import sys
import tempfile
import types
import unittest
from unittest.mock import patch

file = pathlib.Path(__file__).resolve().parents[1] / 'build_catalogue.py'
spec = importlib.util.spec_from_file_location('catalogue_publish_test', file)
catalogue = importlib.util.module_from_spec(spec)
spec.loader.exec_module(catalogue)


class PublishPathTest(unittest.TestCase):
    def test_publisher_stages_master_in_mirror_then_syncs_it(self):
        with tempfile.TemporaryDirectory() as folder:
            mirror = str(pathlib.Path(folder) / 'supabase.db')
            sqlite3.connect(mirror).close()
            calls = []
            fake = types.SimpleNamespace(stage_master=lambda db, data: calls.append(('stage', data)) or 1,
                                         sync_mirror=lambda path: calls.append(('sync', str(path))) or {})
            with patch.object(catalogue, 'MIRROR', mirror, create=True), \
                 patch.object(catalogue, 'catalogue', return_value={'titles': [{'id': 1}]}), \
                 patch.object(catalogue, 'supabase_env', return_value={'SUPABASE_URL': 'https://example.invalid', 'SUPABASE_SERVICE_ROLE_KEY': 'unused'}), \
                 patch('urllib.request.urlopen', side_effect=AssertionError('Publisher bypassed the mirror')), \
                 patch.dict(sys.modules, {'supabase_mirror': fake}):
                catalogue.publish()
            self.assertEqual(['stage', 'sync'], [call[0] for call in calls])
            self.assertEqual(mirror, calls[1][1])


if __name__ == '__main__':
    unittest.main()

class ProfileDirectionTest(unittest.TestCase):
    def test_sync_reads_cloud_profile_state_via_mirror_without_legacy_push(self):
        with tempfile.TemporaryDirectory() as folder:
            mirror = str(pathlib.Path(folder) / 'supabase.db')
            sqlite3.connect(mirror).close()
            calls = []
            fake = types.SimpleNamespace(sync_mirror=lambda path, **kw: calls.append((str(path),kw)) or {})
            with patch.object(catalogue, 'MIRROR', mirror, create=True), \
                 patch.object(catalogue, 'rest', side_effect=AssertionError('Legacy profile sync should not run')), \
                 patch.dict(sys.modules, {'supabase_mirror': fake}):
                catalogue.sync()
            self.assertEqual([(mirror, {'profiles_only': True})], calls)
