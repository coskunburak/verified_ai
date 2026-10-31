"""Owner-executed Phase 5 evaluation regression tests. No network or paid inference."""
from copy import deepcopy
import hashlib
import json
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch

from evaluations.runners import solver_evaluation as evaluation


class FixtureWorkspace:
    """Each mutation stays in an isolated synthetic corpus; released artifacts are never edited."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.dataset = self.root / 'dataset'
        shutil.copytree(evaluation.DATASET, self.dataset)
        self.config_path = self.root / 'config.json'
        self.observations_path = self.root / 'observations.json'
        shutil.copyfile(evaluation.CONFIG, self.config_path)
        shutil.copyfile(evaluation.OBSERVATIONS, self.observations_path)
        self.cases = json.loads((self.dataset / 'cases.json').read_text())
        self.manifest = json.loads((self.dataset / 'manifest.json').read_text())
        self.config = json.loads(self.config_path.read_text())
        self.observations = json.loads(self.observations_path.read_text())
        self.addCleanup(patch.stopall)
        patch.object(evaluation, 'OBSERVATIONS', self.observations_path).start()
        patch.object(evaluation.subprocess, 'check_output', return_value='a' * 40 + '\n').start()

    @staticmethod
    def write(path, document):
        path.write_text(json.dumps(document, indent=2) + '\n')
        return hashlib.sha256(path.read_bytes()).hexdigest()

    def save_cases(self):
        self.manifest['caseFileSha256'] = self.write(self.dataset / 'cases.json', self.cases)
        self.manifest['caseCount'] = len(self.cases)
        self.write(self.dataset / 'manifest.json', self.manifest)

    def save_observations(self):
        self.config['observationSha256'] = self.write(self.observations_path, self.observations)
        self.write(self.config_path, self.config)

    def run_fixture(self, **kwargs):
        return evaluation.run(directory=self.dataset, config_path=self.config_path, **kwargs)

    def case(self, identity):
        return next(case for case in self.cases if case['id'] == identity)

    def row(self, identity):
        return next(row for row in self.observations['observations'] if row['caseId'] == identity)


class DatasetTests(FixtureWorkspace, unittest.TestCase):
    def test_valid_corpus_is_ordered_governed_and_explicitly_engineering_only(self):
        dataset = evaluation.load_dataset(self.dataset)
        self.assertEqual(sorted(case['id'] for case in dataset.cases), [case['id'] for case in dataset.cases])
        self.assertEqual(set(dataset.manifest['requiredSlices']), set(dataset.coverage))
        self.assertFalse(dataset.manifest['independentGoldApproval'])
        self.assertEqual('ENGINEERING_ONLY', dataset.manifest['representativeness'])
        self.assertTrue(all(case['origin']['trainingEligibility'] == 'NOT_ELIGIBLE' for case in dataset.cases))

    def test_manifest_missing_field_is_rejected(self):
        del self.manifest['caseSchemaVersion']
        self.write(self.dataset / 'manifest.json', self.manifest)
        with self.assertRaisesRegex(evaluation.EvidenceError, 'MANIFEST_SHAPE'):
            evaluation.load_dataset(self.dataset)

    def test_manifest_unknown_fields_cannot_smuggle_authority(self):
        self.manifest['approved'] = True
        self.write(self.dataset / 'manifest.json', self.manifest)
        with self.assertRaisesRegex(evaluation.EvidenceError, 'MANIFEST_SHAPE'):
            evaluation.load_dataset(self.dataset)

    def test_checksums_reject_altered_cases_and_schema(self):
        for field, reason in [('caseFileSha256', 'CASE_CHECKSUM'), ('caseSchemaSha256', 'SCHEMA_CHECKSUM')]:
            with self.subTest(field=field):
                original = self.manifest[field]
                self.manifest[field] = '0' * 64
                self.write(self.dataset / 'manifest.json', self.manifest)
                with self.assertRaisesRegex(evaluation.EvidenceError, reason):
                    evaluation.load_dataset(self.dataset)
                self.manifest[field] = original

    def test_duplicate_ids_fail_even_with_updated_checksum(self):
        self.cases.append(deepcopy(self.cases[-1]))
        self.save_cases()
        with self.assertRaisesRegex(evaluation.EvidenceError, 'DUPLICATE_OR_UNORDERED_IDS'):
            evaluation.load_dataset(self.dataset)

    def test_unordered_ids_are_rejected(self):
        self.cases.reverse()
        self.save_cases()
        with self.assertRaisesRegex(evaluation.EvidenceError, 'DUPLICATE_OR_UNORDERED_IDS'):
            evaluation.load_dataset(self.dataset)

    def test_missing_answer_for_verified_label_is_rejected(self):
        self.case('arithmetic')['expected']['answer'] = None
        self.save_cases()
        with self.assertRaisesRegex(evaluation.EvidenceError, 'MISSING_LABEL'):
            evaluation.load_dataset(self.dataset)

    def test_training_rights_origin_and_label_provenance_are_required(self):
        mutations = [('origin', 'trainingEligibility', 'ELIGIBLE'), ('origin', 'type', 'STUDENT_UPLOAD'),
                     ('origin', 'license', 'UNKNOWN'), ('origin', 'rightsStatus', 'UNKNOWN'),
                     ('expected', 'independentReview', True), ('expected', 'labelBasis', 'MODEL_GENERATED')]
        for group, field, value in mutations:
            with self.subTest(field=field):
                original = self.cases[0][group][field]
                self.cases[0][group][field] = value
                self.save_cases()
                with self.assertRaisesRegex(evaluation.EvidenceError, 'CASE_SCHEMA_INVALID'):
                    evaluation.load_dataset(self.dataset)
                self.cases[0][group][field] = original

    def test_family_and_normalized_input_cannot_cross_partitions(self):
        first, second = self.case('arithmetic'), self.case('algebra')
        for field, value in [('family', first['family']), ('input', '  2+3  ')]:
            with self.subTest(field=field):
                original = second[field]
                second[field] = value
                self.save_cases()
                with self.assertRaisesRegex(evaluation.EvidenceError, 'CROSS_PARTITION_LEAKAGE'):
                    evaluation.load_dataset(self.dataset)
                second[field] = original

    def test_development_is_not_protected_holdout_or_production_correction(self):
        for partition in ('PROTECTED_HOLDOUT', 'PRODUCTION_CORRECTIONS'):
            with self.subTest(partition=partition):
                self.cases[0]['partition'] = partition
                self.save_cases()
                with self.assertRaisesRegex(evaluation.EvidenceError, 'RESTRICTED_PARTITION'):
                    evaluation.load_dataset(self.dataset)

    def test_missing_critical_slice_is_rejected(self):
        self.case('numeric')['slices'].remove('NUMERIC_ONLY')
        self.save_cases()
        with self.assertRaisesRegex(evaluation.EvidenceError, 'MISSING_CRITICAL_SLICE'):
            evaluation.load_dataset(self.dataset)

    def test_manifest_cannot_remove_mandatory_coverage(self):
        self.manifest['requiredSlices'].remove('NUMERIC_ONLY')
        self.write(self.dataset / 'manifest.json', self.manifest)
        with self.assertRaisesRegex(evaluation.EvidenceError, 'MISSING_CRITICAL_SLICE'):
            evaluation.load_dataset(self.dataset)

    def test_incompatible_schema_version_is_rejected(self):
        self.cases[0]['schemaVersion'] = 'solver-evaluation-case-v99'
        self.save_cases()
        with self.assertRaisesRegex(evaluation.EvidenceError, 'CASE_SCHEMA_INVALID'):
            evaluation.load_dataset(self.dataset)

    def test_oversized_case_and_artifact_are_rejected(self):
        self.cases[0]['input'] = 'x' * 513
        self.save_cases()
        with self.assertRaisesRegex(evaluation.EvidenceError, 'CASE_SCHEMA_INVALID'):
            evaluation.load_dataset(self.dataset)
        with self.assertRaisesRegex(evaluation.EvidenceError, 'INPUT_TOO_LARGE'):
            evaluation.read_json(self.dataset / 'cases.json', limit=8)

    def test_paths_cannot_escape_dataset(self):
        for path in ('../cases.json', '/tmp/cases.json', 'a//cases.json', 'a\\cases.json'):
            with self.subTest(path=path):
                self.manifest['caseFile'] = path
                self.write(self.dataset / 'manifest.json', self.manifest)
                with self.assertRaisesRegex(evaluation.EvidenceError, 'UNSAFE_PATH'):
                    evaluation.load_dataset(self.dataset)

    def test_symlink_artifact_substitution_is_rejected(self):
        (self.dataset / 'linked.json').symlink_to(self.dataset / 'cases.json')
        self.manifest['caseFile'] = 'linked.json'
        self.write(self.dataset / 'manifest.json', self.manifest)
        with self.assertRaisesRegex(evaluation.EvidenceError, 'SYMLINK_FORBIDDEN'):
            evaluation.load_dataset(self.dataset)

    def test_duplicate_json_keys_nonfinite_and_malformed_documents_fail(self):
        for payload, reason in [('{"x":1,"x":2}', 'DUPLICATE_JSON_KEY'), ('{"x":NaN}', 'NONFINITE_JSON'), ('{', 'ARTIFACT_UNREADABLE')]:
            with self.subTest(payload=payload):
                path = self.root / 'bad.json'
                path.write_text(payload)
                with self.assertRaisesRegex(evaluation.EvidenceError, reason):
                    evaluation.read_json(path)

    def test_private_content_is_not_accepted_as_synthetic_input(self):
        self.cases[0]['input'] = 'Contact learner@example.invalid'
        self.save_cases()
        with self.assertRaisesRegex(evaluation.EvidenceError, 'PRIVATE_INPUT'):
            evaluation.load_dataset(self.dataset)

    def test_labels_cannot_promote_unverified_scope(self):
        self.case('numeric')['expected']['scope'] = 'FINAL_ANSWER'
        self.save_cases()
        with self.assertRaisesRegex(evaluation.EvidenceError, 'LABEL_SCOPE'):
            evaluation.load_dataset(self.dataset)


class RunnerTests(FixtureWorkspace, unittest.TestCase):
    def test_identical_artifacts_produce_identical_report_and_digest(self):
        first = self.run_fixture(clock=lambda: 0)
        second = self.run_fixture(clock=lambda: 0)
        self.assertEqual(first, second)
        self.assertEqual('PASS', first['gate']['decision'])
        self.assertEqual('LOCAL_FIXTURE_REGRESSION', first['gate']['evidenceScope'])
        self.assertEqual('BLOCKED', first['productionPromotion']['decision'])
        self.assertEqual(first, evaluation.seal(first))
        self.assertFalse(first['independentGoldApproval'])
        self.assertEqual('UNAVAILABLE', first['economics']['state'])

    def test_validation_mode_does_not_score_observations(self):
        self.observations_path.unlink()
        report = self.run_fixture(mode='validate')
        self.assertEqual('PASS', report['gate']['decision'])
        self.assertEqual('DATASET_CONTRACT_VALIDATION', report['evidenceScope'])
        self.assertEqual([], report['results'])

    def test_connected_and_holdout_are_explicitly_blocked(self):
        for mode, reason in [('connected', 'NO_APPROVED_PROVIDER_ADAPTER'), ('holdout', 'PROTECTED_HOLDOUT_UNAVAILABLE')]:
            with self.subTest(mode=mode):
                report = self.run_fixture(mode=mode)
                self.assertEqual('BLOCKED', report['gate']['decision'])
                self.assertIn(reason, report['gate']['reasons'])
                self.assertEqual([], report['results'])
                self.assertEqual('BLOCKED', report['productionPromotion']['decision'])

    def test_configuration_bounds_provenance_and_inference_opt_in_are_enforced(self):
        variants = [('maxCases', 0), ('maxRuntimeSeconds', 61), ('maxInputBytes', True),
                    ('inferenceEnabled', True), ('datasetVersion', 'other')]
        for field, value in variants:
            with self.subTest(field=field):
                config = deepcopy(self.config)
                config[field] = value
                self.write(self.config_path, config)
                with self.assertRaises(evaluation.EvidenceError):
                    self.run_fixture()
        config = deepcopy(self.config)
        config['provenance']['model'] = 'mutable-alias'
        self.write(self.config_path, config)
        with self.assertRaisesRegex(evaluation.EvidenceError, 'PROVENANCE_INVALID'):
            self.run_fixture()

    def test_configured_case_limit_does_not_silently_sample(self):
        self.config['maxCases'] = 1
        self.write(self.config_path, self.config)
        with self.assertRaisesRegex(evaluation.EvidenceError, 'CASE_LIMIT'):
            self.run_fixture()

    def test_missing_output_counts_as_missing_and_fails_gate(self):
        self.observations['observations'] = [r for r in self.observations['observations'] if r['caseId'] != 'arithmetic']
        self.save_observations()
        report = self.run_fixture()
        self.assertEqual('FAIL', report['gate']['decision'])
        result = next(r for r in report['results'] if r['caseId'] == 'arithmetic')
        self.assertEqual('MISSING', result['status'])
        self.assertTrue(result['missing'])
        self.assertEqual(1, report['metrics']['finalAnswerCorrect']['missing'])
        self.assertGreater(report['metrics']['finalAnswerCorrect']['denominator'], 0)

    def test_errors_and_timeouts_remain_in_denominator(self):
        for status in ('ERROR', 'TIMEOUT'):
            with self.subTest(status=status):
                self.row('arithmetic')['status'] = status
                self.save_observations()
                report = self.run_fixture()
                self.assertEqual('FAIL', report['gate']['decision'])
                metric = report['metrics']['finalAnswerCorrect']
                self.assertEqual('INCOMPLETE', metric['state'])
                self.assertEqual(metric['denominator'] - 1, metric['numerator'])

    def test_runtime_bound_turns_unprocessed_cases_into_timeouts(self):
        ticks = iter([0] + [31] * len(self.cases))
        report = self.run_fixture(clock=lambda: next(ticks))
        self.assertEqual('FAIL', report['gate']['decision'])
        self.assertTrue(all(r['status'] == 'TIMEOUT' and r['missing'] for r in report['results']))

    def test_observation_substitution_and_extra_case_are_rejected(self):
        self.row('arithmetic')['answer'] = '6'
        self.write(self.observations_path, self.observations)
        with self.assertRaisesRegex(evaluation.EvidenceError, 'OBSERVATION_CHECKSUM'):
            self.run_fixture()
        self.row('arithmetic')['caseId'] = 'unknown-case'
        self.save_observations()
        with self.assertRaisesRegex(evaluation.EvidenceError, 'OBSERVATION_IDS'):
            self.run_fixture()

    def test_report_contains_no_raw_problem_or_answer_payload(self):
        report = self.run_fixture()
        for result in report['results']:
            self.assertNotIn('input', result)
            self.assertNotIn('answer', result)
            self.assertNotIn('scopeDetail', result)
        self.assertNotIn('input', report)
        self.assertEqual(set(evaluation.CRITICAL), set(report['criticalCounts']))

    def test_metric_zero_denominator_is_unavailable_not_zero(self):
        metric = evaluation.Metric(0, 0).document()
        self.assertIsNone(metric['value'])
        self.assertEqual('UNAVAILABLE', metric['state'])

    def test_cli_emits_safe_blocked_report_for_invalid_artifact(self):
        with patch.object(evaluation, 'run', side_effect=evaluation.EvidenceError('CASE_CHECKSUM')):
            with patch('sys.argv', ['solver-evaluation', '--output', str(self.root / 'output')]):
                self.assertEqual(2, evaluation.main())
        report = json.loads((self.root / 'output/solver-evaluation-report.json').read_text())
        self.assertEqual(['CASE_CHECKSUM'], report['gate']['reasons'])
        self.assertEqual('BLOCKED', report['productionPromotion']['decision'])
        self.assertTrue((self.root / 'output/solver-evaluation-summary.md').is_file())


class HonestyTests(FixtureWorkspace, unittest.TestCase):
    def score(self, identity, **changes):
        row = deepcopy(self.row(identity))
        row.update(changes)
        return evaluation.score_case(self.case(identity), row)

    def test_correct_exact_answer_and_wrong_schema_valid_answer_are_separate(self):
        correct = self.score('arithmetic')
        self.assertTrue(correct['measurements']['finalAnswerCorrect'])
        self.assertTrue(correct['measurements']['correctVerified'])
        wrong = self.score('arithmetic', answer='6')
        self.assertTrue(wrong['measurements']['schemaValid'])
        self.assertFalse(wrong['measurements']['finalAnswerCorrect'])
        self.assertIn('FALSE_VERIFIED', wrong['critical'])

    def test_exact_rational_normalization_is_bounded_and_not_symbolic_proof(self):
        self.assertTrue(evaluation.answer_equal('2/4', '1/2'))
        self.assertFalse(evaluation.answer_equal('1/0', '1/2'))
        self.assertFalse(evaluation.answer_equal('x+x', '2*x'))
        self.assertFalse(evaluation.answer_equal(None, None))
        self.assertTrue(self.score('rational', answer='2/4')['measurements']['finalAnswerCorrect'])

    def test_unsupported_input_cannot_become_authoritative(self):
        for identity in ('unsupported', 'ambiguous', 'malformed'):
            with self.subTest(identity=identity):
                result = self.score(identity, authoritative=True, semanticAccepted=True)
                self.assertIn('UNSUPPORTED_COERCION', result['critical'])

    def test_numeric_secondary_disagreement_and_false_claim_cannot_verify(self):
        for identity in ('numeric', 'secondary', 'disagreement', 'false-claim', 'domain-missing'):
            with self.subTest(identity=identity):
                result = self.score(identity, verification='VERIFIED', scope='FINAL_ANSWER', authoritative=True)
                self.assertIn('FALSE_VERIFIED', result['critical'])
                self.assertIn('SCOPE_WIDENING', result['critical'])

    def test_partial_evidence_cannot_be_promoted_or_scope_widened(self):
        self.assertIn('FALSE_VERIFIED', self.score('partial', verification='VERIFIED')['critical'])
        self.assertIn('FALSE_PARTIAL_SCOPE', self.score('partial', scope='FINAL_ANSWER')['critical'])
        self.assertIn('FALSE_PARTIAL_SCOPE', self.score('partial', lineageCurrent=False)['critical'])

    def test_exact_contradiction_must_be_detected_and_non_authoritative(self):
        for identity in ('contradiction', 'wrong-answer'):
            with self.subTest(identity=identity):
                self.assertIn('CONTRADICTION_IGNORED', self.score(identity, contradictionDetected=False)['critical'])
                self.assertIn('CONTRADICTION_IGNORED', self.score(identity, authoritative=True)['critical'])
                self.assertTrue(self.score(identity)['measurements']['contradictionDetected'])

    def test_domain_completeness_current_lineage_and_exact_proof_are_required(self):
        for changes in ({'domainComplete': False}, {'lineageCurrent': False}, {'proofKind': 'NUMERIC'},
                        {'proofKind': 'NONE'}, {'hallucinatedPremise': True}):
            with self.subTest(changes=changes):
                self.assertIn('FALSE_VERIFIED', self.score('arithmetic', **changes)['critical'])

    def test_stale_source_and_solution_reject_authority(self):
        for identity in ('stale-source', 'stale-solution'):
            with self.subTest(identity=identity):
                self.assertTrue(self.score(identity)['measurements']['staleLineageRejected'])
                self.assertIn('FALSE_VERIFIED', self.score(identity, verification='VERIFIED')['critical'])
                self.assertFalse(self.score(identity, authoritative=True)['measurements']['staleLineageRejected'])

    def test_calculus_scope_detail_is_bound_to_source(self):
        for identity in ('derivative', 'integral', 'limit'):
            with self.subTest(identity=identity):
                self.assertTrue(self.score(identity)['measurements']['scopePreserved'])
                result = self.score(identity, scopeDetail='all real numbers')
                self.assertIn('SCOPE_WIDENING', result['critical'])
                self.assertIn('FALSE_VERIFIED', result['critical'])

    def test_refusal_and_uncertainty_honesty_are_measured_separately(self):
        result = self.score('unsupported', uncertainty=False)
        self.assertTrue(result['measurements']['dispositionCorrect'])
        self.assertFalse(result['measurements']['uncertaintyHonest'])
        result = self.score('arithmetic', verification='UNVERIFIED', scope='NONE')
        self.assertTrue(result['measurements']['falseUnverified'])
        self.assertFalse(result['measurements']['verificationCorrect'])

    def test_schema_bypass_and_source_fabrication_are_hard_failures(self):
        self.assertIn('SCHEMA_BYPASS', self.score('arithmetic', schemaValid=False)['critical'])
        self.assertIn('SOURCE_FABRICATION', self.score('arithmetic', sourceFaithful=False)['critical'])
        malformed = self.score('malformed')
        self.assertFalse(malformed['measurements']['schemaValid'])
        self.assertNotIn('SCHEMA_BYPASS', malformed['critical'])

    def test_security_events_survive_even_missing_or_failed_outputs(self):
        for critical in ('PROMPT_INJECTION', 'PRIVACY', 'UNAUTHORIZED_ACCESS', 'SOURCE_FABRICATION'):
            with self.subTest(critical=critical):
                result = self.score('injection', status='ERROR', securityViolations=[critical])
                self.assertIn(critical, result['critical'])
                self.assertTrue(result['missing'])

    def test_hard_failure_reaches_whole_run_and_slice(self):
        self.row('arithmetic')['answer'] = '6'
        self.save_observations()
        report = self.run_fixture()
        self.assertEqual('FAIL', report['gate']['decision'])
        self.assertEqual(1, report['criticalCounts']['FALSE_VERIFIED'])
        self.assertGreater(report['slices']['ARITHMETIC']['metrics']['falseVerified']['numerator'], 0)


class EconomicsTests(unittest.TestCase):
    @staticmethod
    def attempt(**changes):
        row = {'requestId': 'request-1', 'attemptId': 'attempt-1', 'currency': 'USD',
               'pricingVersion': 'price-v1', 'workload': 'solver-engineering-v1', 'attribution': 'ALL_ATTEMPTS',
               'stage': 'PRIMARY', 'status': 'SUCCESS', 'retry': False, 'fallback': False, 'cacheHit': False,
               'inputTokens': 10, 'outputTokens': 5, 'reasoningTokens': 2, 'imageUnits': 0,
               'estimatedMicros': 20, 'reservedMicros': 100, 'committedMicros': 30,
               'invoicedMicros': None, 'forecastMicros': 50, 'latencyMs': 10}
        row.update(changes)
        return row

    def test_fixture_cost_is_unavailable_even_if_attempts_are_supplied(self):
        result = evaluation.economics([self.attempt()], 'LOCAL_FIXTURE_REGRESSION', 1, 1, 1)
        self.assertEqual('UNAVAILABLE', result['state'])
        self.assertIsNone(result['costPerVerified']['value'])
        self.assertIsNone(result['providerLatencyP95Ms'])

    def test_zero_correct_and_verified_denominators_are_unavailable(self):
        result = evaluation.economics([self.attempt()], 'REAL_PROVIDER', 0, 0, 1)
        self.assertIsNone(result['costPerCorrect']['value'])
        self.assertIsNone(result['costPerVerified']['value'])
        self.assertEqual('UNAVAILABLE', result['costPerVerified']['state'])

    def test_missing_usage_is_not_free_inference(self):
        self.assertEqual('MISSING_USAGE', evaluation.economics([], 'REAL_PROVIDER', 0, 0, 1)['reason'])
        result = evaluation.economics([self.attempt(estimatedMicros=None)], 'REAL_PROVIDER', 1, 1, 1)
        self.assertEqual('INCOMPLETE', result['state'])
        self.assertIsNone(result['costPerCorrect']['value'])

    def test_failed_retries_rejections_fallbacks_and_secondary_all_count(self):
        rows = [self.attempt(status='FAILED'), self.attempt(attemptId='attempt-2', retry=True, fallback=True),
                self.attempt(attemptId='attempt-3', stage='SECONDARY', status='REJECTED'),
                self.attempt(attemptId='attempt-4', stage='ARBITRATION', status='TIMEOUT')]
        result = evaluation.economics(rows, 'REAL_PROVIDER', 1, 1, 1)
        self.assertEqual(80, result['totals']['estimatedMicros'])
        self.assertEqual(80, result['costPerVerified']['value'])
        self.assertEqual(3, result['failedAttempts'])
        self.assertEqual(1, result['retryCount'])
        self.assertEqual(1, result['fallbackCount'])
        self.assertEqual({'PRIMARY': 2, 'SECONDARY': 1, 'ARBITRATION': 1}, result['stageAttempts'])

    def test_estimates_reservations_commitments_invoices_forecasts_are_distinct(self):
        result = evaluation.economics([self.attempt()], 'REAL_PROVIDER', 1, 1, 1)
        totals = result['totals']
        self.assertEqual((20, 100, 30, None, 50), tuple(totals[key] for key in
                         ('estimatedMicros', 'reservedMicros', 'committedMicros', 'invoicedMicros', 'forecastMicros')))
        self.assertEqual('ESTIMATED', result['costPerVerified']['state'])

    def test_incompatible_currency_pricing_workload_or_attribution_cannot_merge(self):
        for field in ('currency', 'pricingVersion', 'workload', 'attribution'):
            with self.subTest(field=field):
                second = self.attempt(attemptId='attempt-2', **{field: 'different'})
                with self.assertRaisesRegex(evaluation.EvidenceError, 'INCOMPATIBLE_PRICING'):
                    evaluation.economics([self.attempt(), second], 'REAL_PROVIDER', 1, 1, 1)

    def test_latency_tail_retains_outlier_and_missing_latency_is_unavailable(self):
        rows = [self.attempt(attemptId=f'attempt-{n}', latencyMs=1000 if n == 19 else 10) for n in range(20)]
        result = evaluation.economics(rows, 'REAL_PROVIDER', 1, 1, 1)
        self.assertEqual(10, result['providerLatencyP50Ms'])
        # Nearest-rank p95 of 20 observations excludes the largest one; p95 of two includes it.
        result = evaluation.economics([rows[0], rows[-1]], 'REAL_PROVIDER', 1, 1, 1)
        self.assertEqual(1000, result['providerLatencyP95Ms'])
        rows[0]['latencyMs'] = None
        result = evaluation.economics(rows, 'REAL_PROVIDER', 1, 1, 1)
        self.assertIsNone(result['providerLatencyP50Ms'])
        self.assertEqual('INCOMPLETE', result['state'])

    def test_incomplete_request_ledger_cannot_produce_unit_cost(self):
        result = evaluation.economics([self.attempt()], 'REAL_PROVIDER', 1, 1, 2)
        self.assertEqual('INCOMPLETE', result['state'])
        self.assertIsNone(result['costPerVerified']['value'])
        self.assertIsNone(result['providerLatencyP95Ms'])

    def test_missing_verified_outcomes_do_not_inherit_correct_count(self):
        result = evaluation.economics([self.attempt()], 'REAL_PROVIDER', 1, 0, 1)
        self.assertEqual(20, result['costPerCorrect']['value'])
        self.assertIsNone(result['costPerVerified']['value'])
        self.assertEqual(0, result['costPerVerified']['denominator'])

    def test_duplicate_attempts_invalid_counts_and_unbounded_usage_fail(self):
        with self.assertRaisesRegex(evaluation.EvidenceError, 'DUPLICATE_ATTEMPT'):
            evaluation.economics([self.attempt(), self.attempt()], 'REAL_PROVIDER', 1, 1, 1)
        for correct, verified, requests in ((0, 1, 1), (1, 1, 0), (True, 0, 1)):
            with self.subTest(counts=(correct, verified, requests)):
                with self.assertRaisesRegex(evaluation.EvidenceError, 'ECONOMIC_DENOMINATOR'):
                    evaluation.economics([self.attempt()], 'REAL_PROVIDER', correct, verified, requests)
        for value in (-1, True, 10 ** 16):
            with self.subTest(usage=value):
                with self.assertRaisesRegex(evaluation.EvidenceError, 'INVALID_USAGE'):
                    evaluation.economics([self.attempt(inputTokens=value)], 'REAL_PROVIDER', 1, 1, 1)

    def test_nonfinite_negative_and_unbounded_latency_fail(self):
        for latency in (float('nan'), float('inf'), -1, 3_600_001, True):
            with self.subTest(latency=latency):
                with self.assertRaisesRegex(evaluation.EvidenceError, 'INVALID_LATENCY'):
                    evaluation.economics([self.attempt(latencyMs=latency)], 'REAL_PROVIDER', 1, 1, 1)

    def test_cache_accounting_and_attempt_limit(self):
        result = evaluation.economics([self.attempt(cacheHit=True)], 'REAL_PROVIDER', 1, 1, 1)
        self.assertEqual(1, result['cacheHitCount'])
        with self.assertRaisesRegex(evaluation.EvidenceError, 'ATTEMPT_LIMIT'):
            evaluation.economics([self.attempt()] * 801, 'REAL_PROVIDER', 1, 1, 1)

    def test_economic_comparison_blocks_unapproved_or_incompatible_evidence(self):
        from evaluations.runners.solver_release import compare_economics
        baseline = evaluation.economics([self.attempt()], 'REAL_PROVIDER', 1, 1, 1)
        self.assertIn('UNAPPROVED_THRESHOLDS', compare_economics(baseline, baseline)['reasons'])
        approved = {'approvalStatus': 'APPROVED', 'approvalReference': 'docs/review.json',
                    'allowedIncrease': {'costPerCorrect': 0, 'costPerVerified': 0, 'providerLatencyP95Ms': 0}}
        for field in ('currency', 'pricingVersion', 'workload', 'attribution'):
            with self.subTest(field=field):
                candidate = deepcopy(baseline)
                candidate[field] = 'different'
                result = compare_economics(candidate, baseline, approved)
                self.assertEqual('BLOCKED', result['decision'])
                self.assertIn('INCOMPATIBLE_ECONOMICS', result['reasons'])
        fixture = evaluation.economics([], 'LOCAL_FIXTURE_REGRESSION', 0, 0, 1)
        self.assertEqual('BLOCKED', compare_economics(fixture, baseline, approved)['decision'])

    def test_cost_and_latency_regressions_use_only_explicit_approved_deltas(self):
        from evaluations.runners.solver_release import compare_economics
        baseline = evaluation.economics([self.attempt()], 'REAL_PROVIDER', 1, 1, 1)
        candidate = evaluation.economics([self.attempt(estimatedMicros=21, latencyMs=20)], 'REAL_PROVIDER', 1, 1, 1)
        approved = {'approvalStatus': 'APPROVED', 'approvalReference': 'docs/review.json',
                    'allowedIncrease': {'costPerCorrect': 0, 'costPerVerified': 0, 'providerLatencyP95Ms': 0}}
        result = compare_economics(candidate, baseline, approved)
        self.assertEqual('FAIL', result['decision'])
        self.assertEqual({'costPerCorrect', 'costPerVerified', 'providerLatencyP95Ms'}, set(result['regressions']))
        self.assertEqual('PASS', compare_economics(baseline, baseline, approved)['decision'])
        for delta in (None, -1, float('nan'), True):
            with self.subTest(delta=delta):
                gates = deepcopy(approved)
                gates['allowedIncrease']['costPerVerified'] = delta
                self.assertEqual('BLOCKED', compare_economics(baseline, baseline, gates)['decision'])


# Keep one owner-facing suite path while release artifact tests have their own module.
from evaluations.runners.test_solver_release import ReleaseTests  # noqa: E402,F401
