#!/usr/bin/env python3
"""Strict H-03 fixtures, mutations, and dual-path OpenAPI wiring."""
from pathlib import Path
import json
from jsonschema import Draft202012Validator, FormatChecker
import yaml

ROOT=Path(__file__).resolve().parents[2]

def validate():
    base=ROOT/'packages/schemas/exam/v1'
    spec=yaml.safe_load((ROOT/'packages/openapi/verified-ai-core-api.yaml').read_text())
    assert spec['openapi']=='3.1.0'
    for name in ('exam-attribution-report','exam-study-rebalance-request','exam-study-rebalance-response'):
        schema=json.loads((base/f'{name}.schema.json').read_text())
        Draft202012Validator.check_schema(schema)
        validator=Draft202012Validator(schema,format_checker=FormatChecker())
        good=json.loads((base/'examples/valid'/f'{name}.json').read_text())
        validator.validate(good)
        bad=list((base/'examples/invalid').glob(f'{name}.*.json'))
        assert len(bad)>=3
        for path in bad:
            assert list(validator.iter_errors(json.loads(path.read_text()))), f'Invalid fixture accepted: {path.name}'
        for key in schema['required']:
            mutation=dict(good);mutation.pop(key)
            assert list(validator.iter_errors(mutation)),f'Missing required field accepted: {name}.{key}'
    for prefix in ('/v1','/api/v1'):
        for route,verb in (('attribution-report','get'),('apply-study-rebalance','post')):
            operation=spec['paths'][f'{prefix}/exams/sessions/{{sessionId}}/{route}'][verb]
            assert operation['security']==[{'bearerAuth':[]}]
            assert '200' in operation['responses'] and '403' in operation['responses']
            if verb=='post': assert any(p['name']=='Idempotency-Key' and p['required'] for p in operation['parameters'])
    print('PASS: Sprint 9.7 schemas, positive/negative fixtures and dual-path OpenAPI')

if __name__=='__main__': validate()
