#!/usr/bin/env python3
"""Owner-only synthetic API/schema/localization checks. Never called during source implementation."""
import copy
import json
from pathlib import Path
import jsonschema
import yaml

ROOT = Path(__file__).resolve().parents[2]

def main():
    schema = json.loads((ROOT / 'packages/schemas/mastery-skills-v1.schema.json').read_text())
    fixture = json.loads((ROOT / 'packages/schemas/fixtures/mastery-skills-v1.json').read_text())
    jsonschema.Draft202012Validator.check_schema(schema)
    validator = jsonschema.Draft202012Validator(schema, format_checker=jsonschema.FormatChecker())
    validator.validate(fixture)
    for key in fixture:
        bad = copy.deepcopy(fixture); del bad[key]
        assert list(validator.iter_errors(bad)), key
    for evidence in ('INDEPENDENT', 'HINT_ASSISTED', 'SOLUTION_REVEALED_ASSISTED', 'UNSUPPORTED'):
        value = copy.deepcopy(fixture)
        value['outcomes'][0].update(evidenceType=evidence, observedCorrectness=None if evidence == 'UNSUPPORTED' else False)
        if evidence == 'UNSUPPORTED':
            value['outcomes'][0].pop('canonicalSkillCode', None)
        validator.validate(value)
    legacy = copy.deepcopy(fixture)
    legacy['outcomes'][0].pop('canonicalSkillCode')
    for key in ('retentionScore', 'confidence', 'reassessmentDue', 'reassessmentReason'):
        del legacy['skills'][0][key]
    validator.validate(legacy)
    for patch in ({'retentionScore': 1.5}, {'confidence': -1}, {'reassessmentDue': True}, {'reassessmentReason': None}):
        invalid = copy.deepcopy(fixture)
        invalid['skills'][0].update(patch)
        assert list(validator.iter_errors(invalid)), patch
    for mutation in ('owner', 'falseMastered', 'disabled', 'unsupported'):
        bad = copy.deepcopy(fixture)
        if mutation == 'owner': bad['ownerId'] = 'forged'
        if mutation == 'falseMastered': bad['skills'][0].update(status='MASTERED', independentEvidenceCount=0)
        if mutation == 'disabled': bad['enabled'] = False
        if mutation == 'unsupported': bad['outcomes'][0]['evidenceType'] = 'UNSUPPORTED'
        assert list(validator.iter_errors(bad)), mutation
    api = yaml.safe_load((ROOT / 'packages/contracts/openapi/public-api.yaml').read_text())
    assert api['paths']['/api/v1/mastery/skills']['get']['security'] == [{'bearerAuth': []}]
    assert api['components']['schemas']['MasterySkillsView'] == {k: v for k, v in schema.items() if k not in ('$schema', 'title')}
    strings = json.loads((ROOT / 'apps/ios/VerifiedAI/Resources/Localizable.xcstrings').read_text())['strings']
    for key in ('independent', 'independentIncorrect', 'hint', 'hintIncorrect', 'revealed', 'unsupported', 'loading', 'empty', 'failed', 'retry'):
        for language in ('tr', 'en'):
            assert strings['attempt.mastery.' + key]['localizations'][language]['stringUnit']['value']
    print('PASS: mastery contracts, anti-crutch payloads and EN/TR localization')

if __name__ == '__main__':
    main()
