import copy
import json
import math
from pathlib import Path
from uuid import UUID

import pytest
from pydantic import ValidationError

from app.api.schemas.numeric import NumericRequest, NumericResult
from app.verifiers.numeric_sampling import Unstable, checked, compare, execute, sample_points

CASES = json.loads((Path(__file__).resolve().parents[4] / "packages/test-fixtures/verification/numeric-v1/cases.json").read_text())


@pytest.mark.parametrize("case", CASES, ids=lambda c: c["id"])
def test_hand_authored_diagnostic_expectations(case):
    request = NumericRequest.model_validate(case["request"])
    result = execute(request)
    assert (result.status, result.reasonCode) == (case["expectedStatus"], case["expectedReason"])
    assert result == execute(request)
    assert result.evaluated == result.agreeing + result.disagreeing
    assert "proof" not in result.model_dump()
    assert result.status not in {"PROVEN", "DISPROVEN"}


def test_seed_uses_math_not_incidental_identifiers():
    request = NumericRequest.model_validate(CASES[0]["request"])
    other = request.model_copy(update={"executionId": UUID(int=100), "scopeId": UUID(int=101), "correlationId": "other"})
    assert list(sample_points(request)) == list(sample_points(other))
    assert len(set(sample_points(request))) == 16
    changed = NumericRequest.model_validate(CASES[1]["request"])
    assert list(sample_points(request)) != list(sample_points(changed))


@pytest.mark.parametrize("value", [math.nan, math.inf, -math.inf, 1e101, 1e-260])
def test_nonfinite_and_extreme_values_are_never_compared(value):
    with pytest.raises(Unstable):
        checked(value)


def test_tolerance_boundary_near_equality_and_cancellation():
    assert compare(checked(0.1 + 0.2), checked(0.3))
    assert compare(checked(0.0), checked(0.9e-10))
    assert not compare(checked(0.0), checked(1.1e-10))
    with pytest.raises(Unstable):
        compare((0.0, 1e-20), (1e-10, 1e-20))
    with pytest.raises(Unstable):
        compare((1.0, 1.0), (1.0, 0.0))


@pytest.mark.parametrize("mutation", [
    lambda p: p.update(schemaVersion="verification-execution-v1"),
    lambda p: p.update(method="SYMBOLIC_EQUIVALENCE"),
    lambda p: p.update(policyVersion="loose-tolerance"),
    lambda p: p.update(absoluteTolerance=1.0),
    lambda p: p["original"]["expression"].update(kind="PYTHON"),
    lambda p: p["variables"].append(p["variables"][0]),
])
def test_malicious_or_unversioned_requests_are_rejected(mutation):
    payload = copy.deepcopy(CASES[0]["request"])
    mutation(payload)
    with pytest.raises(ValidationError):
        NumericRequest.model_validate(payload)


def test_ast_depth_nodes_functions_exponents_and_total_work_are_bounded():
    base = copy.deepcopy(CASES[0]["request"])
    node = {"kind": "NUMBER", "numericType": "INTEGER", "value": "1"}
    for _ in range(33):
        node = {"kind": "UNARY", "operator": "NEGATE", "operand": node}
    base["original"]["expression"] = node
    assert execute(NumericRequest.model_validate(base)).reasonCode == "COMPLEXITY_LIMIT"
    node = {"kind": "VARIABLE", "symbol": "x"}
    for _ in range(9):
        node = {"kind": "FUNCTION", "function": "SIN", "args": [node]}
    base["original"]["expression"] = node
    assert execute(NumericRequest.model_validate(base)).reasonCode == "COMPLEXITY_LIMIT"
    base["original"]["expression"] = {"kind": "BINARY", "operator": "POWER", "left": {"kind": "VARIABLE", "symbol": "x"}, "right": {"kind": "NUMBER", "numericType": "INTEGER", "value": "13"}}
    assert execute(NumericRequest.model_validate(base)).status == "UNSUPPORTED"
    node = {"kind": "NUMBER", "numericType": "INTEGER", "value": "1"}
    for _ in range(6):
        node = {"kind": "BINARY", "operator": "ADD", "left": node, "right": node}
    base["original"]["expression"] = node
    assert execute(NumericRequest.model_validate(base)).reasonCode == "COMPLEXITY_LIMIT"
    base = copy.deepcopy(CASES[0]["request"])
    node = {"kind": "NUMBER", "numericType": "INTEGER", "value": "1"}
    for _ in range(5):
        node = {"kind": "BINARY", "operator": "ADD", "left": node, "right": node}
    base["restrictions"] = [{"id": f"r{i}", "relation": "EQUALS", "left": node, "right": {"kind": "NUMBER", "numericType": "INTEGER", "value": "32"}} for i in range(20)]
    assert execute(NumericRequest.model_validate(base)).reasonCode == "COMPLEXITY_LIMIT"


@pytest.mark.parametrize("field,value", [("status", "PROVEN"), ("proof", {}), ("attempted", 17), ("agreeing", True), ("disagreeing", 1), ("reasonCode", "EXACT_EQUAL")])
def test_results_cannot_claim_proof_or_forge_counts(field, value):
    result = execute(NumericRequest.model_validate(CASES[0]["request"])).model_dump(mode="json")
    result[field] = value
    with pytest.raises(ValidationError):
        NumericResult.model_validate(result)


@pytest.mark.parametrize("case_id,exact_outcome", [
    ("identity", "PROVEN"), ("nonidentity", "DISPROVEN"),
    ("unrestricted-hole", "INCONCLUSIVE"), ("restricted-hole", "PROVEN"),
    ("removable-singularity", "PROVEN"), ("sqrt-wrong-domain", "UNSUPPORTED"),
    ("close-but-unequal", "DISPROVEN"), ("unstable-identity", "PROVEN"),
    ("unsupported-exact-contradiction", "UNSUPPORTED"), ("contradictory-domain", "BLOCKED"),
])
def test_existing_exact_executor_keeps_independent_mathematical_authority(case_id, exact_outcome):
    from app.api.schemas.execution import ExecutionRequest
    from app.verifiers.canonical_execution import execute as exact_execute

    case = next(c for c in CASES if c["id"] == case_id)
    payload = copy.deepcopy(case["request"])
    del payload["scopeId"]
    del payload["policyVersion"]
    payload.update(schemaVersion="verification-execution-v1", method="SYMBOLIC_EQUIVALENCE")
    exact = exact_execute(ExecutionRequest.model_validate(payload))
    assert exact.outcome == exact_outcome
    diagnostic = execute(NumericRequest.model_validate(case["request"]))
    assert diagnostic.status == case["expectedStatus"]
    assert "proof" not in diagnostic.model_dump()


def test_relation_and_unsupported_function_restrictions_are_not_sampled():
    payload = copy.deepcopy(CASES[0]["request"])
    x = {"kind": "VARIABLE", "symbol": "x"}
    zero = {"kind": "NUMBER", "numericType": "INTEGER", "value": "0"}
    payload["original"] = {"kind": "RELATION", "relation": "EQUALS", "left": x, "right": zero}
    assert execute(NumericRequest.model_validate(payload)).status == "UNSUPPORTED"
    payload = copy.deepcopy(CASES[0]["request"])
    payload["restrictions"] = [{"id": "sin-restriction", "relation": "NOT_EQUALS", "left": {"kind": "FUNCTION", "function": "SIN", "args": [x]}, "right": zero}]
    result = execute(NumericRequest.model_validate(payload))
    assert result.status == "UNSUPPORTED"
    assert result.attempted == 0
