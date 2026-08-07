from __future__ import annotations

import logging
import sys

logger = logging.getLogger("math-verifier")


class DefaultFieldsFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        defaults = {
            "correlation_id": "",
            "path": "",
            "status_code": "",
            "duration_ms": "",
        }
        for key, value in defaults.items():
            if not hasattr(record, key):
                setattr(record, key, value)
        return True


def configure_logging() -> None:
    handler = logging.StreamHandler(sys.stdout)
    handler.addFilter(DefaultFieldsFilter())
    formatter = logging.Formatter(
        '{"timestamp":"%(asctime)s","level":"%(levelname)s","service":"math-verifier",'
        '"correlationId":"%(correlation_id)s","operation":"%(path)s","outcome":"%(status_code)s",'
        '"durationMs":"%(duration_ms)s","message":"%(message)s"}'
    )
    handler.setFormatter(formatter)
    root = logging.getLogger()
    root.handlers = [handler]
    root.setLevel(logging.INFO)
