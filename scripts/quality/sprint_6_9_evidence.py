#!/usr/bin/env python3
"""Sprint 6.9 full-run evidence. No historical PASS reuse; skips always explicit."""
import hashlib, json, subprocess, sys, xml.etree.ElementTree as ET
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]

LINUX_RESOURCE_TEST_PREFIXES = {
    'test_calculus_security.test_linux_calculus_resource_failure_is_reaped_and_nonproof',
    'test_execution_security.test_actual_memory_limit_exhaustion_is_a_nonproof_resource_failure',
    'test_numeric_security.test_linux_memory_and_cpu_limits_apply_to_numeric',
}


def fingerprint(out):
    roots = ['AGENTS.md','README.md','services/api','services/math-verifier','apps/ios','packages/contracts','packages/schemas',
             'scripts/quality','evaluations/golden-datasets/attempts','evaluations/golden-datasets/methods','evaluations/golden-datasets/visualizations','.github/workflows','docs/verified_ai_learning_platform_docs_v4']
    names = subprocess.check_output(['git','ls-files','-z','--cached','--others','--exclude-standard','--',*roots], cwd=ROOT).decode().split('\0')
    digest = hashlib.sha256(); files = {}
    for name in sorted(set(filter(None,names))):
        path = ROOT/name
        if any(p.startswith('.env') or p in {'.aws','.ssh','.config','.codex','.agents','.venv','__pycache__'} for p in path.parts): continue
        if path.suffix in {'.pem','.key','.p12','.pfx'}: continue
        if path.is_symlink() or not path.resolve().is_relative_to(ROOT) or not path.is_file():
            # Deleted tracked source participates in fingerprint instead of silently disappearing.
            files[name] = 'MISSING_OR_SYMLINK'
        else: files[name] = hashlib.sha256(path.read_bytes()).hexdigest()
        digest.update((name+'\0'+files[name]+'\n').encode())
    value = {'sha256':digest.hexdigest(),'files':files}
    Path(out).write_text(json.dumps(value,indent=2)+'\n')


def junit(paths, required, kind='verifier', platform_override=None):
    current_platform = platform_override if platform_override is not None else sys.platform
    seen, failures, skipped, count, cases = set(), [], [], 0, []
    failure_selectors = []; passed_selectors = []
    xml_corrupted = False

    for path in paths:
        try:
            root = ET.parse(path).getroot()
        except (ET.ParseError, OSError, UnicodeDecodeError):
            xml_corrupted = True
            failures.append(f'CORRUPTED_XML:{Path(path).name}')
            failure_selectors.append(f'CORRUPTED_XML:{Path(path).name}')
            continue

        for case in root.iter('testcase'):
            name = case.get('classname', '').split('.')[-1]
            seen.add(name); count += 1; label = name + '.' + case.get('name', ''); cases.append(label)
            if case.find('failure') is not None or case.find('error') is not None:
                failures.append(label)
            if case.find('skipped') is not None:
                skipped.append(label)
            parts = case.get('classname', '').split('.')
            modules = [i for i, part in enumerate(parts) if part.startswith('test_')]
            if modules:
                module = modules[-1]; relative = '/'.join(parts[:module + 1]) + '.py'
                if not relative.startswith('tests/'): relative = 'tests/' + relative
                selector = relative + '::' + '::'.join(parts[module + 1:] + [case.get('name', '')])
                if case.find('failure') is not None or case.find('error') is not None:
                    failure_selectors.append(selector)
                elif case.find('skipped') is None:
                    passed_selectors.append(selector)

    missing = sorted(set(required) - seen)

    platform_skips = []
    unexpected_skips = []

    if kind == 'verifier':
        # On non-Linux platforms (e.g. Darwin/macOS), the known Linux resource enforcement tests are legitimate skips.
        # On Linux, these tests MUST execute and cannot be skipped.
        is_linux = current_platform.startswith('linux')
        for case in skipped:
            base_label = case.split('[')[0]
            if (not is_linux) and (base_label in LINUX_RESOURCE_TEST_PREFIXES):
                platform_skips.append(case)
            else:
                unexpected_skips.append(case)
    else:
        unexpected_skips = list(skipped)

    is_pending = bool(platform_skips) and not current_platform.startswith('linux')
    platform_validation = (
        'OWNER_EXECUTED' if current_platform.startswith('linux') and not platform_skips
        else 'PLATFORM_SPECIFIC_VALIDATION_PENDING' if platform_skips
        else 'NOT_VERIFIED'
    )

    if xml_corrupted or not count or failures or missing:
        status = 'FAIL'
    elif unexpected_skips:
        status = 'BLOCKED'
    else:
        status = 'PASS'

    return {
        'status': status,
        'executed': count,
        'failureSelectors': failure_selectors,
        'passedSelectors': passed_selectors,
        'failures': failures,
        'failedCases': failures,
        'missing': missing,
        'missingCases': missing,
        'skipped': skipped,
        'unexpectedSkipped': unexpected_skips,
        'platformSpecificSkipped': platform_skips,
        'platformSpecificValidationPending': is_pending,
        'platformSpecificValidation': platform_validation,
        'caseNames': cases,
    }


def summary(out, exit_code):
    def read(name):
        try: return json.loads((out/name).read_text())
        except (ValueError,OSError): return {'status':'NOT_RUN'}
    gates = {n: (out / (n + '.status')).read_text().strip() for n in ('A', 'B', 'C', 'D', 'cleanup')}
    before = read('source-before.json'); after = read('source-after.json')
    unchanged = bool(before.get('sha256')) and before.get('sha256') == after.get('sha256')
    reports = {name: read(name + '-summary.json') for name in ('verifier', 'backend', 'ios')}
    golden = read('golden.json')
    dataset = ROOT / 'evaluations/golden-datasets/visualizations/bounded-real-v1.json'
    golden_valid = (
        golden.get('status') == 'PASS'
        and golden.get('sha256') == hashlib.sha256(dataset.read_bytes()).hexdigest()
        and golden.get('caseCount') == len(json.loads(dataset.read_text())['cases'])
        and golden.get('synthetic') is True
        and all(c.get('status') == 'PASS' for c in golden.get('cases', []))
    )
    missing = [k for k, v in reports.items() if v.get('status') != 'PASS']
    if not golden_valid: missing.append('golden')
    gates['E'] = 'PASS' if not missing else 'BLOCKED'
    gates['F'] = 'PASS' if unchanged else 'FAIL'
    status = 'FAIL' if exit_code or 'FAIL' in gates.values() else 'BLOCKED' if any(v != 'PASS' for v in gates.values()) else 'PASS'

    inventory = {
        k: {
            'failures': v.get('failures', []),
            'failedCases': v.get('failedCases', v.get('failures', [])),
            'missing': v.get('missing', []),
            'missingCases': v.get('missingCases', v.get('missing', [])),
            'skipped': v.get('skipped', []),
            'unexpectedSkipped': v.get('unexpectedSkipped', []),
            'platformSpecificSkipped': v.get('platformSpecificSkipped', []),
            'platformSpecificValidationPending': v.get('platformSpecificValidationPending', False),
        }
        for k, v in reports.items()
    }
    inventory['missingMandatoryEvidence'] = missing
    (out / 'failure-inventory.json').write_text(json.dumps(inventory, indent=2) + '\n')

    linux_skips = reports.get('verifier', {}).get('platformSpecificSkipped', [])
    linux_status = (
        'OWNER_EXECUTED' if sys.platform.startswith('linux') and reports.get('verifier', {}).get('status') == 'PASS' and not linux_skips
        else 'PLATFORM_SPECIFIC_VALIDATION_PENDING' if linux_skips
        else 'NOT_VERIFIED'
    )

    data = {
        'sprint': '6.9',
        'overall': status,
        'currentSourceAcceptance': 'LOCAL_ACCEPTANCE_PASSED' if status == 'PASS' else status,
        'localCurrentSourceAcceptance': 'LOCAL_ACCEPTANCE_PASSED' if status == 'PASS' else status,
        'actualHead': (out / 'head.txt').read_text().strip(),
        'sourceFingerprint': before.get('sha256'),
        'sourceUnchanged': unchanged,
        'gates': gates,
        **reports,
        'goldenEvaluation': golden,
        'hostedCI': 'NOT_VERIFIED',
        'physicalDeviceAccessibility': 'NOT_VERIFIED',
        'productionAuthorization': 'NOT_VERIFIED',
        'migration': 'NO_NEW_MIGRATION',
        'linuxResourceEvidence': {
            'status': linux_status,
            'skipped': linux_skips,
        },
        'missingMandatoryEvidence': missing,
        'failureInventory': 'failure-inventory.json',
    }
    (out / 'summary.json').write_text(json.dumps(data, indent=2) + '\n')

    md_lines = [
        '# Sprint 6.9 owner acceptance',
        '',
        f"{'LOCAL_ACCEPTANCE_PASSED' if status == 'PASS' else status} (Overall: {status})",
        '',
        '\n'.join('- ' + k + ': ' + v for k, v in gates.items()),
        '',
        f'Source unchanged: {unchanged}',
        f'Linux resource validation: {linux_status} (skipped {len(linux_skips)} Linux-only cases on macOS)' if sys.platform != 'linux' else f'Linux resource validation: {linux_status}',
        'Hosted CI: NOT_VERIFIED. Physical-device accessibility: NOT_VERIFIED. Production authorization: NOT_VERIFIED.',
        'See failure-inventory.json and per-gate logs.',
        ''
    ]
    (out / 'summary.md').write_text('\n'.join(md_lines))
    return 0 if status == 'PASS' else 1 if status == 'FAIL' else 2


if __name__ == '__main__':
    command = sys.argv[1]; out = Path(sys.argv[2])
    if command == 'fingerprint':
        fingerprint(out)
    elif command == 'summary':
        sys.exit(summary(out, int(sys.argv[3])))
    elif command == 'junit':
        kind = sys.argv[3]
        required = (
            ['VisualizationContractTest', 'VisualizationVerifierTest', 'VisualizationControllerTest',
             'VisualizationIntegrationTest', 'ModularityTest', 'StudentAttemptIntegrationTest',
             'AccountPrivacyApplicationServiceTest']
            if kind == 'backend'
            else ['test_math_visualization', 'test_method_comparison_v2', 'test_attempt_v2_product']
        )
        report = junit(list((out / ('maven-reports' if kind == 'backend' else 'verifier-reports')).glob('*.xml')), required, kind=kind)
        (out / (kind + '-summary.json')).write_text(json.dumps(report, indent=2) + '\n')
        sys.exit(0 if report['status'] == 'PASS' else 2 if report['status'] == 'BLOCKED' else 1)
