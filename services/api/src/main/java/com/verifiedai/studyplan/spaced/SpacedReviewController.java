package com.verifiedai.studyplan.spaced;

import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import com.verifiedai.studyplan.spaced.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping(path = {"/v1/study-plan/spaced-queue", "/api/v1/study-plan/spaced-queue"})
public class SpacedReviewController {

    private final SpacedReviewService service;

    public SpacedReviewController(SpacedReviewService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<SpacedQueueResponse> getDailySpacedQueue(
            @AuthenticationPrincipal Object principal
    ) {
        UUID userId = resolveUserId(principal);
        SpacedQueueResponse response = service.getDailySpacedQueue(userId, Instant.now());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{itemId}/complete")
    public ResponseEntity<SpacedItemCompletionResponse> completeQueueItem(
            @AuthenticationPrincipal Object principal,
            @PathVariable("itemId") UUID itemId,
            @RequestBody SpacedItemCompleteRequest request
    ) {
        UUID userId = resolveUserId(principal);
        SpacedItemCompletionResponse response = service.completeQueueItem(userId, itemId, request, Instant.now());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{itemId}/dismiss")
    public ResponseEntity<SpacedQueueItemDto> dismissQueueItem(
            @AuthenticationPrincipal Object principal,
            @PathVariable("itemId") UUID itemId,
            @RequestBody SpacedItemDismissRequest request
    ) {
        UUID userId = resolveUserId(principal);
        SpacedQueueItemDto response = service.dismissQueueItem(userId, itemId, request, Instant.now());
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(SpacedQueueItemNotFoundException.class)
    public ResponseEntity<ProblemDetailsResponse> handleNotFound(SpacedQueueItemNotFoundException ex) {
        ProblemDetailsResponse response = new ProblemDetailsResponse(
                "https://errors.verified-ai-learning.example/resource-not-found",
                ex.getMessage(),
                HttpStatus.NOT_FOUND.value(),
                ApiErrorCode.RESOURCE_NOT_FOUND,
                CorrelationIds.current(),
                Map.of("recoverable", false, "userAction", "VERIFY_SPACED_ITEM_ID")
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ProblemDetailsResponse> handleApiProblem(ApiProblemException ex) {
        ProblemDetailsResponse response = new ProblemDetailsResponse(
                "https://errors.verified-ai-learning.example/" + ex.code().name().toLowerCase().replace('_', '-'),
                ex.getMessage(),
                ex.status().value(),
                ex.code(),
                CorrelationIds.current(),
                ex.details()
        );
        return ResponseEntity.status(ex.status()).body(response);
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
