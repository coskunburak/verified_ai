"""Public decision schema only; arbitration remains exclusively in Java."""
import json
from pathlib import Path

import pytest
from jsonschema import Draft202012Validator, FormatChecker, ValidationError

ROOT = Path(__file__).resolve().parents[4]
SCHEMA = json.loads((ROOT / "packages/schemas/verification-decision.schema.json").read_text())
VALIDATOR = Draft202012Validator(SCHEMA, format_checker=FormatChecker())
FIXTURES = ROOT / "packages/test-fixtures/verification/decision-v1"


@pytest.mark.parametrize("name", ["verified", "partial", "unverified", "contradiction",
                                  "calculus-derivative", "calculus-integral", "calculus-limit"])
def test_public_decision_fixtures(name):
    VALIDATOR.validate(json.loads((FIXTURES / f"{name}.json").read_text()))


@pytest.mark.parametrize("field,value", [
    ("status", "PROVEN"), ("status", 1), ("policyVersion", "unknown"),
    ("privateReasoning", "forbidden"), ("recovery", "AUTOMATIC_MODEL_CALL"),
    ("unchecked", ["DOMAIN_CONSTRAINT_CHECK"]), ("uncertainty", []),
    ("contradictions", ["EQUATION_SUBSTITUTION"]), ("reasons", ["UNKNOWN"]),
    ("checked", ["NUMERIC_SAMPLING"]), ("checked", ["DOMAIN_CONSTRAINT_CHECK"]),
])
def test_unknown_or_inconsistent_verdict_rejects(field, value):
    document = json.loads((FIXTURES / "verified.json").read_text())
    document[field] = value
    with pytest.raises(ValidationError):
        VALIDATOR.validate(document)


@pytest.mark.parametrize("field", SCHEMA["required"])
def test_every_required_field_is_required(field):
    document = json.loads((FIXTURES / "verified.json").read_text())
    del document[field]
    with pytest.raises(ValidationError):
        VALIDATOR.validate(document)


@pytest.mark.parametrize("name", ["calculus-derivative", "calculus-integral", "calculus-limit"])
@pytest.mark.parametrize("field,value", [
    ("scope", "FINAL_ANSWER"), ("status", "PARTIALLY_VERIFIED"),
    ("checked", ["NUMERIC_SAMPLING"]), ("checked", ["DOMAIN_CONSTRAINT_CHECK"]),
    ("unchecked", ["SOLUTION_SET_COMPLETENESS"]),
])
def test_calculus_scope_cannot_expand_to_generic_final_answer(name, field, value):
    document = json.loads((FIXTURES / f"{name}.json").read_text())
    document[field] = value
    with pytest.raises(ValidationError):
        VALIDATOR.validate(document)


@pytest.mark.parametrize("name,wrong_scope", [
    ("calculus-derivative", "ANTIDERIVATIVE_REPRESENTATIVE"),
    ("calculus-integral", "PUNCTURED_NEIGHBORHOOD"),
    ("calculus-limit", "OPEN_INTERVAL"),
])
def test_calculus_certificate_kind_must_match_public_scope(name, wrong_scope):
    document = json.loads((FIXTURES / f"{name}.json").read_text())
    document["scope"] = wrong_scope
    with pytest.raises(ValidationError):
        VALIDATOR.validate(document)
