package com.verifiedai.studyplan.gamification;

import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import com.verifiedai.studyplan.gamification.GamificationDtos.GamificationActivityRecordRequestDto;
import com.verifiedai.studyplan.gamification.GamificationDtos.GamificationSummaryResponseDto;
import com.verifiedai.studyplan.gamification.GamificationExceptions.GamificationActivityWindowInvalidException;
import com.verifiedai.studyplan.gamification.GamificationExceptions.GamificationEvidenceUnsupportedException;
import com.verifiedai.studyplan.gamification.GamificationExceptions.GamificationFeatureDisabledException;
import com.verifiedai.studyplan.gamification.GamificationExceptions.GamificationTimezoneInvalidException;
import com.verifiedai.studyplan.gamification.GamificationModels.GamificationActivityCommand;
import com.verifiedai.studyplan.gamification.GamificationModels.GamificationSummaryView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for Sprint 8.8 verified streaks, monthly freeze continuity, and
 * pedagogical achievements with strict BOLA enforcement and RFC 9457 ProblemDetails responses.
 */
@RestController
@RequestMapping({"/v1/gamification", "/api/v1/gamification"})
public class GamificationController {

    private final GamificationPolicyService policyService;

    public GamificationController(GamificationPolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping("/summary")
    public ResponseEntity<GamificationSummaryResponseDto> getSummary(
            @RequestParam(name = "timezone", required = false, defaultValue = "UTC") String timezone,
            @RequestParam(name = "userId", required = false) UUID requestedUserId,
            Authentication authentication
    ) {
        UUID learnerId = resolveLearnerId(authentication);
        if (requestedUserId != null && !requestedUserId.equals(learnerId)) {
            throw new AccessDeniedException("Cross-learner gamification access is forbidden");
        }
        GamificationSummaryView summary = policyService.getSummary(learnerId, timezone);
        return ResponseEntity.ok(GamificationSummaryResponseDto.fromDomain(summary));
    }

    @PostMapping("/activity")
    public ResponseEntity<GamificationSummaryResponseDto> recordActivity(
            @Valid @RequestBody GamificationActivityRecordRequestDto request,
            Authentication authentication
    ) {
        UUID learnerId = resolveLearnerId(authentication);
        GamificationEvidenceType evidenceType = GamificationEvidenceType.fromRequest(request.evidenceType());
        GamificationActivityCommand command = new GamificationActivityCommand(
                request.idempotencyKey().trim(),
                evidenceType,
                request.evidenceReference().trim(),
                request.learnerTimezone().trim(),
                request.occurredAtUtc(),
                request.conceptId() != null ? request.conceptId().trim() : null,
                Boolean.TRUE.equals(request.misconceptionRepaired()),
                Boolean.TRUE.equals(request.spacedReviewCompleted()),
                request.verifiedMasteredConceptCount() != null ? request.verifiedMasteredConceptCount() : 0
        );
        GamificationSummaryView summary = policyService.recordActivity(learnerId, command);
        return ResponseEntity.ok(GamificationSummaryResponseDto.fromDomain(summary));
    }

    @ExceptionHandler(GamificationFeatureDisabledException.class)
    public ResponseEntity<ProblemDetailsResponse> handleDisabled(GamificationFeatureDisabledException ex) {
        return problemResponse(ex);
    }

    @ExceptionHandler(GamificationTimezoneInvalidException.class)
    public ResponseEntity<ProblemDetailsResponse> handleTimezoneInvalid(GamificationTimezoneInvalidException ex) {
        return problemResponse(ex);
    }

    @ExceptionHandler(GamificationEvidenceUnsupportedException.class)
    public ResponseEntity<ProblemDetailsResponse> handleEvidenceUnsupported(GamificationEvidenceUnsupportedException ex) {
        return problemResponse(ex);
    }

    @ExceptionHandler(GamificationActivityWindowInvalidException.class)
    public ResponseEntity<ProblemDetailsResponse> handleActivityWindowInvalid(GamificationActivityWindowInvalidException ex) {
        return problemResponse(ex);
    }

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ProblemDetailsResponse> handleApiProblem(ApiProblemException ex) {
        return problemResponse(ex);
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetailsResponse> handleValidation(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(buildProblemDetails(
                "https://verifiedai.com/errors/" + ApiErrorCode.REQUEST_VALIDATION_FAILED.name().toLowerCase(),
                "Validation failed for gamification activity payload",
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.REQUEST_VALIDATION_FAILED,
                false,
                "FIX_REQUEST_FIELDS"
        ));
    }

    private static ResponseEntity<ProblemDetailsResponse> problemResponse(ApiProblemException ex) {
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
        throw new AccessDeniedException("Authenticated principal is not permitted to access learner gamification");
    }
}
