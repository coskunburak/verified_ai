#!/usr/bin/env python3
"""Owner-operated contract and source integration checks. Never invokes a provider."""
import json
import subprocess
from pathlib import Path
from jsonschema import Draft202012Validator, FormatChecker
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT202012
from copy import deepcopy

ROOT = Path(__file__).resolve().parents[2]
BASE = "0349160164a45c6956dcb656a72ca7a34aa391e2"


def check_submission_extension():
    schema_root=ROOT/"packages/schemas"
    resources={p.name:json.loads(p.read_text()) for p in schema_root.glob("*.schema.json")}
    registry=Registry().with_resources((name,Resource.from_contents(value,default_specification=DRAFT202012)) for name,value in resources.items())
    def validator(name):
        schema=resources[name+".schema.json"]; Draft202012Validator.check_schema(schema)
        return Draft202012Validator(schema,registry=registry,format_checker=FormatChecker())
    for name in ("attempt-submission-v3","student-attempt-v2"):
        value=json.loads((schema_root/"fixtures"/(name+".json")).read_text())
        check=validator(name); check.validate(value)
        bad=deepcopy(value); bad['unexpected']=True; assert list(check.iter_errors(bad))
        for field in value:
            if field in check.schema.get('required',[]):
                bad=deepcopy(value); del bad[field]; assert list(check.iter_errors(bad)),field
    submit=json.loads((schema_root/"fixtures/attempt-submission-v3.json").read_text())
    check=validator("attempt-submission-v3")
    check.validate(json.loads((schema_root/"fixtures/attempt-submission-v3-work.json").read_text()))
    for change in ({'finalAnswer':None},{'finalAnswer':4},{'finalAnswer':''},{'steps':['x']*12},
                   {'realDomainConfirmed':True},{'schemaVersion':'attempt-submission-v4'},{'trainingEligible':True}):
        assert list(check.iter_errors({**submit,**change})),change
    legacy={'source':submit['source'],'steps':[' x=4 ']}
    validator('attempt-submission-v1').validate(legacy); validator('attempt-submission-v2').validate(legacy)
    assert list(validator('attempt-submission-v2').iter_errors(submit))
    saved=json.loads((schema_root/"fixtures/student-attempt-v2.json").read_text())
    for change in ({'status':'ANALYZED'},{'trainingEligible':True},{'analysis':{}},{'finalAnswer':None}):
        assert list(validator('student-attempt-v2').iter_errors({**saved,**change}))
    display=deepcopy(saved); display['schemaVersion']='student-attempt-v1'
    display['steps']=[{'index':1,'content':display.pop('finalAnswer'),'normalizedStatement':None}]
    validator('student-attempt-response').validate(display)
    # Frozen contracts remain byte-identical. Old consumers opt into the new representation explicitly.
    for name in ('attempt-submission-v1','attempt-submission-v2','student-attempt-v1','student-attempt-response'):
        path=f'packages/schemas/{name}.schema.json'
        assert (ROOT/path).read_bytes()==subprocess.check_output(['git','show','0349160164a45c6956dcb656a72ca7a34aa391e2:'+path],cwd=ROOT)
    import yaml
    from openapi_spec_validator import validate
    api_path=ROOT/'packages/contracts/openapi/public-api.yaml'
    document=yaml.safe_load(api_path.read_text())
    # The legacy validate_spec shortcut in 0.7 drops base_uri, which breaks relative schema references.
    validate(document, base_uri=api_path.as_uri())
    endpoint=document['paths']['/api/v1/problem-sessions/{sessionId}/attempts']
    assert {'200','400','401','403','404','409','413','422','429','503'}<=set(endpoint['post']['responses'])
    assert endpoint['get']['parameters'][0]['name']=='representationVersion'


def main():
    check_submission_extension()
    schemas = {}
    for name in ("attempt-verification-v1", "attempt-analysis-v1", "attempt-submission-v1", "student-attempt-v1", "attempt-context-v1"):
        schema = json.loads((ROOT / f"packages/schemas/{name}.schema.json").read_text())
        Draft202012Validator.check_schema(schema)
        schemas[name] = Draft202012Validator(schema)
    for name in ("attempt-verification-v2","attempt-analysis-v2","attempt-submission-v2","student-attempt-response",
                 "attempt-context-v2-response","canonical-problem-v3","step-diff-v1","counterfactual-twin-v1",
                 "attempt-learning-v1","attempt-learning-result-v1","attempt-extraction-v1","mistake-passport-v1"):
        Draft202012Validator.check_schema(json.loads((ROOT/f"packages/schemas/{name}.schema.json").read_text()))
    current_taxonomy=json.loads((ROOT/"packages/schemas/attempt-taxonomy-v2.json").read_text())
    assert len(current_taxonomy['specificCodes'])==current_taxonomy['listedSpecificCodeCount']==17
    assert current_taxonomy['generic']=='INVALID_TRANSFORMATION'
    for version in (30,31,32,33): assert len(list((ROOT/"services/api/src/main/resources/db/migration/platform").glob(f"V0{version}__*.sql")))==1
    dataset = json.loads((ROOT / "evaluations/golden-datasets/attempts/manual-polynomial-v1.json").read_text())
    assert dataset["synthetic"] and not dataset["handwriting"]
    assert len({c["id"] for c in dataset["cases"]}) == len(dataset["cases"])
    for case in dataset["cases"]: schemas["attempt-verification-v1"].validate(case["request"])
    taxonomy = json.loads((ROOT / "packages/schemas/attempt-taxonomy-v1.json").read_text())
    assert len(taxonomy["specificCodes"]) == taxonomy["listedSpecificCodeCount"] == 17
    assert [c["code"] for c in taxonomy["supported"]] == ["ALG_TRANSFORMATION_INVALID"]
    catalog = json.loads((ROOT / "apps/ios/VerifiedAI/Resources/Localizable.xcstrings").read_text())["strings"]
    for key, value in catalog.items():
        if key.startswith("attempt."):
            for language in ("en", "tr"): assert value["localizations"][language]["stringUnit"]["value"]
    project = (ROOT / "apps/ios/VerifiedAI.xcodeproj/project.pbxproj").read_text()
    for path in (ROOT / "apps/ios/VerifiedAI/Features/AttemptAnalysis").rglob("*.swift"):
        assert f"path = {path.relative_to(ROOT/'apps/ios')};" in project
        assert f"{path.name} in Sources" in project
    assert "enabled: ${ATTEMPT_ANALYSIS_ENABLED:false}" in (ROOT / "services/api/src/main/resources/application.yml").read_text()
    # Historical migration bytes must not change, even if the owner tests an uncommitted tree.
    migration_root = "services/api/src/main/resources/db/migration/platform"
    names = subprocess.check_output(["git", "ls-tree", "-r", "--name-only", BASE, "--", migration_root], cwd=ROOT, text=True).splitlines()
    assert names
    for name in names:
        assert (ROOT / name).read_bytes() == subprocess.check_output(["git", "show", f"{BASE}:{name}"], cwd=ROOT), name
    assert not list((ROOT/migration_root).glob("V025*")), "Reserved V025 must not be inserted behind V027-V029; V030 is the forward successor"
    assert (ROOT / "services/api/src/main/resources/db/planned/V025__create_student_attempt_and_step_verification_lifecycle.sql").is_file()
    print("Attempt schemas, taxonomy, synthetic fixtures, iOS registration, localization and migration immutability checked.")


if __name__ == "__main__": main()
