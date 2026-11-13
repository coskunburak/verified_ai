package com.verifiedai.studyplan.coach;

import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import com.verifiedai.studyplan.coach.dto.DailyCoachPreferencesRequest;
import com.verifiedai.studyplan.coach.dto.DailyCoachSummaryResponse;
import com.verifiedai.studyplan.coach.dto.DailyCoachTaskCompleteRequest;
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
import java.util.UUID;

@RestController
@RequestMapping(path = {"/v1/coach", "/api/v1/coach"})
public class DailyCoachController {

    private final DailyCoachSessionService service;

    public DailyCoachController(DailyCoachSessionService service) {
        this.service = service;
    }

    @GetMapping("/today-summary")
    public ResponseEntity<DailyCoachSummaryResponse> getTodaySummary(
            @AuthenticationPrincipal Object principal,
            @RequestParam(name = "localDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate localDate
    ) {
        UUID userId = resolveUserId(principal);
        DailyCoachSummaryResponse response = service.getOrCreateTodaySummary(userId, localDate);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/preferences")
    public ResponseEntity<DailyCoachSummaryResponse> updatePreferences(
            @AuthenticationPrincipal Object principal,
            @Valid @RequestBody DailyCoachPreferencesRequest request
    ) {
        UUID userId = resolveUserId(principal);
        DailyCoachSummaryResponse response = service.updatePreferences(userId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/missions/{id}/complete")
    public ResponseEntity<DailyCoachSummaryResponse> completeTask(
            @AuthenticationPrincipal Object principal,
            @PathVariable("id") UUID id,
            @Valid @RequestBody DailyCoachTaskCompleteRequest request
    ) {
        UUID userId = resolveUserId(principal);
        DailyCoachSummaryResponse response = service.completeTask(userId, id, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/missions/{id}/downshift")
    public ResponseEntity<DailyCoachSummaryResponse> downshiftMission(
            @AuthenticationPrincipal Object principal,
            @PathVariable("id") UUID id
    ) {
        UUID userId = resolveUserId(principal);
        DailyCoachSummaryResponse response = service.downshiftMission(userId, id);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(DailyCoachSessionNotFoundException.class)
    public ResponseEntity<ProblemDetailsResponse> handleNotFound(DailyCoachSessionNotFoundException ex) {
        ProblemDetailsResponse response = new ProblemDetailsResponse(
                "https://errors.verified-ai-learning.example/resource-not-found",
                ex.getMessage(),
                HttpStatus.NOT_FOUND.value(),
                ApiErrorCode.RESOURCE_NOT_FOUND,
                CorrelationIds.current(),
                Map.of("recoverable", false, "userAction", "VERIFY_COACH_SESSION_ID")
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
