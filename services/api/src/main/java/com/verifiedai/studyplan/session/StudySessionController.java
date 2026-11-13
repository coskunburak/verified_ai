package com.verifiedai.studyplan.session;

import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API controller exposing durable study session orchestration endpoints
 * with strict JWT BOLA isolation and RFC 9457 ProblemDetails error mapping.
 */
@RestController
@RequestMapping(path = {"/v1/study-sessions", "/api/v1/study-sessions"})
public class StudySessionController {

    private final StudySessionOrchestrationService service;

    public StudySessionController(StudySessionOrchestrationService service) {
        this.service = service;
    }

    @PostMapping("/start")
    public ResponseEntity<StudySessionStartResponse> startSession(
        @AuthenticationPrincipal Object principal,
        @Valid @RequestBody StudySessionStartRequest request
    ) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.startSession(userId, request));
    }

    @GetMapping("/active")
    public ResponseEntity<StudySessionStateDto> getActiveSession(
        @AuthenticationPrincipal Object principal
    ) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(service.getActiveSession(userId));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<StudySessionStateDto> getSessionById(
        @AuthenticationPrincipal Object principal,
        @PathVariable("sessionId") UUID sessionId
    ) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(service.getSessionById(userId, sessionId));
    }

    @PutMapping("/{sessionId}/checkpoint")
    public ResponseEntity<StudySessionCheckpointResponse> checkpointSession(
        @AuthenticationPrincipal Object principal,
        @PathVariable("sessionId") UUID sessionId,
        @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody StudySessionCheckpointRequest request
    ) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(service.checkpointSession(userId, sessionId, idempotencyKey, request));
    }

    @PostMapping("/{sessionId}/pause")
    public ResponseEntity<StudySessionStateDto> pauseSession(
        @AuthenticationPrincipal Object principal,
        @PathVariable("sessionId") UUID sessionId,
        @Valid @RequestBody(required = false) StudySessionPauseRequest request
    ) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(service.pauseSession(userId, sessionId, request));
    }

    @PostMapping("/{sessionId}/resume")
    public ResponseEntity<StudySessionStateDto> resumeSession(
        @AuthenticationPrincipal Object principal,
        @PathVariable("sessionId") UUID sessionId,
        @Valid @RequestBody(required = false) StudySessionResumeRequest request
    ) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(service.resumeSession(userId, sessionId, request));
    }

    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<StudySessionCompleteResponse> completeSession(
        @AuthenticationPrincipal Object principal,
        @PathVariable("sessionId") UUID sessionId,
        @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody StudySessionCompleteRequest request
    ) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(service.completeSession(userId, sessionId, idempotencyKey, request));
    }

    @ExceptionHandler(StudySessionNotFoundException.class)
    public ResponseEntity<ProblemDetailsResponse> handleNotFound(StudySessionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ProblemDetailsResponse(
            "https://errors.verified-ai-learning.example/study-session-not-found",
            ex.getMessage(),
            HttpStatus.NOT_FOUND.value(),
            ApiErrorCode.STUDY_SESSION_NOT_FOUND,
            CorrelationIds.current(),
            Map.of("recoverable", false, "userAction", "START_NEW_STUDY_SESSION")
        ));
    }

    @ExceptionHandler(IllegalSessionStateTransitionException.class)
    public ResponseEntity<ProblemDetailsResponse> handleIllegalTransition(IllegalSessionStateTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ProblemDetailsResponse(
            "https://errors.verified-ai-learning.example/illegal-session-state-transition",
            ex.getMessage(),
            HttpStatus.CONFLICT.value(),
            ApiErrorCode.ILLEGAL_SESSION_STATE_TRANSITION,
            CorrelationIds.current(),
            Map.of("recoverable", false, "userAction", "REFRESH_SESSION_STATE")
        ));
    }

    @ExceptionHandler(StudySessionInvalidCheckpointException.class)
    public ResponseEntity<ProblemDetailsResponse> handleInvalidCheckpoint(StudySessionInvalidCheckpointException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ProblemDetailsResponse(
            "https://errors.verified-ai-learning.example/study-session-invalid-checkpoint",
            ex.getMessage(),
            HttpStatus.UNPROCESSABLE_ENTITY.value(),
            ApiErrorCode.STUDY_SESSION_INVALID_CHECKPOINT,
            CorrelationIds.current(),
            Map.of("recoverable", true, "userAction", "FIX_CHECKPOINT_PAYLOAD")
        ));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemDetailsResponse> handleMissingHeader(MissingRequestHeaderException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ProblemDetailsResponse(
            "https://errors.verified-ai-learning.example/request-validation-failed",
            ex.getMessage(),
            HttpStatus.BAD_REQUEST.value(),
            ApiErrorCode.REQUEST_VALIDATION_FAILED,
            CorrelationIds.current(),
            Map.of("recoverable", true, "userAction", "PROVIDE_REQUIRED_HEADER")
        ));
    }

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ProblemDetailsResponse> handleApiProblem(ApiProblemException ex) {
        return ResponseEntity.status(ex.status()).body(new ProblemDetailsResponse(
            "https://errors.verified-ai-learning.example/" + ex.code().name().toLowerCase().replace('_', '-'),
            ex.getMessage(),
            ex.status().value(),
            ex.code(),
            CorrelationIds.current(),
            ex.details()
        ));
    }

    private static UUID resolveUserId(Object principal) {
        if (principal instanceof VerifiedJwtPrincipal verified) {
            return verified.userId();
        }
        if (principal instanceof AuthenticatedLearnerPrincipal learner) {
            return learner.learnerId();
        }
        if (principal instanceof Jwt jwt) {
            return VerifiedJwtPrincipal.from(jwt).userId();
        }
        if (principal instanceof UUID uuid) {
            return uuid;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            Object authPrincipal = authentication.getPrincipal();
            if (authPrincipal instanceof VerifiedJwtPrincipal verified) {
                return verified.userId();
            }
            if (authPrincipal instanceof AuthenticatedLearnerPrincipal learner) {
                return learner.learnerId();
            }
            if (authPrincipal instanceof Jwt jwt) {
                return VerifiedJwtPrincipal.from(jwt).userId();
            }
            if (authPrincipal instanceof UUID uuid) {
                return uuid;
            }
        }
        throw new ApiProblemException(
            HttpStatus.UNAUTHORIZED,
            ApiErrorCode.AUTH_CREDENTIALS_INVALID,
            "Authentication required",
            false,
            "SIGN_IN"
        );
    }
}
