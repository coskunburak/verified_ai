#!/usr/bin/env python3
"""Owner-only synthetic evaluation. No handwriting or production accuracy claim."""
import hashlib
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "services/math-verifier"))
from app.api.schemas.attempt import AttemptRequest
from app.counterexample_engine import evaluate
from app.policies.process_executor import ProcessExecutor
from fractions import Fraction
from jsonschema import Draft202012Validator


def main():
    path = ROOT / "evaluations/golden-datasets/attempts/manual-polynomial-v1.json"
    dataset = json.loads(path.read_text())
    schema = Draft202012Validator(json.loads((ROOT / "packages/schemas/attempt-analysis-v1.schema.json").read_text()))
    rows, errors, valid, wrong, recall, false_accusations, witnesses, sound, uncertain = [], [], 0, 0, 0, 0, 0, 0, 0
    classified = 0
    domain_cases = domain_safe = 0
    executor = ProcessExecutor()
    for case in dataset["cases"]:
        request = AttemptRequest.model_validate(case["request"])
        result = executor.execute(request)
        schema.validate(result.model_dump(mode="json"))
        expected = case["expected"]
        actual = {"outcome": result.outcome, "firstWrongStepIndex": result.firstWrongStepIndex}
        passed = actual == expected
        if not passed: errors.append(case["id"])
        if expected["outcome"] == "INCOMPLETE_VALID_SO_FAR":
            valid += 1
            false_accusations += result.firstWrongStepIndex is not None
        if expected["firstWrongStepIndex"] is not None:
            wrong += 1
            recall += result.firstWrongStepIndex == expected["firstWrongStepIndex"]
            classified += result.steps[expected["firstWrongStepIndex"]-1].mistakeCode == "ALG_TRANSFORMATION_INVALID"
        if case["id"] in {"denominator-hole", "introduced-denominator", "radical-negative", "log-domain", "negative-power-unavailable", "zero-power-domain", "irrational-roots-unavailable"}:
            domain_cases += 1
            domain_safe += result.outcome == "NEEDS_REVIEW" and result.firstWrongStepIndex is None
        uncertain += result.outcome == "NEEDS_REVIEW"
        for i, step in enumerate(result.steps):
            if step.counterexample:
                witnesses += 1
                values = {k: Fraction(int(v.numerator), int(v.denominator)) for k, v in step.counterexample.variableValues.items()}
                previous = request.original if i == 0 else request.steps[i-1].statement
                checked = evaluate(previous, values) != evaluate(request.steps[i].statement, values)
                sound += checked
                if not checked: errors.append(case["id"] + ":unsound-witness")
        rows.append({"id": case["id"], "expected": expected, "actual": actual, "passed": passed,
                     "result": result.model_dump(mode="json")})
    report = {"version": dataset["version"], "algorithm": dataset["algorithm"], "datasetSize": len(rows),
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "status": "FAIL" if errors else "PASS",
        "syntheticOnly": True, "handwritingAccuracy": "NOT_EVALUATED", "firstWrongStepRecall": recall/wrong if wrong else None,
        "falseAccusationRate": false_accusations/valid if valid else None, "uncertainCount": uncertain,
        "counterexamples": {"published": witnesses, "sound": sound},
        "supportedTaxonomyAccuracy": classified/wrong if wrong else None,
        "domainProtectionCases": {"total": domain_cases, "safe": domain_safe},
        "taxonomyScope": "Generic non-equivalence only; specific misconception classification NOT_IMPLEMENTED",
        "domainProtection": "Unsupported domains must remain NEEDS_REVIEW; see per-case outcomes",
        "failures": errors, "cases": rows}
    Path(sys.argv[1]).write_text(json.dumps(report, indent=2) + "\n")
    return 1 if errors else 0


if __name__ == "__main__": sys.exit(main())
