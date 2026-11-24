package com.verifiedai.admin.api;

import com.verifiedai.admin.api.dto.AdminAiOpsOverviewResponse;
import com.verifiedai.admin.application.AdminSupportQueryService;
import com.verifiedai.admin.domain.AdminRedactionPolicy;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative AI Operations controller exposing provider health, route/prompt/model provenance,
 * aggregated cost totals, redacted failure samples, and kill-switch states.
 */
@RestController
@RequestMapping({"/api/v1/admin/ai-ops", "/v1/admin/ai-ops"})
public class AdminAiOperationsController {

    private final AdminSupportQueryService queryService;

    public AdminAiOperationsController(AdminSupportQueryService queryService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
    }

    @GetMapping(path = "/overview", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminAiOpsOverviewResponse> getAiOpsOverview(
        @AuthenticationPrincipal Jwt jwt,
        Authentication authentication,
        @RequestParam(name = "from", required = false) String from,
        @RequestParam(name = "to", required = false) String to
    ) {
        UUID actorUserId = AuthenticatedUser.from(jwt).userId();
        String actorRole = AdminRedactionPolicy.extractActorRole(
            authentication == null ? List.of() : authentication.getAuthorities()
        );
        LocalDate fromDate = parseDate(from, "from");
        LocalDate toDate = parseDate(to, "to");
        var view = queryService.getAiOpsOverview(fromDate, toDate, actorUserId, actorRole);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(AdminAiOpsOverviewResponse.from(view));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetailsResponse> handleAccessDenied(AccessDeniedException exception) {
        ProblemDetailsResponse response = new ProblemDetailsResponse(
            "https://errors.verified-ai-learning.example/admin-support-forbidden",
            "Administrative role is required for AI operations overview",
            HttpStatus.FORBIDDEN.value(),
            ApiErrorCode.ADMIN_SUPPORT_FORBIDDEN,
            CorrelationIds.current(),
            Map.of("recoverable", false, "userAction", "USE_ADMIN_ACCOUNT")
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .cacheControl(CacheControl.noStore().cachePrivate())
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(response);
    }

    private static LocalDate parseDate(String raw, String paramName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException exception) {
            throw new ApiProblemException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                ApiErrorCode.ADMIN_VALIDATION_FAILED,
                "Query parameter '" + paramName + "' must use ISO YYYY-MM-DD format",
                false,
                "FIX_DATE_PARAM"
            );
        }
    }
}
