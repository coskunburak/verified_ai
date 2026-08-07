from app.api.schemas.verification_request import EquivalenceVerificationRequest
from app.verifiers.equivalence import verify_equivalence


def test_equivalent_expressions_are_verified() -> None:
    result = verify_equivalence(EquivalenceVerificationRequest(left="x^2 - 1", right="(x - 1)*(x + 1)", variables=["x"]))

    assert result.status == "VERIFIED"
    assert result.equivalent is True


def test_non_equivalent_expressions_are_contradicted() -> None:
    result = verify_equivalence(EquivalenceVerificationRequest(left="x + 1", right="x + 2", variables=["x"]))

    assert result.status == "CONTRADICTED"
    assert result.equivalent is False

