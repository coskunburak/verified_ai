"""Owner/CI-run regression tests for fail-closed scanner exit handling."""
from __future__ import annotations

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


SCRIPT = Path(__file__).with_name("secret_scan.sh")


class SecretScanTest(unittest.TestCase):
    def run_scan(self, scan_status=1, filter_status=1, output="", installed=True):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            binaries = root / "bin"
            binaries.mkdir()
            if installed:
                rg = binaries / "rg"
                rg.write_text(
                    '#!/bin/bash\n'
                    'if [[ "$1" == "-v" ]]; then\n'
                    '  while IFS= read -r line; do\n'
                    '    if [[ "$FILTER_STATUS" == "0" ]]; then printf "%s\\n" "$line"; fi\n'
                    '  done\n'
                    '  exit "$FILTER_STATUS"\n'
                    'fi\n'
                    'if [[ -n "$SCAN_OUTPUT" ]]; then printf "%s\\n" "$SCAN_OUTPUT"; fi\n'
                    'exit "$SCAN_STATUS"\n'
                )
                rg.chmod(0o755)
            tee = shutil.which("tee")
            if tee is None:
                self.fail("Test prerequisite missing: tee")
            (binaries / "tee").symlink_to(tee)
            environment = dict(os.environ, PATH=str(binaries), TMPDIR=str(root),
                               SCAN_STATUS=str(scan_status), FILTER_STATUS=str(filter_status),
                               SCAN_OUTPUT=output)
            return subprocess.run(["/bin/bash", str(SCRIPT)], cwd=root, env=environment,
                                  capture_output=True, text=True, timeout=10, check=False)

    def test_missing_ripgrep_fails_without_success_message(self):
        result = self.run_scan(installed=False)
        self.assertEqual(2, result.returncode)
        self.assertIn("required on PATH", result.stderr)
        self.assertNotIn("secret scan passed", result.stdout)

    def test_no_matches_is_success(self):
        result = self.run_scan(scan_status=1)
        self.assertEqual(0, result.returncode)
        self.assertIn("secret scan passed", result.stdout)

    def test_reviewed_placeholder_filter_with_no_remaining_matches_is_success(self):
        result = self.run_scan(scan_status=0, filter_status=1, output="synthetic placeholder")
        self.assertEqual(0, result.returncode)
        self.assertIn("secret scan passed", result.stdout)

    def test_remaining_findings_fail(self):
        result = self.run_scan(scan_status=0, filter_status=0, output="fixture.txt:1:synthetic finding")
        self.assertEqual(1, result.returncode)
        self.assertIn("potential secret matches", result.stderr)
        self.assertNotIn("secret scan passed", result.stdout)

    def test_scan_errors_are_not_treated_as_no_matches(self):
        for code in (2, 127, 130):
            with self.subTest(code=code):
                result = self.run_scan(scan_status=code)
                self.assertEqual(code, result.returncode)
                self.assertIn("secret scan failed", result.stderr)
                self.assertNotIn("secret scan passed", result.stdout)

    def test_partial_results_do_not_hide_scan_error(self):
        result = self.run_scan(scan_status=2, output="partial synthetic finding")
        self.assertEqual(2, result.returncode)
        self.assertNotIn("secret scan passed", result.stdout)
        self.assertNotIn("partial synthetic finding", result.stdout)

    def test_filter_error_is_not_success(self):
        result = self.run_scan(scan_status=0, filter_status=2, output="synthetic finding")
        self.assertEqual(2, result.returncode)
        self.assertIn("filter exited with status 2", result.stderr)
        self.assertNotIn("secret scan passed", result.stdout)


if __name__ == "__main__":
    unittest.main()
