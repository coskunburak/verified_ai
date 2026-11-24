package com.verifiedai.admin.application;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.admin.domain.AdminAuditAction;
import com.verifiedai.admin.domain.AdminAuditTargetType;
import com.verifiedai.admin.domain.AdminRedactionPolicy;
import com.verifiedai.admin.infrastructure.AdminSupportProperties;
import com.verifiedai.admin.infrastructure.AdminSupportReadJdbcRepository;
import com.verifiedai.admin.infrastructure.AdminSupportReadJdbcRepository.EntitlementSnapshotRow;
import com.verifiedai.admin.infrastructure.AdminSupportReadJdbcRepository.KillSwitchStateRow;
import com.verifiedai.admin.infrastructure.AdminSupportWriteJdbcRepository;
import com.verifiedai.admin.infrastructure.AdminSupportWriteJdbcRepository.StoredAuditLog;
import com.verifiedai.billing.application.AppleBillingApplicationService;
import com.verifiedai.billing.application.ApplePurchaseEvidenceResult;
import com.verifiedai.billing.application.EffectiveEntitlementAccess;
import com.verifiedai.billing.application.EntitlementApplicationService;
import com.verifiedai.billing.application.QuotaLedgerService;
import com.verifiedai.billing.application.QuotaSummary;
import com.verifiedai.featureflags.application.FeatureFlagDefinitionView;
import com.verifiedai.featureflags.application.FeatureFlagGovernanceService;
import com.verifiedai.featureflags.application.FeatureFlagGovernanceService.FeatureFlagListView;
import com.verifiedai.featureflags.application.ToggleKillSwitchCommand;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Safe domain-command remediation service (INV-ADM-003 & INV-ADM-007).
 * Delegates strictly to owning-module @NamedInterface("application") services rather than
 * mutating foreign tables directly, with advisory-locked idempotency and immutable audit trail.
 */
@Service
public class AdminRemediationService {

    public static final String REMEDIATION_SCHEMA_VERSION = "admin-remediation-result-v1";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final AppleBillingApplicationService appleBillingApplicationService;
    private final EntitlementApplicationService entitlementApplicationService;
    private final QuotaLedgerService quotaLedgerService;
    private final FeatureFlagGovernanceService featureFlagGovernanceService;
    private final AdminSupportReadJdbcRepository readRepository;
    private final AdminSupportWriteJdbcRepository writeRepository;
    private final AdminSupportProperties properties;
    private final AdminSupportMetrics metrics;
    private final Clock clock;

    public AdminRemediationService(
        AppleBillingApplicationService appleBillingApplicationService,
        EntitlementApplicationService entitlementApplicationService,
        QuotaLedgerService quotaLedgerService,
        FeatureFlagGovernanceService featureFlagGovernanceService,
        AdminSupportReadJdbcRepository readRepository,
        AdminSupportWriteJdbcRepository writeRepository,
        AdminSupportProperties properties,
        AdminSupportMetrics metrics,
        Clock clock
    ) {
        this.appleBillingApplicationService = Objects.requireNonNull(
            appleBillingApplicationService,
            "appleBillingApplicationService must not be null"
        );
        this.entitlementApplicationService = Objects.requireNonNull(
            entitlementApplicationService,
            "entitlementApplicationService must not be null"
        );
        this.quotaLedgerService = Objects.requireNonNull(
            quotaLedgerService,
            "quotaLedgerService must not be null"
        );
        this.featureFlagGovernanceService = Objects.requireNonNull(
            featureFlagGovernanceService,
            "featureFlagGovernanceService must not be null"
        );
        this.readRepository = Objects.requireNonNull(readRepository, "readRepository must not be null");
        this.writeRepository = Objects.requireNonNull(writeRepository, "writeRepository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public AdminRemediationResultView executeBillingReconcile(
        UUID userId,
        String rawTransactionId,
        String rawReason,
        String rawIdempotencyKey,
        UUID actorUserId,
        String actorRole
    ) {
        requireRemediationEnabled();
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        if (userId == null) {
            throw validationFailed("userId is required");
        }
        if (rawTransactionId == null || rawTransactionId.isBlank()) {
            throw validationFailed("transactionId is required");
        }
        String transactionId = rawTransactionId.trim();
        if (transactionId.length() > 128) {
            throw validationFailed("transactionId exceeds 128 characters");
        }
        AdminRedactionPolicy.assertNoSensitivePatterns(transactionId, "transactionId");
        String reason = AdminRedactionPolicy.validateStandardReason(rawReason);
        String idempotencyKey = AdminRedactionPolicy.validateIdempotencyKey(rawIdempotencyKey);

        String payloadHash = AdminRedactionPolicy.sha256Hex(
            "BILLING_RECONCILE|" + userId + "|" + transactionId + "|" + reason
        );

        writeRepository.acquireAdvisoryLock("adm-rem-idem:" + idempotencyKey);
        Optional<StoredAuditLog> existingAudit = writeRepository.findAuditByIdempotencyKey(idempotencyKey);
        if (existingAudit.isPresent()) {
            StoredAuditLog audit = existingAudit.get();
            if (audit.action() != AdminAuditAction.BILLING_RECONCILE_EXECUTED
                || !userId.toString().equals(audit.targetId())
                || !payloadHash.equals(audit.payloadHash())) {
                metrics.recordRemediation("billing_reconcile", "idempotency_conflict");
                throw idempotencyConflict();
            }
            metrics.recordRemediation("billing_reconcile", "replayed");
            return new AdminRemediationResultView(
                REMEDIATION_SCHEMA_VERSION,
                audit.id(),
                audit.action().name(),
                audit.targetType().name(),
                audit.targetId(),
                true,
                "REPLAYED",
                Map.of("userId", userId.toString(), "transactionId", transactionId),
                audit.occurredAt(),
                0,
                false
            );
        }

        if (readRepository.findUserAccount(userId).isEmpty()) {
            throw targetNotFound("User account not found: " + userId);
        }

        Optional<EntitlementSnapshotRow> beforeEntitlement = readRepository.findUserEntitlement(userId);
        ApplePurchaseEvidenceResult result = appleBillingApplicationService.reconcileFromTransaction(
            userId,
            transactionId
        );
        Optional<EntitlementSnapshotRow> afterEntitlement = readRepository.findUserEntitlement(userId);

        Map<String, Object> beforeMap = entitlementAuditMap(beforeEntitlement.orElse(null));
        Map<String, Object> afterMap = new LinkedHashMap<>(entitlementAuditMap(afterEntitlement.orElse(null)));
        if (result != null) {
            afterMap.put("reconciledTransactionId", result.transactionId());
            afterMap.put("originalTransactionId", result.originalTransactionId());
            afterMap.put("billingDuplicate", result.duplicate());
        }

        Instant now = clock.instant();
        UUID auditId = UUID.randomUUID();
        writeRepository.insertAuditLog(new StoredAuditLog(
            auditId,
            AdminAuditAction.BILLING_RECONCILE_EXECUTED,
            AdminAuditTargetType.BILLING,
            userId.toString(),
            actorUserId,
            normalizeRole(actorRole),
            idempotencyKey,
            payloadHash,
            reason,
            toJsonObject(beforeMap),
            toJsonObject(afterMap),
            normalizeCorrelationId(),
            false,
            now
        ));
        metrics.recordRemediation("billing_reconcile", "applied");
        return new AdminRemediationResultView(
            REMEDIATION_SCHEMA_VERSION,
            auditId,
            AdminAuditAction.BILLING_RECONCILE_EXECUTED.name(),
            AdminAuditTargetType.BILLING.name(),
            userId.toString(),
            false,
            "APPLIED",
            afterMap,
            now,
            0,
            false
        );
    }

    @Transactional
    public AdminRemediationResultView executeQuotaSync(
        UUID userId,
        String rawReason,
        String rawIdempotencyKey,
        UUID actorUserId,
        String actorRole
    ) {
        requireRemediationEnabled();
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        if (userId == null) {
            throw validationFailed("userId is required");
        }
        String reason = AdminRedactionPolicy.validateStandardReason(rawReason);
        String idempotencyKey = AdminRedactionPolicy.validateIdempotencyKey(rawIdempotencyKey);

        String payloadHash = AdminRedactionPolicy.sha256Hex("QUOTA_SYNC|" + userId + "|" + reason);

        writeRepository.acquireAdvisoryLock("adm-rem-idem:" + idempotencyKey);
        Optional<StoredAuditLog> existingAudit = writeRepository.findAuditByIdempotencyKey(idempotencyKey);
        if (existingAudit.isPresent()) {
            StoredAuditLog audit = existingAudit.get();
            if (audit.action() != AdminAuditAction.QUOTA_SYNC_EXECUTED
                || !userId.toString().equals(audit.targetId())
                || !payloadHash.equals(audit.payloadHash())) {
                metrics.recordRemediation("quota_sync", "idempotency_conflict");
                throw idempotencyConflict();
            }
            metrics.recordRemediation("quota_sync", "replayed");
            return new AdminRemediationResultView(
                REMEDIATION_SCHEMA_VERSION,
                audit.id(),
                audit.action().name(),
                audit.targetType().name(),
                audit.targetId(),
                true,
                "REPLAYED",
                Map.of("userId", userId.toString()),
                audit.occurredAt(),
                0,
                false
            );
        }

        if (readRepository.findUserAccount(userId).isEmpty()) {
            throw targetNotFound("User account not found: " + userId);
        }

        var beforeLedgers = readRepository.findQuotaLedgers(userId, 1);
        EffectiveEntitlementAccess access = entitlementApplicationService.resolveAccess(userId);
        QuotaSummary summary = quotaLedgerService.getOrInitializeSummary(userId, access);

        Map<String, Object> beforeMap = new LinkedHashMap<>();
        if (beforeLedgers.isEmpty()) {
            beforeMap.put("ledgerPresent", false);
        } else {
            var ledger = beforeLedgers.getFirst();
            beforeMap.put("ledgerPresent", true);
            beforeMap.put("periodKey", ledger.periodKey());
            beforeMap.put("tierCode", ledger.tierCode());
            beforeMap.put("aiVerificationsUsed", ledger.aiVerificationsUsed());
            beforeMap.put("aiVerificationsLimit", ledger.aiVerificationsLimit());
        }

        Map<String, Object> afterMap = new LinkedHashMap<>();
        afterMap.put("effectiveTier", access.effectiveTier());
        afterMap.put("packageCode", access.packageCode());
        afterMap.put("paidAccessActive", access.paidAccessActive());
        afterMap.put("periodKey", summary.periodKey());
        afterMap.put("aiVerificationsUsed", summary.aiVerificationsUsed());
        afterMap.put("aiVerificationsLimit", summary.aiVerificationsLimit());
        afterMap.put("remainingAiChecks", summary.remainingAiChecks());
        afterMap.put("costCircuitBreakerTripped", summary.costCircuitBreakerTripped());

        Instant now = clock.instant();
        UUID auditId = UUID.randomUUID();
        writeRepository.insertAuditLog(new StoredAuditLog(
            auditId,
            AdminAuditAction.QUOTA_SYNC_EXECUTED,
            AdminAuditTargetType.USER,
            userId.toString(),
            actorUserId,
            normalizeRole(actorRole),
            idempotencyKey,
            payloadHash,
            reason,
            toJsonObject(beforeMap),
            toJsonObject(afterMap),
            normalizeCorrelationId(),
            false,
            now
        ));
        metrics.recordRemediation("quota_sync", "applied");
        return new AdminRemediationResultView(
            REMEDIATION_SCHEMA_VERSION,
            auditId,
            AdminAuditAction.QUOTA_SYNC_EXECUTED.name(),
            AdminAuditTargetType.USER.name(),
            userId.toString(),
            false,
            "APPLIED",
            afterMap,
            now,
            0,
            false
        );
    }

    @Transactional
    public AdminRemediationResultView executeKillSwitch(
        String rawFlagKey,
        Boolean active,
        String rawReason,
        Long expectedVersion,
        String rawIdempotencyKey,
        UUID actorUserId,
        String actorRole
    ) {
        requireRemediationEnabled();
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        if (rawFlagKey == null || rawFlagKey.isBlank()) {
            throw validationFailed("flagKey is required");
        }
        if (active == null || expectedVersion == null || expectedVersion < 0) {
            throw validationFailed("active and non-negative expectedVersion are required");
        }
        String flagKey = rawFlagKey.trim();
        AdminRedactionPolicy.assertNoSensitivePatterns(flagKey, "flagKey");
        String reason = AdminRedactionPolicy.validateStandardReason(rawReason);
        String idempotencyKey = AdminRedactionPolicy.validateIdempotencyKey(rawIdempotencyKey);

        String payloadHash = AdminRedactionPolicy.sha256Hex(
            "KILL_SWITCH|" + flagKey + "|" + active + "|" + reason + "|" + expectedVersion
        );

        writeRepository.acquireAdvisoryLock("adm-rem-idem:" + idempotencyKey);
        Optional<StoredAuditLog> existingAudit = writeRepository.findAuditByIdempotencyKey(idempotencyKey);
        if (existingAudit.isPresent()) {
            StoredAuditLog audit = existingAudit.get();
            if (audit.action() != AdminAuditAction.KILL_SWITCH_LINKED_EXECUTED
                || !flagKey.equals(audit.targetId())
                || !payloadHash.equals(audit.payloadHash())) {
                metrics.recordRemediation("kill_switch", "idempotency_conflict");
                throw idempotencyConflict();
            }
            metrics.recordRemediation("kill_switch", "replayed");
            return new AdminRemediationResultView(
                REMEDIATION_SCHEMA_VERSION,
                audit.id(),
                audit.action().name(),
                audit.targetType().name(),
                audit.targetId(),
                true,
                "REPLAYED",
                Map.of("flagKey", flagKey, "killSwitchActive", active),
                audit.occurredAt(),
                0,
                false
            );
        }

        Optional<KillSwitchStateRow> beforeFlag = readRepository.findFeatureFlagSummary(flagKey);
        FeatureFlagListView listView = featureFlagGovernanceService.toggleKillSwitch(
            flagKey,
            new ToggleKillSwitchCommand(active, reason, expectedVersion, idempotencyKey),
            actorUserId
        );

        FeatureFlagDefinitionView updatedFlag = listView.flags().stream()
            .filter(f -> flagKey.equals(f.flagKey()))
            .findFirst()
            .orElse(null);

        Map<String, Object> beforeMap = new LinkedHashMap<>();
        beforeFlag.ifPresent(b -> {
            beforeMap.put("enabled", b.enabled());
            beforeMap.put("killSwitchActive", b.killSwitchActive());
            beforeMap.put("version", b.version());
        });

        Map<String, Object> afterMap = new LinkedHashMap<>();
        afterMap.put("flagKey", flagKey);
        if (updatedFlag != null) {
            afterMap.put("enabled", updatedFlag.enabled());
            afterMap.put("killSwitchActive", updatedFlag.killSwitchActive());
            afterMap.put("version", updatedFlag.version());
        } else {
            afterMap.put("killSwitchActive", active);
        }

        Instant now = clock.instant();
        UUID auditId = UUID.randomUUID();
        writeRepository.insertAuditLog(new StoredAuditLog(
            auditId,
            AdminAuditAction.KILL_SWITCH_LINKED_EXECUTED,
            AdminAuditTargetType.FEATURE_FLAG,
            flagKey,
            actorUserId,
            normalizeRole(actorRole),
            idempotencyKey,
            payloadHash,
            reason,
            toJsonObject(beforeMap),
            toJsonObject(afterMap),
            normalizeCorrelationId(),
            false,
            now
        ));
        metrics.recordRemediation("kill_switch", "applied");
        return new AdminRemediationResultView(
            REMEDIATION_SCHEMA_VERSION,
            auditId,
            AdminAuditAction.KILL_SWITCH_LINKED_EXECUTED.name(),
            AdminAuditTargetType.FEATURE_FLAG.name(),
            flagKey,
            false,
            "APPLIED",
            afterMap,
            now,
            0,
            false
        );
    }

    private void requireRemediationEnabled() {
        if (!properties.remediationEnabled()) {
            throw new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.ADMIN_REMEDIATION_DISABLED,
                "Admin safe remediation endpoints are disabled",
                true,
                "RETRY_LATER"
            );
        }
        if (!properties.consoleEnabled()) {
            throw new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.ADMIN_CONSOLE_DISABLED,
                "Admin support console is disabled",
                true,
                "RETRY_LATER"
            );
        }
    }

    private static Map<String, Object> entitlementAuditMap(EntitlementSnapshotRow row) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (row == null) {
            map.put("entitlementPresent", false);
            return map;
        }
        map.put("entitlementPresent", true);
        map.put("tier", row.tier());
        map.put("packageCode", row.packageCode());
        map.put("status", row.status());
        map.put("version", row.version());
        return map;
    }

    private static String normalizeRole(String actorRole) {
        if (actorRole == null || actorRole.isBlank()) {
            return "ROLE_ADMIN";
        }
        String trimmed = actorRole.trim();
        return trimmed.length() > 48 ? trimmed.substring(0, 48) : trimmed;
    }

    private static String normalizeCorrelationId() {
        String current = CorrelationIds.current();
        if (current == null || current.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return current.length() > 64 ? current.substring(0, 64) : current;
    }

    private static String toJsonObject(Map<String, Object> values) {
        try {
            return JSON_MAPPER.writeValueAsString(new LinkedHashMap<>(values));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize remediation audit JSON", exception);
        }
    }

    private static ApiProblemException validationFailed(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.ADMIN_VALIDATION_FAILED,
            message,
            false,
            "FIX_REMEDIATION_REQUEST"
        );
    }

    private static ApiProblemException targetNotFound(String message) {
        return new ApiProblemException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.ADMIN_TARGET_NOT_FOUND,
            message,
            false,
            "CHECK_USER_ID"
        );
    }

    private static ApiProblemException idempotencyConflict() {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            ApiErrorCode.ADMIN_IDEMPOTENCY_CONFLICT,
            "Idempotency-Key already used with a different remediation target or payload",
            false,
            "USE_NEW_IDEMPOTENCY_KEY"
        );
    }
}
