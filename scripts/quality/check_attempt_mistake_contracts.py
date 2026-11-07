#!/usr/bin/env python3
"""Owner-only Sprint 7.4 contracts. Synthetic local inputs; no inference or network."""
import copy
import json
import re
from pathlib import Path
import jsonschema

ROOT = Path(__file__).resolve().parents[2]
SCHEMAS = ROOT / 'packages/schemas'
def load(name): return json.loads((SCHEMAS / name).read_text())

def main():
    for name in ('attempt-mistake-request-v1', 'attempt-mistake-classification-v1', 'attempt-mistake-view-v1'):
        schema = load(name + '.schema.json')
        jsonschema.Draft202012Validator.check_schema(schema)
        validator = jsonschema.Draft202012Validator(schema, format_checker=jsonschema.FormatChecker())
        fixture = load('fixtures/' + name + '.json')
        validator.validate(fixture)
        for field in fixture:
            bad = copy.deepcopy(fixture); del bad[field]
            assert list(validator.iter_errors(bad)), (name, field)
        for field in ('confidence', 'provider', 'clientOwner', 'verified'):
            bad = copy.deepcopy(fixture); bad[field] = 'forged'
            assert list(validator.iter_errors(bad)), (name, field)
    name = 'attempt-mistake-classification-v1'
    validator = jsonschema.Draft202012Validator(load(name + '.schema.json'))
    fixture = load('fixtures/' + name + '.json')
    for path, values in {
        ('taxonomyVersion',): ['attempt-taxonomy-v99', 'attempt-taxonomy-v1'],
        ('trainingEligible',): [True], ('supportingEvidenceId',): [None, 'forged'],
        ('decision', 'authority'): ['MODEL_SUGGESTION', 'USER_CORRECTION', 'HUMAN_REVIEW'],
        ('decision', 'evidenceStrength'): ['NONE', 'EXACT_DISPROOF', 0.95],
        ('decision', 'reviewStatus'): ['USER_DISPUTED', 'HUMAN_REVIEWED'],
        ('decision', 'category'): ['UNIT_ERROR', 'NOTATION_ERROR', 'OTHER_REVIEWED'],
        ('decision', 'skillId'): ['attempt-skill-v1:ALG_SIGN_FLIP_DISTRIBUTE', 'invented-skill'],
    }.items():
        for value in values:
            bad = copy.deepcopy(fixture); target = bad
            for part in path[:-1]: target = target[part]
            target[path[-1]] = value
            assert list(validator.iter_errors(bad)), (path, value)
    taxonomy = load('attempt-taxonomy-v2.json')
    mapping = load('mistake-category-v1.json')
    codes = {c['code'] for c in taxonomy['specificCodes']}
    assert set(mapping['unsupportedCategories']) == {'CALCULATION_ERROR','UNIT_ERROR','NOTATION_ERROR','INCOMPLETE_SOLUTION','OTHER_REVIEWED'}
    assert len(codes) == 17 and {m['code'] for m in mapping['mappings']} == codes
    java = (ROOT / 'services/api/src/main/java/com/verifiedai/mistake/MistakeTaxonomy.java').read_text()
    swift = (ROOT / 'apps/ios/VerifiedAI/Features/AttemptAnalysis/Domain/AttemptAnalysisModels.swift').read_text()
    java_mapping = dict(re.findall(r'Map.entry\("([A-Z_]+)", Category\.([A-Z_]+)\)', java))
    assert java_mapping == {m['code']: m['category'] for m in mapping['mappings']}
    swift_names = {'sign':'SIGN_ERROR','algebra':'ALGEBRA_ERROR','concept':'CONCEPT_ERROR','formula':'FORMULA_ERROR','interpretation':'INTERPRETATION_ERROR'}
    swift_mapping = {code:swift_names[category] for code,category in re.findall(r'"([A-Z_]+)": \.(sign|algebra|concept|formula|interpretation)\b', swift)}
    assert swift_mapping == java_mapping
    localization = json.loads((ROOT / 'apps/ios/VerifiedAI/Resources/Localizable.xcstrings').read_text())['strings']
    for key, entry in localization.items():
        if key.startswith('attempt.classification.'):
            for lang in ('en', 'tr'): assert entry['localizations'][lang]['stringUnit']['value'].strip()
    for row in mapping['mappings']:
        assert 'attempt.classification.correction.' + row['code'] in localization
        assert 'attempt.classification.category.' + row['category'] in localization
        if row['skillId']: assert row['skillId'] in java and row['skillId'] in swift
    golden = load('fixtures/attempt-mistake-golden-v1.json')
    assert golden['syntheticOnly'] is True and {c['code'] for c in golden['cases']} == codes
    for case in golden['cases']:
        assert case['evidenceJustification']
        jsonschema.validate(case['request'], load('attempt-verification-v2.schema.json'))
    work = load('fixtures/attempt-mistake-work-v1.json')
    jsonschema.validate(work, load('attempt-structured-work-v1.schema.json'))
    evaluation = load('fixtures/attempt-mistake-evaluation-v1.json')
    assert evaluation['stepIds'] == [step['id'] for step in work['steps']]
    jsonschema.validate(evaluation, load('attempt-evaluation-v1.schema.json'))
    del evaluation['analysis']['steps'][0]['diagnosis']['classifierPolicyVersion']
    jsonschema.validate(evaluation, load('attempt-evaluation-v1.schema.json'))
    evaluation['analysis']['steps'][0]['diagnosis']['classifierPolicyVersion'] = 'future-v99'
    assert list(jsonschema.Draft202012Validator(load('attempt-evaluation-v1.schema.json')).iter_errors(evaluation))
    # New category contract must never reinterpret historical mathematical evidence.
    for name in ('attempt-analysis-v1', 'attempt-analysis-v2'):
        schema = load(name + '.schema.json'); jsonschema.Draft202012Validator.check_schema(schema)
    assert taxonomy['legacyGeneric'] == 'ALG_TRANSFORMATION_INVALID'
    public = (ROOT / 'packages/contracts/openapi/public-api.yaml').read_text()
    assert 'operationId: classifyAttemptMistake' in public and 'operationId: readAttemptMistakeClassification' in public
    assert 'ATTEMPT_CLASSIFICATION_NOT_READY' in public
    print('Sprint 7.4 strict contracts, category mapping and synthetic inventory passed')

if __name__ == '__main__': main()
