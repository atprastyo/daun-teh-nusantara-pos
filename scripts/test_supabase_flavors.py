"""Offline Gradle configuration checks. Uses synthetic public values; no backend IO."""
import base64
import json
import subprocess
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class SupabaseFlavorConfigTest(unittest.TestCase):
    def validate(self, environment, url, key, valid, build_config=False):
        task = ':app:generateProdDebugBuildConfig' if build_config else f':app:validate{environment.title()}SupabaseConfiguration'
        args = [str(ROOT / 'gradlew'), task,
                '--offline', '--max-workers=2', '--console=plain',
                f'-P{environment}_SUPABASE_URL={url}', f'-P{environment}_SUPABASE_PUBLIC_KEY={key}',
                '-PSUPABASE_URL=https://legacy-fixture.invalid',
                '-PSUPABASE_PUBLIC_KEY=sb_publishable_legacy_fixture']
        result = subprocess.run(args, cwd=ROOT, capture_output=True, text=True, timeout=180)
        # Do not include Gradle output or configuration values in assertion diagnostics.
        self.assertEqual(result.returncode == 0, valid, f'{environment} validation exit={result.returncode}')
        if not valid:
            self.assertIn(f'{environment}_SUPABASE_', result.stdout + result.stderr)

    def test_production_has_no_legacy_fallback(self):
        self.validate('PROD', '', '', False, build_config=True)

    def test_production_accepts_explicit_publishable_configuration(self):
        self.validate('PROD', 'https://prod-fixture.invalid', 'sb_publishable_prod_fixture', True)
        payload = base64.urlsafe_b64encode(json.dumps({'role': 'anon'}).encode()).decode().rstrip('=')
        self.validate('PROD', 'https://prod-fixture.invalid', f'fixture.{payload}.signature', True)

    def test_dev_can_be_unconfigured_without_affecting_production(self):
        self.validate('DEV', '', '', True)

    def test_service_role_keys_and_non_origin_urls_are_rejected(self):
        for url, key in [('http://fixture.invalid', 'sb_publishable_fixture'),
                         ('https://fixture.invalid/rest/v1', 'sb_publishable_fixture'),
                         ('https://user@fixture.invalid', 'sb_publishable_fixture'),
                         ('https://fixture.invalid?token=fixture', 'sb_publishable_fixture'),
                         ('https://fixture.invalid', 'sb_secret_fixture')]:
            with self.subTest(case='invalid public client configuration'):
                self.validate('PROD', url, key, False)
        payload = base64.urlsafe_b64encode(json.dumps({'role': 'service_role'}).encode()).decode().rstrip('=')
        self.validate('PROD', 'https://fixture.invalid', f'fixture.{payload}.signature', False)


if __name__ == '__main__':
    unittest.main()
