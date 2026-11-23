package com.verifiedai.featureflags.application;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.verifiedai.featureflags.application.ExperimentDefinitionView.VariantWeightView;
import com.verifiedai.featureflags.domain.DeterministicBucketingPolicy;
import com.verifiedai.featureflags.domain.ExperimentStateMachine;
import com.verifiedai.featureflags.domain.ExperimentStatus;
import com.verifiedai.featureflags.domain.FeatureFlagDefinitionSnapshot;
import com.verifiedai.featureflags.domain.FeatureFlagEvaluationPolicy;
import com.verifiedai.featureflags.domain.FlagAuditAction;
import com.verifiedai.featureflags.infrastructure.FeatureFlagJdbcRepository;
import com.verifiedai.featureflags.infrastructure.FeatureFlagJdbcRepository.StoredAuditLog;
import com.verifiedai.featureflags.infrastructure.FeatureFlagJdbcRepository.StoredExperimentDefinition;
import com.verifiedai.featureflags.infrastructure.FeatureFlagProperties;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrative governance service handling ROLE_ADMIN feature flag updates,
 * immediate kill-switch toggles, experiment state transitions, optimistic locking,
 * advisory-locked idempotency replay/conflict checks, and immutable audit logs.
 */
@Service
public class FeatureFlagGovernanceService {

    public static final String FLAG_LIST_SCHEMA_VERSION = "feature-flag-list-v1";
    public static final String AUDIT_LIST_SCHEMA_VERSION = "feature-flag-audit-list-v1";
    private static final String ADMIN_ACTOR_ROLE = "ROLE_ADMIN";
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final FeatureFlagJdbcRepository repository;
    private final FeatureFlagProperties properties;
    private final FeatureFlagMetrics metrics;
    private final Clock clock;

    public FeatureFlagGovernanceService(
        FeatureFlagJdbcRepository repository,
        FeatureFlagProperties properties,
        FeatureFlagMetrics metrics,
        Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public FeatureFlagListView listAllFlagsAndExperiments() {
        requireFeatureFlagsEnabled();
        return buildFlagListView(clock.instant());
    }

    @Transactional(readOnly = true)
    public FeatureFlagAuditListView listAuditLogs(int requestedLimit) {
        requireFeatureFlagsEnabled();
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
        List<FeatureFlagAuditEntryView> entries = repository.findRecentAuditLogs(effectiveLimit).stream()
            .map(log -> new FeatureFlagAuditEntryView(
                log.id(),
                log.targetType(),
                log.targetKey(),
                log.action(),
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
        return new FeatureFlagAuditListView(
            AUDIT_LIST_SCHEMA_VERSION,
            entries.size(),
            entries,
            clock.instant(),
            0,
            false
        );
    }

    @Transactional
    public FeatureFlagListView updateFlag(
        String flagKey,
        UpdateFeatureFlagCommand command,
        UUID actorUserId
    ) {
        requireAdminMutationsEnabled();
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        if (command == null) {
            throw validationFailed("Feature flag update request body is required");
        }
        String normalizedFlagKey = requireNonBlankKey(flagKey, "flagKey");
        String idempotencyKey = FeatureFlagEvaluationPolicy.validateIdempotencyKey(command.idempotencyKey());
        String reason = FeatureFlagEvaluationPolicy.validateReason(command.reason());
        DeterministicBucketingPolicy.validateRolloutBasisPoints(command.rolloutBasisPoints());
        if (command.expectedVersion() < 0) {
            throw validationFailed("expectedVersion must be non-negative");
        }
        if (command.currentValueJson() == null || command.currentValueJson().isBlank()) {
            throw validationFailed("currentValueJson is required");
        }

        String payloadHash = FeatureFlagEvaluationPolicy.sha256Hex(
            "FLAG_UPDATE|" + normalizedFlagKey + "|" + command.enabled() + "|"
                + command.rolloutBasisPoints() + "|" + command.currentValueJson().trim() + "|"
                + (command.plannedRemovalAt() != null ? command.plannedRemovalAt().toString() : "null") + "|"
                + reason + "|" + command.expectedVersion()
        );

        repository.acquireAdvisoryLock("ff-idem:" + idempotencyKey);
        Optional<StoredAuditLog> existingAudit = repository.findAuditByIdempotencyKey(idempotencyKey);
        if (existingAudit.isPresent()) {
            StoredAuditLog audit = existingAudit.get();
            if (!"FLAG".equals(audit.targetType())
                || !normalizedFlagKey.equals(audit.targetKey())
                || !payloadHash.equals(audit.payloadHash())) {
                metrics.recordAdminMutation(FlagAuditAction.UPDATED.name(), "idempotency_conflict");
                throw idempotencyConflict();
            }
            metrics.recordAdminMutation(audit.action(), "replayed");
            return buildFlagListView(clock.instant());
        }

        repository.acquireAdvisoryLock("ff-target:FLAG:" + normalizedFlagKey);
        FeatureFlagDefinitionSnapshot existing = repository.findFlagByKey(normalizedFlagKey)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.FEATURE_FLAG_NOT_FOUND,
                "Feature flag not found: " + normalizedFlagKey,
                false,
                "CHECK_FLAG_KEY"
            ));

        String canonicalValueJson = FeatureFlagEvaluationPolicy.validateAndNormalizeValue(
            normalizedFlagKey,
            existing.valueType(),
            command.currentValueJson()
        );
        if (command.plannedRemovalAt() != null && !command.plannedRemovalAt().isAfter(existing.createdAt())) {
            throw validationFailed("plannedRemovalAt must be strictly after createdAt");
        }
        if (existing.version() != command.expectedVersion()) {
            metrics.recordAdminMutation(FlagAuditAction.UPDATED.name(), "optimistic_conflict");
            throw optimisticConflict("Feature flag version mismatch for " + normalizedFlagKey);
        }

        Instant now = clock.instant();
        int updatedRows = repository.updateFlagOptimistic(
            normalizedFlagKey,
            command.enabled(),
            command.rolloutBasisPoints(),
            canonicalValueJson,
            command.plannedRemovalAt(),
            command.expectedVersion(),
            now
        );
        if (updatedRows != 1) {
            metrics.recordAdminMutation(FlagAuditAction.UPDATED.name(), "optimistic_conflict");
            throw optimisticConflict("Feature flag concurrent modification for " + normalizedFlagKey);
        }

        FlagAuditAction action = existing.rolloutBasisPoints() != command.rolloutBasisPoints()
            ? FlagAuditAction.ROLLOUT_CHANGED
            : FlagAuditAction.UPDATED;
        FeatureFlagDefinitionSnapshot after = repository.findFlagByKey(normalizedFlagKey).orElseThrow();
        repository.insertAuditLog(new StoredAuditLog(
            UUID.randomUUID(),
            "FLAG",
            normalizedFlagKey,
            action.name(),
            actorUserId,
            ADMIN_ACTOR_ROLE,
            idempotencyKey,
            payloadHash,
            reason,
            flagStateJson(existing),
            flagStateJson(after),
            normalizeCorrelationId(),
            false,
            now
        ));

        metrics.recordAdminMutation(action.name(), "applied");
        return buildFlagListView(now);
    }

    @Transactional
    public FeatureFlagListView toggleKillSwitch(
        String flagKey,
        ToggleKillSwitchCommand command,
        UUID actorUserId
    ) {
        requireAdminMutationsEnabled();
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        if (command == null) {
            throw validationFailed("Kill-switch request body is required");
        }
        String normalizedFlagKey = requireNonBlankKey(flagKey, "flagKey");
        String idempotencyKey = FeatureFlagEvaluationPolicy.validateIdempotencyKey(command.idempotencyKey());
        String reason = FeatureFlagEvaluationPolicy.validateReason(command.reason());
        if (command.expectedVersion() < 0) {
            throw validationFailed("expectedVersion must be non-negative");
        }

        String payloadHash = FeatureFlagEvaluationPolicy.sha256Hex(
            "KILL_SWITCH|" + normalizedFlagKey + "|" + command.active() + "|" + reason + "|" + command.expectedVersion()
        );

        FlagAuditAction action = command.active()
            ? FlagAuditAction.KILL_SWITCH_ENABLED
            : FlagAuditAction.KILL_SWITCH_DISABLED;

        repository.acquireAdvisoryLock("ff-idem:" + idempotencyKey);
        Optional<StoredAuditLog> existingAudit = repository.findAuditByIdempotencyKey(idempotencyKey);
        if (existingAudit.isPresent()) {
            StoredAuditLog audit = existingAudit.get();
            if (!"FLAG".equals(audit.targetType())
                || !normalizedFlagKey.equals(audit.targetKey())
                || !payloadHash.equals(audit.payloadHash())) {
                metrics.recordAdminMutation(action.name(), "idempotency_conflict");
                throw idempotencyConflict();
            }
            metrics.recordAdminMutation(audit.action(), "replayed");
            return buildFlagListView(clock.instant());
        }

        repository.acquireAdvisoryLock("ff-target:FLAG:" + normalizedFlagKey);
        FeatureFlagDefinitionSnapshot existing = repository.findFlagByKey(normalizedFlagKey)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.FEATURE_FLAG_NOT_FOUND,
                "Feature flag not found: " + normalizedFlagKey,
                false,
                "CHECK_FLAG_KEY"
            ));

        if (existing.version() != command.expectedVersion()) {
            metrics.recordAdminMutation(action.name(), "optimistic_conflict");
            throw optimisticConflict("Feature flag version mismatch for " + normalizedFlagKey);
        }

        Instant now = clock.instant();
        int updatedRows = repository.updateKillSwitchOptimistic(
            normalizedFlagKey,
            command.active(),
            command.expectedVersion(),
            now
        );
        if (updatedRows != 1) {
            metrics.recordAdminMutation(action.name(), "optimistic_conflict");
            throw optimisticConflict("Feature flag concurrent modification for " + normalizedFlagKey);
        }

        FeatureFlagDefinitionSnapshot after = repository.findFlagByKey(normalizedFlagKey).orElseThrow();
        repository.insertAuditLog(new StoredAuditLog(
            UUID.randomUUID(),
            "FLAG",
            normalizedFlagKey,
            action.name(),
            actorUserId,
            ADMIN_ACTOR_ROLE,
            idempotencyKey,
            payloadHash,
            reason,
            flagStateJson(existing),
            flagStateJson(after),
            normalizeCorrelationId(),
            false,
            now
        ));

        metrics.recordKillSwitchToggle(command.active());
        metrics.recordAdminMutation(action.name(), "applied");
        return buildFlagListView(now);
    }

    @Transactional
    public FeatureFlagListView updateExperiment(
        String experimentKey,
        UpdateExperimentCommand command,
        UUID actorUserId
    ) {
        requireAdminMutationsEnabled();
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        if (command == null) {
            throw experimentValidationFailed("Experiment update request body is required");
        }
        String normalizedExpKey = requireNonBlankKey(experimentKey, "experimentKey");
        String idempotencyKey = FeatureFlagEvaluationPolicy.validateIdempotencyKey(command.idempotencyKey());
        String reason = FeatureFlagEvaluationPolicy.validateReason(command.reason());
        if (command.expectedVersion() < 0) {
            throw experimentValidationFailed("expectedVersion must be non-negative");
        }
        ExperimentStatus targetStatus = parseExperimentStatus(command.status());
        if (command.rolloutBasisPoints() < 0
            || command.rolloutBasisPoints() > DeterministicBucketingPolicy.BASIS_POINTS_TOTAL) {
            throw experimentValidationFailed("rolloutBasisPoints must be between 0 and 10000");
        }

        String payloadHash = FeatureFlagEvaluationPolicy.sha256Hex(
            "EXP_UPDATE|" + normalizedExpKey + "|" + targetStatus.name() + "|"
                + command.rolloutBasisPoints() + "|" + reason + "|" + command.expectedVersion()
        );

        repository.acquireAdvisoryLock("ff-idem:" + idempotencyKey);
        Optional<StoredAuditLog> existingAudit = repository.findAuditByIdempotencyKey(idempotencyKey);
        if (existingAudit.isPresent()) {
            StoredAuditLog audit = existingAudit.get();
            if (!"EXPERIMENT".equals(audit.targetType())
                || !normalizedExpKey.equals(audit.targetKey())
                || !payloadHash.equals(audit.payloadHash())) {
                metrics.recordAdminMutation(FlagAuditAction.EXPERIMENT_STATE_CHANGED.name(), "idempotency_conflict");
                throw idempotencyConflict();
            }
            metrics.recordAdminMutation(audit.action(), "replayed");
            return buildFlagListView(clock.instant());
        }

        repository.acquireAdvisoryLock("ff-target:EXPERIMENT:" + normalizedExpKey);
        StoredExperimentDefinition existing = repository.findExperimentByKey(normalizedExpKey)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.EXPERIMENT_NOT_FOUND,
                "Experiment not found: " + normalizedExpKey,
                false,
                "CHECK_EXPERIMENT_KEY"
            ));

        ExperimentStateMachine.validateUpdate(existing.status(), targetStatus);
        DeterministicBucketingPolicy.validateVariants(existing.variants(), existing.controlVariant());

        if (existing.version() != command.expectedVersion()) {
            metrics.recordAdminMutation(FlagAuditAction.EXPERIMENT_STATE_CHANGED.name(), "optimistic_conflict");
            throw optimisticConflict("Experiment version mismatch for " + normalizedExpKey);
        }

        Instant now = clock.instant();
        int updatedRows = repository.updateExperimentOptimistic(
            normalizedExpKey,
            targetStatus,
            command.rolloutBasisPoints(),
            command.expectedVersion(),
            now
        );
        if (updatedRows != 1) {
            metrics.recordAdminMutation(FlagAuditAction.EXPERIMENT_STATE_CHANGED.name(), "optimistic_conflict");
            throw optimisticConflict("Experiment concurrent modification for " + normalizedExpKey);
        }

        StoredExperimentDefinition after = repository.findExperimentByKey(normalizedExpKey).orElseThrow();
        repository.insertAuditLog(new StoredAuditLog(
            UUID.randomUUID(),
            "EXPERIMENT",
            normalizedExpKey,
            FlagAuditAction.EXPERIMENT_STATE_CHANGED.name(),
            actorUserId,
            ADMIN_ACTOR_ROLE,
            idempotencyKey,
            payloadHash,
            reason,
            experimentStateJson(existing),
            experimentStateJson(after),
            normalizeCorrelationId(),
            false,
            now
        ));

        metrics.recordAdminMutation(FlagAuditAction.EXPERIMENT_STATE_CHANGED.name(), "applied");
        return buildFlagListView(now);
    }

    private FeatureFlagListView buildFlagListView(Instant generatedAt) {
        List<FeatureFlagDefinitionView> flags = repository.findAllFlags().stream()
            .map(f -> new FeatureFlagDefinitionView(
                f.flagKey(),
                f.category().name(),
                f.valueType().name(),
                f.clientSafe(),
                f.enabled(),
                f.killSwitchActive(),
                f.rolloutBasisPoints(),
                f.bucketingSalt(),
                f.defaultValueJson(),
                f.currentValueJson(),
                f.owner(),
                f.purpose(),
                f.plannedRemovalAt(),
                f.version(),
                f.trainingEligible(),
                f.createdAt(),
                f.updatedAt()
            ))
            .toList();

        List<ExperimentDefinitionView> experiments = repository.findAllExperiments().stream()
            .map(e -> new ExperimentDefinitionView(
                e.experimentKey(),
                e.linkedFlagKey(),
                e.status().name(),
                e.clientSafe(),
                e.rolloutBasisPoints(),
                e.bucketingSalt(),
                e.controlVariant(),
                e.variants().stream()
                    .map(v -> new VariantWeightView(v.key(), v.weightBasisPoints()))
                    .toList(),
                e.owner(),
                e.purpose(),
                e.plannedRemovalAt(),
                e.experimentVersion(),
                e.version(),
                e.trainingEligible(),
                e.createdAt(),
                e.updatedAt()
            ))
            .toList();

        return new FeatureFlagListView(
            FLAG_LIST_SCHEMA_VERSION,
            flags.size(),
            experiments.size(),
            flags,
            experiments,
            generatedAt,
            0,
            false
        );
    }

    private void requireFeatureFlagsEnabled() {
        if (!properties.enabled()) {
            throw new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.FEATURE_FLAG_DISABLED,
                "Feature flag management is disabled",
                true,
                "RETRY_LATER"
            );
        }
    }

    private void requireAdminMutationsEnabled() {
        if (!properties.enabled() || !properties.adminMutationsEnabled()) {
            throw new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.FEATURE_FLAG_DISABLED,
                "Feature flag admin mutations are disabled",
                true,
                "RETRY_LATER"
            );
        }
    }

    private static ExperimentStatus parseExperimentStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw experimentValidationFailed("Experiment status is required");
        }
        try {
            return ExperimentStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw experimentValidationFailed("Unsupported experiment status: " + raw);
        }
    }

    private static String requireNonBlankKey(String key, String label) {
        if (key == null || key.isBlank()) {
            throw validationFailed(label + " must not be blank");
        }
        return key.trim();
    }

    private static String normalizeCorrelationId() {
        String current = CorrelationIds.current();
        if (current == null || current.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return current.length() > 64 ? current.substring(0, 64) : current;
    }

    private static String flagStateJson(FeatureFlagDefinitionSnapshot f) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("enabled", f.enabled());
        state.put("killSwitchActive", f.killSwitchActive());
        state.put("rolloutBasisPoints", f.rolloutBasisPoints());
        state.put("currentValueJson", f.currentValueJson());
        state.put("plannedRemovalAt", f.plannedRemovalAt() != null ? f.plannedRemovalAt().toString() : null);
        state.put("version", f.version());
        try {
            return JSON_MAPPER.writeValueAsString(state);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize flag state snapshot", exception);
        }
    }

    private static String experimentStateJson(StoredExperimentDefinition e) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", e.status().name());
        state.put("rolloutBasisPoints", e.rolloutBasisPoints());
        state.put("experimentVersion", e.experimentVersion());
        state.put("version", e.version());
        try {
            return JSON_MAPPER.writeValueAsString(state);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to serialize experiment state snapshot", exception);
        }
    }

    private static ApiProblemException validationFailed(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.FEATURE_FLAG_VALIDATION_FAILED,
            message,
            false,
            "FIX_FEATURE_FLAG_INPUT"
        );
    }

    private static ApiProblemException experimentValidationFailed(String message) {
        return new ApiProblemException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            ApiErrorCode.EXPERIMENT_VALIDATION_FAILED,
            message,
            false,
            "FIX_EXPERIMENT_INPUT"
        );
    }

    private static ApiProblemException optimisticConflict(String message) {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            ApiErrorCode.OPTIMISTIC_CONFLICT,
            message,
            true,
            "REFRESH_AND_RETRY"
        );
    }

    private static ApiProblemException idempotencyConflict() {
        return new ApiProblemException(
            HttpStatus.CONFLICT,
            ApiErrorCode.FEATURE_FLAG_IDEMPOTENCY_CONFLICT,
            "Idempotency-Key already used with a different mutation target or payload",
            false,
            "USE_NEW_IDEMPOTENCY_KEY"
        );
    }

    public record FeatureFlagListView(
        String schemaVersion,
        int flagCount,
        int experimentCount,
        List<FeatureFlagDefinitionView> flags,
        List<ExperimentDefinitionView> experiments,
        Instant generatedAt,
        int llmTokensUsed,
        boolean trainingEligible
    ) {
        public FeatureFlagListView {
            flags = flags == null ? List.of() : List.copyOf(flags);
            experiments = experiments == null ? List.of() : List.copyOf(experiments);
        }
    }

    public record FeatureFlagAuditListView(
        String schemaVersion,
        int entryCount,
        List<FeatureFlagAuditEntryView> entries,
        Instant generatedAt,
        int llmTokensUsed,
        boolean trainingEligible
    ) {
        public FeatureFlagAuditListView {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }
    }
}
