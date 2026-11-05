"""Tests for Sprint 6.9 evidence analyzer (sprint_6_9_evidence.py).

Verifies fail-closed behavior, platform-specific skip handling, and reconciliation.
Author-written suite for owner execution.
"""
import hashlib
import json
import sys
import tempfile
import unittest
from pathlib import Path

QUALITY_DIR = Path(__file__).resolve().parents[3] / "scripts" / "quality"
if str(QUALITY_DIR) not in sys.path:
    sys.path.insert(0, str(QUALITY_DIR))

import sprint_6_9_evidence as evidence


class TestSprint69Evidence(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.out = Path(self.temp_dir.name)

    def tearDown(self):
        self.temp_dir.cleanup()

    def _write_junit_xml(self, filename, content):
        p = self.out / filename
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(content, encoding="utf-8")
        return p

    def test_all_tests_passed_no_skips(self):
        xml = """<?xml version="1.0" encoding="utf-8"?>
<testsuites>
  <testsuite name="pytest" tests="3">
    <testcase classname="tests.test_math_visualization" name="test_one" time="0.01"/>
    <testcase classname="tests.test_method_comparison_v2" name="test_two" time="0.02"/>
    <testcase classname="tests.test_attempt_v2_product" name="test_three" time="0.03"/>
  </testsuite>
</testsuites>"""
        p = self._write_junit_xml("report.xml", xml)
        req = ["test_math_visualization", "test_method_comparison_v2", "test_attempt_v2_product"]
        rep = evidence.junit([p], req, kind="verifier", platform_override="darwin")
        self.assertEqual(rep["status"], "PASS")
        self.assertEqual(rep["executed"], 3)
        self.assertEqual(rep["failures"], [])
        self.assertEqual(rep["unexpectedSkipped"], [])
        self.assertEqual(rep["platformSpecificSkipped"], [])
        self.assertFalse(rep["platformSpecificValidationPending"])

    def test_expected_linux_only_skips_on_macos(self):
        xml = """<?xml version="1.0" encoding="utf-8"?>
<testsuites>
  <testsuite name="pytest" tests="8">
    <testcase classname="tests.test_math_visualization" name="test_one" time="0.01"/>
    <testcase classname="tests.test_method_comparison_v2" name="test_two" time="0.02"/>
    <testcase classname="tests.test_attempt_v2_product" name="test_three" time="0.03"/>
    <testcase classname="tests.security.test_calculus_security" name="test_linux_calculus_resource_failure_is_reaped_and_nonproof[cpu_worker]"><skipped message="linux"/></testcase>
    <testcase classname="tests.security.test_calculus_security" name="test_linux_calculus_resource_failure_is_reaped_and_nonproof[memory_worker]"><skipped message="linux"/></testcase>
    <testcase classname="tests.security.test_execution_security" name="test_actual_memory_limit_exhaustion_is_a_nonproof_resource_failure"><skipped message="linux"/></testcase>
    <testcase classname="tests.security.test_numeric_security" name="test_linux_memory_and_cpu_limits_apply_to_numeric[cpu]"><skipped message="linux"/></testcase>
    <testcase classname="tests.security.test_numeric_security" name="test_linux_memory_and_cpu_limits_apply_to_numeric[memory]"><skipped message="linux"/></testcase>
  </testsuite>
</testsuites>"""
        p = self._write_junit_xml("report.xml", xml)
        req = ["test_math_visualization", "test_method_comparison_v2", "test_attempt_v2_product"]
        rep = evidence.junit([p], req, kind="verifier", platform_override="darwin")
        self.assertEqual(rep["status"], "PASS")
        self.assertEqual(len(rep["platformSpecificSkipped"]), 5)
        self.assertEqual(rep["unexpectedSkipped"], [])
        self.assertTrue(rep["platformSpecificValidationPending"])
        self.assertEqual(rep["platformSpecificValidation"], "PLATFORM_SPECIFIC_VALIDATION_PENDING")

    def test_unexpected_skipped_test(self):
        xml = """<?xml version="1.0" encoding="utf-8"?>
<testsuites>
  <testsuite name="pytest" tests="4">
    <testcase classname="tests.test_math_visualization" name="test_one" time="0.01"/>
    <testcase classname="tests.test_method_comparison_v2" name="test_two" time="0.02"/>
    <testcase classname="tests.test_attempt_v2_product" name="test_three" time="0.03"/>
    <testcase classname="tests.test_math_visualization" name="test_unexpected"><skipped message="unexpected"/></testcase>
  </testsuite>
</testsuites>"""
        p = self._write_junit_xml("report.xml", xml)
        req = ["test_math_visualization", "test_method_comparison_v2", "test_attempt_v2_product"]
        rep = evidence.junit([p], req, kind="verifier", platform_override="darwin")
        self.assertEqual(rep["status"], "BLOCKED")
        self.assertIn("test_math_visualization.test_unexpected", rep["unexpectedSkipped"])

    def test_known_test_actually_fails(self):
        xml = """<?xml version="1.0" encoding="utf-8"?>
<testsuites>
  <testsuite name="pytest" tests="4">
    <testcase classname="tests.test_math_visualization" name="test_one" time="0.01"/>
    <testcase classname="tests.test_method_comparison_v2" name="test_two" time="0.02"/>
    <testcase classname="tests.test_attempt_v2_product" name="test_three" time="0.03"/>
    <testcase classname="tests.security.test_execution_security" name="test_actual_memory_limit_exhaustion_is_a_nonproof_resource_failure">
      <failure message="OOM failed">Stack trace</failure>
    </testcase>
  </testsuite>
</testsuites>"""
        p = self._write_junit_xml("report.xml", xml)
        req = ["test_math_visualization", "test_method_comparison_v2", "test_attempt_v2_product"]
        rep = evidence.junit([p], req, kind="verifier", platform_override="darwin")
        self.assertEqual(rep["status"], "FAIL")
        self.assertEqual(len(rep["failedCases"]), 1)

    def test_missing_mandatory_test(self):
        xml = """<?xml version="1.0" encoding="utf-8"?>
<testsuites>
  <testsuite name="pytest" tests="2">
    <testcase classname="tests.test_method_comparison_v2" name="test_two" time="0.02"/>
    <testcase classname="tests.test_attempt_v2_product" name="test_three" time="0.03"/>
  </testsuite>
</testsuites>"""
        p = self._write_junit_xml("report.xml", xml)
        req = ["test_math_visualization", "test_method_comparison_v2", "test_attempt_v2_product"]
        rep = evidence.junit([p], req, kind="verifier", platform_override="darwin")
        self.assertEqual(rep["status"], "FAIL")
        self.assertIn("test_math_visualization", rep["missingCases"])

    def test_corrupted_junit_report(self):
        corrupted_xml = """<testsuite><testcase name="broken" """
        p = self._write_junit_xml("report.xml", corrupted_xml)
        req = ["test_math_visualization"]
        rep = evidence.junit([p], req, kind="verifier", platform_override="darwin")
        self.assertEqual(rep["status"], "FAIL")
        self.assertTrue(any("CORRUPTED_XML" in f for f in rep["failedCases"]))

    def test_wrong_platform_classification_skips_on_linux_are_blocked(self):
        xml = """<?xml version="1.0" encoding="utf-8"?>
<testsuites>
  <testsuite name="pytest" tests="4">
    <testcase classname="tests.test_math_visualization" name="test_one" time="0.01"/>
    <testcase classname="tests.test_method_comparison_v2" name="test_two" time="0.02"/>
    <testcase classname="tests.test_attempt_v2_product" name="test_three" time="0.03"/>
    <testcase classname="tests.security.test_execution_security" name="test_actual_memory_limit_exhaustion_is_a_nonproof_resource_failure"><skipped message="linux"/></testcase>
  </testsuite>
</testsuites>"""
        p = self._write_junit_xml("report.xml", xml)
        req = ["test_math_visualization", "test_method_comparison_v2", "test_attempt_v2_product"]
        rep = evidence.junit([p], req, kind="verifier", platform_override="linux")
        self.assertEqual(rep["status"], "BLOCKED")
        self.assertEqual(len(rep["unexpectedSkipped"]), 1)
        self.assertEqual(rep["platformSpecificSkipped"], [])

    def test_incomplete_test_enumeration_fails_closed(self):
        xml = """<?xml version="1.0" encoding="utf-8"?>
<testsuites>
  <testsuite name="pytest" tests="1">
    <testcase classname="" name="" time="0.01"/>
  </testsuite>
</testsuites>"""
        p = self._write_junit_xml("report.xml", xml)
        req = ["test_math_visualization"]
        rep = evidence.junit([p], req, kind="verifier", platform_override="darwin")
        self.assertEqual(rep["status"], "FAIL")
        self.assertIn("test_math_visualization", rep["missingCases"])

    def test_previous_run_with_different_source_fingerprint(self):
        for gate in ("A", "B", "C", "D", "cleanup"):
            (self.out / f"{gate}.status").write_text("PASS\n")
        (self.out / "head.txt").write_text("0123456789abcdef\n")
        (self.out / "source-before.json").write_text(json.dumps({"sha256": "aaaa"}))
        (self.out / "source-after.json").write_text(json.dumps({"sha256": "bbbb"}))
        (self.out / "verifier-summary.json").write_text(json.dumps({"status": "PASS"}))
        (self.out / "backend-summary.json").write_text(json.dumps({"status": "PASS"}))
        (self.out / "ios-summary.json").write_text(json.dumps({"status": "PASS"}))
        dataset_path = evidence.ROOT / "evaluations/golden-datasets/visualizations/bounded-real-v1.json"
        dataset_cases = json.loads(dataset_path.read_text())["cases"]
        (self.out / "golden.json").write_text(json.dumps({
            "status": "PASS",
            "synthetic": True,
            "sha256": hashlib.sha256(dataset_path.read_bytes()).hexdigest(),
            "caseCount": len(dataset_cases),
            "cases": [{"status": "PASS"} for _ in dataset_cases],
        }))
        code = evidence.summary(self.out, 0)
        self.assertEqual(code, 1)
        summary_data = json.loads((self.out / "summary.json").read_text())
        self.assertFalse(summary_data["sourceUnchanged"])
        self.assertEqual(summary_data["gates"]["F"], "FAIL")

    def test_multiple_parameterized_skipped_cases(self):
        xml = """<?xml version="1.0" encoding="utf-8"?>
<testsuites>
  <testsuite name="pytest" tests="5">
    <testcase classname="tests.test_math_visualization" name="test_one" time="0.01"/>
    <testcase classname="tests.test_method_comparison_v2" name="test_two" time="0.02"/>
    <testcase classname="tests.test_attempt_v2_product" name="test_three" time="0.03"/>
    <testcase classname="tests.security.test_calculus_security" name="test_linux_calculus_resource_failure_is_reaped_and_nonproof[cpu_worker]"><skipped/></testcase>
    <testcase classname="tests.security.test_calculus_security" name="test_linux_calculus_resource_failure_is_reaped_and_nonproof[memory_worker]"><skipped/></testcase>
  </testsuite>
</testsuites>"""
        p = self._write_junit_xml("report.xml", xml)
        req = ["test_math_visualization", "test_method_comparison_v2", "test_attempt_v2_product"]
        rep = evidence.junit([p], req, kind="verifier", platform_override="darwin")
        self.assertEqual(rep["status"], "PASS")
        self.assertEqual(len(rep["platformSpecificSkipped"]), 2)
        self.assertEqual(rep["unexpectedSkipped"], [])

    def test_no_actual_executed_tests(self):
        xml = """<?xml version="1.0" encoding="utf-8"?>
<testsuites>
  <testsuite name="pytest" tests="0">
  </testsuite>
</testsuites>"""
        p = self._write_junit_xml("report.xml", xml)
        req = ["test_math_visualization"]
        rep = evidence.junit([p], req, kind="verifier", platform_override="darwin")
        self.assertEqual(rep["status"], "FAIL")
        self.assertEqual(rep["executed"], 0)

    def test_missing_evidence_artifacts_fails_closed(self):
        for gate in ("A", "B", "C", "D", "cleanup"):
            (self.out / f"{gate}.status").write_text("PASS\n")
        (self.out / "head.txt").write_text("0123456789abcdef\n")
        (self.out / "source-before.json").write_text(json.dumps({"sha256": "aaaa"}))
        (self.out / "source-after.json").write_text(json.dumps({"sha256": "aaaa"}))
        code = evidence.summary(self.out, 0)
        self.assertEqual(code, 2)
        summary_data = json.loads((self.out / "summary.json").read_text())
        self.assertEqual(summary_data["gates"]["E"], "BLOCKED")
        self.assertIn("verifier", summary_data["missingMandatoryEvidence"])


if __name__ == "__main__":
    unittest.main()
