package com.verifiedai.exam.api;

import com.verifiedai.exam.application.ExamAnalyticsService;
import com.verifiedai.exam.domain.ExamProgressReport;
import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dual-prefix REST controller for Sprint 9.9 longitudinal exam progress analytics (INV-EXAM-005).
 */
@RestController
@RequestMapping({"/v1/analytics/exams", "/api/v1/analytics/exams"})
public class ExamAnalyticsController {

    private static final Set<String> FORBIDDEN_IDENTITY_PARAMS = Set.of(
            "userId", "user_id", "learnerId", "learner_id", "ownerId", "owner_id"
    );

    private final ExamAnalyticsService analyticsService;

    public ExamAnalyticsController(ExamAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/progress")
    public ResponseEntity<ExamProgressReport> progress(
            @RequestParam(value = "examCode", required = false) String examCode,
            HttpServletRequest request,
            Authentication authentication
    ) {
        UUID learnerId = resolveLearnerId(authentication);
        rejectForeignIdentityParameters(request, learnerId);
        ExamProgressReport report = analyticsService.getProgressReport(learnerId, examCode);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noStore())
                .body(report);
    }

    @ExceptionHandler(ExamAnalyticsService.Failure.class)
    public ResponseEntity<ProblemDetail> handleServiceFailure(ExamAnalyticsService.Failure failure) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(failure.status),
                failure.detail != null ? failure.detail : failure.code
        );
        detail.setProperty("code", failure.code);
        detail.setProperty("errorCode", failure.code);
        return ResponseEntity.status(failure.status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .body(detail);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParam(MissingServletRequestParameterException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Required query parameter 'examCode' is missing"
        );
        detail.setProperty("code", "REQUEST_VALIDATION_FAILED");
        detail.setProperty("errorCode", "REQUEST_VALIDATION_FAILED");
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .body(detail);
    }

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ProblemDetail> handleApiProblem(ApiProblemException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        detail.setProperty("code", ex.code().name());
        detail.setProperty("errorCode", ex.code().name());
        return ResponseEntity.status(ex.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .body(detail);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN,
                "Authenticated principal is not permitted to access exam analytics"
        );
        detail.setProperty("code", "EXAM_ACCESS_DENIED");
        detail.setProperty("errorCode", "EXAM_ACCESS_DENIED");
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .body(detail);
    }

    private static void rejectForeignIdentityParameters(HttpServletRequest request, UUID authenticatedLearnerId) {
        if (request == null) {
            return;
        }
        for (String paramName : FORBIDDEN_IDENTITY_PARAMS) {
            String supplied = request.getParameter(paramName);
            if (supplied != null && !supplied.isBlank()) {
                if (!authenticatedLearnerId.toString().equalsIgnoreCase(supplied.trim())) {
                    throw new ExamAnalyticsService.Failure(
                            403,
                            "EXAM_ACCESS_DENIED",
                            "Client-supplied learner identifier is forbidden"
                    );
                }
                throw new ExamAnalyticsService.Failure(
                        400,
                        "REQUEST_VALIDATION_FAILED",
                        "Unexpected identity query parameter"
                );
            }
        }
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
        throw new AccessDeniedException("Authenticated principal is not permitted to access exam analytics endpoints");
    }
}
