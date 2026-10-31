package com.verifiedai.verification.api;

import com.verifiedai.verification.application.VerificationArbitrationService;
import com.verifiedai.verification.domain.model.VerificationDecision;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import com.verifiedai.sharedkernel.error.*;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/problem-sessions/{sessionId}")
class VerificationDecisionController {
    private final VerificationArbitrationService service;
    VerificationDecisionController(VerificationArbitrationService service) { this.service = service; }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    record Response(VerificationDecisionView decision) { }
    @GetMapping("/solves/{jobId}/verification")
    Response current(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId, @PathVariable UUID jobId) {
        return new Response(service.currentForJob(AuthenticatedUser.from(jwt).userId(), sessionId, jobId)
            .map(VerificationDecisionView::of).orElse(null));
    }
    @PostMapping("/solutions/{solutionId}/verification/{runKey}")
    VerificationDecisionView evaluate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId,
        @PathVariable UUID solutionId, @PathVariable UUID runKey, @RequestHeader(value="Idempotency-Key", required=false) String key) {
        UUID request;
        try {
            request = UUID.fromString(key);
            if (!request.toString().equalsIgnoreCase(key)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.IDEMPOTENCY_KEY_REQUIRED, "UUID Idempotency-Key required", false, "REVIEW");
        }
        return VerificationDecisionView.of(service.evaluate(AuthenticatedUser.from(jwt).userId(), sessionId, solutionId, runKey, request));
    }
}
