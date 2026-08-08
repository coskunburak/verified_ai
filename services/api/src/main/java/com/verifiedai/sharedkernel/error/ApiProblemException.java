package com.verifiedai.sharedkernel.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

public final class ApiProblemException extends RuntimeException {
    private final HttpStatus status;
    private final ApiErrorCode code;
    private final boolean recoverable;
    private final String userAction;

    public ApiProblemException(HttpStatus status, ApiErrorCode code, String title, boolean recoverable, String userAction) {
        super(title);
        this.status = status;
        this.code = code;
        this.recoverable = recoverable;
        this.userAction = userAction;
    }

    public HttpStatus status() {
        return status;
    }

    public ApiErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return Map.of("recoverable", recoverable, "userAction", userAction);
    }
}
