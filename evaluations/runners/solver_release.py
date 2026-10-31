#!/usr/bin/env python3
"""Fail-closed solver report comparison and explicit create-only local baseline promotion.

Approval authenticity is the repository's human review/branch protection boundary.
Hashes detect substitution, not who approved a document. This tool cannot mint a
production authorization bundle; external evidence and stage approvals remain required.
"""
from __future__ import annotations

import argparse
from datetime import datetime
import json
import math
import os
from pathlib import Path
import re

from jsonschema import Draft202012Validator

try:
    from .solver_evaluation import (ROOT, POLICY, CRITICAL, SHA, EvidenceError, aggregate,
        digest, exact_keys, read_json, require, safe_path, seal, load_dataset, load_config, load_observations)
except ImportError:
    from solver_evaluation import (ROOT, POLICY, CRITICAL, SHA, EvidenceError, aggregate,
        digest, exact_keys, read_json, require, safe_path, seal, load_dataset, load_config, load_observations)

COMPATIBILITY = ('datasetVersion', 'datasetSha256', 'caseSchemaVersion', 'metricVersion', 'evidenceScope')
HIGHER_BETTER = ('finalAnswerCorrect', 'dispositionCorrect', 'verificationCorrect', 'scopePreserved',
                 'uncertaintyHonest', 'sourceFaithful', 'schemaValid', 'semanticAccepted',
                 'stepValid', 'methodAppropriate', 'unsupportedHandled', 'contradictionDetected',
                 'staleLineageRejected', 'domainHandled', 'secondaryHandled')
LOWER_BETTER = ('falseVerified', 'falseAuthoritative', 'hallucinatedPremise', 'falseUnverified')


def validate_report(report):
    require(isinstance(report, dict), 'REPORT_SHAPE')
    require(report.get('reportSchemaVersion') == 'solver-evaluation-report-v1', 'REPORT_VERSION')
    require(isinstance(report.get('reportSha256'), str) and SHA.fullmatch(report['reportSha256'])
            and seal(report)['reportSha256'] == report['reportSha256'], 'REPORT_TAMPERED')
    schema, _ = read_json(ROOT / 'packages/schemas/solver-evaluation-report.schema.json')
    require(Draft202012Validator(schema).is_valid(report), 'REPORT_SCHEMA_INVALID')
    require(report.get('evidenceScope') == 'LOCAL_FIXTURE_REGRESSION', 'REPORT_SCOPE_UNSUPPORTED')
    require(report.get('gate', {}).get('evidenceScope') == report['evidenceScope'], 'REPORT_SCOPE_MISMATCH')
    require(report.get('gate', {}).get('decision') in {'PASS', 'FAIL', 'BLOCKED'}, 'REPORT_DECISION')
    require(report.get('productionPromotion', {}).get('decision') == 'BLOCKED', 'FIXTURE_PROMOTION_FORBIDDEN')
    for field in ('datasetSha256', 'configSha256', 'observationSha256', 'evaluationCodeSha256'):
        require(isinstance(report.get(field), str) and SHA.fullmatch(report[field]), 'REPORT_PROVENANCE')
    require(isinstance(report.get('gitCommit'), str) and re.fullmatch(r'[0-9a-f]{40,64}', report['gitCommit']), 'CODE_COMMIT_MISSING')
    require(report.get('metricVersion') == 'solver-metrics-v1'
            and report.get('caseSchemaVersion') == 'solver-evaluation-case-v1'
            and report.get('datasetVersion') == 'solver-engineering-v1', 'REPORT_COMPATIBILITY')
    results = report.get('results')
    require(isinstance(results, list) and 0 < len(results) <= 100 and len(results) == report.get('caseCount'), 'REPORT_CASE_COUNT')
    require(all(isinstance(r, dict) and isinstance(r.get('caseId'), str)
                and isinstance(r.get('critical'), list) and set(r['critical']) <= CRITICAL
                and type(r.get('missing')) is bool for r in results), 'REPORT_RESULTS')
    ids = [r['caseId'] for r in results]
    require(ids == sorted(set(ids)), 'REPORT_CASE_IDS')
    names = set(HIGHER_BETTER + LOWER_BETTER + ('correctVerified',))
    for result in results:
        require(isinstance(result.get('measurements'), dict) and set(result['measurements']) == names
                and all(v is None or type(v) is bool for v in result['measurements'].values()), 'REPORT_MEASUREMENTS')
        require(result.get('status') in {'COMPLETED', 'ERROR', 'TIMEOUT', 'MISSING'}
                and result['missing'] == (result['status'] != 'COMPLETED'), 'REPORT_STATUS')
    require(aggregate(results) == report.get('metrics'), 'REPORT_METRIC_MISMATCH')
    counts = {key: sum(key in r['critical'] for r in results) for key in sorted(CRITICAL)}
    require(report.get('criticalCounts') == counts, 'REPORT_CRITICAL_MISMATCH')
    require(report['metrics']['falseVerified']['numerator'] == counts['FALSE_VERIFIED'], 'REPORT_FALSE_VERIFIED_MISMATCH')
    require(isinstance(report.get('coverage'), dict) and report['coverage'], 'REPORT_COVERAGE')
    require(isinstance(report.get('slices'), dict) and set(report['slices']) == set(report['coverage']), 'REPORT_SLICES')
    for name, count in report['coverage'].items():
        require(type(count) is int and 0 < count <= len(results), 'REPORT_COVERAGE_COUNT')
        slice_data = report['slices'][name]
        require(isinstance(slice_data, dict) and slice_data.get('count') == count
                and set(slice_data.get('metrics', {})) == names, 'REPORT_SLICE_METRICS')
        for metric in slice_data['metrics'].values():
            exact_keys(metric, {'numerator', 'denominator', 'missing', 'value', 'state'}, 'REPORT_METRIC_SHAPE')
            n, d, m = metric['numerator'], metric['denominator'], metric['missing']
            require(all(type(v) is int for v in (n, d, m)) and 0 <= n <= d <= count and 0 <= m <= count, 'REPORT_METRIC_BOUNDS')
            require(metric['value'] == (n / d if d else None)
                    and metric['state'] == ('MEASURED' if d and not m else 'INCOMPLETE' if d else 'UNAVAILABLE'), 'REPORT_METRIC_VALUE')
    dataset = load_dataset()
    if dataset.sha256 == report['datasetSha256']:
        require(ids == [case['id'] for case in dataset.cases], 'REPORT_CASE_IDS')
        require(report['coverage'] == dataset.coverage, 'REPORT_COVERAGE_MISMATCH')
        for name in dataset.coverage:
            subset = [r for case, r in zip(dataset.cases, results) if name in case['slices']]
            require(report['slices'][name]['metrics'] == aggregate(subset), 'REPORT_SLICE_MISMATCH')
    require(report.get('provenance') == {'provider': 'LOCAL_FIXTURE', 'model': 'synthetic-observations-v1',
        'route': 'offline-scoring-v1', 'prompt': 'NONE_NO_INFERENCE', 'schema': 'solver-evaluation-case-v1',
        'verificationPolicy': 'verification-policy-v1'}, 'REPORT_ARTIFACT_SUBSTITUTION')
    require(report.get('economics', {}).get('state') == 'UNAVAILABLE'
            and report['economics'].get('reason') == 'FIXTURE_NOT_PROVIDER_COST', 'FIXTURE_COST_FORBIDDEN')
    return report


def approval_binding(report, policy):
    return {'reportSha256': report['reportSha256'], 'datasetSha256': report['datasetSha256'],
            'provenanceSha256': digest(report['provenance']), 'policySha256': digest(policy),
            'evaluationCodeSha256': report['evaluationCodeSha256']}


def verify_review(root, reference, sha256, report, policy):
    require(isinstance(reference, str) and reference.startswith('docs/') and reference.endswith('.json'), 'APPROVAL_REFERENCE')
    review, actual_hash = read_json(safe_path(root, reference))
    require(actual_hash == sha256, 'APPROVAL_SUBSTITUTED')
    exact_keys(review, {'schemaVersion', 'decision', 'evidenceScope', 'approvedBy', 'approvedAt',
                        'binding', 'rationaleReference', 'rationaleSha256'}, 'APPROVAL_SHAPE')
    require(review['schemaVersion'] == 'solver-baseline-review-v1' and review['decision'] == 'APPROVED'
            and review['evidenceScope'] == 'LOCAL_FIXTURE_REGRESSION', 'APPROVAL_MISSING')
    require(isinstance(review['approvedBy'], str) and re.fullmatch(r'[A-Za-z0-9_.-]{2,96}', review['approvedBy']), 'APPROVER_MISSING')
    require(isinstance(review['approvedAt'], str), 'APPROVAL_TIME')
    try:
        stamp = datetime.fromisoformat(review['approvedAt'].replace('Z', '+00:00'))
        require(stamp.tzinfo is not None, 'APPROVAL_TIME')
    except ValueError:
        raise EvidenceError('APPROVAL_TIME') from None
    require(review['binding'] == approval_binding(report, policy), 'APPROVAL_BINDING')
    reason_ref = review['rationaleReference']
    require(isinstance(reason_ref, str) and reason_ref.startswith('docs/') and reason_ref.endswith('.md'), 'RATIONALE_REFERENCE')
    reason = safe_path(root, reason_ref)
    require(reason.is_file() and reason.stat().st_size <= 65536, 'RATIONALE_MISSING')
    import hashlib
    require(hashlib.sha256(reason.read_bytes()).hexdigest() == review['rationaleSha256'], 'RATIONALE_SUBSTITUTED')
    return review


def load_baseline(root, reference, policy):
    require(reference.startswith('evaluations/baselines/solver-approved-') and reference.endswith('.json'), 'BASELINE_PATH')
    baseline, _ = read_json(safe_path(root, reference))
    exact_keys(baseline, {'schemaVersion', 'evidenceScope', 'report', 'reviewReference', 'reviewSha256', 'baselineSha256'}, 'BASELINE_SHAPE')
    require(baseline['schemaVersion'] == 'solver-approved-baseline-v1'
            and baseline['evidenceScope'] == 'LOCAL_FIXTURE_REGRESSION', 'BASELINE_SCOPE')
    unhashed = dict(baseline); unhashed.pop('baselineSha256')
    require(digest(unhashed) == baseline['baselineSha256'], 'BASELINE_TAMPERED')
    report = validate_report(baseline['report'])
    require(report['gate']['decision'] == 'PASS' and not any(report['criticalCounts'].values())
            and not any(r['missing'] for r in report['results']), 'BASELINE_NOT_PASSING')
    verify_review(root, baseline['reviewReference'], baseline['reviewSha256'], report, policy)
    return report


def compare(candidate, baseline=None, policy=None, scope='LOCAL_FIXTURE_REGRESSION'):
    """baseline is an already verified report from load_baseline, never an unreviewed CLI candidate.

    Programmatic callers must load approved artifacts through compare_files. This pure
    comparison kernel is separately testable with in-memory reviewed fixture evidence.
    """
    failed, blocked, regressions = [], [], []
    policy = policy if policy is not None else read_json(POLICY)[0]
    try:
        validate_report(candidate)
        require(policy.get('policyVersion') == 'solver-release-policy-v1'
                and set(policy.get('criticalGates', [])) == CRITICAL, 'POLICY_INVALID')
        if any(candidate['criticalCounts'].values()):
            failed.append('CRITICAL_SAFETY_FAILURE')
        if candidate['gate']['decision'] == 'FAIL':
            failed.append('CANDIDATE_REGRESSION')
        if candidate['gate']['decision'] == 'BLOCKED' or any(r['missing'] for r in candidate['results']):
            blocked.append('INCOMPLETE_OBSERVATIONS')
        require(scope in {'LOCAL_FIXTURE_REGRESSION', 'PRODUCTION'}, 'RELEASE_SCOPE_INVALID')
        if scope == 'PRODUCTION':
            blocked.extend(candidate['productionPromotion']['reasons'])
            blocked.append('EXTERNAL_PRODUCTION_AUTHORIZATION_REQUIRED')
        if policy.get('productionApproval') is not None or policy.get('approvedThresholds'):
            # No approved production policy exists in this engineering version. A future
            # policy needs a versioned reviewed implementation, not a boolean flip.
            blocked.append('UNSUPPORTED_THRESHOLD_APPROVAL')
        if baseline is None:
            blocked.append('NO_APPROVED_BASELINE')
        else:
            validate_report(baseline)
            for field in COMPATIBILITY:
                if candidate.get(field) != baseline.get(field):
                    blocked.append('INCOMPATIBLE_' + field.upper())
            if candidate['provenance'] != baseline['provenance']:
                blocked.append('ARTIFACT_PROVENANCE_MISMATCH')
            if set(candidate['coverage']) != set(baseline['coverage']):
                blocked.append('INCOMPATIBLE_SLICES')
            if not blocked:
                groups = [('ALL', candidate['metrics'], baseline['metrics'])]
                groups += [(name, candidate['slices'][name]['metrics'], baseline['slices'][name]['metrics'])
                           for name in policy['requiredSlices'] if name in candidate['slices'] and name in baseline['slices']]
                for name, current, previous in groups:
                    for metric in HIGHER_BETTER + LOWER_BETTER:
                        a, b = current[metric], previous[metric]
                        if a['denominator'] != b['denominator'] or a['missing'] or b['missing']:
                            blocked.append('INSUFFICIENT_COMPARABLE_OBSERVATIONS')
                            continue
                        if a['value'] is None and b['value'] is None:
                            continue  # Unmeasured step review remains unavailable, not success.
                        if a['value'] is None or b['value'] is None:
                            blocked.append('METRIC_UNAVAILABLE')
                            continue
                        worse = a['value'] < b['value'] if metric in HIGHER_BETTER else a['value'] > b['value']
                        if worse:
                            failed.append('DETERMINISTIC_REGRESSION')
                            regressions.append({'slice': name, 'metric': metric, 'before': b['value'], 'after': a['value']})
        if any(candidate['coverage'].get(s, 0) < 1 for s in policy['requiredSlices']):
            blocked.append('MISSING_CRITICAL_SLICE')
    except (EvidenceError, KeyError, TypeError, ValueError) as exc:
        blocked.append(str(exc) if isinstance(exc, EvidenceError) else 'INVALID_RELEASE_EVIDENCE')
    decision = 'FAIL' if failed else 'BLOCKED' if blocked else 'PASS'
    return {'decision': decision, 'evidenceScope': scope, 'reasons': sorted(set(failed + blocked)),
            'regressions': regressions, 'productionAuthorized': False}


def compare_files(root, candidate_reference, baseline_reference=None, scope='LOCAL_FIXTURE_REGRESSION'):
    try:
        policy = read_json(root / 'evaluations/baselines/solver-release-policy-v1.json')[0]
        candidate = read_json(safe_path(root, candidate_reference))[0]
        # A critical candidate failure must not be hidden by an absent or invalid baseline.
        preliminary = compare(candidate, None, policy, scope)
        if preliminary['decision'] == 'FAIL':
            return preliminary
        baseline = load_baseline(root, baseline_reference, policy) if baseline_reference else None
        return compare(candidate, baseline, policy, scope)
    except (EvidenceError, KeyError, TypeError, ValueError) as exc:
        return {'decision': 'BLOCKED', 'evidenceScope': scope, 'productionAuthorized': False,
                'reasons': [str(exc) if isinstance(exc, EvidenceError) else 'INVALID_RELEASE_EVIDENCE'], 'regressions': []}


def promote(root, report_reference, review_reference, review_sha256, destination, explicit_approve=False):
    require(explicit_approve and not os.environ.get('CI') and not os.environ.get('GITHUB_ACTIONS'), 'HUMAN_PROMOTION_REQUIRED')
    policy = read_json(root / 'evaluations/baselines/solver-release-policy-v1.json')[0]
    report = validate_report(read_json(safe_path(root, report_reference))[0])
    require(report['gate']['decision'] == 'PASS' and not any(report['criticalCounts'].values())
            and not any(r['missing'] for r in report['results']), 'REPORT_NOT_PASSING')
    require(all(report['metrics'][m]['value'] == 1 for m in ('finalAnswerCorrect', 'dispositionCorrect',
            'verificationCorrect', 'scopePreserved', 'uncertaintyHonest', 'sourceFaithful')), 'REPORT_NOT_PASSING')
    dataset = load_dataset()
    config, config_hash = load_config(dataset)
    _, observation_hash = load_observations(dataset, config)
    require(report['datasetSha256'] == dataset.sha256 and report['configSha256'] == config_hash
            and report['observationSha256'] == observation_hash, 'PROMOTION_ARTIFACT_MISMATCH')
    verify_review(root, review_reference, review_sha256, report, policy)
    require(destination.startswith('evaluations/baselines/solver-approved-') and destination.endswith('.json'), 'BASELINE_PATH')
    target = safe_path(root, destination)
    baseline = {'schemaVersion': 'solver-approved-baseline-v1', 'evidenceScope': 'LOCAL_FIXTURE_REGRESSION',
                'report': report, 'reviewReference': review_reference, 'reviewSha256': review_sha256}
    baseline['baselineSha256'] = digest(baseline)
    try:
        # Exclusive creation is the mutation boundary. Existing baselines are never overwritten.
        with target.open('x', encoding='utf-8') as stream:
            stream.write(json.dumps(baseline, sort_keys=True, indent=2) + '\n')
    except FileExistsError:
        raise EvidenceError('BASELINE_IMMUTABLE') from None
    return baseline


def compare_economics(candidate, baseline, approved_gates=None):
    """No invented numeric thresholds. Approved review supplies explicit absolute deltas."""
    reasons, regressions = [], []
    for report in (candidate, baseline):
        if report.get('state') != 'MEASURED':
            reasons.append('COST_LATENCY_UNAVAILABLE')
    for field in ('currency', 'pricingVersion', 'workload', 'attribution', 'expectedRequests'):
        if not candidate.get(field) or candidate.get(field) != baseline.get(field):
            reasons.append('INCOMPATIBLE_ECONOMICS')
    if not approved_gates or approved_gates.get('approvalStatus') != 'APPROVED' or not approved_gates.get('approvalReference'):
        reasons.append('UNAPPROVED_THRESHOLDS')
    if reasons:
        return {'decision': 'BLOCKED', 'reasons': sorted(set(reasons)), 'regressions': []}
    # These must be approved, finite, nonnegative deltas in the declared compatible units.
    for metric in ('costPerCorrect', 'costPerVerified', 'providerLatencyP95Ms'):
        delta = approved_gates.get('allowedIncrease', {}).get(metric)
        if type(delta) not in {int, float} or not math.isfinite(delta) or delta < 0:
            reasons.append('UNAPPROVED_THRESHOLDS')
            continue
        a, b = candidate.get(metric), baseline.get(metric)
        if metric.startswith('cost'):
            a = a.get('value') if isinstance(a, dict) else None
            b = b.get('value') if isinstance(b, dict) else None
        if any(type(v) not in {int, float} or not math.isfinite(v) or v < 0 for v in (a, b)):
            reasons.append('COST_LATENCY_UNAVAILABLE')
        elif a - b > delta:
            regressions.append(metric)
    return {'decision': 'FAIL' if regressions else 'BLOCKED' if reasons else 'PASS',
            'reasons': sorted(set(reasons)), 'regressions': regressions}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    check = sub.add_parser('compare')
    check.add_argument('--candidate', required=True)
    check.add_argument('--baseline')
    check.add_argument('--scope', choices=['LOCAL_FIXTURE_REGRESSION', 'PRODUCTION'], default='LOCAL_FIXTURE_REGRESSION')
    promotion = sub.add_parser('promote')
    promotion.add_argument('--report', required=True)
    promotion.add_argument('--review', required=True)
    promotion.add_argument('--review-sha256', required=True)
    promotion.add_argument('--destination', required=True)
    promotion.add_argument('--approve', action='store_true')
    args = parser.parse_args()
    if args.command == 'compare':
        result = compare_files(ROOT, args.candidate, args.baseline, args.scope)
    else:
        try:
            promote(ROOT, args.report, args.review, args.review_sha256, args.destination, args.approve)
            result = {'decision': 'PASS', 'evidenceScope': 'LOCAL_BASELINE_CREATION_ONLY', 'productionAuthorized': False}
        except (EvidenceError, OSError, KeyError, TypeError, ValueError) as exc:
            result = {'decision': 'BLOCKED', 'evidenceScope': 'LOCAL_BASELINE_CREATION_ONLY',
                      'reasons': [str(exc) if isinstance(exc, EvidenceError) else 'INVALID_PROMOTION'], 'productionAuthorized': False}
    print(json.dumps(result, indent=2, sort_keys=True))
    return {'PASS': 0, 'FAIL': 1, 'BLOCKED': 2}[result['decision']]


if __name__ == '__main__':
    raise SystemExit(main())
