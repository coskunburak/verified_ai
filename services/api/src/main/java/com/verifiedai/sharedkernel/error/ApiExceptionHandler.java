package com.verifiedai.sharedkernel.error;

import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;

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

    @ExceptionHandler(ApiProblemException.class)
    ResponseEntity<ProblemDetailsResponse> handleApiProblem(ApiProblemException exception) {
        ProblemDetailsResponse response = new ProblemDetailsResponse(
            "https://errors.verified-ai-learning.example/" + exception.code().name().toLowerCase().replace('_', '-'),
            exception.getMessage(),
            exception.status().value(),
            exception.code(),
            CorrelationIds.current(),
            exception.details()
        );
        return ResponseEntity.status(exception.status()).body(response);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetailsResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException exception) {
        ProblemDetailsResponse response = new ProblemDetailsResponse(
            "https://errors.verified-ai-learning.example/method-not-allowed",
            "Method not allowed",
            HttpStatus.METHOD_NOT_ALLOWED.value(),
            ApiErrorCode.METHOD_NOT_ALLOWED,
            CorrelationIds.current(),
            Map.of("recoverable", false, "userAction", "USE_SUPPORTED_METHOD")
        );
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(response);
    }
}
