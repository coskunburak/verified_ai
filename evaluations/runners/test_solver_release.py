"""Authored Sprint 5.12 release regressions. Execution belongs to the human owner."""
from copy import deepcopy
import hashlib
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

try:
    from . import solver_evaluation as evaluation
    from . import solver_release as release
except ImportError:
    import solver_evaluation as evaluation
    import solver_release as release


class ReleaseTests(unittest.TestCase):
    def setUp(self):
        self.report = evaluation.run()
        self.policy = evaluation.read_json(evaluation.POLICY)[0]

    def changed(self, name, value, critical=None):
        report = deepcopy(self.report)
        report['results'][0]['measurements'][name] = value
        if critical:
            report['results'][0]['critical'].append(critical)
            report['criticalCounts'][critical] += 1
        report['metrics'] = evaluation.aggregate(report['results'])
        cases = evaluation.load_dataset().cases
        for name in report['slices']:
            subset = [r for c, r in zip(cases, report['results']) if name in c['slices']]
            report['slices'][name]['metrics'] = evaluation.aggregate(subset)
        return evaluation.seal(report)

    def test_compatible_deterministic_baseline_kernel(self):
        result = release.compare(self.report, self.report, self.policy)
        self.assertEqual('PASS', result['decision'])
        self.assertFalse(result['productionAuthorized'])

    def test_unapproved_candidate_has_no_baseline(self):
        self.assertEqual('BLOCKED', release.compare(self.report)['decision'])

    def test_production_never_inherits_fixture_pass(self):
        result = release.compare(self.report, self.report, scope='PRODUCTION')
        self.assertEqual('BLOCKED', result['decision'])
        self.assertIn('NO_REAL_MODEL_EVIDENCE', result['reasons'])
        self.assertFalse(result['productionAuthorized'])

    def test_quality_regression_even_when_candidate_claims_pass(self):
        bad = self.changed('dispositionCorrect', False)
        result = release.compare(bad, self.report)
        self.assertEqual('FAIL', result['decision'])
        self.assertTrue(result['regressions'])

    def test_hard_gate_takes_precedence_over_missing_external_evidence(self):
        bad = self.changed('falseVerified', True, 'FALSE_VERIFIED')
        result = release.compare(bad, scope='PRODUCTION')
        self.assertEqual('FAIL', result['decision'])
        self.assertIn('CRITICAL_SAFETY_FAILURE', result['reasons'])

    def test_incompatible_dataset_blocks(self):
        bad = deepcopy(self.report); bad['datasetSha256'] = 'f' * 64
        self.assertEqual('BLOCKED', release.compare(evaluation.seal(bad), self.report)['decision'])

    def test_report_tampering_blocks(self):
        bad = deepcopy(self.report); bad['metrics']['schemaValid']['value'] = 1
        result = release.compare(bad, self.report)
        self.assertEqual('BLOCKED', result['decision'])
        self.assertIn('REPORT_TAMPERED', result['reasons'])

    def test_rehashed_inconsistent_metrics_are_rejected(self):
        bad = deepcopy(self.report); bad['metrics']['schemaValid']['value'] = 1
        result = release.compare(evaluation.seal(bad), self.report)
        self.assertIn('REPORT_METRIC_MISMATCH', result['reasons'])

    def test_prompt_substitution_is_not_new_approval(self):
        # The typed report schema pins provenance before semantic comparison.
        # Rehashing a substituted artifact must not bypass that earlier boundary.
        for field in self.report['provenance']:
            with self.subTest(field=field):
                bad = deepcopy(self.report)
                bad['provenance'][field] = 'substituted'
                result = release.compare(evaluation.seal(bad), self.report)
                self.assertEqual('BLOCKED', result['decision'])
                self.assertIn('REPORT_SCHEMA_INVALID', result['reasons'])
                self.assertFalse(result['productionAuthorized'])

    def test_missing_critical_slice_blocks(self):
        bad = deepcopy(self.report)
        bad['coverage'].pop('CALCULUS'); bad['slices'].pop('CALCULUS')
        self.assertEqual('BLOCKED', release.compare(evaluation.seal(bad), self.report)['decision'])

    def test_unapproved_threshold_cannot_be_enabled_by_boolean(self):
        policy = deepcopy(self.policy); policy['productionApproval'] = True
        result = release.compare(self.report, self.report, policy)
        self.assertIn('UNSUPPORTED_THRESHOLD_APPROVAL', result['reasons'])

    def test_missing_and_nonfinite_report_metrics_rejected(self):
        for value in (None, -1, True):
            with self.subTest(value=value):
                bad = deepcopy(self.report); bad['metrics']['schemaValid']['value'] = value
                self.assertEqual('BLOCKED', release.compare(evaluation.seal(bad), self.report)['decision'])

    def prepare_review(self, root):
        def put(ref, value):
            p = root / ref; p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(json.dumps(value, indent=2) + '\n')
            return hashlib.sha256(p.read_bytes()).hexdigest()
        put('evaluations/baselines/solver-release-policy-v1.json', self.policy)
        put('.generated/candidate.json', self.report)
        rationale = root / 'docs/approval.md'; rationale.parent.mkdir(exist_ok=True)
        rationale.write_text('Synthetic test approval only. Not a repository release approval.\n')
        review = {'schemaVersion': 'solver-baseline-review-v1', 'decision': 'APPROVED',
                  'evidenceScope': 'LOCAL_FIXTURE_REGRESSION', 'approvedBy': 'test-reviewer',
                  'approvedAt': '2026-10-31T12:00:00+03:00',
                  'binding': release.approval_binding(self.report, self.policy),
                  'rationaleReference': 'docs/approval.md',
                  'rationaleSha256': hashlib.sha256(rationale.read_bytes()).hexdigest()}
        review_hash = put('docs/review.json', review)
        return review_hash

    def promote(self, root, sha, **kwargs):
        return release.promote(root, '.generated/candidate.json', 'docs/review.json', sha,
                               'evaluations/baselines/solver-approved-test.json', **kwargs)

    def test_human_reviewed_create_only_promotion_and_rollback(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {}, clear=True):
            root = Path(directory); sha = self.prepare_review(root)
            self.promote(root, sha, explicit_approve=True)
            with self.assertRaisesRegex(evaluation.EvidenceError, 'BASELINE_IMMUTABLE'):
                self.promote(root, sha, explicit_approve=True)
            ref = 'evaluations/baselines/solver-approved-test.json'
            baseline = release.load_baseline(root, ref, self.policy)
            altered = self.changed('sourceFaithful', False)
            self.assertEqual('FAIL', release.compare(altered, baseline)['decision'])
            self.assertEqual('PASS', release.compare(self.report, baseline)['decision'])
            self.assertEqual('BLOCKED', release.compare(self.report, baseline, scope='PRODUCTION')['decision'])
            self.assertEqual('PASS', release.compare_files(root, '.generated/candidate.json', ref)['decision'])

    def test_promotion_requires_explicit_owner_not_ci(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {}, clear=True):
            root = Path(directory); sha = self.prepare_review(root)
            with self.assertRaisesRegex(evaluation.EvidenceError, 'HUMAN_PROMOTION_REQUIRED'):
                self.promote(root, sha)
            with patch.dict(os.environ, {'CI': 'true'}):
                with self.assertRaisesRegex(evaluation.EvidenceError, 'HUMAN_PROMOTION_REQUIRED'):
                    self.promote(root, sha, explicit_approve=True)

    def test_review_bytes_and_report_binding_detect_substitution(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {}, clear=True):
            root = Path(directory); sha = self.prepare_review(root)
            path = root / 'docs/review.json'
            data = json.loads(path.read_text()); data['approvedBy'] = 'other-reviewer'
            path.write_text(json.dumps(data))
            with self.assertRaisesRegex(evaluation.EvidenceError, 'APPROVAL_SUBSTITUTED'):
                self.promote(root, sha, explicit_approve=True)
            data['binding']['provenanceSha256'] = '0' * 64; path.write_text(json.dumps(data))
            sha = hashlib.sha256(path.read_bytes()).hexdigest()
            with self.assertRaisesRegex(evaluation.EvidenceError, 'APPROVAL_BINDING'):
                self.promote(root, sha, explicit_approve=True)

    def test_baseline_hash_and_review_are_rechecked(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {}, clear=True):
            root = Path(directory); sha = self.prepare_review(root)
            self.promote(root, sha, explicit_approve=True)
            ref = 'evaluations/baselines/solver-approved-test.json'; path = root / ref
            baseline = json.loads(path.read_text()); baseline['reviewSha256'] = '0' * 64
            path.write_text(json.dumps(baseline))
            self.assertEqual('BLOCKED', release.compare_files(root, '.generated/candidate.json', ref)['decision'])

    def test_unsafe_review_reference_and_symlink(self):
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, {}, clear=True):
            root = Path(directory); sha = self.prepare_review(root)
            with self.assertRaises(evaluation.EvidenceError):
                release.promote(root, '.generated/candidate.json', 'docs/../review.json', sha,
                                'evaluations/baselines/solver-approved-test.json', True)
            (root / 'docs/link.json').symlink_to(root / 'docs/review.json')
            with self.assertRaisesRegex(evaluation.EvidenceError, 'SYMLINK_FORBIDDEN'):
                release.verify_review(root, 'docs/link.json', sha, self.report, self.policy)

    def test_economic_regression_cannot_compensate_quality_regression(self):
        result = release.compare(self.changed('dispositionCorrect', False), self.report)
        self.assertEqual('FAIL', result['decision'])
        self.assertFalse(result['productionAuthorized'])
