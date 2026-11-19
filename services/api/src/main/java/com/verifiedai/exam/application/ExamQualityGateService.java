package com.verifiedai.exam.application;

import com.verifiedai.exam.config.ExamProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Read-only, fail-closed Phase 09 Exit Gate quality evaluator (GET /v1/admin/exams/quality-gate).
 * Never runs migrations, tests, benchmarks, or LLM inference at request time; reads only
 * persisted Flyway schema history and owner-supplied signed/static evidence manifests.
 */
@Service
public class ExamQualityGateService {

    public static final String SCHEMA_VERSION = "exam-quality-gate-v1";
    public static final String PHASE = "PHASE_09";
    public static final double LATENCY_SLA_MS = 350.0d;
    public static final int MIN_WARMED_SAMPLES = 200;

    private static final List<String> REQUIRED_MIGRATION_VERSIONS = List.of(
            "054", "055", "056", "057", "058", "059", "060", "061", "062", "063", "064"
    );

    public enum GateStatus {
        PASS,
        FAIL,
        BLOCKED
    }

    public record FlywayChainCheck(
            GateStatus status,
            String evidence,
            String verifiedMinVersion,
            String verifiedMaxVersion,
            List<String> missingVersions
    ) {
    }

    public record LatencySlaCheck(
            GateStatus status,
            Double p95Ms,
            int sampleCount,
            double thresholdMs,
            String measurementSource
    ) {
    }

    public record CriticalDefectsCheck(
            GateStatus status,
            Integer openP0Count,
            Integer openP1Count,
            String ledgerSource
    ) {
    }

    public record EvidenceGateCheck(
            GateStatus status,
            String evidence,
            Integer verifiedFormulaCount,
            String detail
    ) {
    }

    public record QualityGateChecks(
            FlywayChainCheck flywayV054ToV064,
            LatencySlaCheck cntLos10P95,
            LatencySlaCheck cntLos11P95,
            CriticalDefectsCheck criticalDefects,
            EvidenceGateCheck securityAndReplay,
            EvidenceGateCheck accessibilityTrTR,
            EvidenceGateCheck offlineRecovery,
            EvidenceGateCheck privacyLifecycle
    ) {
    }

    public record ExamQualityGateReport(
            String schemaVersion,
            String phase,
            GateStatus gateStatus,
            boolean phase9ExitAuthorized,
            String sourceCommitSha,
            String evidenceRunId,
            Instant generatedAt,
            QualityGateChecks checks,
            boolean trainingEligible,
            int llmTokensUsed,
            BigDecimal inferenceCostUsd
    ) {
    }

    /**
     * External owner-run evidence payload read strictly as input; never assumed valid until checked.
     */
    public record OwnerEvidenceManifest(
            String sourceCommitSha,
            String evidenceRunId,
            Instant evidenceGeneratedAt,
            Double cntLos10P95Ms,
            Integer cntLos10SampleCount,
            String cntLos10MeasurementSource,
            Double cntLos11P95Ms,
            Integer cntLos11SampleCount,
            String cntLos11MeasurementSource,
            Integer openP0Count,
            Integer openP1Count,
            String defectLedgerSource,
            Boolean securityAndReplayPassed,
            String securityAndReplayDetail,
            Boolean accessibilityTrTRPassed,
            Integer verifiedFormulaCount,
            String accessibilityDetail,
            Boolean offlineRecoveryPassed,
            String offlineRecoveryDetail,
            Boolean privacyLifecyclePassed,
            String privacyLifecycleDetail,
            Boolean ownerSignOffAuthorized
    ) {
    }

    @FunctionalInterface
    public interface OwnerEvidenceSupplier {
        Optional<OwnerEvidenceManifest> load();
    }

    private final ExamProperties examProperties;
    private final JdbcClient jdbcClient;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final String expectedBuildSha;
    private final OwnerEvidenceSupplier evidenceSupplier;
    private final JsonMapper jsonMapper;
    private final AtomicReference<String> lastCountedPassRunId = new AtomicReference<>();

    @Autowired
    public ExamQualityGateService(
            ExamProperties examProperties,
            ObjectProvider<JdbcClient> jdbcClientProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            ObjectProvider<Clock> clockProvider,
            @Value("${verifiedai.build.commit-sha:}") String expectedBuildSha
    ) {
        this.examProperties = Objects.requireNonNull(examProperties, "examProperties cannot be null");
        this.jdbcClient = jdbcClientProvider.getIfAvailable();
        this.meterRegistry = meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new);
        this.clock = clockProvider.getIfAvailable(Clock::systemUTC);
        this.expectedBuildSha = expectedBuildSha == null ? "" : expectedBuildSha.trim();
        this.jsonMapper = JsonMapper.builder().findAndAddModules().build();
        this.evidenceSupplier = this::readManifestFromConfiguredPath;
    }

    public ExamQualityGateService(
            ExamProperties examProperties,
            JdbcClient jdbcClient,
            MeterRegistry meterRegistry,
            Clock clock,
            String expectedBuildSha,
            OwnerEvidenceSupplier evidenceSupplier
    ) {
        this.examProperties = Objects.requireNonNull(examProperties, "examProperties cannot be null");
        this.jdbcClient = jdbcClient;
        this.meterRegistry = meterRegistry == null ? new SimpleMeterRegistry() : meterRegistry;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.expectedBuildSha = expectedBuildSha == null ? "" : expectedBuildSha.trim();
        this.jsonMapper = JsonMapper.builder().findAndAddModules().build();
        this.evidenceSupplier = Objects.requireNonNull(evidenceSupplier, "evidenceSupplier cannot be null");
    }

    @Transactional(readOnly = true)
    public ExamQualityGateReport evaluateReadOnlyGate() {
        Instant now = Instant.now(clock);
        FlywayChainCheck flywayCheck = evaluateFlywayChain();
        Optional<OwnerEvidenceManifest> manifestOpt = evidenceSupplier.load();

        if (manifestOpt.isEmpty()) {
            return buildBlockedReport(now, null, null, flywayCheck);
        }

        OwnerEvidenceManifest manifest = manifestOpt.get();
        String commitSha = trimToNull(manifest.sourceCommitSha());
        String runId = trimToNull(manifest.evidenceRunId());

        if (commitSha == null || runId == null) {
            return buildBlockedReport(now, commitSha, runId, flywayCheck);
        }
        if (!expectedBuildSha.isEmpty() && !expectedBuildSha.equalsIgnoreCase(commitSha)) {
            return buildStaleShaBlockedReport(now, commitSha, runId, flywayCheck);
        }

        LatencySlaCheck los10Check = evaluateLatency(
                manifest.cntLos10P95Ms(),
                manifest.cntLos10SampleCount(),
                manifest.cntLos10MeasurementSource()
        );
        LatencySlaCheck los11Check = evaluateLatency(
                manifest.cntLos11P95Ms(),
                manifest.cntLos11SampleCount(),
                manifest.cntLos11MeasurementSource()
        );
        CriticalDefectsCheck defectsCheck = evaluateDefects(
                manifest.openP0Count(),
                manifest.openP1Count(),
                manifest.defectLedgerSource()
        );
        EvidenceGateCheck securityCheck = evaluateBooleanEvidence(
                manifest.securityAndReplayPassed(),
                null,
                manifest.securityAndReplayDetail()
        );
        EvidenceGateCheck accessibilityCheck = evaluateAccessibilityEvidence(
                manifest.accessibilityTrTRPassed(),
                manifest.verifiedFormulaCount(),
                manifest.accessibilityDetail()
        );
        EvidenceGateCheck offlineCheck = evaluateBooleanEvidence(
                manifest.offlineRecoveryPassed(),
                null,
                manifest.offlineRecoveryDetail()
        );
        EvidenceGateCheck privacyCheck = evaluateBooleanEvidence(
                manifest.privacyLifecyclePassed(),
                null,
                manifest.privacyLifecycleDetail()
        );

        QualityGateChecks checks = new QualityGateChecks(
                flywayCheck,
                los10Check,
                los11Check,
                defectsCheck,
                securityCheck,
                accessibilityCheck,
                offlineCheck,
                privacyCheck
        );

        GateStatus overall = aggregateStatus(
                flywayCheck.status(),
                los10Check.status(),
                los11Check.status(),
                defectsCheck.status(),
                securityCheck.status(),
                accessibilityCheck.status(),
                offlineCheck.status(),
                privacyCheck.status()
        );

        boolean authorized = overall == GateStatus.PASS
                && Boolean.TRUE.equals(manifest.ownerSignOffAuthorized());

        if (overall == GateStatus.PASS) {
            emitPassMetricOncePerRun(runId);
        }

        return new ExamQualityGateReport(
                SCHEMA_VERSION,
                PHASE,
                overall,
                authorized,
                commitSha,
                runId,
                now,
                checks,
                false,
                0,
                BigDecimal.ZERO
        );
    }

    private FlywayChainCheck evaluateFlywayChain() {
        if (jdbcClient == null) {
            return new FlywayChainCheck(
                    GateStatus.BLOCKED,
                    "MISSING",
                    null,
                    null,
                    REQUIRED_MIGRATION_VERSIONS
            );
        }
        try {
            var rows = jdbcClient.sql("""
                    SELECT version, success
                    FROM flyway_schema_history
                    WHERE version IN ('054','055','056','057','058','059','060','061','062','063','064')
                    ORDER BY installed_rank ASC
                    """)
                    .query((rs, rowNum) -> new FlywayRow(
                            rs.getString("version"),
                            rs.getBoolean("success")
                    ))
                    .list();

            Set<String> succeeded = new TreeSet<>();
            boolean anyFailed = false;
            for (FlywayRow row : rows) {
                if (row.success()) {
                    succeeded.add(row.version());
                } else {
                    anyFailed = true;
                }
            }
            List<String> missing = new ArrayList<>();
            for (String required : REQUIRED_MIGRATION_VERSIONS) {
                if (!succeeded.contains(required)) {
                    missing.add(required);
                }
            }
            String minVer = succeeded.isEmpty() ? null : succeeded.iterator().next();
            String maxVer = null;
            for (String v : succeeded) {
                maxVer = v;
            }
            if (anyFailed) {
                return new FlywayChainCheck(GateStatus.FAIL, "FAILED", minVer, maxVer, missing);
            }
            if (!missing.isEmpty()) {
                return new FlywayChainCheck(
                        GateStatus.BLOCKED,
                        succeeded.isEmpty() ? "MISSING" : "INCOMPLETE",
                        minVer,
                        maxVer,
                        missing
                );
            }
            return new FlywayChainCheck(GateStatus.PASS, "VERIFIED", "054", "064", List.of());
        } catch (DataAccessException ex) {
            return new FlywayChainCheck(
                    GateStatus.BLOCKED,
                    "MISSING",
                    null,
                    null,
                    REQUIRED_MIGRATION_VERSIONS
            );
        }
    }

    private record FlywayRow(String version, boolean success) {
    }

    private static LatencySlaCheck evaluateLatency(Double p95Ms, Integer sampleCount, String source) {
        int samples = sampleCount == null ? 0 : Math.max(0, sampleCount);
        String cleanSource = trimToNull(source);
        if (p95Ms == null || samples < MIN_WARMED_SAMPLES || cleanSource == null) {
            return new LatencySlaCheck(GateStatus.BLOCKED, p95Ms, samples, LATENCY_SLA_MS, cleanSource);
        }
        if (Double.isNaN(p95Ms) || Double.isInfinite(p95Ms) || p95Ms < 0.0d) {
            return new LatencySlaCheck(GateStatus.BLOCKED, null, samples, LATENCY_SLA_MS, cleanSource);
        }
        GateStatus status = p95Ms < LATENCY_SLA_MS ? GateStatus.PASS : GateStatus.FAIL;
        return new LatencySlaCheck(status, p95Ms, samples, LATENCY_SLA_MS, cleanSource);
    }

    private static CriticalDefectsCheck evaluateDefects(Integer openP0, Integer openP1, String source) {
        String cleanSource = trimToNull(source);
        if (openP0 == null || openP1 == null || cleanSource == null || openP0 < 0 || openP1 < 0) {
            return new CriticalDefectsCheck(GateStatus.BLOCKED, openP0, openP1, cleanSource);
        }
        GateStatus status = (openP0 == 0 && openP1 == 0) ? GateStatus.PASS : GateStatus.FAIL;
        return new CriticalDefectsCheck(status, openP0, openP1, cleanSource);
    }

    private static EvidenceGateCheck evaluateBooleanEvidence(Boolean passed, Integer formulaCount, String detail) {
        if (passed == null) {
            return new EvidenceGateCheck(GateStatus.BLOCKED, "MISSING", formulaCount, trimToNull(detail));
        }
        return passed
                ? new EvidenceGateCheck(GateStatus.PASS, "VERIFIED", formulaCount, trimToNull(detail))
                : new EvidenceGateCheck(GateStatus.FAIL, "FAILED", formulaCount, trimToNull(detail));
    }

    private static EvidenceGateCheck evaluateAccessibilityEvidence(Boolean passed, Integer formulaCount, String detail) {
        if (passed == null || formulaCount == null || formulaCount < 50) {
            return new EvidenceGateCheck(
                    GateStatus.BLOCKED,
                    passed != null && formulaCount != null ? "INCOMPLETE" : "MISSING",
                    formulaCount,
                    trimToNull(detail)
            );
        }
        return passed
                ? new EvidenceGateCheck(GateStatus.PASS, "VERIFIED", formulaCount, trimToNull(detail))
                : new EvidenceGateCheck(GateStatus.FAIL, "FAILED", formulaCount, trimToNull(detail));
    }

    private static GateStatus aggregateStatus(GateStatus... statuses) {
        boolean anyBlocked = false;
        for (GateStatus s : statuses) {
            if (s == GateStatus.FAIL) {
                return GateStatus.FAIL;
            }
            if (s == GateStatus.BLOCKED) {
                anyBlocked = true;
            }
        }
        return anyBlocked ? GateStatus.BLOCKED : GateStatus.PASS;
    }

    private ExamQualityGateReport buildBlockedReport(
            Instant now,
            String sha,
            String runId,
            FlywayChainCheck flywayCheck
    ) {
        QualityGateChecks checks = new QualityGateChecks(
                flywayCheck,
                new LatencySlaCheck(GateStatus.BLOCKED, null, 0, LATENCY_SLA_MS, null),
                new LatencySlaCheck(GateStatus.BLOCKED, null, 0, LATENCY_SLA_MS, null),
                new CriticalDefectsCheck(GateStatus.BLOCKED, null, null, null),
                new EvidenceGateCheck(GateStatus.BLOCKED, "MISSING", null, "Owner security/replay evidence missing"),
                new EvidenceGateCheck(GateStatus.BLOCKED, "MISSING", null, "Owner 50-formula VoiceOver evidence missing"),
                new EvidenceGateCheck(GateStatus.BLOCKED, "MISSING", null, "Owner offline recovery evidence missing"),
                new EvidenceGateCheck(GateStatus.BLOCKED, "MISSING", null, "Owner privacy lifecycle evidence missing")
        );
        return new ExamQualityGateReport(
                SCHEMA_VERSION,
                PHASE,
                GateStatus.BLOCKED,
                false,
                sha,
                runId,
                now,
                checks,
                false,
                0,
                BigDecimal.ZERO
        );
    }

    private ExamQualityGateReport buildStaleShaBlockedReport(
            Instant now,
            String sha,
            String runId,
            FlywayChainCheck flywayCheck
    ) {
        String reason = "Owner evidence SHA does not match running build SHA";
        QualityGateChecks checks = new QualityGateChecks(
                flywayCheck,
                new LatencySlaCheck(GateStatus.BLOCKED, null, 0, LATENCY_SLA_MS, null),
                new LatencySlaCheck(GateStatus.BLOCKED, null, 0, LATENCY_SLA_MS, null),
                new CriticalDefectsCheck(GateStatus.BLOCKED, null, null, null),
                new EvidenceGateCheck(GateStatus.BLOCKED, "STALE_SHA", null, reason),
                new EvidenceGateCheck(GateStatus.BLOCKED, "STALE_SHA", null, reason),
                new EvidenceGateCheck(GateStatus.BLOCKED, "STALE_SHA", null, reason),
                new EvidenceGateCheck(GateStatus.BLOCKED, "STALE_SHA", null, reason)
        );
        return new ExamQualityGateReport(
                SCHEMA_VERSION,
                PHASE,
                GateStatus.BLOCKED,
                false,
                sha,
                runId,
                now,
                checks,
                false,
                0,
                BigDecimal.ZERO
        );
    }

    private Optional<OwnerEvidenceManifest> readManifestFromConfiguredPath() {
        String rawPath = trimToNull(examProperties.getQualityGateEvidencePath());
        if (rawPath == null) {
            return Optional.empty();
        }
        try {
            Path path = Path.of(rawPath);
            if (!Files.isRegularFile(path)) {
                return Optional.empty();
            }
            String json = Files.readString(path, StandardCharsets.UTF_8);
            return Optional.ofNullable(jsonMapper.readValue(json, OwnerEvidenceManifest.class));
        } catch (JacksonException | java.io.IOException | SecurityException ex) {
            return Optional.empty();
        }
    }

    private void emitPassMetricOncePerRun(String runId) {
        String prev = lastCountedPassRunId.get();
        if (!Objects.equals(prev, runId) && lastCountedPassRunId.compareAndSet(prev, runId)) {
            Counter.builder("exam_quality_gate_passed")
                    .tag("outcome", "PASS")
                    .tag("phase", PHASE)
                    .register(meterRegistry)
                    .increment();
            Counter.builder("verifiedai.exam.quality_gate.passed")
                    .tag("outcome", "PASS")
                    .tag("phase", PHASE)
                    .register(meterRegistry)
                    .increment();
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
