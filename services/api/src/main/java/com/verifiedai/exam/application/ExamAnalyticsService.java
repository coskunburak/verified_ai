package com.verifiedai.exam.application;

import com.verifiedai.exam.config.ExamProperties;
import com.verifiedai.exam.domain.ExamAnalyticsPolicy;
import com.verifiedai.exam.domain.ExamAnalyticsPolicy.ComputedAnalytics;
import com.verifiedai.exam.domain.ExamAnalyticsPolicy.ItemTimingObservation;
import com.verifiedai.exam.domain.ExamAnalyticsPolicy.ScoredSessionInput;
import com.verifiedai.exam.domain.ExamAnalyticsPolicy.TrapIncidenceObservation;
import com.verifiedai.exam.domain.ExamProgressReport;
import com.verifiedai.exam.domain.ExamReadinessCalculator.ActiveTrapSignal;
import com.verifiedai.exam.infrastructure.persistence.ExamAnalyticsSnapshotRepository;
import com.verifiedai.exam.infrastructure.persistence.ExamAnalyticsSnapshotRepository.SnapshotRow;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Application service for Sprint 9.9 longitudinal exam progress analytics (INV-EXAM-005).
 * Enforces feature-flag gate before reading learner data, owner-scoped BOLA isolation,
 * 24-hour snapshot caching with source fingerprinting, and privacy-safe Micrometer telemetry.
 */
@Service
public class ExamAnalyticsService {

    public static final class Failure extends RuntimeException {
        public final int status;
        public final String code;
        public final String detail;

        public Failure(int status, String code, String detail) {
            super(code);
            this.status = status;
            this.code = code;
            this.detail = detail;
        }
    }

    private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");
    private static final Duration SNAPSHOT_TTL = Duration.ofHours(24);

    private final ExamProperties examProperties;
    private final ExamAnalyticsSnapshotRepository snapshotRepository;
    private final ExamReadinessSignalReader signalReader;
    private final ExamAnalyticsPolicy policy;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @Autowired
    public ExamAnalyticsService(
            ExamProperties examProperties,
            ExamAnalyticsSnapshotRepository snapshotRepository,
            ExamReadinessSignalReader signalReader,
            JsonMapper jsonMapper,
            ObjectProvider<Clock> clockProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider
    ) {
        this(
                examProperties,
                snapshotRepository,
                signalReader,
                new ExamAnalyticsPolicy(),
                jsonMapper,
                clockProvider != null ? clockProvider.getIfAvailable(Clock::systemUTC) : Clock.systemUTC(),
                meterRegistryProvider != null
                        ? meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new)
                        : new SimpleMeterRegistry()
        );
    }

    public ExamAnalyticsService(
            ExamProperties examProperties,
            ExamAnalyticsSnapshotRepository snapshotRepository,
            ExamReadinessSignalReader signalReader,
            ExamAnalyticsPolicy policy,
            JsonMapper jsonMapper,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.examProperties = Objects.requireNonNull(examProperties, "examProperties cannot be null");
        this.snapshotRepository = Objects.requireNonNull(snapshotRepository, "snapshotRepository cannot be null");
        this.signalReader = Objects.requireNonNull(signalReader, "signalReader cannot be null");
        this.policy = Objects.requireNonNull(policy, "policy cannot be null");
        this.jsonMapper = Objects.requireNonNull(jsonMapper, "jsonMapper cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry cannot be null");
    }

    @Transactional
    public ExamProgressReport getProgressReport(UUID authenticatedUserId, String rawExamCode) {
        return getProgressReport(authenticatedUserId, authenticatedUserId, rawExamCode);
    }

    /**
     * Service-level BOLA guard: rejects any mismatch between authenticated principal and target owner
     * before touching persistence.
     */
    @Transactional
    public ExamProgressReport getProgressReport(
            UUID authenticatedUserId,
            UUID targetOwnerUserId,
            String rawExamCode
    ) {
        if (!examProperties.isAnalyticsEnabled()) {
            throw new Failure(403, "FEATURE_DISABLED", "Exam progress analytics feature is currently disabled");
        }
        if (authenticatedUserId == null || targetOwnerUserId == null) {
            throw new Failure(401, "AUTH_TOKEN_EXPIRED", "Authentication token is missing or invalid");
        }
        if (!authenticatedUserId.equals(targetOwnerUserId)) {
            meterRegistry.counter("exam.analytics.bola.denied").increment();
            throw new Failure(403, "EXAM_ACCESS_DENIED", "Cross-learner exam analytics access is forbidden");
        }

        String canonicalExam = resolveSupportedCanonicalExamCode(rawExamCode);
        Timer.Sample timerSample = Timer.start(meterRegistry);

        try {
            snapshotRepository.lockOwnerExam(authenticatedUserId, canonicalExam);
            Instant now = clock.instant();
            LocalDate asOfDate = LocalDate.now(clock.withZone(ISTANBUL_ZONE));

            List<ScoredSessionInput> latestSessions = snapshotRepository.findLatestScoredSessionsByUserAndExam(
                    authenticatedUserId,
                    canonicalExam,
                    ExamAnalyticsPolicy.MAX_MOCK_WINDOW
            );
            List<UUID> sessionIds = latestSessions.stream()
                    .map(ScoredSessionInput::sessionId)
                    .sorted()
                    .toList();

            List<ItemTimingObservation> itemTimings = snapshotRepository.findItemTimingsForSessions(
                    authenticatedUserId,
                    sessionIds
            );
            List<TrapIncidenceObservation> trapIncidences = snapshotRepository.findTrapIncidencesForSessions(
                    authenticatedUserId,
                    sessionIds
            );
            List<ActiveTrapSignal> activeTraps = signalReader.loadActiveTrapSignals(authenticatedUserId);
            Integer activeTrapCountNow = activeTraps == null ? null : activeTraps.size();

            String sourceFingerprint = computeSourceFingerprint(
                    authenticatedUserId,
                    canonicalExam,
                    asOfDate,
                    latestSessions,
                    itemTimings,
                    trapIncidences,
                    activeTraps
            );

            Optional<SnapshotRow> cached = snapshotRepository.findFreshSnapshot(
                    authenticatedUserId,
                    canonicalExam,
                    sourceFingerprint,
                    ExamProgressReport.ALGORITHM_VERSION,
                    asOfDate,
                    now
            );
            if (cached.isPresent()) {
                SnapshotRow hit = cached.get();
                if (!authenticatedUserId.equals(hit.userId()) || !canonicalExam.equals(hit.examCode())) {
                    throw new Failure(403, "EXAM_ACCESS_DENIED", "Snapshot owner mismatch");
                }
                meterRegistry.counter("exam.analytics.cache", "result", "hit").increment();
                recordViewedAfterCommit();
                return hit.toReport();
            }

            meterRegistry.counter("exam.analytics.cache", "result", "miss").increment();

            ComputedAnalytics computed = policy.compute(
                    authenticatedUserId,
                    canonicalExam,
                    latestSessions,
                    itemTimings,
                    trapIncidences,
                    activeTrapCountNow
            );

            Instant expiresAt = now.plus(SNAPSHOT_TTL);
            ExamProgressReport draftReport = policy.toReport(
                    UUID.randomUUID(),
                    authenticatedUserId,
                    canonicalExam,
                    computed,
                    asOfDate,
                    now,
                    expiresAt
            );

            SnapshotRow saved = snapshotRepository.upsertSnapshot(
                    draftReport,
                    sourceFingerprint,
                    computed.sourceLastScoreId()
            );
            recordViewedAfterCommit();
            return saved.toReport();
        } finally {
            timerSample.stop(meterRegistry.timer("exam.analytics.latency"));
        }
    }

    public static String resolveSupportedCanonicalExamCode(String rawExamCode) {
        if (rawExamCode == null || rawExamCode.isBlank()) {
            throw new Failure(400, "REQUEST_VALIDATION_FAILED", "Query parameter examCode is required");
        }
        String canonical = ExamReadinessProjectionService.canonicalExamCode(rawExamCode);
        if (canonical == null || !ExamProgressReport.SUPPORTED_EXAM_CODES.contains(canonical)) {
            throw new Failure(400, "REQUEST_VALIDATION_FAILED", "Unsupported examCode for progress analytics");
        }
        return canonical;
    }

    public String computeSourceFingerprint(
            UUID userId,
            String canonicalExamCode,
            LocalDate asOfDate,
            List<ScoredSessionInput> sessions,
            List<ItemTimingObservation> timings,
            List<TrapIncidenceObservation> trapIncidences,
            List<ActiveTrapSignal> activeTraps
    ) {
        Map<String, Object> digestInput = new TreeMap<>();
        digestInput.put("schemaVersion", ExamProgressReport.SCHEMA_VERSION);
        digestInput.put("algorithmVersion", ExamProgressReport.ALGORITHM_VERSION);
        digestInput.put("userId", userId.toString());
        digestInput.put("examCode", canonicalExamCode);
        digestInput.put("asOfDate", asOfDate.toString());

        List<Map<String, Object>> sessionSummaries = (sessions == null ? List.<ScoredSessionInput>of() : sessions)
                .stream()
                .sorted((a, b) -> {
                    int cmp = a.submittedAt().compareTo(b.submittedAt());
                    return cmp != 0 ? cmp : a.sessionId().toString().compareTo(b.sessionId().toString());
                })
                .map(s -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("scoreId", s.scoreId().toString());
                    m.put("sessionId", s.sessionId().toString());
                    m.put("scoreVersion", s.scoreVersion());
                    m.put("submittedAt", s.submittedAt().toString());
                    m.put("updatedAt", s.updatedAt().toString());
                    m.put("netScore", s.netScore().setScale(2, java.math.RoundingMode.HALF_UP).toPlainString());
                    return m;
                })
                .toList();
        digestInput.put("sessions", sessionSummaries);

        List<String> timingSummaries = (timings == null ? List.<ItemTimingObservation>of() : timings)
                .stream()
                .map(t -> t.sessionId() + ":" + t.evaluationId() + ":" + t.topicCode() + ":"
                        + (t.subtopicCode() == null ? "" : t.subtopicCode()) + ":"
                        + (t.skillId() == null ? "" : t.skillId()) + ":"
                        + t.timeSpentSeconds() + ":"
                        + (t.expectedTimeSeconds() == null ? "null" : t.expectedTimeSeconds()))
                .sorted()
                .toList();
        digestInput.put("timings", timingSummaries);

        List<String> trapSummaries = (trapIncidences == null ? List.<TrapIncidenceObservation>of() : trapIncidences)
                .stream()
                .map(tr -> tr.sessionId() + ":" + tr.itemEvaluationId() + ":"
                        + (tr.attributionCategory() == null ? "" : tr.attributionCategory()) + ":"
                        + (tr.matchedMistakeCode() == null ? "" : tr.matchedMistakeCode()))
                .sorted()
                .toList();
        digestInput.put("trapIncidences", trapSummaries);

        List<String> activeTrapDigest = (activeTraps == null ? List.<ActiveTrapSignal>of() : activeTraps)
                .stream()
                .map(sig -> sig.skillCode() + ":" + sig.mistakeCode() + ":" + sig.posteriorRate())
                .sorted()
                .toList();
        digestInput.put("activeTrapsNow", activeTrapDigest);

        byte[] serialized = jsonMapper.writeValueAsString(digestInput).getBytes(StandardCharsets.UTF_8);
        return sha256Hex(serialized);
    }

    private void recordViewedAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    meterRegistry.counter("exam_analytics_viewed").increment();
                }
            });
        } else {
            meterRegistry.counter("exam_analytics_viewed").increment();
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
