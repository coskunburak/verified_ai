#!/usr/bin/env python3
"""Owner-only static reconciliation; no provider access or credential discovery."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def main():
    schema = json.loads((ROOT / 'packages/schemas/tutor-hint-v1.schema.json').read_text())
    api = (ROOT / 'packages/contracts/openapi/public-api.yaml').read_text()
    embedded, _ = json.JSONDecoder().raw_decode(api.split('    TutorHint: ', 1)[1])
    if embedded != {k: v for k, v in schema.items() if k not in ('$schema', '$id', 'title')}:
        raise SystemExit('Hint OpenAPI/schema mismatch')
    for operation in ('requestTutorHint', 'readTutorHints', 'requestTutorAnswerReveal'):
        if operation not in api:
            raise SystemExit('Missing operation: ' + operation)
    config = (ROOT / 'services/api/src/main/resources/application.yml').read_text()
    if 'hints-enabled: ${TUTOR_HINTS_ENABLED:false}' not in config:
        raise SystemExit('Hints must default disabled')
    catalog = json.loads((ROOT / 'apps/ios/VerifiedAI/Resources/Localizable.xcstrings').read_text())
    for key in ('tutor.hint.H1','tutor.hint.H2','tutor.hint.H3','tutor.reveal.confirmation','tutor.reveal.unavailable'):
        for language in ('en', 'tr'):
            if not catalog['strings'][key]['localizations'][language]['stringUnit']['value']:
                raise SystemExit('Missing localized hint copy')
    dataset = json.loads((ROOT / 'evaluations/golden-datasets/tutoring/progressive-hints-v1.json').read_text())
    if dataset['provenance'] != 'SYNTHETIC' or dataset['trainingEligibility'] != 'NOT_ELIGIBLE':
        raise SystemExit('Synthetic evidence eligibility mismatch')
    print('Hint schema, API, localization, feature default and synthetic provenance reconciled.')

if __name__ == '__main__':
    main()
