package com.verifiedai.studyplan.coldstart;

import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import com.verifiedai.studyplan.coldstart.ColdStartModels.ColdStartDiagnosticRequest;
import com.verifiedai.studyplan.coldstart.ColdStartModels.ColdStartDiagnosticResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Learner-scoped REST API for Sprint 8.10 3-question cold-start baseline diagnostics
 * and adaptive preference calibration with strict BOLA and RFC 9457 ProblemDetails.
 */
@RestController
@Validated
@RequestMapping({"/v1/study-plan/cold-start", "/api/v1/study-plan/cold-start"})
public class ColdStartCalibrationController {

    private final ColdStartCalibrationService calibrationService;

    public ColdStartCalibrationController(ColdStartCalibrationService calibrationService) {
        this.calibrationService = Objects.requireNonNull(calibrationService, "calibrationService cannot be null");
    }

    @GetMapping
    public ResponseEntity<ColdStartDiagnosticResponse> getLatestCalibration(
            @RequestParam(name = "userId", required = false) UUID requestedUserId,
            @RequestParam(name = "daysInactive", required = false) @Min(0) @Max(3650) Integer daysInactive,
            Authentication authentication
    ) {
        UUID learnerId = resolveLearnerId(authentication);
        if (requestedUserId != null && !requestedUserId.equals(learnerId)) {
            throw new AccessDeniedException("Cross-learner cold-start calibration access is forbidden");
        }
        ColdStartDiagnosticResponse response = calibrationService.getLatestCalibration(learnerId, daysInactive);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(response);
    }

    @PostMapping
    public ResponseEntity<ColdStartDiagnosticResponse> submitDiagnostic(
            @RequestParam(name = "userId", required = false) UUID requestedUserId,
            @Valid @RequestBody ColdStartDiagnosticRequest request,
            Authentication authentication
    ) {
        UUID learnerId = resolveLearnerId(authentication);
        if (requestedUserId != null && !requestedUserId.equals(learnerId)) {
            throw new AccessDeniedException("Cross-learner cold-start submission is forbidden");
        }
        ColdStartDiagnosticResponse response = calibrationService.submitDiagnostic(learnerId, request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore().mustRevalidate())
                .body(response);
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
                "https://verifiedai.com/errors/" + ApiErrorCode.RESOURCE_FORBIDDEN.name().toLowerCase(),
                ex.getMessage(),
                HttpStatus.FORBIDDEN,
                ApiErrorCode.RESOURCE_FORBIDDEN,
                false,
                "USE_OWN_LEARNER_ACCOUNT"
        ));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ProblemDetailsResponse> handleValidation(Exception ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(buildProblemDetails(
                "https://verifiedai.com/errors/" + ApiErrorCode.REQUEST_VALIDATION_FAILED.name().toLowerCase(),
                "Validation failed for cold-start diagnostic payload",
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.REQUEST_VALIDATION_FAILED,
                false,
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
        throw new AccessDeniedException("Authenticated principal is not permitted to access cold-start calibration");
    }
}
