package com.verifiedai.studyplan.practice;

import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import com.verifiedai.studyplan.practice.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(path = {"/v1/study-plan/practice/sessions", "/api/v1/study-plan/practice/sessions"})
public class PracticeSessionController {

    private final PracticeSessionService sessionService;

    public PracticeSessionController(PracticeSessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public ResponseEntity<PracticeSessionResponse> startPracticeSession(
            @AuthenticationPrincipal Object principal,
            @RequestBody StartPracticeSessionRequest request
    ) {
        UUID userId = resolveUserId(principal);
        PracticeSessionResponse response = sessionService.startPracticeSession(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<PracticeSessionResponse> getPracticeSession(
            @AuthenticationPrincipal Object principal,
            @PathVariable("sessionId") UUID sessionId
    ) {
        UUID userId = resolveUserId(principal);
        PracticeSessionResponse response = sessionService.getPracticeSession(sessionId, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{sessionId}/items/{itemId}/submit")
    public ResponseEntity<PracticeItemSubmissionResponse> submitPracticeItemAnswer(
            @AuthenticationPrincipal Object principal,
            @PathVariable("sessionId") UUID sessionId,
            @PathVariable("itemId") UUID itemId,
            @RequestBody SubmitPracticeItemRequest request
    ) {
        UUID userId = resolveUserId(principal);
        PracticeItemSubmissionResponse response = sessionService.submitPracticeItemAnswer(sessionId, itemId, userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{sessionId}/abandon")
    public ResponseEntity<PracticeSessionResponse> abandonPracticeSession(
            @AuthenticationPrincipal Object principal,
            @PathVariable("sessionId") UUID sessionId,
            @RequestBody(required = false) PracticeSessionAbandonRequest request
    ) {
        UUID userId = resolveUserId(principal);
        PracticeSessionResponse response = sessionService.abandonPracticeSession(sessionId, userId, request);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(RapidGuessGuardException.class)
    public ResponseEntity<ProblemDetailsResponse> handleRapidGuessGuard(RapidGuessGuardException ex) {
        ProblemDetailsResponse response = new ProblemDetailsResponse(
                "https://errors.verified-ai-learning.example/rapid-guess-guard-triggered",
                ex.getMessage(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ApiErrorCode.REQUEST_VALIDATION_FAILED,
                CorrelationIds.current(),
                Map.of("recoverable", true, "userAction", "REASON_ALGEBRAICALLY_BEFORE_SUBMISSION")
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);
    }

    @ExceptionHandler(com.verifiedai.sharedkernel.error.ApiProblemException.class)
    public ResponseEntity<ProblemDetailsResponse> handleApiProblem(com.verifiedai.sharedkernel.error.ApiProblemException exception) {
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

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetailsResponse> handleAccessDenied(AccessDeniedException ex) {
        ProblemDetailsResponse response = new ProblemDetailsResponse(
                "https://errors.verified-ai-learning.example/resource-forbidden",
                ex.getMessage(),
                HttpStatus.FORBIDDEN.value(),
                ApiErrorCode.RESOURCE_FORBIDDEN,
                CorrelationIds.current(),
                Map.of("recoverable", false, "userAction", "AUTHENTICATE_AS_OWNER")
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    private UUID resolveUserId(Object principal) {
        if (principal instanceof AuthenticatedLearnerPrincipal alp) {
            return alp.learnerId();
        }
        if (principal instanceof VerifiedJwtPrincipal vjp) {
            return vjp.userId();
        }
        if (principal instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            try {
                return UUID.fromString(auth.getName());
            } catch (IllegalArgumentException ignored) {
            }
        }
        throw new AccessDeniedException("Unable to resolve authenticated learner ID");
    }
}
