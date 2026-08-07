from __future__ import annotations

import hmac

from app.domain.errors import VerificationError


def verify_internal_token(provided_token: str | None, expected_token: str) -> None:
    if not provided_token or not hmac.compare_digest(provided_token, expected_token):
        raise VerificationError(
            code="INTERNAL_AUTH_REQUIRED",
            public_message="Internal authentication required",
            status_code=401,
            recoverable=False,
        )

