#!/usr/bin/env python3
"""Owner-run H-15 contract gate. No application or database access."""
import json
from pathlib import Path
import yaml
from jsonschema import Draft202012Validator, FormatChecker

ROOT = Path(__file__).resolve().parents[2]


def validate():
    base = ROOT / 'packages/schemas/exam/v1'
    specs = [yaml.safe_load((ROOT / path).read_text()) for path in (
        'packages/openapi/verified-ai-core-api.yaml',
        'packages/contracts/openapi/public-api.yaml')]
    for name, component in [('pre-exam-review-session', 'PreExamReviewSession'),
                            ('exam-cheat-sheet', 'ExamCheatSheet')]:
        schema = json.loads((base / f'{name}.schema.json').read_text())
        Draft202012Validator.check_schema(schema)
        validator = Draft202012Validator(schema, format_checker=FormatChecker())
        good = json.loads((base / 'examples/valid' / f'{name}.json').read_text())
        validator.validate(good)
        invalid = list((base / 'examples/invalid').glob(f'{name}.*.json'))
        assert len(invalid) >= 3
        for path in invalid:
            assert list(validator.iter_errors(json.loads(path.read_text()))), path
        for field in schema['required']:
            mutation = dict(good)
            del mutation[field]
            assert list(validator.iter_errors(mutation)), field
        for spec in specs:
            assert spec['components']['schemas'][component] == schema
    for prefix in ('/v1', '/api/v1'):
        for route in ('pre-exam-review/{examCode}', 'cheat-sheet/{examCode}',
                      'pre-exam-review/sessions/{sessionId}/export-events'):
            path = f'{prefix}/exams/{route}'
            assert specs[0]['paths'][path] == specs[1]['paths'][path]
            post = route.endswith('export-events')
            op = specs[0]['paths'][path]['post' if post else 'get']
            assert op['security'] == [{'bearerAuth': []}]
            assert {'401', '403', '404', '429', '500'} <= op['responses'].keys()
            header = 'Idempotency-Key' if post else 'If-None-Match'
            assert any(p['name'] == header for p in op['parameters'])
            for code in (('204',) if post else ('200', '304')):
                response = op['responses'][code]
                assert response['headers']['Cache-Control']['schema']['const'] == 'no-store'
            if not post:
                assert 'content' not in op['responses']['304']
                assert 'ETag' in op['responses']['200']['headers']
    print('PASS: H-15 schema fixtures and mirrored HTTP contracts only')


if __name__ == '__main__':
    validate()
