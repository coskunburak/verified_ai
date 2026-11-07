"""Synthetic evidence-writer regressions; authored for owner execution only."""
import json
import sys
import pytest
from pathlib import Path

QUALITY = Path(__file__).resolve().parents[3] / 'scripts' / 'quality'
sys.path.insert(0, str(QUALITY))
import sprint_7_3_evidence as evidence


def setup_run(tmp_path, mode='FULL', gates='ABCDEFG'):
    evidence.write(tmp_path / 'execution-metadata.json', {'mode': mode, 'selectedGates': list(gates)})
    for gate in (*evidence.GATES, 'cleanup'):
        (tmp_path / (gate + '.status')).write_text('PASS' if gate in gates or gate == 'cleanup' else 'NOT_RUN')
    for name in ('backend', 'verifier', 'ios', 'security'):
        evidence.write(tmp_path / (name + '-summary.json'), {'status': 'PASS'})
    evidence.write(tmp_path / 'source-before.json', {'sha256': 'synthetic-a'})
    evidence.write(tmp_path / 'source-after.json', {'sha256': 'synthetic-a'})


def test_full_current_evidence_and_external_gates(tmp_path):
    setup_run(tmp_path)
    assert evidence.summary(tmp_path, 0) == 0
    value = evidence.read(tmp_path / 'summary.json')
    assert value['currentSourceAcceptance'] == 'LOCAL_ACCEPTANCE_PASSED'
    assert value['productionAuthorization'] == 'NOT_AUTHORIZED'
    assert value['testFlight50StudentGate'] == 'NOT_VERIFIED'
    assert len(list(tmp_path.glob('gate-*.json'))) == 8


def test_targeted_pass_never_claims_full_acceptance(tmp_path):
    setup_run(tmp_path, 'TARGETED', 'AB')
    assert evidence.summary(tmp_path, 0) == 0
    value = evidence.read(tmp_path / 'summary.json')
    assert value['acceptance'] == 'TARGETED_REPAIR_PASSED'
    assert value['currentSourceAcceptance'] == 'NOT_VERIFIED'
    assert value['gates']['D'] == 'NOT_RUN'


def test_changed_source_invalidates_all_prior_passes(tmp_path):
    setup_run(tmp_path)
    evidence.write(tmp_path / 'source-after.json', {'sha256': 'synthetic-b'})
    assert evidence.summary(tmp_path, 0) == 1
    assert evidence.read(tmp_path / 'failure-inventory.json')['sourceIntegrity'] == 'FAIL'


def test_missing_and_corrupt_reports_are_never_pass(tmp_path):
    setup_run(tmp_path)
    (tmp_path / 'backend-summary.json').write_text('{')
    assert evidence.summary(tmp_path, 0) == 2
    assert 'backend' in evidence.read(tmp_path / 'summary.json')['missingMandatoryEvidence']


def test_unavailable_simulator_is_explicit_blocked(tmp_path):
    setup_run(tmp_path)
    (tmp_path / 'D.status').write_text('BLOCKED')
    (tmp_path / 'D.blocked').write_text('No iOS simulator')
    (tmp_path / 'ios-summary.json').unlink()
    assert evidence.summary(tmp_path, 0) == 2
    assert evidence.read(tmp_path / 'gate-D.json')['reason'] == 'No iOS simulator'


def test_failed_gate_and_aborted_runner_are_failures(tmp_path):
    setup_run(tmp_path)
    assert evidence.summary(tmp_path, 130) == 1
    (tmp_path / 'C.status').write_text('FAIL')
    assert evidence.summary(tmp_path, 0) == 1


def test_junit_absent_corrupt_skipped_and_failed(tmp_path):
    path = tmp_path / 'report.xml'
    assert evidence.junit([], ['Example'], kind='backend')['status'] == 'FAIL'
    path.write_text('broken')
    assert evidence.junit([path], ['Example'], kind='backend')['status'] == 'FAIL'
    for element, status in [('<skipped/>', 'BLOCKED'), ('<failure/>', 'FAIL'), ('', 'PASS')]:
        path.write_text('<testsuite><testcase classname="Example" name="case">' + element + '</testcase></testsuite>')
        assert evidence.junit([path], ['Example'], kind='backend')['status'] == status


def test_backend_requires_specific_upgrade_and_answer_cases(tmp_path):
    folder = tmp_path / 'maven-reports'; folder.mkdir()
    (folder / 'all.xml').write_text('<testsuite>' + ''.join(
        '<testcase classname="' + name + '" name="placeholder"/>' for name in evidence.BACKEND) + '</testsuite>')
    assert evidence.report_junit(tmp_path, 'backend') == 1
    value = evidence.read(tmp_path / 'backend-summary.json')
    assert evidence.CRITICAL[0] in value['missingCases']


def test_secret_paths_and_stores_are_excluded_without_reading_them():
    for name in ('.env', 'services/api/.env.local', '.aws/config', '.ssh/id_rsa', '.config/gcloud/data',
                 'services/api/key.pem', '/private/external', '../outside'):
        assert not evidence.safe_source(name)
    assert evidence.safe_source('services/api/src/main/java/Example.java')


def test_security_requires_fresh_backend_evidence(tmp_path):
    evidence.write(tmp_path / 'source-before.json', {'sha256': 'synthetic', 'files': {}})
    assert evidence.security(tmp_path) == 2
    assert evidence.read(tmp_path / 'security-summary.json')['status'] == 'BLOCKED'


def test_forged_full_mode_with_partial_gate_selection_is_blocked(tmp_path):
    setup_run(tmp_path, mode='FULL', gates='AB')
    assert evidence.summary(tmp_path, 0) == 2
    assert 'incomplete-full-gate-selection' in evidence.read(tmp_path / 'summary.json')['missingMandatoryEvidence']


def test_new_sprint_cannot_use_only_previous_backend_suite_results(tmp_path):
    folder = tmp_path / 'maven-reports'; folder.mkdir()
    (folder / 'legacy.xml').write_text('<testsuite><testcase classname="StudentAttemptIntegrationTest" name="legacy"/></testsuite>')
    assert evidence.report_junit(tmp_path, 'backend') == 1
    assert any('AttemptEvaluation' in name for name in evidence.read(tmp_path / 'backend-summary.json')['missingCases'])


def failed_run():
    return {'sprint': '7.3', 'sourceUnchanged': True,
            'gates': {'A': 'PASS', 'B': 'FAIL', 'C': 'PASS', 'D': 'FAIL', 'E': 'BLOCKED',
                      'F': 'PASS', 'G': 'PASS', 'cleanup': 'PASS'},
            'backend': {'executed': 0, 'failedCases': []},
            'ios': {'failedCases': [['VerifiedAIUITests.AttemptAnalysisUITests', 'testStructuredEnglish'],
                                    ['VerifiedAIUITests.AttemptAnalysisUITests', 'testStructuredTurkish']]}}


def test_repair_selects_unexecuted_backend_failed_ios_and_blocked_dependency_only():
    plan = evidence.repair_plan(failed_run())
    assert plan['selectedGates'] == list('ABDE')
    assert plan['backendScope'] == 'FULL_UNEXECUTED_SUITE'
    assert plan['iosSelectors'] == ['VerifiedAIUITests/AttemptAnalysisUITests/testStructuredEnglish',
                                    'VerifiedAIUITests/AttemptAnalysisUITests/testStructuredTurkish']
    assert plan['previousResultsImported'] is False


def test_repair_rejects_unknown_cases_changed_sources_and_unsupported_failures():
    for key, value in [('sourceUnchanged', False), ('sprint', '7.1')]:
        previous = failed_run(); previous[key] = value
        with pytest.raises(ValueError):
            evidence.repair_plan(previous)
    for cases in [[], [['OtherTarget.SomeTests', 'testFoo']],
                  [['VerifiedAIUITests.AttemptAnalysisUITests', 'testFoo;touch file']],
                  [['VerifiedAIUITests.AttemptAnalysisUITests']]]:
        previous = failed_run(); previous['ios']['failedCases'] = cases
        with pytest.raises(ValueError):
            evidence.repair_plan(previous)
    previous = failed_run(); previous['gates']['C'] = 'FAIL'
    with pytest.raises(ValueError):
        evidence.repair_plan(previous)
    previous = failed_run(); previous['backend']['executed'] = 1
    with pytest.raises(ValueError):
        evidence.repair_plan(previous)


def test_targeted_ios_requires_every_selected_case_and_rejects_other_tests(tmp_path):
    plan = evidence.repair_plan(failed_run())
    evidence.write(tmp_path / 'repair-selection.json', plan)
    (tmp_path / 'VerifiedAI.xcresult').mkdir()
    cases = failed_run()['ios']['failedCases']
    lines = [f"Test Case '-[{suite} {method}]' passed (1.0 seconds).\n" for suite, method in cases]
    log = tmp_path / 'ios-tests.log'
    log.write_text(''.join(lines))
    assert evidence.report_ios(tmp_path, 0) == 0
    assert evidence.read(tmp_path / 'ios-summary.json')['scope'] == 'CURRENT_RUN_SELECTED_CASES'
    log.write_text(lines[0])
    assert evidence.report_ios(tmp_path, 0) == 1
    log.write_text(''.join(lines) + "Test Case '-[VerifiedAITests.OtherTests testUnexpected]' passed\n")
    assert evidence.report_ios(tmp_path, 0) == 1
    log.write_text(''.join(lines))
    assert evidence.report_ios(tmp_path, 65) == 1


def test_partial_ios_cannot_be_promoted_to_full_acceptance(tmp_path):
    setup_run(tmp_path)
    evidence.write(tmp_path / 'ios-summary.json', {'status': 'PASS', 'scope': 'CURRENT_RUN_SELECTED_CASES'})
    assert evidence.summary(tmp_path, 0) == 2
    assert 'partial-ios-evidence-in-full-run' in evidence.read(tmp_path / 'summary.json')['missingMandatoryEvidence']


def test_backend_method_repair_does_not_rerun_passed_ios_or_claim_security():
    previous = failed_run()
    previous['gates']['D'] = 'PASS'
    previous['backend'] = {'executed': 148, 'failedCases': ['ModularityTest.verifiesSpringModulithBoundaries'],
                           'missingCases': [], 'skipped': []}
    plan = evidence.repair_plan(previous)
    assert plan['selectedGates'] == list('AB')
    assert plan['backendSelectors'] == ['ModularityTest#verifiesSpringModulithBoundaries']
    assert plan['iosSelectors'] == []
    assert plan['backendScope'] == 'FAILED_METHODS_ONLY'
    assert plan['securityPrerequisite'] == 'FULL_BACKEND_RERUN_REQUIRED'
    for invalid in ['ModularityTest.*', 'OtherTest.case', 'ModularityTest.case;echo command',
                    'CORRUPTED_XML:report.xml']:
        previous['backend']['failedCases'] = [invalid]
        with pytest.raises(ValueError):
            evidence.repair_plan(previous)


def test_backend_selected_evidence_requires_exact_cases_and_cannot_unlock_security(tmp_path):
    evidence.write(tmp_path / 'repair-selection.json', {'backendSelectors': ['ModularityTest#verifiesSpringModulithBoundaries']})
    folder = tmp_path / 'maven-reports'; folder.mkdir()
    report = folder / 'test.xml'
    case = '<testcase classname="com.verifiedai.architecture.ModularityTest" name="verifiesSpringModulithBoundaries"/>'
    report.write_text('<testsuite>' + case + '</testsuite>')
    assert evidence.report_junit(tmp_path, 'backend') == 0
    assert evidence.read(tmp_path / 'backend-summary.json')['scope'] == 'CURRENT_RUN_SELECTED_CASES'
    evidence.write(tmp_path / 'source-before.json', {'sha256': 'synthetic', 'files': {}})
    assert evidence.security(tmp_path) == 2
    report.write_text('<testsuite>' + case + '<testcase classname="ModularityTest" name="extra"/></testsuite>')
    assert evidence.report_junit(tmp_path, 'backend') == 1
    report.write_text('<testsuite/>')
    assert evidence.report_junit(tmp_path, 'backend') == 1
    report.write_text('<testsuite>' + case.replace('/>', '><failure/></testcase>') + '</testsuite>')
    assert evidence.report_junit(tmp_path, 'backend') == 1


def test_selected_backend_cannot_be_promoted_to_full_acceptance(tmp_path):
    setup_run(tmp_path)
    evidence.write(tmp_path / 'backend-summary.json', {'status': 'PASS', 'scope': 'CURRENT_RUN_SELECTED_CASES'})
    assert evidence.summary(tmp_path, 0) == 2
    assert 'partial-backend-evidence-in-full-run' in evidence.read(tmp_path / 'summary.json')['missingMandatoryEvidence']


def test_evaluation_suite_is_mandatory_not_substituted_by_7_2():
    assert 'AttemptEvaluationIntegrationTest' in evidence.BACKEND
    assert 'AttemptEvaluationPreparerTest' in evidence.BACKEND
    assert 'test_attempt_evaluation_golden' in evidence.VERIFIER
    assert 'test_structured_work_golden' in evidence.VERIFIER
    assert evidence.IOS_REQUIRED['AttemptEvaluationTests'] >= 8


def test_prior_sprint_failure_selection_is_not_current_evidence():
    previous = failed_run(); previous['sprint'] = '7.2'
    with pytest.raises(ValueError): evidence.repair_plan(previous)


def test_repeated_ios_log_lines_cannot_satisfy_missing_cases(tmp_path):
    (tmp_path / 'VerifiedAI.xcresult').mkdir()
    lines = []
    for suite, count in evidence.IOS_REQUIRED.items():
        target = 'VerifiedAIUITests' if suite.endswith('UITests') else 'VerifiedAITests'
        lines.extend([f"Test Case '-[{target}.{suite} testRepeated]' passed (0.1 seconds).\n"] * count)
    (tmp_path / 'ios-tests.log').write_text(''.join(lines))
    assert evidence.report_ios(tmp_path, 0) == 1
    assert 'AttemptEvaluationTests' in evidence.read(tmp_path / 'ios-summary.json')['missingCases']


def test_owner_runner_keeps_verifier_production_authorization_off():
    text = (QUALITY / 'sprint_7_3_owner_acceptance.sh').read_text()
    assert 'MATH_VERIFIER_ATTEMPT_PRODUCTION_AUTHORIZED=false' in text
    assert 'ATTEMPT_EVALUATION_ENABLED=false' in text
    assert 'MATH_VERIFIER_ATTEMPT_EVALUATION_ENABLED' not in text


def test_documentation_and_failed_ios_repair_only_selects_failed_checks():
    previous = failed_run()
    previous['gates'].update(B='PASS', D='FAIL', E='PASS', F='FAIL')
    plan = evidence.repair_plan(previous)
    assert plan['selectedGates'] == ['A', 'D', 'F']
    assert plan['backendSelectors'] == []
    assert plan['backendScope'] == 'NOT_RUN'
    assert len(plan['iosSelectors']) == 2
    assert plan['previousResultsImported'] is False


def test_documentation_only_repair_does_not_run_ios_or_backend():
    previous = failed_run()
    previous['gates'].update(B='PASS', D='PASS', E='PASS', F='FAIL')
    plan = evidence.repair_plan(previous)
    assert plan['selectedGates'] == ['A', 'F']
    assert plan['iosSelectors'] == []
    assert plan['backendSelectors'] == []


def test_second_repair_selects_only_remaining_english_failure():
    previous = failed_run()
    previous['gates'].update(B='NOT_RUN', C='NOT_RUN', D='FAIL', E='NOT_RUN', F='PASS', G='NOT_RUN')
    previous['mode'] = 'TARGETED'
    previous['ios']['failedCases'] = [['VerifiedAIUITests.AttemptAnalysisUITests',
        'testEvaluationEnglishExplicitActionAndAccessibility']]
    previous['ios']['passedCases'] = [['VerifiedAIUITests.AttemptAnalysisUITests',
        'testEvaluationTurkishExplicitActionAndAccessibility']]
    plan = evidence.repair_plan(previous)
    assert plan['selectedGates'] == ['A', 'D']
    assert plan['iosSelectors'] == ['VerifiedAIUITests/AttemptAnalysisUITests/testEvaluationEnglishExplicitActionAndAccessibility']
    assert plan['backendSelectors'] == []
    assert plan['previousResultsImported'] is False
