from __future__ import annotations

from fastapi import Header

from app.config import Settings, get_settings
from app.security.internal_auth import verify_internal_token


def settings() -> Settings:
    return get_settings()


async def require_internal_auth(
    x_internal_token: str | None = Header(default=None, alias="X-Internal-Token"),
) -> None:
    verify_internal_token(provided_token=x_internal_token, expected_token=get_settings().internal_token)

