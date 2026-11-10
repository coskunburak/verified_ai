#!/usr/bin/env python3
"""Owner-invoked Sprint 7.5 synthetic contracts. No network or student data."""
import copy
import json
from pathlib import Path
import jsonschema

ROOT = Path(__file__).resolve().parents[2]
NAMES = ('mistake-dna-view-v1', 'mistake-dispute-request-v1', 'mistake-dispute-result-v1',
         'mistake-effective-state-v1', 'attempt-ocr-correction-request-v1', 'attempt-ocr-correction-result-v1')

def main():
    for name in NAMES:
        schema = json.loads((ROOT / 'packages/schemas' / (name + '.schema.json')).read_text())
        value = json.loads((ROOT / 'packages/schemas/fixtures' / (name + '.json')).read_text())
        jsonschema.Draft202012Validator.check_schema(schema)
        validator = jsonschema.Draft202012Validator(schema, format_checker=jsonschema.FormatChecker())
        validator.validate(value)
        for field in value:
            bad = copy.deepcopy(value); del bad[field]
            assert list(validator.iter_errors(bad)), (name, field)
        for field in ('confidence', 'ownerId', 'authority', 'trainingConsent'):
            bad = copy.deepcopy(value); bad[field] = 'forged'
            assert list(validator.iter_errors(bad)), (name, field)
    source = (ROOT / 'packages/contracts/openapi/public-api.yaml').read_text()
    for route in ('/api/v1/mistakes/dna', '/api/v1/mistakes/{eventId}/dispute',
                  '/api/v1/mistakes/{eventId}/effective-state',
                  '/api/v1/problem-sessions/{problemId}/attempts/{id}/steps/{stepIndex}/correct-ocr'):
        assert route in source, route
    localization = json.loads((ROOT / 'apps/ios/VerifiedAI/Resources/Localizable.xcstrings').read_text())['strings']
    for key in ('dispute', 'excluded', 'correct', 'step', 'correctedText', 'reevaluate', 'save', 'pending', 'reviewHistory'):
        for language in ('en', 'tr'):
            assert localization['attempt.dna.' + key]['localizations'][language]['stringUnit']['value']
    print('PASS: Sprint 7.5 synthetic contracts and localization')

if __name__ == '__main__':
    main()
