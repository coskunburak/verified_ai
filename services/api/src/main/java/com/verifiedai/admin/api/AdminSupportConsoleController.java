package com.verifiedai.admin.api;

import com.verifiedai.admin.api.dto.AdminBillingSupportResponse;
import com.verifiedai.admin.api.dto.AdminProblemTraceResponse;
import com.verifiedai.admin.api.dto.AdminRawContentGrantResponse;
import com.verifiedai.admin.api.dto.AdminRawContentInspectionResponse;
import com.verifiedai.admin.api.dto.AdminRemediationResultResponse;
import com.verifiedai.admin.api.dto.AdminSupportAuditListResponse;
import com.verifiedai.admin.api.dto.AdminUserLookupResponse;
import com.verifiedai.admin.api.dto.BillingReconcileRemediationRequest;
import com.verifiedai.admin.api.dto.KillSwitchRemediationRequest;
import com.verifiedai.admin.api.dto.QuotaSyncRemediationRequest;
import com.verifiedai.admin.api.dto.RawContentGrantRequest;
import com.verifiedai.admin.api.dto.WrongAnswerReportQueueResponse;
import com.verifiedai.admin.api.dto.WrongAnswerReportResponse;
import com.verifiedai.admin.api.dto.WrongAnswerReportReviewRequest;
import com.verifiedai.admin.application.AdminRawContentAccessService;
import com.verifiedai.admin.application.AdminRemediationService;
import com.verifiedai.admin.application.AdminSupportQueryService;
import com.verifiedai.admin.application.WrongAnswerReportService;
import com.verifiedai.admin.domain.AdminRedactionPolicy;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.Collection;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative & Support Console REST Controller (INV-ADM-001..007).
 * Enforces ROLE_ADMIN, elevated authority for raw-content access, Cache-Control: no-store, private,
 * Idempotency-Key on mutations, and typed ProblemDetails responses.
 */
@RestController
@RequestMapping({"/api/v1/admin/support", "/v1/admin/support"})
public class AdminSupportConsoleController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final AdminSupportQueryService queryService;
    private final AdminRawContentAccessService rawContentAccessService;
    private final WrongAnswerReportService wrongAnswerReportService;
    private final AdminRemediationService remediationService;

    public AdminSupportConsoleController(
        AdminSupportQueryService queryService,
        AdminRawContentAccessService rawContentAccessService,
        WrongAnswerReportService wrongAnswerReportService,
        AdminRemediationService remediationService
    ) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
        this.rawContentAccessService = Objects.requireNonNull(
            rawContentAccessService,
            "rawContentAccessService must not be null"
        );
        this.wrongAnswerReportService = Objects.requireNonNull(
            wrongAnswerReportService,
            "wrongAnswerReportService must not be null"
        );
        this.remediationService = Objects.requireNonNull(remediationService, "remediationService must not be null");
    }

    @GetMapping(path = "/users/lookup", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminUserLookupResponse> lookupUser(
        @AuthenticationPrincipal Jwt jwt,
        Authentication authentication,
        @RequestParam(name = "type") String type,
        @RequestParam(name = "value") String value
    ) {
        UUID actorUserId = AuthenticatedUser.from(jwt).userId();
        String actorRole = AdminRedactionPolicy.extractActorRole(authoritiesOf(authentication));
        var view = queryService.lookupUser(type, value, actorUserId, actorRole);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(AdminUserLookupResponse.from(view));
    }

    @GetMapping(path = "/users/{userId}/billing", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminBillingSupportResponse> getBillingSupport(
        @AuthenticationPrincipal Jwt jwt,
        Authentication authentication,
        @PathVariable("userId") UUID userId
    ) {
        UUID actorUserId = AuthenticatedUser.from(jwt).userId();
        String actorRole = AdminRedactionPolicy.extractActorRole(authoritiesOf(authentication));
        var view = queryService.getBillingSupport(userId, actorUserId, actorRole);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(AdminBillingSupportResponse.from(view));
    }

    @GetMapping(path = "/problems/trace", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminProblemTraceResponse> getProblemTrace(
        @AuthenticationPrincipal Jwt jwt,
        Authentication authentication,
        @RequestParam(name = "problemSessionId", required = false) UUID problemSessionId,
        @RequestParam(name = "traceId", required = false) String traceId,
        @RequestParam(name = "correlationId", required = false) String correlationId
    ) {
        UUID actorUserId = AuthenticatedUser.from(jwt).userId();
        String actorRole = AdminRedactionPolicy.extractActorRole(authoritiesOf(authentication));
        var view = queryService.getProblemTrace(problemSessionId, traceId, correlationId, actorUserId, actorRole);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(AdminProblemTraceResponse.from(view));
    }

    @PostMapping(
        path = "/problems/{problemSessionId}/raw-content-access",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminRawContentGrantResponse> createRawContentGrant(
        @AuthenticationPrincipal Jwt jwt,
        Authentication authentication,
        @PathVariable("problemSessionId") UUID problemSessionId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody RawContentGrantRequest request
    ) {
        UUID actorUserId = AuthenticatedUser.from(jwt).userId();
        if (request == null) {
            throw validationFailed("Raw content grant request body is required");
        }
        if (Boolean.TRUE.equals(request.trainingEligible())) {
            throw validationFailed("Admin raw content grant requests are never training eligible (ADR-006)");
        }
        var view = rawContentAccessService.createGrant(
            problemSessionId,
            request.ticketReference(),
            request.reason(),
            request.ttlMinutes(),
            idempotencyKey,
            actorUserId,
            authoritiesOf(authentication)
        );
        HttpStatus status = view.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status)
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(AdminRawContentGrantResponse.from(view));
    }

    @GetMapping(path = "/problems/{problemSessionId}/raw-content", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminRawContentInspectionResponse> inspectRawContent(
        @AuthenticationPrincipal Jwt jwt,
        Authentication authentication,
        @PathVariable("problemSessionId") UUID problemSessionId,
        @RequestParam(name = "grantId") UUID grantId
    ) {
        UUID actorUserId = AuthenticatedUser.from(jwt).userId();
        var view = rawContentAccessService.inspectRawContent(
            problemSessionId,
            grantId,
            actorUserId,
            authoritiesOf(authentication)
        );
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(AdminRawContentInspectionResponse.from(view));
    }

    @GetMapping(path = "/wrong-answer-reports", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WrongAnswerReportQueueResponse> listWrongAnswerReports(
        @RequestParam(name = "status", required = false) String status,
        @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        var view = wrongAnswerReportService.listReports(status, limit);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(WrongAnswerReportQueueResponse.from(view));
    }

    @PatchMapping(
        path = "/wrong-answer-reports/{reportId}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WrongAnswerReportResponse> reviewWrongAnswerReport(
        @AuthenticationPrincipal Jwt jwt,
        Authentication authentication,
        @PathVariable("reportId") UUID reportId,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody WrongAnswerReportReviewRequest request
    ) {
        UUID actorUserId = AuthenticatedUser.from(jwt).userId();
        if (request == null) {
            throw validationFailed("Wrong-answer report review request body is required");
        }
        if (Boolean.TRUE.equals(request.trainingEligible())) {
            throw validationFailed("Wrong-answer report review requests are never training eligible (ADR-006)");
        }
        String actorRole = AdminRedactionPolicy.extractActorRole(authoritiesOf(authentication));
        var view = wrongAnswerReportService.reviewReport(
            reportId,
            request.status(),
            request.resolutionCode(),
            request.resolutionNote(),
            request.reason(),
            request.linkedRemediationAuditId(),
            request.expectedVersion(),
            idempotencyKey,
            actorUserId,
            actorRole
        );
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(WrongAnswerReportResponse.from(view));
    }

    @PostMapping(
        path = "/remediations/billing-reconcile",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminRemediationResultResponse> reconcileBilling(
        @AuthenticationPrincipal Jwt jwt,
        Authentication authentication,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody BillingReconcileRemediationRequest request
    ) {
        UUID actorUserId = AuthenticatedUser.from(jwt).userId();
        if (request == null) {
            throw validationFailed("Billing reconcile remediation request body is required");
        }
        if (Boolean.TRUE.equals(request.trainingEligible())) {
            throw validationFailed("Remediation requests are never training eligible (ADR-006)");
        }
        String actorRole = AdminRedactionPolicy.extractActorRole(authoritiesOf(authentication));
        var view = remediationService.executeBillingReconcile(
            request.userId(),
            request.transactionId(),
            request.reason(),
            idempotencyKey,
            actorUserId,
            actorRole
        );
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(AdminRemediationResultResponse.from(view));
    }

    @PostMapping(
        path = "/remediations/quota-sync",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminRemediationResultResponse> syncQuota(
        @AuthenticationPrincipal Jwt jwt,
        Authentication authentication,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody QuotaSyncRemediationRequest request
    ) {
        UUID actorUserId = AuthenticatedUser.from(jwt).userId();
        if (request == null) {
            throw validationFailed("Quota sync remediation request body is required");
        }
        if (Boolean.TRUE.equals(request.trainingEligible())) {
            throw validationFailed("Remediation requests are never training eligible (ADR-006)");
        }
        String actorRole = AdminRedactionPolicy.extractActorRole(authoritiesOf(authentication));
        var view = remediationService.executeQuotaSync(
            request.userId(),
            request.reason(),
            idempotencyKey,
            actorUserId,
            actorRole
        );
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(AdminRemediationResultResponse.from(view));
    }

    @PostMapping(
        path = "/remediations/kill-switch",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminRemediationResultResponse> triggerKillSwitch(
        @AuthenticationPrincipal Jwt jwt,
        Authentication authentication,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody KillSwitchRemediationRequest request
    ) {
        UUID actorUserId = AuthenticatedUser.from(jwt).userId();
        if (request == null) {
            throw validationFailed("Kill-switch remediation request body is required");
        }
        if (Boolean.TRUE.equals(request.trainingEligible())) {
            throw validationFailed("Remediation requests are never training eligible (ADR-006)");
        }
        String actorRole = AdminRedactionPolicy.extractActorRole(authoritiesOf(authentication));
        var view = remediationService.executeKillSwitch(
            request.flagKey(),
            request.active(),
            request.reason(),
            request.expectedVersion(),
            idempotencyKey,
            actorUserId,
            actorRole
        );
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(AdminRemediationResultResponse.from(view));
    }

    @GetMapping(path = "/audit", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminSupportAuditListResponse> listAuditLogs(
        @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        var view = queryService.listAuditLogs(limit);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(AdminSupportAuditListResponse.from(view));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetailsResponse> handleAccessDenied(AccessDeniedException exception) {
        ProblemDetailsResponse response = new ProblemDetailsResponse(
            "https://errors.verified-ai-learning.example/admin-support-forbidden",
            "Administrative role is required for admin and support console operations",
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

    private static Collection<? extends GrantedAuthority> authoritiesOf(Authentication authentication) {
        return authentication == null ? List.of() : authentication.getAuthorities();
    }

    private static ApiProblemException validationFailed(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.ADMIN_VALIDATION_FAILED,
            message,
            false,
            "FIX_ADMIN_REQUEST"
        );
    }
}
