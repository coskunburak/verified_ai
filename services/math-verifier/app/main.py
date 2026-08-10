from __future__ import annotations

import time
import uuid
from collections.abc import Awaitable, Callable

from fastapi import FastAPI, Request, Response
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.api.routes.health import router as health_router
from app.api.routes.metrics import router as metrics_router
from app.api.routes.verify import router as verify_router
from app.domain.errors import VerificationError
from app.observability.logging import configure_logging, logger

configure_logging()

app = FastAPI(
    title="Verified AI Internal Math Verifier",
    version="0.1.0",
    docs_url=None,
    redoc_url=None,
    openapi_url="/internal/openapi.json",
)


@app.middleware("http")
async def correlation_middleware(
    request: Request,
    call_next: Callable[[Request], Awaitable[Response]],
) -> Response:
    correlation_id = request.headers.get("X-Request-Id") or str(uuid.uuid4())
    request.state.correlation_id = correlation_id
    start = time.perf_counter()
    response = await call_next(request)
    duration_ms = round((time.perf_counter() - start) * 1000, 2)
    response.headers["X-Request-Id"] = correlation_id
    logger.info(
        "request completed",
        extra={
            "correlation_id": correlation_id,
            "method": request.method,
            "path": request.url.path,
            "status_code": response.status_code,
            "duration_ms": duration_ms,
        },
    )
    return response


@app.exception_handler(VerificationError)
async def verification_error_handler(request: Request, exc: VerificationError) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content={
            "code": exc.code,
            "message": exc.public_message,
            "correlationId": getattr(request.state, "correlation_id", "unavailable"),
            "recoverable": exc.recoverable,
        },
    )


@app.exception_handler(RequestValidationError)
async def request_validation_error_handler(request: Request, exc: RequestValidationError) -> JSONResponse:
    return JSONResponse(
        status_code=422,
        content={
            "code": "VERIFIER_INPUT_SCHEMA_INVALID",
            "message": "Request payload is outside the verifier schema",
            "correlationId": getattr(request.state, "correlation_id", "unavailable"),
            "recoverable": True,
        },
    )


app.include_router(health_router)
app.include_router(metrics_router)
app.include_router(verify_router)
