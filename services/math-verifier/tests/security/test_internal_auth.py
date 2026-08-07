import pytest

from app.domain.errors import VerificationError
from app.security.internal_auth import verify_internal_token


def test_internal_token_uses_exact_match() -> None:
    verify_internal_token("expected", "expected")


def test_internal_token_rejects_missing_value() -> None:
    with pytest.raises(VerificationError):
        verify_internal_token(None, "expected")


def test_internal_token_rejects_wrong_value() -> None:
    with pytest.raises(VerificationError):
        verify_internal_token("wrong", "expected")

