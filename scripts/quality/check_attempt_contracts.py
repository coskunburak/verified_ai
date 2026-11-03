#!/usr/bin/env python3
"""Owner-operated contract and source integration checks. Never invokes a provider."""
import json
import subprocess
from pathlib import Path
from jsonschema import Draft202012Validator

ROOT = Path(__file__).resolve().parents[2]
BASE = "4acc57ea8ee9b22536a8caa3f4f98ccc945a7855"


def main():
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
    for version in (30,31,32): assert len(list((ROOT/"services/api/src/main/resources/db/migration/platform").glob(f"V0{version}__*.sql")))==1
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
