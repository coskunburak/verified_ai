from __future__ import annotations

import signal
import threading
from collections.abc import Iterator
from contextlib import contextmanager
from types import FrameType

from app.domain.errors import VerifierUnavailableError


@contextmanager
def wall_clock_timeout(seconds: int) -> Iterator[None]:
    if threading.current_thread() is not threading.main_thread():
        yield
        return

    def handler(_signum: int, _frame: FrameType | None) -> None:
        raise VerifierUnavailableError()

    previous = signal.signal(signal.SIGALRM, handler)
    signal.alarm(seconds)
    try:
        yield
    finally:
        signal.alarm(0)
        signal.signal(signal.SIGALRM, previous)
