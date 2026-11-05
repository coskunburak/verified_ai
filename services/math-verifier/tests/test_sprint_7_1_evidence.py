"""Synthetic evidence-writer regressions; authored for owner execution only."""
import json
import sys
from pathlib import Path

QUALITY = Path(__file__).resolve().parents[3] / 'scripts' / 'quality'
sys.path.insert(0, str(QUALITY))
import sprint_7_1_evidence as evidence


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
