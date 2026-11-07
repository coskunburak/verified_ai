#!/usr/bin/env python3
"""Owner-run, current-source evidence only. No production data, environment dump or network access."""
import hashlib
import json
import re
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path
from sprint_6_9_evidence import junit

ROOT = Path(__file__).resolve().parents[2]
GATES = tuple('ABCDEFG')
BACKEND = ['AttemptSubmissionRequestTest', 'StudentAttemptSubmissionControllerTest', 'StudentAttemptControllerTest',
           'StudentAttemptIntegrationTest', 'AttemptAnalysisPolicyTest', 'AttemptVerifierClientTest',
           'LearnerMasteryPolicyTest', 'MethodComparisonEvidenceTest', 'CanonicalManualStepNormalizerTest',
           'AttemptMathParserV2Test', 'ModularityTest', 'AccountPrivacyApplicationServiceTest', 'RateLimitFilterTest',
           'PrimarySolveIntegrationTest', 'VerificationPolicyTest', 'VerificationExecutionServiceTest',
           'TutorSessionControllerTest', 'ProgressiveHintIntegrationTest', 'AttemptWorkNormalizerTest',
           'AttemptWorkProvenanceTest', 'AttemptWorkRequestTest', 'AttemptWorkControllerTest', 'AttemptWorkIntegrationTest', 'AttemptEvaluationPreparerTest', 'AttemptEvaluationIntegrationTest', 'AttemptEvaluationControllerTest', 'AttemptMistakeClassifierTest', 'AttemptMistakeIntegrationTest', 'AttemptMistakeControllerTest']
VERIFIER = ['test_attempt_verification_and_counterexample', 'test_attempt_v2_product', 'test_method_comparison_v2',
            'test_sprint_7_1_evidence', 'test_sprint_7_2_evidence', 'test_structured_work_golden', 'test_sprint_7_3_evidence', 'test_sprint_7_4_evidence', 'test_attempt_mistake_classification', 'test_attempt_evaluation_golden']
CRITICAL = ['AttemptMistakeIntegrationTest.confirmedDisproofPersistsCausalEvidenceWithoutInventingACategory',
            'AttemptMistakeClassifierTest.seventeenSignaturesMatchSharedCategoryAndOnlyActiveCanonicalSkills',
            'AttemptMistakeClassifierTest.inactiveOrAmbiguousSkillIsNeverInvented',
            'AttemptMistakeClassifierTest.genericDisproofNeverBecomesConceptOrReviewedOther',
            'AttemptMistakeClassifierTest.historicalSignatureWithoutPolicyKeepsDisproofButRequiresReview',
            'AttemptMistakeClassifierTest.incompleteValidWorkIsNotAConfirmedIncompleteSolutionError',
            'AttemptMistakeClassifierTest.uncertaintyUnsupportedAndSourceReviewRemainDistinct',
            'AttemptMistakeClassifierTest.forgedEvidenceIdentityAndUncertainPrefixCannotBeClassified',
            'AttemptMistakeClassifierTest.validPrefixLocatesTheSecondStepAndNeverDuplicatesDownstreamErrors',
            'AttemptMistakeClassifierTest.unconfirmedCaptureRequiresReviewAndCannotAcquireProof',
            'AttemptMistakeClassifierTest.strictContractRejectsAuthorityProbabilityTrainingAndMissingEvidence',
            'AttemptMistakeClassifierTest.disputeUnknownTaxonomyAndWrongEvaluationFailClosed',
            'AttemptMistakeIntegrationTest.classificationTelemetryIsBoundedAndProductionFlagNeedsAuthorization',
            'AttemptMistakeIntegrationTest.classificationIsImmutableExportableAndDeletedWithoutMasterySideEffects',
            'AttemptMistakeIntegrationTest.concurrentIdenticalRequestsAndConflictingKeys',
            'AttemptMistakeIntegrationTest.forgedForeignMissingEvidenceAndStaleSourceAreRejected',
            'AttemptMistakeIntegrationTest.rollbackDisablementPreservesHistoryAndExactReplay',
            'AttemptMistakeIntegrationTest.transactionRollbackAndSessionCascadeDoNotLeavePartialPublication',
            'AttemptMistakeIntegrationTest.databaseRejectsMismatchedEvaluationAndForgedProof',
            'AttemptMistakeIntegrationTest.upgradeFromV035AddsNoBackfillAndPreservesOldEvidence',
            'AttemptMistakeControllerTest.classificationStrictInputBolaAndReadOnlyDisabledCapability',
            'AttemptEvaluationIntegrationTest.sessionDeletionUsesParentCascadeAndRemovesPendingAndPublishedEvidence',
            'AttemptEvaluationIntegrationTest.unexpiredOrAlreadyReclaimedLeaseCannotBeStolen',
            'AttemptEvaluationIntegrationTest.reviewedRealScopeAndFinalCompletenessRemainIndependent',
            'AttemptEvaluationIntegrationTest.rateLimitAndForeignExecutionCannotPublish',
            'AttemptEvaluationIntegrationTest.durableIndependentEvidencePrivacyRollbackAndImmutability',
            'AttemptEvaluationIntegrationTest.concurrencyReplayAndDifferentPayloadConflictNeverDuplicateArtifact',
            'AttemptEvaluationIntegrationTest.failuresAreRecoverableAndLateExecutionCannotOverwriteNewLease',
            'AttemptEvaluationIntegrationTest.sourceChangeSupersessionAndDeletedOwnerBlockLatePublication',
            'AttemptEvaluationIntegrationTest.upgradeFromV034DoesNotBackfillAssessments',
            'AttemptEvaluationControllerTest.explicitEvaluationStrictInputAuthorizationAndDisabledHistory',
            'AttemptWorkIntegrationTest.upgradeFromV033PreservesHistoricalEvidenceWithoutBackfill',
            'AttemptWorkIntegrationTest.captureIsDurableUnassessedImmutableAndExportable',
            'AttemptWorkIntegrationTest.concurrentReplayRevisionConflictAndRollbackRecovery',
            'AttemptWorkIntegrationTest.staleSourceBolaAndDeletedAccountCannotPublish',
            'AttemptWorkIntegrationTest.nativeCorrectionsPreserveRawAndRevisionsDeleteTogether',
            'AttemptWorkControllerTest.authenticationBolaStaleAuthorityAndPayloadLimits',
            'StudentAttemptIntegrationTest.upgradeFromV032PreservesHistoricalEvidenceWithoutRewritingIt',
            'StudentAttemptIntegrationTest.answerOnlyAndReasoningAreExactDurableUnassessedEvidence',
            'StudentAttemptIntegrationTest.finalAnswerAndStepMembershipCannotBeChangedAfterAcceptance',
            'StudentAttemptIntegrationTest.finalAnswerConcurrentReplayStaleAndDisabledHistory',
            'StudentAttemptIntegrationTest.deletedAccountCannotBeRecreatedByLateAssessment',
            'StudentAttemptSubmissionControllerTest.answerAcknowledgementHistoryAndLegacyProjection']
IOS_REQUIRED = {'MathVisualizationTests': 8, 'MathVisualizationUITests': 3, 'AttemptProductContractTests': 3,
                'AttemptAnalysisTests': 22, 'AttemptAnalysisUITests': 13, 'AttemptMistakeTests': 9, 'AttemptEvaluationTests': 9, 'AttemptStructuredWorkTests': 12,
                'MethodComparisonTests': 6, 'TutorTests': 1, 'TutorUITests': 1, 'PrimarySolveUITests': 1}


def ios_selector(case):
    if not isinstance(case, (list, tuple)) or len(case) != 2:
        raise ValueError('Invalid XCTest failure identity')
    suite, method = case
    if not isinstance(suite, str) or not re.fullmatch(r'VerifiedAI(?:UI)?Tests\.[A-Za-z_][A-Za-z0-9_]*', suite):
        raise ValueError('Unexpected XCTest target or class')
    if not isinstance(method, str) or not re.fullmatch(r'test[A-Za-z0-9_]+', method):
        raise ValueError('Invalid XCTest method')
    return suite.replace('.', '/') + '/' + method


def backend_selector(case):
    if not isinstance(case, str):
        raise ValueError('Invalid backend failure identity')
    match = re.fullmatch(r'([A-Za-z_][A-Za-z0-9_]*)\.([A-Za-z_][A-Za-z0-9_]*)(?:\(\))?', case)
    if not match or match[1] not in BACKEND:
        raise ValueError('Unsupported backend failure identity; use --gate B')
    return match[1] + '#' + match[2]


def repair_plan(previous):
    """Use prior failures as selectors only; never carry their PASS evidence forward.

    A backend compilation failure has no failed methods, so its unexecuted suite
    must run in full. A method-only rerun cannot satisfy full security prerequisites.
    """
    gates = previous.get('gates', {})
    failed = {g for g in GATES if gates.get(g) == 'FAIL'}
    if previous.get('sprint') != '7.4' or previous.get('sourceUnchanged') is not True:
        raise ValueError('Expected unchanged-source Sprint 7.4 evidence')
    if not failed or failed - {'B', 'D', 'F'} or gates.get('A') != 'PASS' or gates.get('cleanup') != 'PASS':
        raise ValueError('Use --full or --gate for failures outside backend/iOS/documentation repair')
    selected = {'A'} | failed
    backend_scope = 'NOT_RUN'
    backend_selectors = []
    if 'B' in failed:
        backend = previous.get('backend', {})
        if backend.get('executed') == 0 and backend.get('failedCases') == []:
            backend_scope = 'FULL_UNEXECUTED_SUITE'
            if gates.get('E') == 'BLOCKED':
                selected.add('E')
        else:
            if not backend.get('failedCases') or backend.get('missingCases') or backend.get('skipped'):
                raise ValueError('Incomplete backend evidence; use --gate B')
            backend_selectors = sorted({backend_selector(case) for case in backend['failedCases']})
            backend_scope = 'FAILED_METHODS_ONLY'
    selectors = []
    if 'D' in failed:
        cases = previous.get('ios', {}).get('failedCases', [])
        if not cases:
            raise ValueError('No failed XCTest methods recorded; use --gate D')
        selectors = sorted({ios_selector(case) for case in cases})
    return {'selectedGates': [g for g in GATES if g in selected], 'iosSelectors': selectors,
            'backendScope': backend_scope, 'backendSelectors': backend_selectors,
            'securityPrerequisite': 'FULL_BACKEND_RERUN_REQUIRED' if backend_selectors else 'UNCHANGED',
            'previousResultsImported': False}


def report_ios(out, exit_code):
    text = (out / 'ios-tests.log').read_text(errors='replace')
    passed = re.findall(r"Test Case '-\[([^ ]+) ([^\]]+)\]' passed", text)
    failed = re.findall(r"Test Case '-\[([^ ]+) ([^\]]+)\]' failed", text)
    plan = read(out / 'repair-selection.json')
    selectors = plan.get('iosSelectors', [])
    unexpected = []
    if selectors:
        actual_passes = {ios_selector(case) for case in passed}
        actual = actual_passes | {ios_selector(case) for case in failed}
        missing = sorted(set(selectors) - actual_passes)
        unexpected = sorted(actual - set(selectors))
    else:
        counts = {name: sum(c.split('.')[-1] == name for c, _ in set(passed)) for name in IOS_REQUIRED}
        missing = [name for name, minimum in IOS_REQUIRED.items() if counts[name] < minimum]
        identities = {ios_selector(case) for case in passed}
        missing += [name for name in (
            'VerifiedAIUITests/AttemptAnalysisUITests/testClassificationEnglishUncertaintyAndAccessibility',
            'VerifiedAIUITests/AttemptAnalysisUITests/testClassificationTurkishUncertaintyAndAccessibility') if name not in identities]
    skipped = bool(re.search(r'Test Case .* skipped', text))
    status = 'PASS' if exit_code == 0 and passed and not failed and not missing and not unexpected and not skipped and (out / 'VerifiedAI.xcresult').exists() else 'FAIL'
    write(out / 'ios-summary.json', {'status': status, 'passedCases': passed, 'failedCases': failed,
          'missing': missing, 'missingCases': missing, 'unexpectedCases': unexpected, 'skipped': skipped,
          'artifact': 'VerifiedAI.xcresult', 'scope': 'CURRENT_RUN_SELECTED_CASES' if selectors else 'CURRENT_RUN',
          'selectedCases': selectors})
    return 0 if status == 'PASS' else 1


def write(path, value):
    Path(path).write_text(json.dumps(value, indent=2) + '\n')


def read(path):
    try:
        return json.loads(Path(path).read_text())
    except (OSError, ValueError):
        return {'status': 'NOT_RUN'}


def safe_source(name):
    path = Path(name)
    return not (path.is_absolute() or '..' in path.parts or any(
        part.startswith('.env') or part in {'.aws', '.ssh', '.config', '.codex', '.agents', '.venv', '__pycache__'}
        for part in path.parts) or path.suffix in {'.pem', '.key', '.p12', '.pfx'})


def fingerprint(out):
    roots = ['AGENTS.md', 'README.md', '.gitignore', 'services/api', 'services/math-verifier', 'apps/ios',
             'packages/contracts', 'packages/schemas', 'scripts/quality', 'scripts/security',
             'evaluations/golden-datasets/attempts', 'evaluations/golden-datasets/methods',
             '.github/workflows', 'docs/verified_ai_learning_platform_docs_v4']
    names = subprocess.check_output(['git', 'ls-files', '-z', '--cached', '--others', '--exclude-standard', '--', *roots], cwd=ROOT).decode().split('\0')
    files = {}
    for name in sorted(set(filter(None, names))):
        if not safe_source(name):
            continue
        path = ROOT / name
        files[name] = (hashlib.sha256(path.read_bytes()).hexdigest()
                       if path.is_file() and not path.is_symlink() and path.resolve().is_relative_to(ROOT)
                       else 'MISSING_OR_SYMLINK')
    digest = hashlib.sha256(''.join(name + '\0' + value + '\n' for name, value in files.items()).encode()).hexdigest()
    write(out, {'sha256': digest, 'files': files})


def report_junit(out, kind):
    paths = list((out / ('maven-reports' if kind == 'backend' else 'verifier-reports')).glob('*.xml'))
    selectors = read(out / 'repair-selection.json').get('backendSelectors', []) if kind == 'backend' else []
    required = BACKEND if kind == 'backend' else VERIFIER
    if selectors:
        required = sorted({selector.split('#')[0] for selector in selectors})
    value = junit(paths, required, kind=kind)
    if selectors:
        expected = {s.replace('#', '.') for s in selectors}
        actual = {c.removesuffix('()') for c in value['caseNames']}
        value['missingCases'].extend(sorted(expected - actual))
        value['unexpectedCases'] = sorted(actual - expected)
        value['scope'] = 'CURRENT_RUN_SELECTED_CASES'
        value['selectedCases'] = selectors
        if value['missingCases'] or value['unexpectedCases']:
            value['status'] = 'FAIL'
    elif kind == 'backend':
        value['scope'] = 'CURRENT_RUN_FULL_BACKEND'
        absent = [name for name in CRITICAL if not any(c == name or c == name + '()' for c in value['caseNames'])]
        value['missingCases'].extend(absent)
        if absent:
            value['status'] = 'FAIL'
    if kind == 'verifier':
        codes = json.loads((ROOT / 'packages/schemas/attempt-taxonomy-v2.json').read_text())['specificCodes']
        required_cases = [f"test_attempt_mistake_classification.{method}[{row['code']}]"
                          for row in codes for method in ('test_signature_has_disproof_bound_to_first_causal_step',
                                                          'test_identity_and_unknown_scope_never_produce_category_evidence')]
        required_cases += ['test_attempt_mistake_classification.' + name for name in (
            'test_unconfirmed_line_stops_causal_classification_before_later_error',
            'test_alternative_syntax_and_near_match_do_not_inherit_signature',
            'test_ambiguous_structural_causes_keep_disproof_without_specific_label',
            'test_unbounded_periods_and_oversized_ast_fail_closed',
            'test_explicit_denominator_restriction_is_not_an_extraneous_root',
            'test_multiple_locations_of_one_signature_use_step_level_evidence')]
        absent = sorted(set(required_cases) - set(value['caseNames']))
        value['missingCases'].extend(absent)
        if absent: value['status'] = 'FAIL'
    write(out / (kind + '-summary.json'), value)
    return 0 if value['status'] == 'PASS' else 2 if value['status'] == 'BLOCKED' else 1


def security(out):
    snapshot = read(out / 'source-before.json')
    findings = []
    patterns = [r'(?:AKIA|ASIA)[A-Z0-9]{16}', r'AIza[0-9A-Za-z_-]{35}',
                r'(?<![\w-])sk-(?:ant-)?[A-Za-z0-9_-]{20,}', r'gh[pousr]_[A-Za-z0-9_]{36,}',
                r'-----BEGIN [A-Z ]*PRIVATE KEY-----']
    for name, digest in snapshot.get('files', {}).items():
        if not safe_source(name) or digest == 'MISSING_OR_SYMLINK':
            continue
        path = ROOT / name
        if path.is_symlink() or not path.resolve().is_relative_to(ROOT):
            findings.append({'path': name, 'reason': 'SOURCE_BOUNDARY_CHANGED'}); continue
        text = path.read_bytes().decode('utf-8', errors='replace')
        for number, line in enumerate(text.splitlines(), 1):
            if any(re.search(pattern, line) for pattern in patterns) and not re.search(r'change_me|placeholder|example|not_a_secret|local_dev_', line):
                findings.append({'path': name, 'line': number, 'reason': 'POTENTIAL_SECRET_REDACTED'})
    backend = read(out / 'backend-summary.json')
    complete_backend = backend.get('status') == 'PASS' and backend.get('scope') == 'CURRENT_RUN_FULL_BACKEND'
    passed = complete_backend and bool(snapshot.get('sha256')) and not findings
    value = {'status': 'FAIL' if findings else 'PASS' if passed else 'BLOCKED', 'findings': findings,
             'scope': 'Allowlisted repository source; private files and credential stores excluded',
             'privacyAuthorizationTrainingAndAbuse': 'PASS' if complete_backend else 'NOT_VERIFIED'}
    write(out / 'security-summary.json', value)
    return 0 if passed else 1 if findings else 2


def summary(out, exit_code):
    metadata = read(out / 'execution-metadata.json')
    mode = metadata.get('mode', 'UNKNOWN')
    selected = metadata.get('selectedGates', list(GATES))
    gates = {}
    for gate in (*GATES, 'cleanup'):
        path = out / (gate + '.status')
        gates[gate] = path.read_text().strip() if path.exists() else 'NOT_RUN'
    before, after = read(out / 'source-before.json'), read(out / 'source-after.json')
    unchanged = bool(before.get('sha256')) and before.get('sha256') == after.get('sha256')
    reports = {name: read(out / (name + '-summary.json')) for name in ('backend', 'verifier', 'ios', 'security')}
    dependencies = {'B': 'backend', 'D': 'ios', 'E': 'security', 'G': 'verifier'}
    missing = []
    if metadata.get('sprint') != '7.4' or metadata.get('previousEvidenceImported') is not False:
        missing.append('invalid-run-identity')
    for gate in selected:
        exit_path = out / (gate + '.exit')
        if gates.get(gate) == 'PASS' and (not exit_path.is_file() or exit_path.read_text().strip() != '0'
                                         or not (out / (gate + '.log')).is_file()):
            gates[gate] = 'BLOCKED'; missing.append('missing-gate-execution-' + gate)
    if mode == 'FULL' and set(selected) != set(GATES):
        missing.append('incomplete-full-gate-selection')
    if mode == 'FULL' and reports['ios'].get('scope') == 'CURRENT_RUN_SELECTED_CASES':
        missing.append('partial-ios-evidence-in-full-run')
    if mode == 'FULL' and reports['backend'].get('scope') == 'CURRENT_RUN_SELECTED_CASES':
        missing.append('partial-backend-evidence-in-full-run')
    for gate in selected:
        if gates.get(gate) != 'PASS':
            missing.append('gate-' + gate)
        if gate in dependencies and reports[dependencies[gate]].get('status') != 'PASS':
            missing.append(dependencies[gate])
            if gates[gate] == 'PASS':
                gates[gate] = 'BLOCKED'
    source_status = 'PASS' if unchanged else 'FAIL'
    failure = exit_code not in (0, 2) or not unchanged or any(gates.get(g) == 'FAIL' for g in (*selected, 'cleanup'))
    status = 'FAIL' if failure else 'BLOCKED' if missing or gates['cleanup'] != 'PASS' or mode == 'UNKNOWN' else 'PASS'
    acceptance = 'LOCAL_ACCEPTANCE_PASSED' if status == 'PASS' and mode == 'FULL' else 'TARGETED_REPAIR_PASSED' if status == 'PASS' else status
    inventory = {name: {key: report.get(key, []) for key in ('failedCases', 'missingCases', 'skipped', 'unexpectedSkipped', 'platformSpecificSkipped', 'failureSelectors', 'findings')}
                 for name, report in reports.items()}
    inventory.update(missingMandatoryEvidence=missing, gates={g: v for g, v in gates.items() if v != 'PASS'}, sourceIntegrity=source_status)
    write(out / 'failure-inventory.json', inventory)
    value = {'sprint': '7.4', 'overall': status, 'acceptance': acceptance, 'mode': mode,
             'currentSourceAcceptance': acceptance if mode == 'FULL' else 'NOT_VERIFIED',
             'sourceFingerprint': before.get('sha256'), 'sourceUnchanged': unchanged, 'sourceIntegrity': source_status,
             'gates': gates, 'selectedGates': selected, **reports, 'execution': metadata,
             'finishedAt': datetime.now(timezone.utc).isoformat(), 'missingMandatoryEvidence': missing,
             'migration': 'V036; PostgreSQL fresh and V032/V033/V034/V035 upgrade tests required in Gate B',
             'productionAuthorization': 'NOT_AUTHORIZED', 'hostedCI': 'NOT_VERIFIED',
             'physicalDeviceAccessibility': 'NOT_VERIFIED', 'testFlight50StudentGate': 'NOT_VERIFIED',
             'paidInference': 'DISABLED', 'expectedNewInferenceCost': 0,
             'linuxResourceEnforcement': reports['verifier'].get('platformSpecificValidation', 'NOT_VERIFIED'), 'failureInventory': 'failure-inventory.json'}
    write(out / 'summary.json', value)
    for gate in (*GATES, 'cleanup'):
        blocked = out / (gate + '.blocked')
        write(out / ('gate-' + gate + '.json'), {'gate': gate, 'status': gates[gate], 'log': gate + '.log', 'exitCode': int((out / (gate + '.exit')).read_text()) if (out / (gate + '.exit')).exists() else None,
              'reason': blocked.read_text().strip() if blocked.exists() else None})
    (out / 'summary.md').write_text('# Sprint 7.4 owner acceptance\n\n' + acceptance + '\n\n' +
        '\n'.join('- ' + g + ': ' + v for g, v in gates.items()) +
        f'\n\nSource unchanged: {unchanged}. Mode: {mode}.\n\n'
        'Targeted repair never constitutes full acceptance. No previous results were imported.\n'
        'Production authorization, hosted CI, physical-device accessibility and the 50-student gate remain unverified.\n')
    return 0 if status == 'PASS' else 1 if status == 'FAIL' else 2


if __name__ == '__main__':
    command, destination = sys.argv[1:3]; out = Path(destination)
    if command == 'fingerprint':
        fingerprint(out)
    elif command == 'junit':
        sys.exit(report_junit(out, sys.argv[3]))
    elif command == 'security':
        sys.exit(security(out))
    elif command == 'summary':
        sys.exit(summary(out, int(sys.argv[3])))
    elif command == 'ios':
        sys.exit(report_ios(out, int(sys.argv[3])))
    elif command == 'repair-plan':
        try:
            prior = Path(sys.argv[3]).resolve()
            if not prior.is_relative_to(ROOT / '.generated' / 'sprint-7-4-owner'):
                raise ValueError('Prior run must be inside .generated/sprint-7-4-owner')
            plan = repair_plan(read(prior / 'summary.json'))
            plan['failureSource'] = str(prior.relative_to(ROOT))
            write(out, plan)
        except (ValueError, TypeError, AttributeError) as error:
            print(str(error), file=sys.stderr)
            sys.exit(2)
