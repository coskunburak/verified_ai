"""Hand-authored mathematical oracles; these tests are owner executed only."""
import copy
import json
from pathlib import Path

import pytest
from pydantic import ValidationError

from app.api.schemas.calculus import CalculusRequest, CalculusResult
from app.verifiers.calculus import execute

CASES = json.loads((Path(__file__).resolve().parents[4] / "packages/test-fixtures/verification/calculus-v1/cases.json").read_text())


@pytest.mark.parametrize("case", CASES, ids=lambda c: c["id"])
def test_exact_calculus_and_conservative_boundaries(case):
    request = CalculusRequest.model_validate(case["request"])
    result = execute(request).validate_for(request)
    assert (result.outcome, result.reasonCode) == (case["expectedOutcome"], case["expectedReason"])
    assert (None if result.proof is None else result.proof.descriptor) == case["expectedDescriptor"]
    assert result.restrictionIds == [r.id for r in request.restrictions]
    assert result.verifierVersion == "bounded-calculus-v1"
    if result.diagnostic is not None:
        assert result.outcome == "INCONCLUSIVE"
        assert result.proof is None
        assert result.diagnostic.attempted == (16 if request.approach.direction == "TWO_SIDED" else 8)
        assert execute(request) == result


def payload(id="derivative-polynomial"):
    return copy.deepcopy(next(c["request"] for c in CASES if c["id"] == id))


@pytest.mark.parametrize("mutate", [
    lambda p: p.update(schemaVersion="verification-execution-v1"),
    lambda p: p["scope"].update(sourceVersion="canonical-problem-v1"),
    lambda p: p["scope"].update(candidateVersion="solution-candidate-v1"),
    lambda p: p["scope"].update(sourceRevision=True),
    lambda p: p.update(method="NUMERIC_SAMPLING"),
    lambda p: p.update(approach={"target": {"numerator": "0", "denominator": "1"}, "direction": "RIGHT"}),
    lambda p: p.update(integralScope="COMPLETE_FAMILY"),
    lambda p: p["interval"].update(lower={"numerator": "0", "denominator": "1"}, upper={"numerator": "0", "denominator": "1"}),
    lambda p: p["interval"].update(lower={"numerator": "2", "denominator": "2"}),
    lambda p: p["candidate"].update(kind="PYTHON"),
    lambda p: p["candidate"].update(extra="student input"),
])
def test_invalid_typed_requests_are_rejected(mutate):
    p = payload()
    mutate(p)
    with pytest.raises(ValidationError):
        CalculusRequest.model_validate(p)


@pytest.mark.parametrize("field", ["target", "direction"])
def test_limit_metadata_is_never_guessed(field):
    p = payload("limit-polynomial")
    del p["approach"][field]
    with pytest.raises(ValidationError):
        CalculusRequest.model_validate(p)


def test_undeclared_variable_is_not_silently_differentiated():
    p = payload()
    p["variable"]["symbol"] = "t"
    result = execute(CalculusRequest.model_validate(p))
    assert result.outcome == "UNSUPPORTED"
    assert result.proof is None


def test_duplicate_restriction_ids_are_rejected():
    p = payload("integral-log-positive")
    p["restrictions"] *= 2
    with pytest.raises(ValidationError):
        CalculusRequest.model_validate(p)


@pytest.mark.parametrize("field,value", [
    ("outcome", "DISPROVEN"), ("reasonCode", "TIMEOUT"), ("method", "FINITE_LIMIT"),
    ("verifierVersion", "canonical-executor-v1"), ("methodVersion", "v2"),
])
def test_proof_outcome_method_and_version_contradictions_are_rejected(field, value):
    p = execute(CalculusRequest.model_validate(payload())).model_dump(mode="json")
    p[field] = value
    with pytest.raises(ValidationError):
        CalculusResult.model_validate(p)


def test_limit_values_cannot_contradict_proof():
    p = execute(CalculusRequest.model_validate(payload("limit-removable"))).model_dump(mode="json")
    p["proof"]["leftValue"] = {"numerator": "3", "denominator": "1"}
    with pytest.raises(ValidationError):
        CalculusResult.model_validate(p)


@pytest.mark.parametrize("side,expected", [("LEFT", 8), ("RIGHT", 8), ("TWO_SIDED", 16)])
def test_numerical_sides_are_bounded_and_never_proof(side, expected):
    p = payload("limit-numerical-agreement-not-proof")
    p["approach"]["direction"] = side
    r = execute(CalculusRequest.model_validate(p))
    assert r.diagnostic.attempted == expected
    assert r.proof is None
    assert r.outcome == "INCONCLUSIVE"


def test_numerical_overflow_never_enters_evidence():
    r = execute(CalculusRequest.model_validate(payload("limit-numerical-overflow")))
    assert r.diagnostic.evaluated == 0


def test_unknown_direction_and_infinity_notation_fail_schema():
    p = payload("limit-polynomial")
    for target in ("infinity", {"numerator": "inf", "denominator": "1"}):
        p["approach"]["target"] = target
        with pytest.raises(ValidationError):
            CalculusRequest.model_validate(p)


def test_ast_and_function_depth_budgets_fail_closed():
    p = payload()
    node = {"kind": "VARIABLE", "symbol": "x"}
    for _ in range(9):
        node = {"kind": "FUNCTION", "function": "SIN", "args": [node]}
    p["original"] = node
    assert execute(CalculusRequest.model_validate(p)).reasonCode == "COMPLEXITY_LIMIT"


def test_all_constant_offsets_leave_representative_relation_unchanged():
    for constant in (-17, 0, 23):
        p = payload("integral-additive-constant")
        p["candidate"]["right"]["value"] = str(constant)
        r = execute(CalculusRequest.model_validate(p))
        assert r.outcome == "PROVEN"
        assert r.proof.scope == "ANTIDERIVATIVE_REPRESENTATIVE"


def test_response_binding_includes_revisions_and_restriction_order():
    request = CalculusRequest.model_validate(payload("integral-log-positive"))
    result = execute(request).model_dump(mode="json")
    result["scope"]["candidateRevision"] = 2
    with pytest.raises(ValueError, match="identity mismatch"):
        CalculusResult.model_validate(result).validate_for(request)
    result["scope"]["candidateRevision"] = 1
    result["restrictionIds"] = []
    with pytest.raises(ValueError, match="identity mismatch"):
        CalculusResult.model_validate(result).validate_for(request)


def test_high_order_and_multivariable_requests_are_not_implied():
    p = payload()
    p["derivativeOrder"] = 2
    with pytest.raises(ValidationError):
        CalculusRequest.model_validate(p)
    p.pop("derivativeOrder")
    p["variables"] = [{"symbol": "x", "domain": "REAL"}, {"symbol": "y", "domain": "REAL"}]
    with pytest.raises(ValidationError):
        CalculusRequest.model_validate(p)


@pytest.mark.parametrize("field", ["schemaVersion", "methodVersion", "verifierVersion", "proof", "diagnostic"])
def test_response_versions_and_explicit_null_evidence_are_required(field):
    p = execute(CalculusRequest.model_validate(payload())).model_dump(mode="json")
    del p[field]
    with pytest.raises(ValidationError):
        CalculusResult.model_validate(p)
