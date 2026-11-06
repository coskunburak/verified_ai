#!/usr/bin/env python3
"""Owner-only synthetic contract checks. Does not evaluate mathematical correctness or call AI."""
import json
import subprocess
from copy import deepcopy
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[2]
SCHEMAS = ROOT / 'packages/schemas'
BASE = '8591761b0019396cde94a5cba5beb18bcd639eb7'


def contract(name):
    value = json.loads((SCHEMAS / (name + '.schema.json')).read_text())
    Draft202012Validator.check_schema(value)
    return Draft202012Validator(value, format_checker=FormatChecker())


def main():
    for name in ('attempt-structured-work-request-v1', 'attempt-structured-work-v1'):
        check = contract(name)
        value = json.loads((SCHEMAS / 'fixtures' / (name + '.json')).read_text())
        check.validate(value)
        for key in value:
            bad = deepcopy(value); del bad[key]
            assert list(check.iter_errors(bad)), (name, key)
        for field in ('verified', 'analysis', 'mastery', 'unknown'):
            assert list(check.iter_errors({**value, field: True})), field
        bad = deepcopy(value); bad['steps'] *= 7
        assert list(check.iter_errors(bad))
    request_validator = contract('attempt-structured-work-request-v1')
    for name in ('native', 'gateway'):
        request_validator.validate(json.loads((SCHEMAS / 'fixtures' / ('attempt-structured-work-' + name + '-v1.json')).read_text()))
    negatives = json.loads((SCHEMAS / 'fixtures/attempt-structured-work-invalid-v1.json').read_text())
    assert negatives['synthetic']
    for case in negatives['cases']:
        assert list(request_validator.iter_errors(case['input'])), case['name']
    request = json.loads((SCHEMAS / 'fixtures/attempt-structured-work-request-v1.json').read_text())
    check = contract('attempt-structured-work-request-v1')
    for change in ({'provider': 'forged'}, {'engine': 'UNKNOWN'}, {'captureId': request['source']['canonicalId']},
                   {'observations': [{'id': request['source']['parseId'], 'rawText': 'x', 'confidence': 1.01, 'box': None}]}):
        assert list(check.iter_errors({**request, **change}))
    # Native uncertainty retains immutable observation even when a learner splits it into two steps.
    native = deepcopy(request); native['engine'] = 'NATIVE_VISION'; native['captureId'] = request['source']['canonicalId']
    native['observations'] = [{'id': request['source']['parseId'], 'rawText': '1/l\n0/O', 'confidence': 0.1,
                               'box': {'x': 0, 'y': 0, 'width': 1, 'height': 1}}]
    for step in native['steps']:
        step['observationIds'] = [request['source']['parseId']]; step['confirmed'] = False
    check.validate(native)
    for field, value in [('confidence', 2), ('confidence', '0.8'), ('box', {'x': -1, 'y': 0, 'width': 1, 'height': 1})]:
        bad = deepcopy(native); bad['observations'][0][field] = value
        assert list(check.iter_errors(bad))
    response = json.loads((SCHEMAS / 'fixtures/attempt-structured-work-v1.json').read_text())
    check = contract('attempt-structured-work-v1')
    for change in ({'trainingEligible': True}, {'provenance': 'SERVER_RECORDED'}, {'revision': 31}):
        assert list(check.iter_errors({**response, **change}))
    bad = deepcopy(response); bad['steps'][0]['normalizationStatus'] = 'NORMALIZED'
    assert list(check.iter_errors(bad))  # No AST means no normalized syntax, never false success.
    golden = json.loads((SCHEMAS / 'fixtures/attempt-structured-work-golden.json').read_text())
    assert golden['synthetic'] and len(golden['cases']) >= 20
    assert {'domain-hole', 'root-loss', 'radical', 'log-domain', 'fraction-layout', 'two-solutions', 'injection', 'unconfirmed'} <= {x['id'] for x in golden['cases']}
    assert all(x['status'] in {'NORMALIZED', 'UNSUPPORTED', 'REVIEW_REQUIRED'} for x in golden['cases'])
    # Freeze historical wire meaning and migrations rather than invent new legacy hashes.
    for name in ('attempt-submission-v1', 'attempt-submission-v2', 'attempt-submission-v3', 'student-attempt-v1', 'student-attempt-v2'):
        path = 'packages/schemas/' + name + '.schema.json'
        assert (ROOT / path).read_bytes() == subprocess.check_output(['git', 'show', BASE + ':' + path], cwd=ROOT)
    for version in range(30, 34):
        path = next((ROOT / 'services/api/src/main/resources/db/migration/platform').glob(f'V{version:03}__*.sql'))
        assert path.read_bytes() == subprocess.check_output(['git', 'show', BASE + ':' + path.relative_to(ROOT).as_posix()], cwd=ROOT)
    import yaml
    from openapi_spec_validator import validate
    api = ROOT / 'packages/contracts/openapi/public-api.yaml'
    document = yaml.safe_load(api.read_text()); validate(document, base_uri=api.as_uri())
    route = document['paths']['/api/v1/problem-sessions/{sessionId}/attempts/structured-work']['post']
    assert route['security'] == [{'bearerAuth': []}]
    assert {'200', '401', '403', '404', '409', '413', '422', '429', '503'} <= set(route['responses'])
    catalog = json.loads((ROOT / 'apps/ios/VerifiedAI/Resources/Localizable.xcstrings').read_text())['strings']
    for key in ('scope', 'NORMALIZED', 'UNSUPPORTED', 'REVIEW_REQUIRED', 'saved', 'split', 'merge', 'sourceRegion'):
        assert all(catalog['attempt.work.' + key]['localizations'][lang]['stringUnit']['value'] for lang in ('en', 'tr'))
    assert 'attempt-structured-work-v1.json in Resources' in (ROOT / 'apps/ios/VerifiedAI.xcodeproj/project.pbxproj').read_text()
    print('Structured-work contracts, immutable baselines and synthetic golden inventory checked.')


if __name__ == '__main__':
    main()
