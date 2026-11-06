#!/usr/bin/env python3
"""Fresh full-scope evidence, reusing Phase 7 source/security/JUnit primitives."""
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
import sprint_7_2_evidence as phase7
from sprint_7_2_evidence import ROOT, fingerprint, read, write, security

BACKEND = phase7.BACKEND + ['VisualizationContractTest', 'VisualizationVerifierTest',
    'VisualizationControllerTest', 'VisualizationIntegrationTest', 'SocraticTurnIntegrationTest',
    'TutorPersistenceIntegrationTest']
GATES = tuple('ABCDEFG')
IOS_FILES = [
    'VerifiedAITests/ProblemSolving/PrimarySolveViewModelTests.swift',
    'VerifiedAITests/ProblemSolving/MathPresentationTests.swift',
    'VerifiedAITests/ProblemSolving/ExplanationPresentationTests.swift',
    'VerifiedAITests/ProblemSolving/MathVisualizationTests.swift',
    'VerifiedAITests/ProblemSolving/SolveRecoveryStoreTests.swift',
    'VerifiedAITests/Tutor/TutorTests.swift',
    'VerifiedAITests/AttemptAnalysis/AttemptAnalysisTests.swift',
    'VerifiedAIUITests/PrimarySolveUITests.swift', 'VerifiedAIUITests/TutorUITests.swift',
    'VerifiedAIUITests/MathVisualizationUITests.swift', 'VerifiedAIUITests/AttemptAnalysisUITests.swift']


def expected_ios():
    """Inventory every XCTest method in required source, including Phase 7 classes."""
    expected = set()
    for name in IOS_FILES:
        source = (ROOT / 'apps/ios' / name).read_text()
        matches = list(re.finditer(r'\b(?:final\s+)?class\s+(\w+)\s*:\s*XCTestCase', source))
        if not matches:
            raise ValueError('Missing required XCTest class: ' + name)
        for index, match in enumerate(matches):
            body = source[match.end():matches[index + 1].start() if index + 1 < len(matches) else len(source)]
            methods = re.findall(r'\bfunc\s+(test\w+)\s*\(', body)
            if not methods:
                raise ValueError('Empty required XCTest class: ' + match[1])
            expected.update((name.split('/')[0] + '.' + match[1], method) for method in methods)
    return expected


def reconcile_ios(text, result, expected, exit_code):
    passed = set(re.findall(r"Test Case '-\[([^ ]+) ([^\]]+)\]' passed", text))
    failed = re.findall(r"Test Case '-\[([^ ]+) ([^\]]+)\]' failed", text)
    missing = sorted(expected - passed)
    skipped = bool(re.search(r'Test Case .* skipped', text)) or result.get('skippedTests', 0) != 0
    valid_bundle = (result.get('result') == 'Passed' and result.get('totalTestCount', 0) > 0
                    and result.get('failedTests', -1) == 0 and result.get('passedTests', 0) >= len(expected))
    status = 'PASS' if exit_code == 0 and expected and not missing and not failed and not skipped and valid_bundle else 'FAIL'
    return {'status': status, 'scope': 'CURRENT_RUN_FULL_IOS', 'passedCases': sorted(passed),
            'failedCases': failed, 'missingCases': missing, 'skipped': skipped,
            'bundleSummary': result, 'artifact': 'VerifiedAI.xcresult', 'expectedCount': len(expected)}


def repair_plan(previous):
    if previous.get('sprint') != '6.10' or previous.get('sourceUnchanged') is not True or previous.get('gates', {}).get('D') != 'FAIL':
        raise ValueError('Expected a source-stable Sprint 6.10 failed iOS run')
    ios = previous.get('ios', {})
    passed = {tuple(case) for case in ios.get('passedCases', [])}
    unresolved = {tuple(case) for case in ios.get('failedCases', []) + ios.get('missingCases', [])}
    selected = unresolved - passed
    if not selected or ios.get('skipped'):
        raise ValueError('No unambiguous failed/unexecuted cases available')
    selectors = sorted(phase7.ios_selector(case) for case in selected)
    return {'selectors': selectors, 'expectedCases': sorted(selected),
            'excludedPassedCount': len(passed), 'previousResultsImported': False}


def report_ios(out, code):
    plan = read(out / 'repair-selection.json')
    expected = {tuple(case) for case in plan['expectedCases']} if 'expectedCases' in plan else expected_ios()
    value = reconcile_ios((out / 'ios-tests.log').read_text(errors='replace'),
                          read(out / 'xcresult-summary.json'), expected, code)
    if 'expectedCases' in plan:
        unexpected = sorted({tuple(case) for case in value['passedCases'] + value['failedCases']} - expected)
        value.update(scope='CURRENT_RUN_SELECTED_CASES', selectedCases=sorted(expected), unexpectedCases=unexpected)
        if unexpected:
            value['status'] = 'FAIL'
    write(out / 'ios-summary.json', value)
    return 0 if value['status'] == 'PASS' else 1


def report_junit(out, kind):
    phase7.BACKEND = BACKEND
    code = phase7.report_junit(out, kind)
    value = read(out / 'backend-summary.json')
    # This sprint grants no skip waiver, including platform-specific skips.
    if value.get('skipped') or value.get('platformSpecificSkipped'):
        value['status'] = 'BLOCKED'
        write(out / 'backend-summary.json', value)
        return 2
    return code


def summary(out, code):
    gates = {g: (out / (g + '.status')).read_text().strip() if (out / (g + '.status')).exists() else 'NOT_RUN' for g in (*GATES, 'cleanup')}
    before, after = read(out / 'source-before.json'), read(out / 'source-after.json')
    unchanged = bool(before.get('sha256')) and before.get('sha256') == after.get('sha256')
    reports = {key: read(out / (key + '-summary.json')) for key in ('backend', 'ios', 'security', 'localization', 'evidence-selftests')}
    metadata = read(out / 'execution-metadata.json')
    mode = metadata.get('mode')
    selected = metadata.get('selectedGates', [])
    targeted = mode == 'TARGETED' and selected == ['D']
    valid_scope = targeted or (mode == 'FULL' and selected == list(GATES))
    required_gates = ['D', 'cleanup'] if targeted else [*GATES, 'cleanup']
    required_reports = ['ios'] if targeted else list(reports)
    missing = [g for g in required_gates if gates[g] != 'PASS']
    missing += [key for key in required_reports if reports[key].get('status') != 'PASS']
    if not valid_scope:
        missing.append('execution-scope-metadata')
    if mode == 'FULL' and reports['ios'].get('scope') == 'CURRENT_RUN_SELECTED_CASES':
        missing.append('partial-ios-evidence-in-full-run')
    if targeted and any(gates[g] != 'NOT_RUN' for g in GATES if g != 'D'):
        missing.append('unexpected-gate-in-targeted-run')
    failure = code not in (0, 2) or not unchanged or 'FAIL' in gates.values() or any(r.get('status') == 'FAIL' for r in reports.values())
    status = 'FAIL' if failure else 'BLOCKED' if missing else 'PASS'
    inventory = {**reports, 'missingMandatoryEvidence': missing, 'gates': gates, 'sourceUnchanged': unchanged}
    write(out / 'failure-inventory.json', inventory)
    external = ['Physical-device VoiceOver EN/TR including math and chart navigation',
                'Light/dark increased-contrast and Reduce Motion visual review',
                'Oldest-supported physical-device responsiveness, memory, repeated navigation and keyboard checks',
                'Linux/hosted CI release security gate; no local skip waiver']
    acceptance = 'TARGETED_REPAIR_PASSED' if targeted and status == 'PASS' else status
    value = {'sprint': '6.10', 'overall': status, 'automatedAcceptance': acceptance,
             'mode': mode, 'selectedGates': selected,
             'currentSourceFullAcceptance': status if mode == 'FULL' and valid_scope else 'NOT_VERIFIED',
             'sprintStatus': 'BLOCKED_BY_EXTERNAL_GATE' if status == 'PASS' and not targeted else 'SOURCE_COMPLETE_PENDING_OWNER_ACCEPTANCE',
             'sourceUnchanged': unchanged, 'sourceFingerprint': before.get('sha256'),
             'gates': gates, **reports, 'repairSelection': read(out / 'repair-selection.json'), 'externalValidationRequired': external,
             'performance': 'XCTest measurement attachments in VerifiedAI.xcresult; device baselines not measured by agent',
             'productionAuthorization': 'NOT_AUTHORIZED', 'previousEvidenceImported': False,
             'finishedAt': datetime.now(timezone.utc).isoformat()}
    write(out / 'summary.json', value)
    (out / 'summary.md').write_text('# Sprint 6.10 owner acceptance\n\nAutomated gates: ' + acceptance + '\n\n' +
        '\n'.join('- ' + g + ': ' + state for g, state in gates.items()) +
        '\n\nScope: ' + str(mode) + '. Targeted runs do not certify full acceptance.' + '\nSource unchanged: ' + str(unchanged) + '\n\nExternal evidence still required:\n\n' +
        '\n'.join('- ' + item for item in external) + '\n\nNo production authorization.\n')
    for gate, state in gates.items():
        reason = out / (gate + '.blocked')
        write(out / ('gate-' + gate + '.json'), {'status': state, 'log': gate + '.log',
              'reason': reason.read_text() if reason.exists() else None})
    return 0 if status == 'PASS' else 1 if status == 'FAIL' else 2


if __name__ == '__main__':
    command, destination = sys.argv[1:3]
    out = Path(destination)
    if command == 'fingerprint': fingerprint(out)
    elif command == 'junit': sys.exit(report_junit(out, sys.argv[3]))
    elif command == 'security': sys.exit(security(out))
    elif command == 'ios': sys.exit(report_ios(out, int(sys.argv[3])))
    elif command == 'summary': sys.exit(summary(out, int(sys.argv[3])))
    elif command == 'repair-plan':
        prior = Path(sys.argv[3]).resolve()
        if not prior.is_relative_to(ROOT / '.generated' / 'sprint-6-10-owner'):
            raise ValueError('Prior run must be in the Sprint 6.10 evidence directory')
        plan = repair_plan(read(prior / 'summary.json'))
        plan['failureSource'] = str(prior.relative_to(ROOT))
        write(out, plan)
