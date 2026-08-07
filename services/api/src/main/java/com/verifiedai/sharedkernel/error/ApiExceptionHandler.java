package com.verifiedai.sharedkernel.error;

import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetailsResponse> handleUnexpected(Exception exception) {
        ProblemDetailsResponse response = new ProblemDetailsResponse(
            "https://errors.verified-ai-learning.example/internal-error",
            "Internal error",
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            ApiErrorCode.INTERNAL_ERROR,
            CorrelationIds.current(),
            Map.of("recoverable", false, "userAction", "CONTACT_SUPPORT")
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
