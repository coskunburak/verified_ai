package com.verifiedai.featureflags.application;

import com.verifiedai.featureflags.application.ClientRemoteConfigView.ClientExperimentEntry;
import com.verifiedai.featureflags.application.ClientRemoteConfigView.ClientFlagEntry;
import com.verifiedai.featureflags.domain.DeterministicBucketingPolicy;
import com.verifiedai.featureflags.domain.ExperimentStatus;
import com.verifiedai.featureflags.domain.FeatureFlagDefinitionSnapshot;
import com.verifiedai.featureflags.domain.FeatureFlagEvaluationPolicy;
import com.verifiedai.featureflags.domain.FeatureFlagEvaluationPolicy.EvaluatedFlag;
import com.verifiedai.featureflags.domain.FlagValueType;
import com.verifiedai.featureflags.infrastructure.FeatureFlagJdbcRepository;
import com.verifiedai.featureflags.infrastructure.FeatureFlagJdbcRepository.StoredExperimentAssignment;
import com.verifiedai.featureflags.infrastructure.FeatureFlagJdbcRepository.StoredExperimentDefinition;
import com.verifiedai.featureflags.infrastructure.FeatureFlagProperties;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service implementing {@link FeatureFlagEvaluationPort} with fail-closed defaults,
 * kill-switch supremacy (INV-FF-002), deterministic user-stable bucketing (INV-FF-003),
 * strict client_safe filtering (INV-FF-001), and strong ETag computation.
 */
@Service
public class FeatureFlagEvaluationService implements FeatureFlagEvaluationPort {

    public static final String REMOTE_CONFIG_SCHEMA_VERSION = "remote-config-v1";

    private final FeatureFlagJdbcRepository repository;
    private final FeatureFlagProperties properties;
    private final FeatureFlagMetrics metrics;
    private final Clock clock;

    public FeatureFlagEvaluationService(
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

    @Override
    @Transactional(readOnly = true)
    public boolean evaluateBoolean(String flagKey, UUID userId, boolean fallbackValue) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (!properties.enabled()) {
            metrics.recordFlagEvaluation("UNKNOWN", "system_disabled");
            return fallbackValue;
        }
        Optional<FeatureFlagDefinitionSnapshot> flagOpt = repository.findFlagByKey(flagKey);
        if (flagOpt.isEmpty()) {
            metrics.recordFlagEvaluation("UNKNOWN", "not_found_fallback");
            return fallbackValue;
        }
        FeatureFlagDefinitionSnapshot def = flagOpt.get();
        if (def.valueType() != FlagValueType.BOOLEAN) {
            metrics.recordFlagEvaluation(def.category().name(), "type_mismatch_fallback");
            return fallbackValue;
        }
        EvaluatedFlag evaluated = FeatureFlagEvaluationPolicy.evaluateFlag(def, userId, clock.instant());
        metrics.recordFlagEvaluation(def.category().name(), evaluated.reason());
        return evaluated.enabled();
    }

    @Override
    @Transactional(readOnly = true)
    public FlagEvaluationResult evaluateFlag(String flagKey, UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Instant now = clock.instant();
        if (!properties.enabled()) {
            return failClosedDefaultFlag(flagKey, now);
        }
        FeatureFlagDefinitionSnapshot def = repository.findFlagByKey(flagKey)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.FEATURE_FLAG_NOT_FOUND,
                "Feature flag not found: " + flagKey,
                false,
                "CHECK_FLAG_KEY"
            ));
        EvaluatedFlag evaluated = FeatureFlagEvaluationPolicy.evaluateFlag(def, userId, now);
        metrics.recordFlagEvaluation(def.category().name(), evaluated.reason());
        return new FlagEvaluationResult(
            evaluated.flagKey(),
            evaluated.category(),
            evaluated.valueType(),
            evaluated.enabled(),
            evaluated.valueJson(),
            evaluated.killSwitchActive(),
            evaluated.reason(),
            evaluated.evaluatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public String evaluateConfigJson(String flagKey, UUID userId, String fallbackJson) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (!properties.enabled()) {
            metrics.recordFlagEvaluation("REMOTE_CONFIG", "system_disabled");
            return fallbackJson;
        }
        Optional<FeatureFlagDefinitionSnapshot> flagOpt = repository.findFlagByKey(flagKey);
        if (flagOpt.isEmpty()) {
            metrics.recordFlagEvaluation("REMOTE_CONFIG", "not_found_fallback");
            return fallbackJson;
        }
        FeatureFlagDefinitionSnapshot def = flagOpt.get();
        EvaluatedFlag evaluated = FeatureFlagEvaluationPolicy.evaluateFlag(def, userId, clock.instant());
        metrics.recordFlagEvaluation(def.category().name(), evaluated.reason());
        return evaluated.valueJson();
    }

    @Override
    @Transactional
    public ExperimentAssignmentView assignExperiment(String experimentKey, UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Instant now = clock.instant();
        if (!properties.enabled()) {
            metrics.recordExperimentAssignment("DRAFT", "FEATURE_FLAG_SYSTEM_DISABLED");
            return new ExperimentAssignmentView(
                experimentKey,
                1,
                FeatureFlagEvaluationPolicy.canonicalExperimentControl(experimentKey),
                "DRAFT",
                0,
                "FALLBACK_CONTROL",
                now
            );
        }
        StoredExperimentDefinition exp = repository.findExperimentByKey(experimentKey)
            .orElseThrow(() -> new ApiProblemException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.EXPERIMENT_NOT_FOUND,
                "Experiment not found: " + experimentKey,
                false,
                "CHECK_EXPERIMENT_KEY"
            ));
        return evaluateExperimentInternal(exp, userId, now);
    }

    @Override
    @Transactional
    public ClientRemoteConfigView evaluateClientBundle(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        if (!properties.enabled() || !properties.clientConfigEnabled()) {
            throw new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE,
                ApiErrorCode.FEATURE_FLAG_DISABLED,
                "Feature flag remote configuration is disabled",
                true,
                "RETRY_LATER"
            );
        }
        if (!repository.isAccountActive(userId)) {
            throw new ApiProblemException(
                HttpStatus.FORBIDDEN,
                ApiErrorCode.ACCOUNT_NOT_ACTIVE,
                "Account is not active",
                false,
                "SIGN_IN"
            );
        }

        Instant now = clock.instant();
        List<FeatureFlagDefinitionSnapshot> safeFlags = FeatureFlagEvaluationPolicy.filterClientSafeFlags(
            repository.findClientSafeFlags(properties.maxClientFlags()),
            properties.maxClientFlags()
        );

        List<ClientFlagEntry> clientFlags = new ArrayList<>(safeFlags.size());
        List<String> activeKillSwitches = new ArrayList<>();
        for (FeatureFlagDefinitionSnapshot flag : safeFlags) {
            EvaluatedFlag evaluated = FeatureFlagEvaluationPolicy.evaluateFlag(flag, userId, now);
            metrics.recordFlagEvaluation(flag.category().name(), evaluated.reason());
            clientFlags.add(new ClientFlagEntry(
                evaluated.flagKey(),
                evaluated.category(),
                evaluated.valueType(),
                evaluated.enabled(),
                evaluated.killSwitchActive(),
                evaluated.reason()
            ));
            if (evaluated.killSwitchActive()) {
                activeKillSwitches.add(evaluated.flagKey());
            }
        }

        List<StoredExperimentDefinition> safeExperiments = repository.findClientSafeExperiments().stream()
            .filter(StoredExperimentDefinition::clientSafe)
            .toList();
        List<ClientExperimentEntry> clientExperiments = new ArrayList<>(safeExperiments.size());
        for (StoredExperimentDefinition exp : safeExperiments) {
            ExperimentAssignmentView assignment = evaluateExperimentInternal(exp, userId, now);
            clientExperiments.add(new ClientExperimentEntry(
                assignment.experimentKey(),
                assignment.experimentVersion(),
                assignment.variantKey(),
                assignment.status(),
                assignment.bucketValue(),
                assignment.reason()
            ));
        }

        String etag = computeStrongEtag(
            REMOTE_CONFIG_SCHEMA_VERSION,
            properties.clientEtagMaxAgeSeconds(),
            clientFlags,
            clientExperiments,
            activeKillSwitches
        );

        return new ClientRemoteConfigView(
            REMOTE_CONFIG_SCHEMA_VERSION,
            etag,
            properties.clientEtagMaxAgeSeconds(),
            clientFlags,
            clientExperiments,
            activeKillSwitches,
            now,
            0,
            false
        );
    }

    private ExperimentAssignmentView evaluateExperimentInternal(
        StoredExperimentDefinition exp,
        UUID userId,
        Instant now
    ) {
        // INV-FF-002: Experiment KILLED status or linked flag kill switch immediately forces control.
        if (exp.status() == ExperimentStatus.KILLED) {
            metrics.recordExperimentAssignment(exp.status().name(), "KILL_SWITCH_ACTIVE");
            return new ExperimentAssignmentView(
                exp.experimentKey(),
                exp.experimentVersion(),
                exp.controlVariant(),
                exp.status().name(),
                0,
                "KILL_SWITCH_ACTIVE",
                now
            );
        }

        if (exp.linkedFlagKey() != null) {
            Optional<FeatureFlagDefinitionSnapshot> linkedFlag = repository.findFlagByKey(exp.linkedFlagKey());
            if (linkedFlag.isPresent() && linkedFlag.get().killSwitchActive()) {
                metrics.recordExperimentAssignment(exp.status().name(), "KILL_SWITCH_ACTIVE");
                return new ExperimentAssignmentView(
                    exp.experimentKey(),
                    exp.experimentVersion(),
                    exp.controlVariant(),
                    exp.status().name(),
                    0,
                    "KILL_SWITCH_ACTIVE",
                    now
                );
            }
        }

        if (exp.status() != ExperimentStatus.RUNNING) {
            metrics.recordExperimentAssignment(exp.status().name(), "FALLBACK_CONTROL");
            return new ExperimentAssignmentView(
                exp.experimentKey(),
                exp.experimentVersion(),
                exp.controlVariant(),
                exp.status().name(),
                0,
                "FALLBACK_CONTROL",
                now
            );
        }

        // Concurrency-safe zero-flicker assignment under (userId, experimentKey, experimentVersion).
        String lockKey = "exp-assign:" + userId + ":" + exp.experimentKey() + ":" + exp.experimentVersion();
        repository.acquireAdvisoryLock(lockKey);

        Optional<StoredExperimentAssignment> existingOpt = repository.findAssignment(
            userId,
            exp.experimentKey(),
            exp.experimentVersion()
        );
        if (existingOpt.isPresent()) {
            StoredExperimentAssignment existing = existingOpt.get();
            metrics.recordExperimentAssignment(exp.status().name(), existing.assignmentReason());
            return new ExperimentAssignmentView(
                existing.experimentKey(),
                existing.experimentVersion(),
                existing.variantKey(),
                exp.status().name(),
                existing.bucketValue(),
                existing.assignmentReason(),
                existing.assignedAt()
            );
        }

        int bucket = DeterministicBucketingPolicy.computeBucket(exp.experimentKey(), exp.bucketingSalt(), userId);
        boolean inRollout = bucket < exp.rolloutBasisPoints();
        String variantKey = inRollout
            ? DeterministicBucketingPolicy.selectVariant(bucket, exp.variants())
            : exp.controlVariant();
        String reason = inRollout ? "BUCKET_MATCH" : "FALLBACK_CONTROL";

        repository.insertAssignmentIdempotent(new StoredExperimentAssignment(
            UUID.randomUUID(),
            userId,
            exp.experimentKey(),
            exp.experimentVersion(),
            variantKey,
            bucket,
            reason,
            now,
            false
        ));

        StoredExperimentAssignment persisted = repository.findAssignment(
            userId,
            exp.experimentKey(),
            exp.experimentVersion()
        ).orElseThrow();
        metrics.recordExperimentAssignment(exp.status().name(), persisted.assignmentReason());
        return new ExperimentAssignmentView(
            persisted.experimentKey(),
            persisted.experimentVersion(),
            persisted.variantKey(),
            exp.status().name(),
            persisted.bucketValue(),
            persisted.assignmentReason(),
            persisted.assignedAt()
        );
    }

    private FlagEvaluationResult failClosedDefaultFlag(String flagKey, Instant now) {
        Optional<FeatureFlagDefinitionSnapshot> canonical = FeatureFlagEvaluationPolicy.canonicalDefault(flagKey);
        if (canonical.isPresent()) {
            FeatureFlagDefinitionSnapshot def = canonical.get();
            metrics.recordFlagEvaluation(def.category().name(), "system_disabled");
            boolean defaultEnabled = def.valueType() == FlagValueType.BOOLEAN
                && Boolean.parseBoolean(def.defaultValueJson());
            return new FlagEvaluationResult(
                def.flagKey(),
                def.category().name(),
                def.valueType().name(),
                defaultEnabled,
                def.defaultValueJson(),
                false,
                "FEATURE_FLAG_SYSTEM_DISABLED",
                now
            );
        }
        metrics.recordFlagEvaluation("UNKNOWN", "system_disabled");
        return new FlagEvaluationResult(
            flagKey,
            "RELEASE_FLAG",
            "BOOLEAN",
            false,
            "false",
            false,
            "FEATURE_FLAG_SYSTEM_DISABLED",
            now
        );
    }

    private static String computeStrongEtag(
        String schemaVersion,
        int maxAgeSeconds,
        List<ClientFlagEntry> flags,
        List<ClientExperimentEntry> experiments,
        List<String> killSwitches
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append(schemaVersion).append('|').append(maxAgeSeconds).append('|');
        for (ClientFlagEntry flag : flags) {
            sb.append(flag.flagKey()).append(':')
                .append(flag.category()).append(':')
                .append(flag.valueType()).append(':')
                .append(flag.enabled()).append(':')
                .append(flag.killSwitchActive()).append(':')
                .append(flag.reason()).append(';');
        }
        sb.append('|');
        for (ClientExperimentEntry exp : experiments) {
            sb.append(exp.experimentKey()).append(':')
                .append(exp.experimentVersion()).append(':')
                .append(exp.variantKey()).append(':')
                .append(exp.status()).append(':')
                .append(exp.bucketValue()).append(':')
                .append(exp.reason()).append(';');
        }
        sb.append('|');
        for (String ks : killSwitches) {
            sb.append(ks).append(',');
        }
        String digest = FeatureFlagEvaluationPolicy.sha256Hex(sb.toString());
        return "\"" + digest.substring(0, 32) + "\"";
    }
}
