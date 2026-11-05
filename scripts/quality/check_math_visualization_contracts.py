#!/usr/bin/env python3
"""Owner-run strict fixtures, localization, registration and contract checks."""
import json
import re
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker
ROOT=Path(__file__).resolve().parents[2]

def main():
    for kind in ('request','source','result','public','context'):
        name='math-visualization-'+kind+'-v1'
        schema=json.loads((ROOT/'packages/schemas'/f'{name}.schema.json').read_text())
        Draft202012Validator.check_schema(schema)
        value=json.loads((ROOT/'packages/schemas/fixtures'/f'{name}.json').read_text())
        validator=Draft202012Validator(schema,format_checker=FormatChecker()); validator.validate(value)
        value['unexpected']=True
        assert list(validator.iter_errors(value)),name+' permits unknown fields'
    catalog=json.loads((ROOT/'apps/ios/VerifiedAI/Resources/Localizable.xcstrings').read_text())['strings']
    keys={k for k in catalog if k.startswith('visualization.')}
    assert len(keys)>=45
    for key in keys:
        for language in ('en','tr'):
            unit=catalog[key]['localizations'][language]['stringUnit']
            assert unit['state']=='translated' and unit['value'].strip(),key
    pbx=(ROOT/'apps/ios/VerifiedAI.xcodeproj/project.pbxproj').read_text()
    for name in ('MathVisualization.swift','MathVisualizationViewModel.swift','MathVisualizationView.swift','MathVisualizationTests.swift','MathVisualizationUITests.swift'):
        assert name+' in Sources' in pbx,name
    assert 'math-visualization-public-v1.json in Resources' in pbx
    for file in ('public-api.yaml','internal-math-verifier.yaml'):
        assert 'math-visualization-' in (ROOT/'packages/contracts/openapi'/file).read_text()
    # Reject accidental dependency/cost/authority regressions within this new feature.
    roots=[ROOT/'services/api/src/main/java/com/verifiedai/visualization',ROOT/'apps/ios/VerifiedAI/Features/ProblemSolving/Presentation/Visualization']
    for root in roots:
        for path in root.rglob('*'):
            if path.is_file():
                text=path.read_text()
                assert not re.search(r'WKWebView|Keychain|INSERT INTO|\.save\(|openai\.com',text,re.I),str(path)
    print('Visualization contracts, EN/TR coverage and source registration checked; runtime evidence remains separate.')
if __name__=='__main__': main()
