from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    environment: str
    internal_token: str
    expression_max_length: int = 256
    operation_timeout_ms: int = 750


def get_settings() -> Settings:
    return Settings(
        environment=os.getenv("APP_ENV", "local"),
        internal_token=os.getenv("MATH_VERIFIER_INTERNAL_TOKEN", "local_math_verifier_token_change_me"),
        expression_max_length=int(os.getenv("MATH_VERIFIER_EXPRESSION_MAX_LENGTH", "256")),
        operation_timeout_ms=int(os.getenv("MATH_VERIFIER_TIMEOUT_MS", "750")),
    )

