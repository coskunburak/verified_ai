from __future__ import annotations


class VerificationError(Exception):
    def __init__(
        self,
        code: str,
        public_message: str,
        status_code: int = 422,
        recoverable: bool = False,
    ) -> None:
        super().__init__(public_message)
        self.code = code
        self.public_message = public_message
        self.status_code = status_code
        self.recoverable = recoverable


class UnsafeExpressionError(VerificationError):
    def __init__(self, public_message: str = "Expression is outside the supported verifier subset") -> None:
        super().__init__(
            code="UNSAFE_EXPRESSION",
            public_message=public_message,
            status_code=422,
            recoverable=True,
        )


class VerifierUnavailableError(VerificationError):
    def __init__(self) -> None:
        super().__init__(
            code="VERIFIER_UNAVAILABLE",
            public_message="Verifier could not complete within the allowed budget",
            status_code=503,
            recoverable=True,
        )

