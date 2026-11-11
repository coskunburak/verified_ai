package com.verifiedai.mastery.application;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public class TwinSolutionLockedException extends ApiProblemException {

    private final Instant lockUntil;
    private final long retryAfterSeconds;

    public TwinSolutionLockedException(Instant lockUntil, long retryAfterSeconds) {
        super(
                HttpStatus.LOCKED,
                ApiErrorCode.TWIN_SOLUTION_LOCKED,
                "Twin solution is locked for unassisted challenge",
                true,
                "WAIT_OR_FORFEIT"
        );
        this.lockUntil = Objects.requireNonNull(lockUntil);
        this.retryAfterSeconds = Math.max(0, retryAfterSeconds);
    }

    public Instant getLockUntil() {
        return lockUntil;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    @Override
    public Map<String, Object> details() {
        return Map.of(
                "recoverable", true,
                "userAction", "WAIT_OR_FORFEIT",
                "lockUntil", lockUntil.toString(),
                "retryAfterSeconds", retryAfterSeconds
        );
    }
}
