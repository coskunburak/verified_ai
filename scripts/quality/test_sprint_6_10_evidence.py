#!/usr/bin/env python3
"""Synthetic owner-run regression for the acceptance gate itself."""
import json
import sys
import tempfile
import unittest
from pathlib import Path
from check_sprint_6_10_localization import inspect_catalog, placeholders
from sprint_6_10_evidence import reconcile_ios, summary, write, repair_plan


class EvidenceTests(unittest.TestCase):
    expected = {('VerifiedAITests.MathPresentationTests', 'testSpeech')}
    log = "Test Case '-[VerifiedAITests.MathPresentationTests testSpeech]' passed (0.01 seconds)."
    result = {'result': 'Passed', 'totalTestCount': 1, 'passedTests': 1, 'failedTests': 0, 'skippedTests': 0}

    def test_fresh_complete_ios(self):
        self.assertEqual(reconcile_ios(self.log, self.result, self.expected, 0)['status'], 'PASS')

    def test_zero_exit_without_test_is_failure(self):
        self.assertEqual(reconcile_ios('', self.result, self.expected, 0)['status'], 'FAIL')

    def test_corrupt_bundle_is_failure(self):
        self.assertEqual(reconcile_ios(self.log, {}, self.expected, 0)['status'], 'FAIL')

    def test_unexpected_skip_is_failure(self):
        result = dict(self.result, skippedTests=1)
        self.assertEqual(reconcile_ios(self.log, result, self.expected, 0)['status'], 'FAIL')

    def test_process_failure_overrides_passes(self):
        self.assertEqual(reconcile_ios(self.log, self.result, self.expected, 65)['status'], 'FAIL')

    def test_missing_method_is_failure(self):
        expected = self.expected | {('VerifiedAIUITests.TutorUITests', 'testLargeText')}
        self.assertEqual(reconcile_ios(self.log, self.result, expected, 0)['status'], 'FAIL')

    def test_positional_placeholders_and_escaped_percent(self):
        self.assertEqual(placeholders('%@ %lld %%'), placeholders('%2$lld %1$@ %%'))
        self.assertNotEqual(placeholders('%lld'), placeholders('%d'))

    def test_catalog_requires_both_languages(self):
        _, errors = inspect_catalog({'tutor.send': {'localizations': {'en': {'stringUnit': {'state': 'translated', 'value': 'Send'}}}}})
        self.assertIn('tutor.send:tr:missing', errors)

    def test_plural_variant_placeholder_mismatch_fails(self):
        english = {'stringUnit': {'state': 'translated', 'value': 'Attempt %lld'}}
        turkish_unit = {'stringUnit': {'state': 'translated', 'value': 'Deneme %@'}}
        turkish = {'variations': {'plural': {'other': turkish_unit}}}
        catalog = {'solve.attempt %lld': {'localizations': {'en': english, 'tr': turkish}}}
        _, errors = inspect_catalog(catalog)
        self.assertIn('solve.attempt %lld:tr:placeholder-mismatch', errors)

    def test_source_change_invalidates_all_pass_flags(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            for gate in (*'ABCDEFG', 'cleanup'):
                (out / (gate + '.status')).write_text('PASS')
            write(out / 'source-before.json', {'sha256': 'before'})
            write(out / 'source-after.json', {'sha256': 'after'})
            write(out / 'execution-metadata.json', {'mode': 'FULL', 'selectedGates': list('ABCDEFG')})
            for name in ('backend', 'ios', 'security', 'localization', 'evidence-selftests'):
                write(out / (name + '-summary.json'), {'status': 'PASS'})
            self.assertEqual(summary(out, 0), 1)
            self.assertEqual(json.loads((out / 'summary.json').read_text())['overall'], 'FAIL')

    def test_targeted_d_does_not_require_or_claim_previous_passes(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            for gate in (*'ABCDEFG', 'cleanup'):
                (out / (gate + '.status')).write_text('PASS' if gate in ('D', 'cleanup') else 'NOT_RUN')
            write(out / 'source-before.json', {'sha256': 'same'})
            write(out / 'source-after.json', {'sha256': 'same'})
            write(out / 'execution-metadata.json', {'mode': 'TARGETED', 'selectedGates': ['D']})
            write(out / 'ios-summary.json', {'status': 'PASS'})
            self.assertEqual(summary(out, 0), 0)
            result = json.loads((out / 'summary.json').read_text())
            self.assertEqual(result['automatedAcceptance'], 'TARGETED_REPAIR_PASSED')
            self.assertEqual(result['currentSourceFullAcceptance'], 'NOT_VERIFIED')
            self.assertEqual(result['gates']['B'], 'NOT_RUN')
            self.assertFalse(result['previousEvidenceImported'])
            (out / 'D.status').write_text('FAIL')
            self.assertEqual(summary(out, 0), 1)
            (out / 'D.status').write_text('PASS')
            (out / 'ios-summary.json').unlink()
            self.assertEqual(summary(out, 0), 2)

    def test_repair_plan_excludes_passed_cases(self):
        passed = ['VerifiedAIUITests.TutorUITests', 'testAlreadyPassed']
        failed = ['VerifiedAIUITests.TutorUITests', 'testNeedsRepair']
        prior = {'sprint': '6.10', 'sourceUnchanged': True, 'gates': {'D': 'FAIL'},
                 'ios': {'passedCases': [passed], 'failedCases': [failed], 'missingCases': [failed, passed], 'skipped': False}}
        plan = repair_plan(prior)
        self.assertEqual(plan['selectors'], ['VerifiedAIUITests/TutorUITests/testNeedsRepair'])
        self.assertEqual(plan['excludedPassedCount'], 1)
        self.assertFalse(plan['previousResultsImported'])
        prior['ios']['failedCases'] = [['BadTarget', 'testInjection']]
        with self.assertRaises(ValueError):
            repair_plan(prior)

    def test_missing_evidence_is_blocked(self):
        with tempfile.TemporaryDirectory() as directory:
            out = Path(directory)
            write(out / 'source-before.json', {'sha256': 'same'})
            write(out / 'source-after.json', {'sha256': 'same'})
            self.assertEqual(summary(out, 0), 2)


if __name__ == '__main__':
    suite = unittest.defaultTestLoader.loadTestsFromTestCase(EvidenceTests)
    result = unittest.TextTestRunner(verbosity=2).run(suite)
    value = {'status': 'PASS' if result.wasSuccessful() and result.testsRun >= 13 and not result.skipped else 'FAIL',
             'executed': result.testsRun, 'failedCases': [str(test) for test, _ in result.failures + result.errors],
             'skipped': [str(test) for test, _ in result.skipped]}
    write(Path(sys.argv[1]), value)
    sys.exit(0 if value['status'] == 'PASS' else 1)
