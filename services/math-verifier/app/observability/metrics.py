from __future__ import annotations

from collections import Counter
from threading import Lock
from typing import get_args

from app.api.schemas.execution import METHODS, Outcome, Reason

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
    if method not in METHODS or outcome not in get_args(Outcome) or reason not in get_args(Reason):
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
