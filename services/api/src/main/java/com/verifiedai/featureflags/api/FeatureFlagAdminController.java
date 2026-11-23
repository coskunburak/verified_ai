package com.verifiedai.featureflags.api;

import com.verifiedai.featureflags.api.dto.ExperimentUpdateRequest;
import com.verifiedai.featureflags.api.dto.FeatureFlagAuditListResponse;
import com.verifiedai.featureflags.api.dto.FeatureFlagKillSwitchRequest;
import com.verifiedai.featureflags.api.dto.FeatureFlagListResponse;
import com.verifiedai.featureflags.api.dto.FeatureFlagUpdateRequest;
import com.verifiedai.featureflags.application.FeatureFlagGovernanceService;
import com.verifiedai.featureflags.application.ToggleKillSwitchCommand;
import com.verifiedai.featureflags.application.UpdateExperimentCommand;
import com.verifiedai.featureflags.application.UpdateFeatureFlagCommand;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * Administrative governance controller for feature flags, kill switches, experiments, and immutable audit logs.
 * Strictly requires ROLE_ADMIN, Idempotency-Key on mutations, optimistic locking, and Cache-Control: no-store, private.
 */
@RestController
@RequestMapping({"/api/v1/admin/feature-flags", "/v1/admin/feature-flags"})
public class FeatureFlagAdminController {

    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final FeatureFlagGovernanceService governanceService;

    public FeatureFlagAdminController(FeatureFlagGovernanceService governanceService) {
        this.governanceService = Objects.requireNonNull(governanceService, "governanceService must not be null");
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeatureFlagListResponse> listFeatureFlags() {
        var view = governanceService.listAllFlagsAndExperiments();
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(FeatureFlagListResponse.from(view));
    }

    @PatchMapping(
        path = "/{flagKey}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeatureFlagListResponse> updateFlag(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable("flagKey") String flagKey,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody FeatureFlagUpdateRequest request
    ) {
        UUID actorId = AuthenticatedUser.from(jwt).userId();
        if (request == null || request.enabled() == null
            || request.rolloutBasisPoints() == null || request.expectedVersion() == null) {
            throw flagValidationFailed("enabled, rolloutBasisPoints, and expectedVersion are required");
        }
        if (Boolean.TRUE.equals(request.trainingEligible())) {
            throw flagValidationFailed("Feature flag governance requests are never training eligible (ADR-006)");
        }
        var view = governanceService.updateFlag(
            flagKey,
            new UpdateFeatureFlagCommand(
                request.enabled(),
                request.rolloutBasisPoints(),
                request.currentValueJson(),
                request.plannedRemovalAt(),
                request.reason(),
                request.expectedVersion(),
                idempotencyKey
            ),
            actorId
        );
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(FeatureFlagListResponse.from(view));
    }

    @PostMapping(
        path = "/{flagKey}/kill-switch",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeatureFlagListResponse> toggleKillSwitch(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable("flagKey") String flagKey,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody FeatureFlagKillSwitchRequest request
    ) {
        UUID actorId = AuthenticatedUser.from(jwt).userId();
        if (request == null || request.active() == null || request.expectedVersion() == null) {
            throw flagValidationFailed("active and expectedVersion are required");
        }
        if (Boolean.TRUE.equals(request.trainingEligible())) {
            throw flagValidationFailed("Kill-switch governance requests are never training eligible (ADR-006)");
        }
        var view = governanceService.toggleKillSwitch(
            flagKey,
            new ToggleKillSwitchCommand(
                request.active(),
                request.reason(),
                request.expectedVersion(),
                idempotencyKey
            ),
            actorId
        );
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(FeatureFlagListResponse.from(view));
    }

    @GetMapping(path = "/experiments", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeatureFlagListResponse> listExperiments() {
        var view = governanceService.listAllFlagsAndExperiments();
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(FeatureFlagListResponse.from(view));
    }

    @PatchMapping(
        path = "/experiments/{experimentKey}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeatureFlagListResponse> updateExperiment(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable("experimentKey") String experimentKey,
        @RequestHeader(value = IDEMPOTENCY_KEY_HEADER, required = false) String idempotencyKey,
        @RequestBody ExperimentUpdateRequest request
    ) {
        UUID actorId = AuthenticatedUser.from(jwt).userId();
        if (request == null || request.status() == null
            || request.rolloutBasisPoints() == null || request.expectedVersion() == null) {
            throw experimentValidationFailed("status, rolloutBasisPoints, and expectedVersion are required");
        }
        if (Boolean.TRUE.equals(request.trainingEligible())) {
            throw experimentValidationFailed("Experiment governance requests are never training eligible (ADR-006)");
        }
        var view = governanceService.updateExperiment(
            experimentKey,
            new UpdateExperimentCommand(
                request.status(),
                request.rolloutBasisPoints(),
                request.reason(),
                request.expectedVersion(),
                idempotencyKey
            ),
            actorId
        );
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(FeatureFlagListResponse.from(view));
    }

    @GetMapping(path = "/audit", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FeatureFlagAuditListResponse> listAuditLogs(
        @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        var view = governanceService.listAuditLogs(limit);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(FeatureFlagAuditListResponse.from(view));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetailsResponse> handleAccessDenied(AccessDeniedException exception) {
        ProblemDetailsResponse response = new ProblemDetailsResponse(
            "https://errors.verified-ai-learning.example/feature-flag-admin-forbidden",
            "Administrative role is required for feature flag and experiment governance",
            HttpStatus.FORBIDDEN.value(),
            ApiErrorCode.FEATURE_FLAG_ADMIN_FORBIDDEN,
            CorrelationIds.current(),
            Map.of("recoverable", false, "userAction", "USE_ADMIN_ACCOUNT")
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .contentType(MediaType.APPLICATION_PROBLEM_JSON)
            .body(response);
    }

    private static ApiProblemException flagValidationFailed(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.FEATURE_FLAG_VALIDATION_FAILED,
            message,
            false,
            "FIX_FEATURE_FLAG_REQUEST"
        );
    }

    private static ApiProblemException experimentValidationFailed(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.EXPERIMENT_VALIDATION_FAILED,
            message,
            false,
            "FIX_EXPERIMENT_REQUEST"
        );
    }
}
