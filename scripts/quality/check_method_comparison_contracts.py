#!/usr/bin/env python3
"""Owner-only strict contract, localization and immutable-history checks."""
import json
import subprocess
from pathlib import Path
from jsonschema import Draft202012Validator
ROOT=Path(__file__).resolve().parents[2]
NAMES=['method-comparison-v2','method-comparison-result-v2','method-comparison-source-v2','method-comparison-public-v2']

def main():
    schemas={name:json.loads((ROOT/f'packages/schemas/{name}.schema.json').read_text()) for name in NAMES}
    for schema in schemas.values(): Draft202012Validator.check_schema(schema)
    fixture=json.loads((ROOT/'packages/schemas/fixtures/method-comparison-public-v2.json').read_text())
    Draft202012Validator(schemas['method-comparison-public-v2']).validate(fixture)
    catalog=json.loads((ROOT/'apps/ios/VerifiedAI/Resources/Localizable.xcstrings').read_text())['strings']
    for method in fixture['evidence']['methods']:
        c=method['comparison']
        keys=['attempt.method.'+method['methodId']]+['attempt.methods.'+c[k] for k in ['reasonCode','applicability','advantage','limitation','recommendation']]
        keys+=['attempt.methods.prerequisite.'+c['prerequisite'],'attempt.methods.complexity.'+c['manipulationLevel']]
        keys+=['attempt.methods.step.'+r for r in method['stepReasons']]
        for key in keys:
            for lang in ['en','tr']: assert catalog[key]['localizations'][lang]['stringUnit']['value']
    # Exhaustively check all schema-defined families, reason codes, prerequisites and UI states
    res_schema = schemas['method-comparison-result-v2']
    method_props = res_schema['properties']['methods']['items']['properties']
    comp_props = method_props['comparison']['properties']
    all_keys = ['attempt.method.' + m for m in method_props['methodId']['enum']]
    all_keys += ['attempt.methods.' + r for r in comp_props['reasonCode']['enum']]
    all_keys += ['attempt.methods.' + a for a in comp_props['applicability']['enum']]
    all_keys += ['attempt.methods.prerequisite.' + p for p in comp_props['prerequisite']['enum']]
    all_keys += ['attempt.methods.' + a for a in comp_props['advantage']['enum']]
    all_keys += ['attempt.methods.' + l for l in comp_props['limitation']['enum']]
    all_keys += ['attempt.methods.' + r for r in comp_props['recommendation']['enum']]
    all_keys += ['attempt.methods.complexity.' + l for l in comp_props['manipulationLevel']['enum']]
    all_keys += ['attempt.methods.step.' + s for s in method_props['stepReasons']['items']['enum']]
    all_keys += ['attempt.methods.' + c for c in comp_props['domainCautions']['items']['enum']]
    all_keys += ['attempt.methods.error.' + e for e in ['network','unavailable','stale','authorization','invalid','request','rateLimit']]
    all_keys += ['attempt.methods.' + l for l in ['learnerInvalid','learnerVerified','learnerNeedsReview']]
    for key in all_keys:
        for lang in ['en','tr']:
            assert key in catalog and catalog[key]['localizations'][lang]['stringUnit']['value'], f'Missing {lang} localization for {key}'

    baseline='fd7e0c3aae1a4b8154e71d628bd1a9bd47688fc9'
    immutable=['packages/schemas/attempt-learning-v1.schema.json','packages/schemas/attempt-learning-result-v1.schema.json',
               'evaluations/golden-datasets/attempts/manual-polynomial-v1.json']
    immutable += [str(p.relative_to(ROOT)) for p in (ROOT/'services/api/src/main/resources/db/migration/platform').glob('V03[012]__*.sql')]
    for name in immutable:
        before=subprocess.check_output(['git','show',baseline+':'+name],cwd=ROOT)
        assert before==(ROOT/name).read_bytes(), 'Historical artifact changed: '+name
    print('Method comparison v2 contracts/localization and historical artifacts checked; runtime acceptance is separate.')
if __name__=='__main__': main()
