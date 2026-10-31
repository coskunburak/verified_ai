#!/usr/bin/env python3
"""Offline Phase 5 engineering evaluation. Never grants mathematical or deployment authority.

Fixture observations are authored inputs to a scorer, not captured executions of the
backend. Owner acceptance separately executes the existing policy and verifier suites.
No provider adapter is enabled; connected and holdout modes return explicit BLOCKED.
"""
from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import asdict, dataclass
from fractions import Fraction
import hashlib
import json
import math
from pathlib import Path
import re
import subprocess
import time

from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[2]
DATASET = ROOT / 'evaluations/golden-datasets/solving/solver-v1'
SCHEMA = ROOT / 'packages/schemas/solver-evaluation-case.schema.json'
CONFIG = ROOT / 'evaluations/fixtures/solver/evaluation-config-v1.json'
OBSERVATIONS = ROOT / 'evaluations/fixtures/solver/evaluation-observations-v1.json'
POLICY = ROOT / 'evaluations/baselines/solver-release-policy-v1.json'
MAX_BYTES = 1_048_576
MAX_CASES = 100
SHA = re.compile(r'^[0-9a-f]{64}$')
SCOPES = {'NONE', 'FINAL_ANSWER', 'CHECKED_METHOD_CLAIMS', 'OPEN_INTERVAL',
          'ANTIDERIVATIVE_REPRESENTATIVE', 'PUNCTURED_NEIGHBORHOOD'}
CRITICAL = {'FALSE_VERIFIED', 'FALSE_PARTIAL_SCOPE', 'CONTRADICTION_IGNORED',
            'SCOPE_WIDENING', 'UNSUPPORTED_COERCION', 'SCHEMA_BYPASS',
            'PROMPT_INJECTION', 'PRIVACY', 'UNAUTHORIZED_ACCESS', 'SOURCE_FABRICATION'}
PARTITIONS = {'DEVELOPMENT', 'REGRESSION', 'HARD_TAIL', 'PROTECTED_HOLDOUT', 'PRODUCTION_CORRECTIONS'}


class EvidenceError(ValueError):
    """Controlled diagnostic code; never include raw input or parser exceptions."""


def require(condition, code):
    if not condition:
        raise EvidenceError(code)


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=True,
                      allow_nan=False).encode('utf-8')


def digest(value):
    return hashlib.sha256(canonical(value)).hexdigest()


def seal(report):
    report = dict(report)
    report.pop('reportSha256', None)
    report['reportSha256'] = digest(report)
    return report


def pairs(items):
    result = {}
    for key, value in items:
        require(key not in result, 'DUPLICATE_JSON_KEY')
        result[key] = value
    return result


def read_json(path, limit=MAX_BYTES):
    try:
        require(not path.is_symlink(), 'SYMLINK_FORBIDDEN')
        with path.open('rb') as stream:
            data = stream.read(limit + 1)
        require(len(data) <= limit, 'INPUT_TOO_LARGE')
        document = json.loads(data, object_pairs_hook=pairs,
                              parse_constant=lambda _: (_ for _ in ()).throw(EvidenceError('NONFINITE_JSON')))
        return document, hashlib.sha256(data).hexdigest()
    except (OSError, UnicodeError, json.JSONDecodeError, RecursionError):
        raise EvidenceError('ARTIFACT_UNREADABLE') from None


def safe_path(root, reference):
    require(isinstance(reference, str) and bool(re.fullmatch(r'[A-Za-z0-9_. /-]+', reference)), 'UNSAFE_PATH')
    path = Path(reference)
    require(not path.is_absolute() and all(x not in {'', '.', '..'} for x in reference.split('/'))
            and '//' not in reference and '\\' not in reference, 'UNSAFE_PATH')
    current = root
    for part in path.parts:
        current = current / part
        require(not current.is_symlink(), 'SYMLINK_FORBIDDEN')
    require(current.resolve().is_relative_to(root.resolve()), 'UNSAFE_PATH')
    return current


def exact_keys(value, keys, code):
    require(isinstance(value, dict) and set(value) == set(keys), code)


@dataclass(frozen=True)
class Dataset:
    manifest: dict
    cases: tuple[dict, ...]
    sha256: str
    coverage: dict


def load_dataset(directory=DATASET):
    manifest, manifest_hash = read_json(directory / 'manifest.json')
    exact_keys(manifest, {'schemaVersion', 'datasetVersion', 'caseSchemaVersion', 'caseSchemaSha256',
                         'caseFile', 'caseFileSha256', 'caseCount', 'requiredSlices', 'partitions',
                         'representativeness', 'independentGoldApproval', 'trainingEligibility'}, 'MANIFEST_SHAPE')
    require(manifest['schemaVersion'] == 'solver-dataset-manifest-v1'
            and manifest['datasetVersion'] == 'solver-engineering-v1'
            and manifest['caseSchemaVersion'] == 'solver-evaluation-case-v1', 'DATASET_VERSION')
    require(manifest['trainingEligibility'] == 'NOT_ELIGIBLE'
            and manifest['independentGoldApproval'] is False
            and manifest['representativeness'] == 'ENGINEERING_ONLY', 'GOVERNANCE_INVALID')
    require(manifest['partitions'] == {'DEVELOPMENT': 'AVAILABLE', 'REGRESSION': 'AVAILABLE',
            'HARD_TAIL': 'AVAILABLE', 'PROTECTED_HOLDOUT': 'BLOCKED_RESTRICTED_STORAGE_UNAVAILABLE',
            'PRODUCTION_CORRECTIONS': 'BLOCKED_GOVERNANCE_UNAVAILABLE'}, 'PARTITION_CONTRACT')
    schema, schema_hash = read_json(SCHEMA)
    require(schema_hash == manifest['caseSchemaSha256'], 'SCHEMA_CHECKSUM')
    cases, cases_hash = read_json(safe_path(directory, manifest['caseFile']))
    require(cases_hash == manifest['caseFileSha256'], 'CASE_CHECKSUM')
    require(isinstance(cases, list) and 0 < len(cases) <= MAX_CASES
            and type(manifest['caseCount']) is int and len(cases) == manifest['caseCount'], 'CASE_COUNT')
    validator = Draft202012Validator(schema)
    ids, families, sources = [], {}, {}
    for case in cases:
        require(validator.is_valid(case), 'CASE_SCHEMA_INVALID')
        require(case['partition'] in {'DEVELOPMENT', 'REGRESSION', 'HARD_TAIL'}, 'RESTRICTED_PARTITION')
        ids.append(case['id'])
        normalized = ' '.join(case['input'].casefold().split())
        for values, key in ((families, case['family']), (sources, normalized)):
            require(key not in values or values[key] == case['partition'], 'CROSS_PARTITION_LEAKAGE')
            values[key] = case['partition']
        expected = case['expected']
        require((expected['verification'] == 'UNVERIFIED') == (expected['scope'] == 'NONE'), 'LABEL_SCOPE')
        require(expected['verification'] != 'VERIFIED' or expected['answer'] is not None, 'MISSING_LABEL')
        require(not re.search(r'\bBearer\s+|[\w.+-]+@[\w.-]+\.[A-Za-z]{2,}|\beyJ[\w-]+\.', case['input']), 'PRIVATE_INPUT')
    require(ids == sorted(set(ids)), 'DUPLICATE_OR_UNORDERED_IDS')
    coverage = dict(sorted(Counter(s for c in cases for s in c['slices']).items()))
    policy, _ = read_json(POLICY)
    require(manifest['requiredSlices'] == policy['requiredSlices']
            and all(coverage.get(s, 0) > 0 for s in policy['requiredSlices']), 'MISSING_CRITICAL_SLICE')
    return Dataset(manifest, tuple(cases), digest([manifest_hash, schema_hash, cases_hash]), coverage)


def load_config(dataset, path=CONFIG):
    config, config_hash = read_json(path)
    exact_keys(config, {'schemaVersion', 'datasetVersion', 'maxCases', 'maxRuntimeSeconds',
                       'maxInputBytes', 'observationSha256', 'provenance', 'inferenceEnabled'}, 'CONFIG_SHAPE')
    require(config['schemaVersion'] == 'solver-evaluation-config-v1'
            and config['datasetVersion'] == dataset.manifest['datasetVersion']
            and config['inferenceEnabled'] is False, 'CONFIG_VERSION')
    for field, ceiling in [('maxCases', MAX_CASES), ('maxRuntimeSeconds', 60), ('maxInputBytes', MAX_BYTES)]:
        require(type(config[field]) is int and 0 < config[field] <= ceiling, 'CONFIG_BOUNDS')
    require(len(dataset.cases) <= config['maxCases'], 'CASE_LIMIT')
    require(isinstance(config['observationSha256'], str) and SHA.fullmatch(config['observationSha256']), 'OBSERVATION_DIGEST')
    require(config['provenance'] == {'provider': 'LOCAL_FIXTURE', 'model': 'synthetic-observations-v1',
            'route': 'offline-scoring-v1', 'prompt': 'NONE_NO_INFERENCE',
            'schema': 'solver-evaluation-case-v1', 'verificationPolicy': 'verification-policy-v1'}, 'PROVENANCE_INVALID')
    return config, config_hash


def validate_observation(row):
    exact_keys(row, {'caseId', 'status', 'answer', 'disposition', 'schemaValid', 'semanticAccepted',
                    'verification', 'scope', 'scopeDetail', 'uncertainty', 'sourceFaithful',
                    'hallucinatedPremise', 'authoritative', 'contradictionDetected', 'proofKind',
                    'lineageCurrent', 'domainComplete', 'stepValid', 'methodAppropriate',
                    'securityViolations'}, 'OBSERVATION_SHAPE')
    require(row['status'] in {'COMPLETED', 'TIMEOUT', 'ERROR', 'MISSING'}, 'OBSERVATION_STATUS')
    require(row['disposition'] in {'CANDIDATE', 'REFUSE', 'REVIEW', 'REJECT'}, 'OBSERVATION_DISPOSITION')
    require(row['verification'] in {'VERIFIED', 'PARTIALLY_VERIFIED', 'UNVERIFIED'}
            and row['scope'] in SCOPES and row['proofKind'] in {'EXACT', 'PARTIAL', 'NUMERIC', 'NONE'}, 'OBSERVATION_VERDICT')
    for field in ('schemaValid', 'semanticAccepted', 'uncertainty', 'sourceFaithful', 'hallucinatedPremise',
                  'authoritative', 'contradictionDetected', 'lineageCurrent', 'domainComplete'):
        require(type(row[field]) is bool, 'OBSERVATION_BOOLEAN')
    for field in ('stepValid', 'methodAppropriate'):
        require(row[field] is None or type(row[field]) is bool, 'OBSERVATION_REVIEW')
    for field in ('answer', 'scopeDetail'):
        require(row[field] is None or isinstance(row[field], str) and len(row[field]) <= 512, 'OBSERVATION_TEXT')
    require(isinstance(row['securityViolations'], list) and len(row['securityViolations']) <= len(CRITICAL)
            and all(isinstance(x, str) and x in CRITICAL for x in row['securityViolations']), 'OBSERVATION_SECURITY')


def load_observations(dataset, config):
    artifact, artifact_hash = read_json(OBSERVATIONS, config['maxInputBytes'])
    require(artifact_hash == config['observationSha256'], 'OBSERVATION_CHECKSUM')
    exact_keys(artifact, {'schemaVersion', 'evidenceScope', 'observations'}, 'OBSERVATIONS_SHAPE')
    require(artifact['schemaVersion'] == 'solver-fixture-observations-v1'
            and artifact['evidenceScope'] == 'LOCAL_FIXTURE_REGRESSION', 'OBSERVATIONS_SCOPE')
    rows = artifact['observations']
    require(isinstance(rows, list) and len(rows) <= config['maxCases'], 'OBSERVATION_COUNT')
    ids = []
    for row in rows:
        validate_observation(row)
        ids.append(row['caseId'])
    require(ids == sorted(set(ids)) and set(ids) <= {c['id'] for c in dataset.cases}, 'OBSERVATION_IDS')
    return {r['caseId']: r for r in rows}, artifact_hash


@dataclass(frozen=True)
class Metric:
    numerator: int
    denominator: int
    missing: int = 0

    def document(self):
        return {**asdict(self), 'value': self.numerator / self.denominator if self.denominator else None,
                'state': 'MEASURED' if self.denominator and not self.missing else 'INCOMPLETE' if self.denominator else 'UNAVAILABLE'}


def answer_equal(actual, expected):
    # Only exact bounded rationals are normalized. Symbolic strings are authored labels,
    # not a second mathematical verifier and not a CAS invocation on untrusted input.
    if actual is None or expected is None:
        return False
    pattern = r'-?\d{1,64}(?:/\d{1,64})?'
    if re.fullmatch(pattern, actual) and re.fullmatch(pattern, expected):
        try:
            return Fraction(actual) == Fraction(expected)
        except ZeroDivisionError:
            return False
    return actual == expected


def score_case(case, row):
    expected = case['expected']
    absent = row is None or row['status'] != 'COMPLETED'
    if row is not None:
        validate_observation(row)
    hard = set(row['securityViolations']) if row else set()
    measurements = {'finalAnswerCorrect': None, 'schemaValid': False, 'semanticAccepted': False,
                    'dispositionCorrect': False, 'verificationCorrect': False, 'scopePreserved': False,
                    'uncertaintyHonest': False, 'sourceFaithful': False, 'hallucinatedPremise': None,
                    'falseAuthoritative': None, 'stepValid': None, 'methodAppropriate': None,
                    'contradictionDetected': None, 'staleLineageRejected': None, 'domainHandled': None,
                    'unsupportedHandled': None, 'secondaryHandled': None,
                    'falseVerified': False, 'correctVerified': False, 'falseUnverified': False}
    if expected['answer'] is not None:
        measurements['finalAnswerCorrect'] = False
    if absent:
        return {'caseId': case['id'], 'status': row['status'] if row else 'MISSING',
                'measurements': measurements, 'critical': sorted(hard), 'missing': True}
    verdict = row['verification']
    scope_ok = row['scope'] == expected['scope'] and row['scopeDetail'] == expected['scopeDetail']
    correct = answer_equal(row['answer'], expected['answer']) if expected['answer'] is not None else None
    exact_eligible = (expected['verification'] == 'VERIFIED' and correct is True and scope_ok
                      and row['proofKind'] == 'EXACT' and row['lineageCurrent'] and row['domainComplete']
                      and row['schemaValid'] and row['sourceFaithful'] and not row['hallucinatedPremise'])
    false_verified = verdict == 'VERIFIED' and not exact_eligible
    if false_verified:
        hard.add('FALSE_VERIFIED')
    if verdict == 'PARTIALLY_VERIFIED' and (expected['verification'] != verdict or not scope_ok
            or row['proofKind'] != 'PARTIAL' or not row['lineageCurrent']):
        hard.add('FALSE_PARTIAL_SCOPE')
    if verdict != 'UNVERIFIED' and not scope_ok:
        hard.add('SCOPE_WIDENING')
    if case['problemClass'] in {'CONTRADICTION', 'SCHEMA_VALID_WRONG'} and (
            not row['contradictionDetected'] or verdict != 'UNVERIFIED' or row['authoritative']):
        hard.add('CONTRADICTION_IGNORED')
    if (case['canonicalSupport'] != 'SUPPORTED' or expected['disposition'] != 'CANDIDATE') and (row['authoritative'] or row['semanticAccepted']):
        hard.add('UNSUPPORTED_COERCION')
    if not row['schemaValid'] and (row['semanticAccepted'] or row['authoritative'] or verdict != 'UNVERIFIED'):
        hard.add('SCHEMA_BYPASS')
    if not row['sourceFaithful']:
        hard.add('SOURCE_FABRICATION')
    measurements.update(finalAnswerCorrect=correct, schemaValid=row['schemaValid'],
        semanticAccepted=row['semanticAccepted'], dispositionCorrect=row['disposition'] == expected['disposition'],
        verificationCorrect=verdict == expected['verification'] and not false_verified,
        scopePreserved=scope_ok, uncertaintyHonest=row['uncertainty'] == expected['uncertainty'],
        sourceFaithful=row['sourceFaithful'], hallucinatedPremise=row['hallucinatedPremise'],
        falseAuthoritative=row['authoritative'] and (correct is not True or not exact_eligible),
        stepValid=row['stepValid'], methodAppropriate=row['methodAppropriate'],
        falseVerified=false_verified, correctVerified=verdict == 'VERIFIED' and exact_eligible,
        falseUnverified=verdict == 'UNVERIFIED' and expected['verification'] == 'VERIFIED')
    cls = case['problemClass']
    if cls in {'CONTRADICTION', 'SCHEMA_VALID_WRONG'}:
        measurements['contradictionDetected'] = row['contradictionDetected'] and verdict == 'UNVERIFIED'
    if cls in {'STALE_SOURCE', 'STALE_SOLUTION'}:
        measurements['staleLineageRejected'] = not row['lineageCurrent'] and verdict == 'UNVERIFIED' and not row['authoritative']
    if cls in {'DOMAIN', 'DOMAIN_INCOMPLETE', 'PARTIAL'}:
        measurements['domainHandled'] = scope_ok and verdict == expected['verification']
    if cls in {'UNSUPPORTED', 'AMBIGUOUS', 'MALFORMED'}:
        measurements['unsupportedHandled'] = row['disposition'] == expected['disposition'] and not row['authoritative']
    if cls in {'SECONDARY_AGREEMENT', 'DISAGREEMENT'}:
        measurements['secondaryHandled'] = verdict == 'UNVERIFIED' and row['uncertainty']
    return {'caseId': case['id'], 'status': row['status'], 'measurements': measurements,
            'critical': sorted(hard), 'missing': False}


def aggregate(results):
    require(bool(results), 'EMPTY_RESULTS')
    metrics = {}
    for name in results[0]['measurements']:
        values = [r['measurements'][name] for r in results]
        metrics[name] = Metric(sum(v is True for v in values), sum(v is not None for v in values),
                               sum(r['missing'] for r in results)).document()
    return metrics


def economics(attempts, scope, correct_count, verified_count, expected_requests):
    """Attempt ledger aggregates; caller must supply independently supported denominators.

    No provider ledger importer is enabled. This pure reducer is ready for a governed
    adapter. Reservations, commitments, estimates, invoices and forecasts stay separate.
    """
    unavailable = {'state': 'UNAVAILABLE', 'reason': 'NO_REAL_INFERENCE', 'value': None}
    if scope != 'REAL_PROVIDER':
        return {'state': 'UNAVAILABLE', 'reason': 'FIXTURE_NOT_PROVIDER_COST',
                'costPerCorrect': unavailable, 'costPerVerified': unavailable,
                'providerLatencyP50Ms': None, 'providerLatencyP95Ms': None}
    require(type(correct_count) is int and type(verified_count) is int and type(expected_requests) is int
            and 0 <= verified_count <= correct_count <= expected_requests and expected_requests > 0, 'ECONOMIC_DENOMINATOR')
    if not attempts:
        return {'state': 'UNAVAILABLE', 'reason': 'MISSING_USAGE'}
    require(len(attempts) <= 800, 'ATTEMPT_LIMIT')
    currencies, pricing, workload, attribution, seen = set(), set(), set(), set(), set()
    totals = {k: 0 for k in ('inputTokens', 'outputTokens', 'reasoningTokens', 'imageUnits',
                            'estimatedMicros', 'reservedMicros', 'committedMicros', 'invoicedMicros', 'forecastMicros')}
    missing = set()
    latencies = []
    requests = set()
    stages = Counter()
    retries = fallbacks = cache_hits = failed = 0
    for row in attempts:
        require(isinstance(row, dict), 'ATTEMPT_SHAPE')
        require(isinstance(row.get('requestId'), str) and isinstance(row.get('attemptId'), str)
                and row['attemptId'] not in seen, 'DUPLICATE_ATTEMPT')
        seen.add(row['attemptId']); requests.add(row['requestId'])
        for key, values in [('currency', currencies), ('pricingVersion', pricing), ('workload', workload), ('attribution', attribution)]:
            require(isinstance(row.get(key), str) and bool(row[key]), 'MISSING_PRICING')
            values.add(row[key])
        require(row.get('stage') in {'PRIMARY', 'SECONDARY', 'ARBITRATION'}, 'ATTEMPT_STAGE')
        require(row.get('status') in {'SUCCESS', 'FAILED', 'REJECTED', 'TIMEOUT'}, 'ATTEMPT_STATUS')
        stages[row['stage']] += 1
        failed += row['status'] != 'SUCCESS'
        for field in ('retry', 'fallback', 'cacheHit'):
            require(type(row.get(field)) is bool, 'ATTEMPT_FLAGS')
        retries += row['retry']; fallbacks += row['fallback']; cache_hits += row['cacheHit']
        for key in totals:
            value = row.get(key)
            if value is None:
                missing.add(key)
            else:
                require(type(value) is int and 0 <= value <= 10**15, 'INVALID_USAGE')
                totals[key] += value
        value = row.get('latencyMs')
        if value is None:
            missing.add('latencyMs')
        else:
            require(type(value) in {int, float} and math.isfinite(value) and 0 <= value <= 3_600_000, 'INVALID_LATENCY')
            latencies.append(value)
    require(all(len(x) == 1 for x in (currencies, pricing, workload, attribution)), 'INCOMPATIBLE_PRICING')
    require(len(requests) <= expected_requests, 'REQUEST_COUNT')
    incomplete = len(requests) != expected_requests
    for key in missing:
        if key in totals:
            totals[key] = None
    estimate = totals['estimatedMicros']
    def per_count(count):
        available = estimate is not None and count > 0 and not incomplete
        return {'state': 'ESTIMATED' if available else 'UNAVAILABLE',
                'value': estimate / count if available else None, 'denominator': count}
    latency_complete = not incomplete and 'latencyMs' not in missing and bool(latencies)
    def percentile(p):
        return sorted(latencies)[math.ceil(len(latencies) * p) - 1] if latency_complete else None
    return {'state': 'INCOMPLETE' if incomplete or missing.intersection({'estimatedMicros', 'inputTokens', 'outputTokens', 'reservedMicros', 'committedMicros'}) or not latency_complete else 'MEASURED',
            'currency': next(iter(currencies)), 'pricingVersion': next(iter(pricing)),
            'workload': next(iter(workload)), 'attribution': next(iter(attribution)),
            'totals': totals, 'missingFields': sorted(missing), 'attemptCount': len(attempts),
            'requestCount': len(requests), 'expectedRequests': expected_requests, 'failedAttempts': failed,
            'stageAttempts': dict(sorted(stages.items())), 'retryCount': retries,
            'fallbackCount': fallbacks, 'cacheHitCount': cache_hits,
            'costPerCorrect': per_count(correct_count), 'costPerVerified': per_count(verified_count),
            'providerLatencyP50Ms': percentile(.50), 'providerLatencyP95Ms': percentile(.95)}


def run(mode='local', directory=DATASET, config_path=CONFIG, clock=time.monotonic):
    require(mode in {'validate', 'local', 'connected', 'holdout'}, 'MODE_INVALID')
    start = clock()
    dataset = load_dataset(directory)
    config, config_hash = load_config(dataset, config_path)
    scope = {'validate': 'DATASET_CONTRACT_VALIDATION', 'local': 'LOCAL_FIXTURE_REGRESSION',
             'connected': 'CONNECTED', 'holdout': 'PROTECTED_HOLDOUT'}[mode]
    report = {'reportSchemaVersion': 'solver-evaluation-report-v1', 'metricVersion': 'solver-metrics-v1',
              'datasetVersion': dataset.manifest['datasetVersion'], 'datasetSha256': dataset.sha256,
              'caseSchemaVersion': dataset.manifest['caseSchemaVersion'], 'configSha256': config_hash,
              'provenance': config['provenance'], 'coverage': dataset.coverage,
              'caseCount': len(dataset.cases), 'evidenceScope': scope,
              'labelProvenance': 'SYNTHETIC_AUTHOR_JUDGMENT', 'independentGoldApproval': False,
              'economics': economics([], scope, 0, 0, len(dataset.cases)),
              'results': [], 'metrics': {}, 'slices': {}, 'criticalCounts': {x: 0 for x in sorted(CRITICAL)},
              'productionPromotion': {'decision': 'BLOCKED', 'reasons': ['NO_REAL_MODEL_EVIDENCE',
                  'NO_PROTECTED_HOLDOUT', 'NO_INDEPENDENT_GOLD_APPROVAL', 'NO_APPROVED_REPRESENTATIVE_BENCHMARK',
                  'NO_APPROVED_PRODUCTION_THRESHOLDS', 'NO_APPROVED_COMPARABLE_BASELINE',
                  'NO_OBSERVED_PROVIDER_COST_LATENCY', 'NO_STAGE_APPROVAL']}}
    # Commit plus content digests describe uncommitted implementation without claiming HEAD contains it.
    report['evaluationCodeSha256'] = digest({name: hashlib.sha256(Path(__file__).with_name(name).read_bytes()).hexdigest()
        for name in ('solver_evaluation.py', 'solver_release.py')})
    try:
        report['gitCommit'] = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True, timeout=5).strip()
    except (OSError, subprocess.SubprocessError):
        report['gitCommit'] = 'UNAVAILABLE'
    reasons = []
    if mode in {'connected', 'holdout'}:
        decision = 'BLOCKED'
        reasons = ['NO_APPROVED_PROVIDER_ADAPTER' if mode == 'connected' else 'PROTECTED_HOLDOUT_UNAVAILABLE']
    elif mode == 'validate':
        decision = 'PASS'
    else:
        observations, artifact_hash = load_observations(dataset, config)
        report['observationSha256'] = artifact_hash
        results = []
        for case in dataset.cases:
            timed_out = clock() - start > config['maxRuntimeSeconds']
            row = observations.get(case['id'])
            if timed_out:
                row = dict(row, status='TIMEOUT') if row else None
            results.append(score_case(case, row))
        report['results'] = results
        report['metrics'] = aggregate(results)
        for case, result in zip(dataset.cases, results):
            for critical in result['critical']:
                report['criticalCounts'][critical] += 1
        for slice_name in dataset.coverage:
            subset = [r for c, r in zip(dataset.cases, results) if slice_name in c['slices']]
            report['slices'][slice_name] = {'count': len(subset), 'metrics': aggregate(subset)}
        contract_metrics = ('dispositionCorrect', 'verificationCorrect', 'scopePreserved', 'uncertaintyHonest', 'sourceFaithful')
        failed = any(report['criticalCounts'].values()) or any(r['missing'] for r in results)
        failed |= any(report['metrics'][m]['value'] != 1 for m in contract_metrics)
        failed |= report['metrics']['finalAnswerCorrect']['value'] != 1
        failed |= any(report['metrics'][m]['numerator'] > 0 for m in ('falseAuthoritative', 'hallucinatedPremise'))
        decision = 'FAIL' if failed else 'PASS'
        reasons = ['FIXTURE_CONTRACT_REGRESSION'] if failed else []
    report['gate'] = {'decision': decision, 'evidenceScope': scope, 'reasons': reasons}
    report['diagnostics'] = {'event': 'EVALUATION_COMPLETED', 'gateOutcome': decision,
                             'falseVerifiedCount': report['criticalCounts']['FALSE_VERIFIED'],
                             'productionPromotion': 'BLOCKED'}
    report = seal(report)
    report_schema, _ = read_json(ROOT / 'packages/schemas/solver-evaluation-report.schema.json')
    require(Draft202012Validator(report_schema).is_valid(report), 'REPORT_SCHEMA_INVALID')
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mode', choices=['validate', 'local', 'connected', 'holdout'], default='local')
    parser.add_argument('--output', type=Path, default=ROOT / '.generated/evaluations/solver-local')
    args = parser.parse_args()
    try:
        report = run(args.mode)
    except (EvidenceError, TypeError, KeyError, ValueError) as exc:
        code = str(exc) if isinstance(exc, EvidenceError) else 'INVALID_EVIDENCE'
        report = seal({'reportSchemaVersion': 'solver-evaluation-report-v1',
                       'gate': {'decision': 'BLOCKED', 'evidenceScope': 'INVALID_EVIDENCE', 'reasons': [code]},
                       'productionPromotion': {'decision': 'BLOCKED', 'reasons': [code]},
                       'diagnostics': {'event': 'DATASET_OR_RUN_FAILURE', 'reason': code}})
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / 'solver-evaluation-report.json').write_text(json.dumps(report, indent=2, sort_keys=True) + '\n')
    summary = (f"# Solver engineering evaluation\n\nLocal gate: {report['gate']['decision']}\n\n"
               f"Evidence scope: {report['gate']['evidenceScope']}\n\n"
               "Production promotion: BLOCKED. Fixture success is neither model quality nor release approval.\n\n"
               "Provider latency/cost, protected holdout and independent gold approval are unavailable.\n")
    (args.output / 'solver-evaluation-summary.md').write_text(summary)
    print(json.dumps(report['diagnostics'], sort_keys=True))
    return {'PASS': 0, 'FAIL': 1, 'BLOCKED': 2}[report['gate']['decision']]


if __name__ == '__main__':
    raise SystemExit(main())
