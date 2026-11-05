from __future__ import annotations

from collections import Counter
from threading import Lock
from typing import get_args

from app.api.schemas.calculus import METHODS as CALCULUS_METHODS
from app.api.schemas.calculus import Reason as CalculusReason
from app.api.schemas.execution import METHODS, Outcome, Reason
from app.api.schemas.numeric import Reason as NumericReason
from app.api.schemas.numeric import Status as NumericStatus

VERIFIER_METRIC_NAMES = {
    "requests": "math_verifier_requests_total",
    "latency": "math_verifier_latency_ms",
    "errors": "math_verifier_errors_total",
}


# Only validated finite method/outcome/reason enums may enter these labels.
_execution_counts = Counter()
_execution_ms = Counter()
_execution_lock = Lock()


def record_execution(method, outcome, reason, elapsed_ms):
    reasons = get_args(CalculusReason) if method in CALCULUS_METHODS else get_args(Reason)
    if method not in (*METHODS, *CALCULUS_METHODS) or outcome not in get_args(Outcome) or reason not in reasons:
        raise ValueError("Unbounded verifier metric label")
    with _execution_lock:
        _execution_counts[(method, outcome, reason)] += 1
        _execution_ms[method] += max(0, elapsed_ms)


def execution_metrics():
    with _execution_lock:
        lines = ["# TYPE math_verifier_execution_total counter", "# TYPE math_verifier_execution_ms_total counter"]
        for (method, outcome, reason), count in sorted(_execution_counts.items()):
            lines.append(f'math_verifier_execution_total{{method="{method}",outcome="{outcome}",reason="{reason}"}} {count}')
        for method, value in sorted(_execution_ms.items()):
            lines.append(f'math_verifier_execution_ms_total{{method="{method}"}} {value}')
        return lines


def record_numeric(status, reason, elapsed_ms):
    if status not in get_args(NumericStatus) or reason not in get_args(NumericReason):
        raise ValueError("Unbounded numeric metric label")
    with _execution_lock:
        _execution_counts[("NUMERIC_SAMPLING", status, reason)] += 1
        _execution_ms["NUMERIC_SAMPLING"] += max(0, elapsed_ms)


def record_attempt(result, elapsed_ms):
    from app.api.schemas.attempt import Reason as AttemptReason
    if result.outcome not in {"INCOMPLETE_VALID_SO_FAR", "STEP_ERROR_FOUND", "NEEDS_REVIEW"}:
        raise ValueError("Unbounded attempt outcome")
    reason = result.steps[0].reasonCode
    if reason not in (*get_args(AttemptReason), "UNKNOWN_DOMAIN", "UNDEFINED_SOURCE"):
        raise ValueError("Unbounded attempt reason")
    with _execution_lock:
        _execution_counts[("ATTEMPT", result.outcome, reason)] += 1
        _execution_ms["ATTEMPT"] += max(0, elapsed_ms)


def record_attempt_learning(result, elapsed_ms):
    if result.action not in {'TWIN','METHODS'} or result.status not in {'VERIFIED','UNSUPPORTED','UNAVAILABLE','NO_ALTERNATIVE'}:
        raise ValueError('Unbounded learning metric label')
    with _execution_lock:
        _execution_counts[('ATTEMPT_'+result.action,result.status,'ARTIFACT')] += 1
        _execution_ms['ATTEMPT_'+result.action] += max(0,elapsed_ms)


def record_visualization(result, elapsed_ms):
    from app.api.schemas.math_visualization import Reason
    if result.status not in {'SUPPORTED','UNSUPPORTED','UNAVAILABLE'} or result.reasonCode not in get_args(Reason):
        raise ValueError('Unbounded visualization label')
    with _execution_lock:
        _execution_counts[('VISUALIZATION',result.status,result.reasonCode)] += 1
        _execution_ms['VISUALIZATION'] += max(0,elapsed_ms)
