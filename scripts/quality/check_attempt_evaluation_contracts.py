#!/usr/bin/env python3
"""Owner-only strict contract/golden inventory; never invokes models or production services."""
import copy
import json
from pathlib import Path
import jsonschema

ROOT = Path(__file__).resolve().parents[2]
SCHEMAS = ROOT / 'packages/schemas'

def load(path): return json.loads(path.read_text())

def main():
    for name in ('attempt-evaluation-request-v1', 'attempt-evaluation-v1', 'attempt-evaluation-view-v1'):
        schema = load(SCHEMAS / (name + '.schema.json'))
        jsonschema.Draft202012Validator.check_schema(schema)
        validator = jsonschema.Draft202012Validator(schema, format_checker=jsonschema.FormatChecker())
        fixture = load(SCHEMAS / 'fixtures' / (name + '.json'))
        validator.validate(fixture)
        for key in fixture:
            bad = copy.deepcopy(fixture); del bad[key]
            assert list(validator.iter_errors(bad)), (name, 'missing', key)
        for key in ('provider', 'confidence', 'verified', 'clientOwner'):
            bad = copy.deepcopy(fixture); bad[key] = 'forged'
            assert list(validator.iter_errors(bad)), (name, key)
    work = load(SCHEMAS / 'fixtures/attempt-evaluation-work-v1.json')
    jsonschema.validate(work, load(SCHEMAS / 'attempt-structured-work-v1.schema.json'))
    evidence = load(SCHEMAS / 'fixtures/attempt-evaluation-v1.json')
    assert evidence['stepIds'] == [s['id'] for s in work['steps']]
    assert evidence['finalAnswerStatus'] == 'NOT_PROVIDED'
    assert not any(s['role'] == 'FINAL_ANSWER' for s in work['steps'])
    cases = load(SCHEMAS / 'fixtures/attempt-evaluation-golden.json')['cases']
    assert len(cases) >= 14 and len({c['id'] for c in cases}) == len(cases)
    schema = load(SCHEMAS / 'attempt-verification-v2.schema.json')
    for case in cases: jsonschema.validate(case['request'], schema)
    public = (ROOT / 'packages/contracts/openapi/public-api.yaml').read_text()
    assert 'operationId: evaluateStructuredAttempt' in public and 'operationId: readAttemptEvaluation' in public
    localization = load(ROOT / 'apps/ios/VerifiedAI/Resources/Localizable.xcstrings')['strings']
    for key in ('action', 'historical', 'RETRYABLE', 'final.VERIFIED', 'final.DISPROVEN', 'final.INCONCLUSIVE', 'final.NOT_PROVIDED', 'final.REVIEW_REQUIRED'):
        for lang in ('en', 'tr'):
            assert localization['attempt.evaluation.' + key]['localizations'][lang]['stringUnit']['value']
    print('Sprint 7.3 strict contracts and synthetic inventory passed')

if __name__ == '__main__': main()
