package com.verifiedai.admin.application;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.admin.domain.AdminAuditAction;
import com.verifiedai.admin.domain.AdminAuditTargetType;
import com.verifiedai.admin.domain.AdminLookupType;
import com.verifiedai.admin.domain.AdminRedactionPolicy;
import com.verifiedai.admin.infrastructure.AdminSupportProperties;
import com.verifiedai.admin.infrastructure.AdminSupportReadJdbcRepository;
import com.verifiedai.admin.infrastructure.AdminSupportReadJdbcRepository.AiOpsSnapshot;
import com.verifiedai.admin.infrastructure.AdminSupportReadJdbcRepository.EntitlementSnapshotRow;
import com.verifiedai.admin.infrastructure.AdminSupportReadJdbcRepository.ProblemSessionRow;
import com.verifiedai.admin.infrastructure.AdminSupportReadJdbcRepository.QuotaLedgerRow;
import com.verifiedai.admin.infrastructure.AdminSupportReadJdbcRepository.UserAccountRow;
import com.verifiedai.admin.infrastructure.AdminSupportWriteJdbcRepository;
import com.verifiedai.admin.infrastructure.AdminSupportWriteJdbcRepository.StoredAuditLog;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-heavy administrative query service providing redacted user lookup, problem trace,
 * billing support inspection, AI ops overview, and audit log history with immutable read auditing.
 */
@Service
public class AdminSupportQueryService {

    public static final String USER_LOOKUP_SCHEMA_VERSION = "admin-user-lookup-v1";
    public static final String PROBLEM_TRACE_SCHEMA_VERSION = "admin-problem-trace-v1";
    public static final String BILLING_SUPPORT_SCHEMA_VERSION = "admin-billing-support-v1";
    public static final String AI_OPS_SCHEMA_VERSION = "admin-ai-ops-overview-v1";
    public static final String AUDIT_LIST_SCHEMA_VERSION = "admin-support-audit-list-v1";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final AdminSupportReadJdbcRepository readRepository;
    private final AdminSupportWriteJdbcRepository writeRepository;
    private final AdminSupportProperties properties;
    private final AdminSupportMetrics metrics;
    private final Clock clock;

    public AdminSupportQueryService(
        AdminSupportReadJdbcRepository readRepository,
        AdminSupportWriteJdbcRepository writeRepository,
        AdminSupportProperties properties,
        AdminSupportMetrics metrics,
        Clock clock
    ) {
        this.readRepository = Objects.requireNonNull(readRepository, "readRepository must not be null");
        this.writeRepository = Objects.requireNonNull(writeRepository, "writeRepository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public AdminUserLookupView lookupUser(
        String rawLookupType,
        String rawValue,
        UUID actorUserId,
        String actorRole
    ) {
        requireConsoleEnabled();
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        AdminLookupType lookupType = parseLookupType(rawLookupType);
        if (rawValue == null || rawValue.isBlank()) {
            throw validationFailed("Lookup value must not be blank");
        }
        String value = rawValue.trim();
        if (value.length() > 128) {
            throw validationFailed("Lookup value exceeds 128 characters");
        }
        AdminRedactionPolicy.assertNoSensitivePatterns(value, "value");

        UUID userId = readRepository.resolveUserId(lookupType, value)
            .orElseThrow(() -> targetNotFound("No user matched " + lookupType + " lookup"));
        UserAccountRow account = readRepository.findUserAccount(userId)
            .orElseThrow(() -> targetNotFound("User account not found: " + userId));

        int limit = properties.maxLookupLimit();
        List<AdminUserLookupView.SessionSummaryView> sessions = readRepository.findRecentSessions(userId, limit).stream()
            .map(s -> new AdminUserLookupView.SessionSummaryView(
                s.sessionId(),
                s.status(),
                s.createdAt(),
                s.lastSeenAt(),
                s.expiresAt(),
                s.revokedAt(),
                s.revocationReason()
            ))
            .toList();

        AdminUserLookupView.EntitlementSummaryView entitlement = readRepository.findUserEntitlement(userId)
            .map(AdminSupportQueryService::toEntitlementSummary)
            .orElse(null);

        AdminUserLookupView.QuotaSummaryView activeQuota = readRepository.findQuotaLedgers(userId, 1).stream()
            .findFirst()
            .map(AdminSupportQueryService::toQuotaSummary)
            .orElse(null);

        int reportCount = readRepository.countWrongAnswerReportsForUser(userId);
        Instant now = clock.instant();
        UUID auditId = UUID.randomUUID();
        writeRepository.insertAuditLog(new StoredAuditLog(
            auditId,
            AdminAuditAction.USER_LOOKUP_READ,
            AdminAuditTargetType.USER,
            userId.toString(),
            actorUserId,
            normalizeRole(actorRole),
            null,
            null,
            "Admin user lookup by " + lookupType.name(),
            "{}",
            toJsonObject(Map.of("lookupType", lookupType.name(), "sessionCount", sessions.size())),
            normalizeCorrelationId(),
            false,
            now
        ));
        metrics.recordQuery("user_lookup");

        return new AdminUserLookupView(
            USER_LOOKUP_SCHEMA_VERSION,
            lookupType.name(),
            account.userId(),
            account.status(),
            account.createdAt(),
            account.updatedAt(),
            account.deletionRequestedAt(),
            account.deletedAt(),
            sessions,
            entitlement,
            activeQuota,
            reportCount,
            true,
            auditId,
            now,
            0,
            false
        );
    }

    @Transactional
    public AdminBillingSupportView getBillingSupport(UUID userId, UUID actorUserId, String actorRole) {
        requireConsoleEnabled();
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");

        UserAccountRow account = readRepository.findUserAccount(userId)
            .orElseThrow(() -> targetNotFound("User account not found: " + userId));

        int limit = properties.maxLookupLimit();
        AdminUserLookupView.EntitlementSummaryView entitlement = readRepository.findUserEntitlement(userId)
            .map(AdminSupportQueryService::toEntitlementSummary)
            .orElse(null);

        List<AdminBillingSupportView.SubscriptionView> subscriptions = readRepository
            .findAppStoreSubscriptions(userId, limit)
            .stream()
            .map(s -> new AdminBillingSupportView.SubscriptionView(
                s.subscriptionId(),
                s.originalTransactionId(),
                s.currentTransactionId(),
                s.productId(),
                s.environment(),
                s.status(),
                s.autoRenewStatus(),
                s.expiresAt(),
                s.gracePeriodExpiresAt(),
                s.revokedAt(),
                s.lastReconciledAt(),
                s.updatedAt()
            ))
            .toList();

        List<AdminBillingSupportView.TransactionView> transactions = readRepository
            .findAppStoreTransactions(userId, limit)
            .stream()
            .map(t -> new AdminBillingSupportView.TransactionView(
                t.id(),
                t.transactionId(),
                t.originalTransactionId(),
                t.productId(),
                t.environment(),
                t.purchaseDate(),
                t.expiresDate(),
                t.revocationDate(),
                t.transactionReason(),
                t.ownershipType(),
                t.accessRevoked(),
                t.offerFamily(),
                t.createdAt()
            ))
            .toList();

        List<AdminUserLookupView.QuotaSummaryView> quotaLedgers = readRepository
            .findQuotaLedgers(userId, limit)
            .stream()
            .map(AdminSupportQueryService::toQuotaSummary)
            .toList();

        List<AdminBillingSupportView.BillingTransitionView> transitions = readRepository
            .findBillingTransitions(userId, limit)
            .stream()
            .map(b -> new AdminBillingSupportView.BillingTransitionView(
                b.id(),
                b.fromPackage(),
                b.toPackage(),
                b.fromStatus(),
                b.toStatus(),
                b.reason(),
                b.occurredAt(),
                b.recordedAt()
            ))
            .toList();

        Instant now = clock.instant();
        UUID auditId = UUID.randomUUID();
        writeRepository.insertAuditLog(new StoredAuditLog(
            auditId,
            AdminAuditAction.BILLING_SUPPORT_READ,
            AdminAuditTargetType.BILLING,
            userId.toString(),
            actorUserId,
            normalizeRole(actorRole),
            null,
            null,
            "Admin billing support inspection",
            "{}",
            toJsonObject(Map.of(
                "subscriptionCount", subscriptions.size(),
                "transactionCount", transactions.size(),
                "ledgerCount", quotaLedgers.size()
            )),
            normalizeCorrelationId(),
            false,
            now
        ));
        metrics.recordQuery("billing_support");

        return new AdminBillingSupportView(
            BILLING_SUPPORT_SCHEMA_VERSION,
            account.userId(),
            account.status(),
            entitlement,
            subscriptions,
            transactions,
            quotaLedgers,
            transitions,
            true,
            auditId,
            now,
            0,
            false
        );
    }

    @Transactional
    public AdminProblemTraceView getProblemTrace(
        UUID problemSessionId,
        String traceId,
        String correlationId,
        UUID actorUserId,
        String actorRole
    ) {
        requireConsoleEnabled();
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        if (problemSessionId == null && (traceId == null || traceId.isBlank())
            && (correlationId == null || correlationId.isBlank())) {
            throw validationFailed("One of problemSessionId, traceId, or correlationId is required");
        }
        AdminRedactionPolicy.assertNoSensitivePatterns(traceId, "traceId");
        AdminRedactionPolicy.assertNoSensitivePatterns(correlationId, "correlationId");

        UUID resolvedSessionId = readRepository.resolveProblemSessionId(problemSessionId, traceId, correlationId)
            .orElseThrow(() -> targetNotFound("Problem trace not found for supplied identifier"));

        ProblemSessionRow session = readRepository.findProblemSession(resolvedSessionId)
            .orElseThrow(() -> targetNotFound("Problem session not found: " + resolvedSessionId));

        int limit = properties.maxTraceRecords();
        List<AdminProblemTraceView.RedactedAssetView> assets = readRepository
            .findRedactedProblemAssets(resolvedSessionId, limit)
            .stream()
            .map(a -> new AdminProblemTraceView.RedactedAssetView(
                a.assetId(),
                a.sourceType(),
                a.assetKind(),
                a.status(),
                a.contentType(),
                a.sizeBytes(),
                a.imageWidth(),
                a.imageHeight(),
                a.pageCount(),
                a.retentionClass(),
                a.availableAt(),
                a.deletedAt(),
                a.createdAt()
            ))
            .toList();

        List<AdminProblemTraceView.RedactedParseView> parses = readRepository
            .findRedactedProblemParses(resolvedSessionId, limit)
            .stream()
            .map(p -> new AdminProblemTraceView.RedactedParseView(
                p.parseId(),
                p.parseJobId(),
                p.revision(),
                p.source(),
                p.supportStatus(),
                p.unsupportedReason(),
                p.reviewRequired(),
                p.schemaVersion(),
                p.provider(),
                p.model(),
                p.routePolicyVersion(),
                p.promptId(),
                p.promptVersion(),
                p.fallbackUsed(),
                p.inputTokens(),
                p.outputTokens(),
                p.providerLatencyMs(),
                p.totalLatencyMs(),
                p.estimatedCostMicros(),
                p.createdAt()
            ))
            .toList();

        List<AdminProblemTraceView.AiUsageProvenanceView> aiUsage = readRepository
            .findAiUsageRecords(resolvedSessionId, limit)
            .stream()
            .map(u -> new AdminProblemTraceView.AiUsageProvenanceView(
                u.usageRecordId(),
                u.operationId(),
                u.capability(),
                u.routePolicyVersion(),
                u.routeId(),
                u.provider(),
                u.model(),
                u.promptId(),
                u.promptVersion(),
                u.schemaVersion(),
                u.status(),
                AdminRedactionPolicy.redactString(u.failureClass()),
                u.retryable(),
                u.attemptCount(),
                u.fallbackUsed(),
                u.fallbackChain(),
                AdminRedactionPolicy.redactString(u.escalationReason()),
                u.cacheStatus(),
                u.inputTokenCount(),
                u.outputTokenCount(),
                u.estimatedCostMicros(),
                u.providerLatencyMs(),
                u.gatewayLatencyMs(),
                u.correlationId(),
                u.traceId(),
                u.createdAt(),
                u.completedAt()
            ))
            .toList();

        List<AdminProblemTraceView.VerificationRunSummaryView> verificationRuns = readRepository
            .findRedactedVerificationRuns(resolvedSessionId, limit)
            .stream()
            .map(v -> new AdminProblemTraceView.VerificationRunSummaryView(
                v.runId(),
                v.solutionId(),
                v.canonicalProblemId(),
                v.problemParseId(),
                v.problemParseRevision(),
                v.canonicalRevision(),
                v.policyVersion(),
                v.capabilityVersion(),
                v.correlationId(),
                v.status(),
                v.createdAt()
            ))
            .toList();

        List<AdminProblemTraceView.MistakeDisputeSummaryView> mistakeDisputes = readRepository
            .findMistakeDisputesForUser(session.userId(), limit)
            .stream()
            .map(d -> new AdminProblemTraceView.MistakeDisputeSummaryView(
                d.id(),
                d.attemptId(),
                d.classificationId(),
                d.replacementAttemptId(),
                d.stepIndex(),
                d.disputeType(),
                d.policyVersion(),
                d.createdAt()
            ))
            .toList();

        Instant now = clock.instant();
        UUID auditId = UUID.randomUUID();
        writeRepository.insertAuditLog(new StoredAuditLog(
            auditId,
            AdminAuditAction.PROBLEM_TRACE_READ,
            AdminAuditTargetType.PROBLEM_SESSION,
            resolvedSessionId.toString(),
            actorUserId,
            normalizeRole(actorRole),
            null,
            null,
            "Admin redacted problem trace inspection",
            "{}",
            toJsonObject(Map.of(
                "assetCount", assets.size(),
                "parseCount", parses.size(),
                "aiUsageCount", aiUsage.size(),
                "verificationRunCount", verificationRuns.size(),
                "rawContentRedacted", true
            )),
            normalizeCorrelationId(),
            false,
            now
        ));
        metrics.recordQuery("problem_trace");

        return new AdminProblemTraceView(
            PROBLEM_TRACE_SCHEMA_VERSION,
            session.id(),
            session.userId(),
            session.status(),
            session.inputMode(),
            session.currentParseId(),
            session.problemId(),
            session.solveJobId(),
            session.createdAt(),
            session.updatedAt(),
            session.completedAt(),
            true,
            assets,
            parses,
            aiUsage,
            verificationRuns,
            mistakeDisputes,
            auditId,
            now,
            0,
            false
        );
    }

    @Transactional
    public AdminAiOperationsView getAiOpsOverview(
        LocalDate fromDate,
        LocalDate toDate,
        UUID actorUserId,
        String actorRole
    ) {
        requireConsoleEnabled();
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        LocalDate effectiveTo = toDate == null ? today : toDate;
        LocalDate effectiveFrom = fromDate == null ? effectiveTo.minusDays(7) : fromDate;

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw validationFailed("'from' date must not be after 'to' date");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(effectiveFrom, effectiveTo) > 90) {
            throw validationFailed("AI operations window must not exceed 90 days");
        }

        Instant fromTs = effectiveFrom.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toTs = effectiveTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        AiOpsSnapshot snapshot = readRepository.loadAiOpsOverview(fromTs, toTs, properties.maxTraceRecords());

        List<AdminAiOperationsView.ProviderHealthView> providerHealth = snapshot.providerHealth().stream()
            .map(p -> new AdminAiOperationsView.ProviderHealthView(
                p.provider(),
                p.model(),
                p.totalCalls(),
                p.successCalls(),
                p.failureCalls(),
                p.totalCostMicros(),
                p.totalCostMicros() / 1_000_000.0,
                p.avgLatencyMs()
            ))
            .toList();

        long totalCalls = providerHealth.stream().mapToLong(AdminAiOperationsView.ProviderHealthView::totalCalls).sum();
        long totalFailures = providerHealth.stream().mapToLong(AdminAiOperationsView.ProviderHealthView::failureCalls).sum();
        long totalCostMicros = providerHealth.stream().mapToLong(AdminAiOperationsView.ProviderHealthView::totalCostMicros).sum();

        List<AdminAiOperationsView.ActiveRouteView> activeRoutes = snapshot.activeRoutes().stream()
            .map(r -> new AdminAiOperationsView.ActiveRouteView(
                r.capability(),
                r.routePolicyVersion(),
                r.routeId(),
                r.provider(),
                r.model(),
                r.promptId(),
                r.promptVersion(),
                r.schemaVersion()
            ))
            .toList();

        List<AdminAiOperationsView.RedactedFailureSampleView> failureSamples = snapshot.failureSamples().stream()
            .map(f -> new AdminAiOperationsView.RedactedFailureSampleView(
                f.usageRecordId(),
                f.capability(),
                f.provider(),
                f.model(),
                f.status(),
                f.failureClass(),
                f.retryable(),
                f.fallbackUsed(),
                f.correlationId(),
                f.traceId(),
                f.createdAt()
            ))
            .toList();

        List<AdminAiOperationsView.KillSwitchFlagView> killSwitches = snapshot.killSwitchStates().stream()
            .map(k -> new AdminAiOperationsView.KillSwitchFlagView(
                k.flagKey(),
                k.category(),
                k.enabled(),
                k.killSwitchActive(),
                k.rolloutBasisPoints(),
                k.currentValueJson(),
                k.version(),
                k.updatedAt()
            ))
            .toList();

        Instant now = clock.instant();
        UUID auditId = UUID.randomUUID();
        writeRepository.insertAuditLog(new StoredAuditLog(
            auditId,
            AdminAuditAction.AI_OPS_OVERVIEW_READ,
            AdminAuditTargetType.AI_OPS,
            effectiveFrom + ".." + effectiveTo,
            actorUserId,
            normalizeRole(actorRole),
            null,
            null,
            "Admin AI operations overview read",
            "{}",
            toJsonObject(Map.of(
                "totalCalls", totalCalls,
                "totalFailures", totalFailures,
                "totalCostMicros", totalCostMicros
            )),
            normalizeCorrelationId(),
            false,
            now
        ));
        metrics.recordQuery("ai_ops_overview");

        return new AdminAiOperationsView(
            AI_OPS_SCHEMA_VERSION,
            effectiveFrom,
            effectiveTo,
            totalCalls,
            totalFailures,
            totalCostMicros,
            totalCostMicros / 1_000_000.0,
            snapshot.recentFlagAuditCount(),
            providerHealth,
            activeRoutes,
            failureSamples,
            killSwitches,
            true,
            auditId,
            now,
            0,
            false
        );
    }

    @Transactional(readOnly = true)
    public AdminAuditEntryView.AdminAuditListView listAuditLogs(int requestedLimit) {
        requireConsoleEnabled();
        if (requestedLimit < 1 || requestedLimit > 200) {
            throw new ApiProblemException(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.REQUEST_VALIDATION_FAILED,
                "Audit limit must be between 1 and 200",
                false,
                "FIX_LIMIT_PARAM"
            );
        }
        int effectiveLimit = Math.min(requestedLimit, properties.auditHistoryLimit());
        List<AdminAuditEntryView> entries = writeRepository.findRecentAuditLogs(effectiveLimit).stream()
            .map(log -> new AdminAuditEntryView(
                log.id(),
                log.action().name(),
                log.targetType().name(),
                log.targetId(),
                log.actorUserId(),
                log.actorRole(),
                log.idempotencyKey(),
                log.reason(),
                log.beforeStateJson(),
                log.afterStateJson(),
                log.correlationId(),
                log.trainingEligible(),
                log.occurredAt()
            ))
            .toList();
        metrics.recordQuery("audit_list");
        return new AdminAuditEntryView.AdminAuditListView(
            AUDIT_LIST_SCHEMA_VERSION,
            entries.size(),
            entries,
            clock.instant(),
            0,
            false
        );
    }

    private void requireConsoleEnabled() {
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

    private static AdminLookupType parseLookupType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw validationFailed("Lookup type is required");
        }
        try {
            return AdminLookupType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw validationFailed("Unsupported lookup type: " + raw);
        }
    }

    private static AdminUserLookupView.EntitlementSummaryView toEntitlementSummary(EntitlementSnapshotRow row) {
        return new AdminUserLookupView.EntitlementSummaryView(
            row.entitlementId(),
            row.tier(),
            row.packageCode(),
            row.source(),
            row.status(),
            row.effectiveAt(),
            row.expiresAt(),
            row.gracePeriodExpiresAt(),
            row.originalTransactionId(),
            row.environment(),
            row.lastVerifiedAt(),
            row.updatedAt(),
            row.version()
        );
    }

    private static AdminUserLookupView.QuotaSummaryView toQuotaSummary(QuotaLedgerRow row) {
        return new AdminUserLookupView.QuotaSummaryView(
            row.periodKey(),
            row.tierCode(),
            row.aiVerificationsUsed(),
            row.aiVerificationsLimit(),
            row.hybridTwinsUsed(),
            row.hybridTwinsLimit(),
            row.accumulatedAiCostMicros(),
            row.accumulatedAiCostUsd(),
            row.aiCostCapMicros(),
            row.costCircuitBreakerTripped(),
            row.resetsAt(),
            row.updatedAt()
        );
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
            throw new IllegalStateException("Unable to serialize audit JSON", exception);
        }
    }

    private static ApiProblemException validationFailed(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.ADMIN_VALIDATION_FAILED,
            message,
            false,
            "FIX_ADMIN_QUERY"
        );
    }

    private static ApiProblemException targetNotFound(String message) {
        return new ApiProblemException(
            HttpStatus.NOT_FOUND,
            ApiErrorCode.ADMIN_TARGET_NOT_FOUND,
            message,
            false,
            "CHECK_IDENTIFIER"
        );
    }
}
