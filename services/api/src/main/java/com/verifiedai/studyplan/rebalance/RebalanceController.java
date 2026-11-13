package com.verifiedai.studyplan.rebalance;

import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import com.verifiedai.studyplan.rebalance.dto.StudyPlanRebalanceRequest;
import com.verifiedai.studyplan.rebalance.dto.StudyPlanRebalanceResponse;
import com.verifiedai.studyplan.rebalance.dto.StudyPlanRecoveryStatusResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping(path = {"/v1/study-plan", "/api/v1/study-plan"})
public class RebalanceController {

    private final StudyPlanRebalancingService rebalancingService;

    public RebalanceController(StudyPlanRebalancingService rebalancingService) {
        this.rebalancingService = Objects.requireNonNull(rebalancingService, "rebalancingService cannot be null");
    }

    @GetMapping("/recovery-status")
    public ResponseEntity<StudyPlanRecoveryStatusResponse> getRecoveryStatus(
            @AuthenticationPrincipal Object principal,
            @RequestParam(name = "as_of_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(rebalancingService.getRecoveryStatus(userId, asOfDate));
    }

    @PostMapping("/rebalance")
    public ResponseEntity<StudyPlanRebalanceResponse> rebalanceStudyPlan(
            @AuthenticationPrincipal Object principal,
            @Valid @RequestBody StudyPlanRebalanceRequest request
    ) {
        UUID userId = resolveUserId(principal);
        return ResponseEntity.ok(rebalancingService.executeRebalance(userId, request));
    }

    @ExceptionHandler(StudyPlanRecoveryNotFoundException.class)
    public ResponseEntity<ProblemDetailsResponse> handleRecoveryNotFound(StudyPlanRecoveryNotFoundException ex) {
        ProblemDetailsResponse response = new ProblemDetailsResponse(
                "https://errors.verified-ai-learning.example/study-plan-recovery-not-found",
                ex.getMessage(),
                HttpStatus.NOT_FOUND.value(),
                ApiErrorCode.STUDY_PLAN_RECOVERY_NOT_FOUND,
                CorrelationIds.current(),
                Map.of("recoverable", false, "userAction", "VERIFY_STUDY_PLAN_RECOVERY_ID")
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
        if (principal instanceof Jwt jwt && jwt.getSubject() != null) {
            return UUID.fromString(jwt.getSubject());
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            try {
                return UUID.fromString(auth.getName());
            } catch (IllegalArgumentException ignored) {
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
