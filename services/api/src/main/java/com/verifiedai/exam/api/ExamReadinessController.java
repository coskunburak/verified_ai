package com.verifiedai.exam.api;

import com.verifiedai.exam.application.ExamReadinessProjectionService;
import com.verifiedai.exam.domain.ExamReadinessProjection;
import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * REST controller for Sprint 9.6 Exam Readiness Projection & Active-Trap Penalty Semantics (CNT-LOS-11).
 */
@RestController
@RequestMapping({"/v1/exams/readiness", "/api/v1/exams/readiness"})
public class ExamReadinessController {

    private static final CacheControl NO_STORE = CacheControl.noStore().mustRevalidate();

    private final ExamReadinessProjectionService readinessService;

    public ExamReadinessController(ExamReadinessProjectionService readinessService) {
        this.readinessService = Objects.requireNonNull(readinessService, "readinessService cannot be null");
    }

    public record ExamReadinessRecomputeRequest(
            String examCode,
            UUID userId,
            Boolean trainingEligible
    ) {
    }

    @GetMapping
    public ResponseEntity<ExamReadinessResponse> getReadiness(
            @RequestParam("examCode") String examCode,
            @RequestParam(value = "userId", required = false) UUID userId,
            @RequestParam(value = "recompute", required = false, defaultValue = "false") boolean recompute,
            Authentication authentication
    ) {
        UUID authenticatedLearnerId = resolveLearnerId(authentication);
        ExamReadinessProjection projection = readinessService.getOrComputeReadiness(
                authenticatedLearnerId,
                userId,
                examCode,
                recompute
        );
        return ResponseEntity.ok()
                .cacheControl(NO_STORE)
                .body(ExamReadinessResponse.from(projection));
    }

    @PostMapping("/recompute")
    public ResponseEntity<ExamReadinessResponse> recomputeReadiness(
            @RequestParam(value = "examCode", required = false) String examCodeParam,
            @RequestParam(value = "userId", required = false) UUID userIdParam,
            @RequestBody(required = false) ExamReadinessRecomputeRequest request,
            Authentication authentication
    ) {
        UUID authenticatedLearnerId = resolveLearnerId(authentication);
        if (request != null && Boolean.TRUE.equals(request.trainingEligible())) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.REQUEST_VALIDATION_FAILED,
                    "trainingEligible must be false per ADR-006",
                    false,
                    "SET_TRAINING_ELIGIBLE_FALSE"
            );
        }
        String effectiveExamCode = (request != null && request.examCode() != null && !request.examCode().isBlank())
                ? request.examCode()
                : examCodeParam;
        UUID effectiveUserId = (request != null && request.userId() != null)
                ? request.userId()
                : userIdParam;

        ExamReadinessProjection projection = readinessService.getOrComputeReadiness(
                authenticatedLearnerId,
                effectiveUserId,
                effectiveExamCode,
                true
        );
        return ResponseEntity.ok()
                .cacheControl(NO_STORE)
                .body(ExamReadinessResponse.from(projection));
    }

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ProblemDetailsResponse> handleApiProblem(ApiProblemException ex) {
        Map<String, Object> exDetails = ex.details();
        boolean recoverable = Boolean.TRUE.equals(exDetails.get("recoverable"));
        String userAction = exDetails.getOrDefault("userAction", "REVIEW_REQUEST").toString();
        return ResponseEntity.status(ex.status()).body(buildProblemDetails(
                "https://verifiedai.com/errors/" + ex.code().name().toLowerCase(),
                ex.getMessage(),
                ex.status(),
                ex.code(),
                recoverable,
                userAction
        ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetailsResponse> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(buildProblemDetails(
                "https://verifiedai.com/errors/" + ApiErrorCode.EXAM_ACCESS_DENIED.name().toLowerCase(),
                ex.getMessage(),
                HttpStatus.FORBIDDEN,
                ApiErrorCode.EXAM_ACCESS_DENIED,
                false,
                "USE_OWN_LEARNER_ACCOUNT"
        ));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentNotValidException.class,
            HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ProblemDetailsResponse> handleValidation(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(buildProblemDetails(
                "https://verifiedai.com/errors/" + ApiErrorCode.REQUEST_VALIDATION_FAILED.name().toLowerCase(),
                "Validation failed for exam readiness request",
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.REQUEST_VALIDATION_FAILED,
                true,
                "FIX_REQUEST_FIELDS"
        ));
    }

    private static ProblemDetailsResponse buildProblemDetails(
            String type,
            String title,
            HttpStatus status,
            ApiErrorCode code,
            boolean recoverable,
            String userAction
    ) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("errorCode", code.name());
        details.put("retryable", recoverable);
        details.put("recoverable", recoverable);
        details.put("userAction", userAction);
        return new ProblemDetailsResponse(
                type,
                title,
                status.value(),
                code,
                CorrelationIds.current(),
                details
        );
    }

    private static UUID resolveLearnerId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiProblemException(
                    HttpStatus.UNAUTHORIZED,
                    ApiErrorCode.AUTH_TOKEN_EXPIRED,
                    "Authentication token is missing or invalid",
                    false,
                    "SIGN_IN"
            );
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof AuthenticatedLearnerPrincipal learnerPrincipal) {
            return learnerPrincipal.learnerId();
        }
        if (principal instanceof Jwt jwt) {
            return VerifiedJwtPrincipal.from(jwt).learnerId();
        }
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        String name = authentication.getName();
        if (name != null && !name.isBlank()) {
            try {
                return UUID.fromString(name.trim());
            } catch (IllegalArgumentException ignored) {
                throw new AccessDeniedException("Authenticated principal does not carry a valid learner identifier");
            }
        }
        throw new AccessDeniedException("Authenticated principal is not permitted to access exam readiness endpoints");
    }
}
