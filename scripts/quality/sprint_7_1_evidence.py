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
           'TutorSessionControllerTest', 'ProgressiveHintIntegrationTest']
VERIFIER = ['test_attempt_verification_and_counterexample', 'test_attempt_v2_product', 'test_method_comparison_v2',
            'test_sprint_7_1_evidence']
CRITICAL = ['StudentAttemptIntegrationTest.upgradeFromV032PreservesHistoricalEvidenceWithoutRewritingIt',
            'StudentAttemptIntegrationTest.answerOnlyAndReasoningAreExactDurableUnassessedEvidence',
            'StudentAttemptIntegrationTest.finalAnswerAndStepMembershipCannotBeChangedAfterAcceptance',
            'StudentAttemptIntegrationTest.finalAnswerConcurrentReplayStaleAndDisabledHistory',
            'StudentAttemptIntegrationTest.deletedAccountCannotBeRecreatedByLateAssessment',
            'StudentAttemptSubmissionControllerTest.answerAcknowledgementHistoryAndLegacyProjection']


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
    value = junit(paths, BACKEND if kind == 'backend' else VERIFIER, kind=kind)
    if kind == 'backend':
        absent = [name for name in CRITICAL if not any(c == name or c == name + '()' for c in value['caseNames'])]
        value['missingCases'].extend(absent)
        if absent:
            value['status'] = 'FAIL'
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
    passed = backend.get('status') == 'PASS' and bool(snapshot.get('sha256')) and not findings
    value = {'status': 'FAIL' if findings else 'PASS' if passed else 'BLOCKED', 'findings': findings,
             'scope': 'Allowlisted repository source; private files and credential stores excluded',
             'privacyAuthorizationTrainingAndAbuse': backend.get('status', 'NOT_RUN')}
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
    value = {'sprint': '7.1', 'overall': status, 'acceptance': acceptance, 'mode': mode,
             'currentSourceAcceptance': acceptance if mode == 'FULL' else 'NOT_VERIFIED',
             'sourceFingerprint': before.get('sha256'), 'sourceUnchanged': unchanged, 'sourceIntegrity': source_status,
             'gates': gates, 'selectedGates': selected, **reports, 'execution': metadata,
             'finishedAt': datetime.now(timezone.utc).isoformat(), 'missingMandatoryEvidence': missing,
             'migration': 'V033; PostgreSQL fresh and V032 upgrade tests required in Gate B',
             'productionAuthorization': 'NOT_AUTHORIZED', 'hostedCI': 'NOT_VERIFIED',
             'physicalDeviceAccessibility': 'NOT_VERIFIED', 'testFlight50StudentGate': 'NOT_VERIFIED',
             'paidInference': 'DISABLED', 'failureInventory': 'failure-inventory.json'}
    write(out / 'summary.json', value)
    for gate in (*GATES, 'cleanup'):
        blocked = out / (gate + '.blocked')
        write(out / ('gate-' + gate + '.json'), {'gate': gate, 'status': gates[gate], 'log': gate + '.log',
              'reason': blocked.read_text().strip() if blocked.exists() else None})
    (out / 'summary.md').write_text('# Sprint 7.1 owner acceptance\n\n' + acceptance + '\n\n' +
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
