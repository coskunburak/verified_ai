from __future__ import annotations

import os
import sys
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    environment: str
    internal_token: str
    execution_enabled: bool = False
    expression_max_length: int = 256
    operation_timeout_ms: int = 750


def get_settings() -> Settings:
    environment = os.getenv("APP_ENV", "local")
    token = os.getenv("MATH_VERIFIER_INTERNAL_TOKEN", "local_math_verifier_token_change_me")
    if environment not in {"local", "test"} and (len(token) < 32 or token == "local_math_verifier_token_change_me"):
        raise ValueError("A production internal verifier secret is required")
    enabled = os.getenv("MATH_VERIFIER_EXECUTION_ENABLED", "false").lower() == "true"
    if enabled and environment not in {"local", "test"} and not sys.platform.startswith("linux"):
        raise ValueError("Production execution requires Linux process resource limits")
    return Settings(
        environment=environment,
        internal_token=token,
        execution_enabled=enabled,
        expression_max_length=int(os.getenv("MATH_VERIFIER_EXPRESSION_MAX_LENGTH", "256")),
        operation_timeout_ms=int(os.getenv("MATH_VERIFIER_TIMEOUT_MS", "750")),
    )

