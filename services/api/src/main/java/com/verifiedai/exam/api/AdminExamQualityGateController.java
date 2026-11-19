package com.verifiedai.exam.api;

import com.verifiedai.exam.application.ExamQualityGateService;
import com.verifiedai.exam.application.ExamQualityGateService.ExamQualityGateReport;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.util.Objects;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only (ROLE_ADMIN) read-only Phase 09 Exit Gate quality certification endpoint
 * ({@code GET /v1/admin/exams/quality-gate} and {@code /api/v1/admin/exams/quality-gate}).
 */
@RestController
@RequestMapping({"/v1/admin/exams/quality-gate", "/api/v1/admin/exams/quality-gate"})
public class AdminExamQualityGateController {

    private final ExamQualityGateService qualityGateService;

    public AdminExamQualityGateController(ExamQualityGateService qualityGateService) {
        this.qualityGateService = Objects.requireNonNull(qualityGateService, "qualityGateService cannot be null");
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ExamQualityGateReport> getExamQualityGate(Authentication authentication) {
        enforceAdminRole(authentication);
        ExamQualityGateReport report = qualityGateService.evaluateReadOnlyGate();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(report);
    }

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ProblemDetailsResponse> handleProblem(ApiProblemException ex) {
        ProblemDetailsResponse body = new ProblemDetailsResponse(
                "https://api.verifiedai.app/problems/" + ex.code().name().toLowerCase().replace('_', '-'),
                ex.getMessage(),
                ex.status().value(),
                ex.code(),
                CorrelationIds.current(),
                ex.details()
        );
        return ResponseEntity.status(ex.status())
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    private static void enforceAdminRole(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ApiProblemException(
                    HttpStatus.UNAUTHORIZED,
                    ApiErrorCode.AUTH_TOKEN_EXPIRED,
                    "Admin authentication is required",
                    false,
                    "Authenticate with an administrator account."
            );
        }
        boolean hasAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> "ROLE_ADMIN".equals(auth) || "ADMIN".equals(auth));
        if (!hasAdmin) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_SESSION_FORBIDDEN,
                    "Administrator role (ROLE_ADMIN) is required to view exam quality gate evidence",
                    false,
                    "Sign in with an administrator account."
            );
        }
    }
}
