from __future__ import annotations

from fastapi import APIRouter
from fastapi.responses import PlainTextResponse

from app.observability.metrics import VERIFIER_METRIC_NAMES

router = APIRouter(tags=["observability"])


@router.get("/metrics", include_in_schema=False)
async def metrics() -> PlainTextResponse:
    lines: list[str] = []
    for name in sorted(VERIFIER_METRIC_NAMES.values()):
        lines.append(f"# TYPE {name} counter")
        lines.append(f"{name} 0")
    return PlainTextResponse("\n".join(lines) + "\n", media_type="text/plain; version=0.0.4")
