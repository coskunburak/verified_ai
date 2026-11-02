#!/usr/bin/env python3
"""Owner-only E/D reconciliation. Reads evidence; never invokes tests, Docker or providers."""
import argparse
from datetime import datetime
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from zoneinfo import ZoneInfo

ROOT = Path(__file__).resolve().parents[2]
REPAIRED = 'TutorSessionControllerTest.hintsRejectUnknownDuplicateAuthorityAndOversizedFields'
BASELINE = 'c9f1b5837ed1584dded8afeafe91fe07c1acf118'
IOS_SOURCES = (
    'apps/ios/VerifiedAITests/Tutor/TutorTests.swift',
    'apps/ios/VerifiedAIUITests/TutorUITests.swift',
    'apps/ios/VerifiedAITests/ProblemSolving/ExplanationPresentationTests.swift',
    'apps/ios/VerifiedAITests/ProblemSolving/PrimarySolveContractTests.swift',
    'apps/ios/VerifiedAITests/ProblemSolving/PrimarySolveViewModelTests.swift',
    'apps/ios/VerifiedAITests/ProblemSolving/MathPresentationTests.swift',
    'apps/ios/VerifiedAITests/ProblemSolving/SolveRecoveryStoreTests.swift',
    'apps/ios/VerifiedAITests/Onboarding/OnboardingViewModelTests.swift',
    'apps/ios/VerifiedAIUITests/PrimarySolveUITests.swift',
)
REQUIRED_SUITES = set(('HintSourceTest HintPolicyTest HintEvaluationTest ProgressiveHintIntegrationTest '
    'SocraticPolicyTest SocraticEvaluationTest SocraticTurnIntegrationTest PromptRegistryValidatorTest '
    'ConfiguredAiRoutePlannerTest ConfiguredAiModelGatewayTest AiCapabilityRegistryTest TutorPrivacyCapacityTest '
    'TutorPolicyTest TutorPersistenceIntegrationTest TutorSessionControllerTest ModularityTest '
    'AccountPrivacyApplicationServiceTest LearningProfileControllerTest LearningProfileApplicationServiceTest '
    'PrimarySolveControllerTest PrimarySolveIntegrationTest SolutionCandidateValidatorTest CanonicalSolutionServiceTest '
    'VerificationPolicyTest VerificationArbitrationServiceTest VerificationDecisionControllerTest SolverReleaseRegistryTest '
    'SolverReleaseEvidenceTest').split())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read_json(path):
    return json.loads(path.read_text())


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def workspace_path(value):
    path = (ROOT / value).resolve()
    require(path.is_relative_to(ROOT), 'Evidence must stay inside the repository')
    return path


def cases_from_xml(paths):
    cases, suites = {}, set()
    for path in paths:
        root = ET.parse(path).getroot()
        suite = root.get('name', '').split('.')[-1]
        require(suite and root.tag == 'testsuite', 'Invalid Surefire suite')
        suites.add(suite)
        children = root.findall('testcase')
        require(int(root.get('tests', '-1')) == len(children), 'Surefire case count mismatch')
        for case in children:
            name = suite + '.' + case.get('name', '')
            require(name not in cases, 'Duplicate test evidence: ' + name)
            cases[name] = ('failed' if case.find('failure') is not None or case.find('error') is not None
                           else 'skipped' if case.find('skipped') is not None else 'passed')
    require(cases, 'Missing Surefire cases')
    return cases, suites


def prepare(prior, focused, out, summary):
    original = read_json(prior / 'summary.json')
    require(original.get('version') == 'sprint-6-6-owner-v1', 'Wrong prior evidence version')
    head = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    require(original.get('baselineSha') == BASELINE and original.get('head') == head,
            'Prior evidence does not match this baseline/HEAD')
    for gate in ('prerequisites', 'A', 'B', 'cleanup'):
        require(original['gates'].get(gate) == 'PASS', 'Prior gate missing: ' + gate)
        require((prior / (gate + '.status')).read_text().strip() == 'PASS', 'Gate file mismatch: ' + gate)
    require((prior / 'VerifiedAI.xcresult').is_dir(), 'Missing prior Xcode result bundle')
    original_backend = read_json(prior / 'backend-summary.json')
    previous_cases, suites = cases_from_xml(sorted((prior / 'maven-reports').glob('TEST-*.xml')))
    require(REQUIRED_SUITES <= suites, 'Missing mandatory backend suites')
    require(set(previous_cases) == set(original_backend['caseNames']), 'Prior backend summary/XML mismatch')
    require(original_backend.get('executed') == len(previous_cases), 'Prior backend count mismatch')
    require(original_backend.get('mavenExit') != 0 and original_backend.get('failed') == 1
            and original_backend.get('skipped') == 0, 'Expected the documented single-failure run')
    require({k: v for k, v in previous_cases.items() if v != 'passed'} == {REPAIRED: 'failed'},
            'Prior failures differ from the approved focused repair')
    focused_cases, _ = cases_from_xml([focused])
    require(focused_cases == {REPAIRED: 'passed'}, 'Expected exactly the successful focused repair case')
    require(focused.stat().st_mtime_ns > (prior / 'backend-summary.json').stat().st_mtime_ns,
            'Focused evidence must be newer than the failed run')
    summary['repairSources'] = {}
    for name in (
        'services/api/src/main/java/com/verifiedai/tutoring/api/TutorSessionController.java',
        'services/api/src/test/java/com/verifiedai/tutoring/api/TutorSessionControllerTest.java',
    ):
        source = ROOT / name
        require(source.stat().st_mtime_ns <= focused.stat().st_mtime_ns,
                'Repair source changed after focused retest: ' + name)
        summary['repairSources'][name] = digest(source)
    required_cases = set()
    for source in (ROOT / 'services/api/src/test/java/com/verifiedai/tutoring').rglob('*Test.java'):
        required_cases.update(source.stem + '.' + name for name in
                              re.findall(r'@Test\s+void\s+(\w+)\s*\(', source.read_text()))
    merged = dict(previous_cases)
    merged[REPAIRED] = 'passed'
    require(required_cases and required_cases <= set(merged), 'Missing current mandatory Tutor cases')
    require(all(v == 'passed' for v in merged.values()), 'Unresolved backend failure or skip')
    ios = read_json(prior / 'ios-summary.json')
    require(ios.get('status') == 'PASS' and ios.get('xcodeExit') == 0
            and ios.get('failed') == ios.get('skipped') == 0, 'Invalid iOS evidence')
    require(ios.get('executed') == len(ios.get('cases', {})) and
            all(v == 'passed' for v in ios['cases'].values()), 'iOS case/count mismatch')
    required_ios = set()
    for name in IOS_SOURCES:
        suite = None
        for line in (ROOT / name).read_text().splitlines():
            match = re.search(r'class (\w+): XCTestCase', line)
            if match:
                suite = match[1]
            test = re.search(r'func (test\w+)\(', line)
            if test and suite:
                required_ios.add(suite + '.' + test[1])
    require(required_ios and required_ios <= set(ios['cases']), 'Missing mandatory iOS cases')
    # Copy compact artifacts only; leave original reports, failures and xcresult unchanged.
    evidence = out / 'evidence'
    evidence.mkdir()
    for name in ('summary.json', 'backend-summary.json', 'ios-summary.json',
                 'synthetic-evaluation.json', 'hint-evaluation.json'):
        shutil.copy2(prior / name, evidence / name)
    shutil.copy2(focused, evidence / 'focused-retest.xml')
    summary['inputs'] = {name: digest(prior / name) for name in (
        'summary.json', 'backend-summary.json', 'ios-summary.json',
        'synthetic-evaluation.json', 'hint-evaluation.json')}
    summary['inputs']['focused-retest.xml'] = digest(focused)
    summary['ios'] = {'passedInPriorRun': len(ios['cases']), 'missingMandatoryCases': []}
    summary['backend'] = {'passedInPriorRun': sum(v == 'passed' for v in previous_cases.values()),
        'priorFailures': [REPAIRED], 'focusedRepairPassed': [REPAIRED], 'unresolvedCases': [],
        'coverageAcrossRuns': len(merged), 'singleCurrentSourceRun': False}
    return merged


def evaluate(prior, cases, out):
    results = {}
    for dataset_name, report_name in (
        ('socratic-policy-v1.json', 'synthetic-evaluation.json'),
        ('progressive-hints-v1.json', 'hint-evaluation.json'),
    ):
        dataset = ROOT / 'evaluations/golden-datasets/tutoring' / dataset_name
        source, report = read_json(dataset), read_json(prior / report_name)
        expected = [case['id'] for case in source['cases']]
        actual = [case['id'] for case in report.get('cases', [])]
        require(len(expected) == len(set(expected)) and len(actual) == len(set(actual))
                and set(expected) == set(actual) and report.get('executed') == len(expected),
                'Missing/duplicate evaluation cases: ' + dataset_name)
        require(report.get('status') == 'PASS' and report.get('provenance') == 'SYNTHETIC',
                'Invalid synthetic evidence: ' + report_name)
        require(report.get('datasetSha256') == digest(dataset), 'Evaluation dataset changed: ' + dataset_name)
        require(report.get('pedagogicalProgressionQuality') == 'NOT_EVALUATED', 'Unjustified teaching quality claim')
        require(report.get('answerLeakageViolations') == 0, 'Answer leakage evidence')
        for case in source['integrationCoverage'].values():
            require(cases.get(case) == 'passed', 'Missing passing integration evidence: ' + case)
        if dataset_name == 'socratic-policy-v1.json':
            for field in ('falseCorrectnessClaims', 'unverifiedCertaintyViolations'):
                require(report.get(field) == 0, 'Unsafe Socratic evidence: ' + field)
            for case in report['cases']:
                require(case.get('legalTransition') is True and case.get('schemaValid') is True,
                        'Invalid Socratic case evidence')
                for field in ('leakageViolations', 'falseCorrectnessClaims', 'unverifiedCertaintyViolations'):
                    require(case.get(field) == 0, 'Unsafe Socratic case')
        else:
            require(all(case.get('outcome') == 'PASS' for case in report['cases']), 'Failed hint case')
            require(report.get('inferenceCalls') == 0 and report.get('liveModelQuality') == 'NOT_EVALUATED',
                    'Invalid no-inference quality contract')
        results[dataset_name] = {'status': 'PASS', 'cases': len(expected), 'datasetSha256': digest(dataset)}
    (out / 'E.json').write_text(json.dumps(results, indent=2) + '\n')
    return results


def reconcile(prior, cases, out):
    for case in (
        'ProgressiveHintIntegrationTest.freshV029PersistsThreeLevelsWithoutChangingLegacyMessages',
        'ProgressiveHintIntegrationTest.v028ForwardMigrationPreservesLegacyHistory',
    ):
        require(cases.get(case) == 'passed', 'Missing passing V029 migration case: ' + case)
    name = 'V029__create_progressive_tutor_hints.sql'
    require(digest(prior / 'migration' / name) == digest(ROOT / 'services/api/src/main/resources/db/migration/platform' / name),
            'V029 migration changed after owner execution')
    return {'status': 'PASS', 'scope': 'PRIOR_RUN_PLUS_OWNER_FOCUSED_REPAIR',
        'migration': {'freshV029': 'PASS_IN_PRIOR_RUN', 'v028Forward': 'PASS_IN_PRIOR_RUN',
                      'sharedUpgrade': 'BLOCKED'},
        'sourceBinding': 'OWNER_REPORTED_REPAIR_SCOPE; prior run has no source-content snapshot',
        'singleCurrentSourceRun': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prior-run', default='.generated/sprint-6-6-owner/20261102-211111-8b8qag')
    parser.add_argument('--focused-report', default='services/api/target/surefire-reports/TEST-com.verifiedai.tutoring.api.TutorSessionControllerTest.xml')
    args = parser.parse_args()
    evidence_root = ROOT / '.generated/sprint-6-6-reconciliation'
    evidence_root.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now(ZoneInfo('Europe/Istanbul')).strftime('%Y%m%d-%H%M%S-')
    out = Path(tempfile.mkdtemp(prefix=stamp, dir=evidence_root))
    latest = evidence_root / 'latest'
    if latest.is_symlink():
        latest.unlink()
    if not latest.exists():
        latest.symlink_to(out.name, target_is_directory=True)
    summary = {'version': 'sprint-6-6-targeted-reconciliation-v1', 'gates': {'inputs': 'NOT_RUN', 'E': 'NOT_RUN', 'D': 'NOT_RUN'},
        'overall': 'FAIL', 'localAcceptance': 'PENDING_FULL_CURRENT_SOURCE_RUN',
        'hostedCI': 'NOT_VERIFIED', 'productionRelease': 'NOT_AUTHORIZED',
        'scope': 'Existing owner run plus one focused repair; no tests or inference executed',
        'evidenceDirectory': str(out)}
    stage = 'inputs'
    exit_code = 1
    try:
        prior, focused = workspace_path(args.prior_run), workspace_path(args.focused_report)
        require(prior.is_relative_to((ROOT / '.generated/sprint-6-6-owner').resolve()), 'Prior input must be owner acceptance evidence')
        require(focused.parent == (ROOT / 'services/api/target/surefire-reports').resolve() and focused.name == 'TEST-com.verifiedai.tutoring.api.TutorSessionControllerTest.xml', 'Focused input must be the Tutor Surefire report')
        summary['priorRun'] = str(prior)
        summary['focusedReport'] = str(focused)
        cases = prepare(prior, focused, out, summary)
        summary['gates']['inputs'] = 'PASS'
        stage = 'E'
        summary['evaluations'] = evaluate(prior, cases, out)
        summary['gates']['E'] = 'PASS'
        (out / 'E.log').write_text('PASS: versioned synthetic datasets, case outcomes and integration evidence reconciled. No evaluation rerun.\n')
        stage = 'D'
        summary['reconciliation'] = reconcile(prior, cases, out)
        summary['gates']['D'] = 'PASS'
        (out / 'D.log').write_text('PASS: mandatory cross-run cases and unchanged V029 evidence reconciled. Scope is targeted; full current-source acceptance remains pending.\n')
        summary['overall'] = 'TARGETED_RECONCILIATION_PASSED'
        exit_code = 0
    except (OSError, ValueError, KeyError, TypeError, ET.ParseError, subprocess.SubprocessError) as error:
        summary['gates'][stage] = 'FAIL'
        # Do not emit arbitrary exception payloads or reflected file contents.
        message = str(error) if isinstance(error, ValueError) else type(error).__name__ + ': evidence unavailable or malformed'
        summary['failure'] = {'stage': stage, 'reason': message}
        (out / (stage + '.log')).write_text(message + '\n')
    summary['exitCode'] = exit_code
    (out / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(json.dumps(summary, indent=2))
    print('Evidence: ' + str(out / 'summary.json'))
    return exit_code


if __name__ == '__main__':
    sys.exit(main())
