from __future__ import annotations

from enum import StrEnum


class VerificationStatus(StrEnum):
    VERIFIED = "VERIFIED"
    CONTRADICTED = "CONTRADICTED"
    UNSUPPORTED = "UNSUPPORTED"

