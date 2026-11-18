package com.verifiedai.exam.domain;

import com.verifiedai.exam.domain.ExamProgressReport.ConqueredTrapsEvidenceStatus;
import com.verifiedai.exam.domain.ExamProgressReport.EvidenceStatus;
import com.verifiedai.exam.domain.ExamProgressReport.ScoreTrajectoryPointDto;
import com.verifiedai.exam.domain.ExamProgressReport.TrapDataStatus;
import com.verifiedai.exam.domain.ExamProgressReport.TrapObservationPointDto;
import com.verifiedai.exam.domain.ExamProgressReport.TrapRemediationDto;
import com.verifiedai.exam.domain.ExamProgressReport.TrapSourceKind;
import com.verifiedai.exam.domain.PacingAnalysisDto.PacingClassification;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pure deterministic statistical policy for Sprint 9.9 longitudinal exam analytics (INV-EXAM-005).
 * Zero LLM calls ($0.00 cost), strict HALF_UP rounding, raw-ratio pacing classification, and guarded Pearson r.
 */
public final class ExamAnalyticsPolicy {

    public static final int MAX_MOCK_WINDOW = 20;
    public static final int MOVING_AVERAGE_WINDOW = 3;
    public static final int MIN_PACING_OBSERVATIONS = 2;
    public static final int MIN_CORRELATION_OBSERVATIONS = 3;

    private static final BigDecimal FAST_UPPER_BOUND = new BigDecimal("0.85");
    private static final BigDecimal ON_PACE_UPPER_BOUND = new BigDecimal("1.15");
    private static final double VARIANCE_EPSILON = 1e-12;

    public record ScoredSessionInput(
            UUID scoreId,
            UUID sessionId,
            UUID userId,
            String examCode,
            BigDecimal netScore,
            int scoreVersion,
            Instant submittedAt,
            Instant updatedAt
    ) {
        public ScoredSessionInput {
            Objects.requireNonNull(scoreId, "scoreId cannot be null");
            Objects.requireNonNull(sessionId, "sessionId cannot be null");
            Objects.requireNonNull(userId, "userId cannot be null");
            Objects.requireNonNull(examCode, "examCode cannot be null");
            Objects.requireNonNull(netScore, "netScore cannot be null");
            Objects.requireNonNull(submittedAt, "submittedAt cannot be null");
            Objects.requireNonNull(updatedAt, "updatedAt cannot be null");
            if (scoreVersion < 1) {
                throw new IllegalArgumentException("scoreVersion must be >= 1");
            }
        }
    }

    public record ItemTimingObservation(
            UUID sessionId,
            UUID evaluationId,
            String topicCode,
            String subtopicCode,
            String skillId,
            int timeSpentSeconds,
            Integer expectedTimeSeconds
    ) {
        public ItemTimingObservation {
            Objects.requireNonNull(sessionId, "sessionId cannot be null");
            Objects.requireNonNull(evaluationId, "evaluationId cannot be null");
            Objects.requireNonNull(topicCode, "topicCode cannot be null");
        }
    }

    public record TrapIncidenceObservation(
            UUID sessionId,
            UUID itemEvaluationId,
            String attributionCategory,
            String matchedMistakeCode
    ) {
        public TrapIncidenceObservation {
            Objects.requireNonNull(sessionId, "sessionId cannot be null");
            Objects.requireNonNull(itemEvaluationId, "itemEvaluationId cannot be null");
        }
    }

    public record ComputedAnalytics(
            List<ScoredSessionInput> selectedSessionsChronological,
            UUID sourceLastScoreId,
            EvidenceStatus evidenceStatus,
            BigDecimal deltaNet,
            List<ScoreTrajectoryPointDto> scoreTrajectory,
            List<PacingAnalysisDto> pacingByTopic,
            TrapRemediationDto trapRemediation,
            int conqueredTrapsCount
    ) {}

    private static final Comparator<ScoredSessionInput> LATEST_FIRST_COMPARATOR =
            Comparator.comparing(ScoredSessionInput::submittedAt, Comparator.reverseOrder())
                    .thenComparing(s -> s.sessionId().toString(), Comparator.reverseOrder());

    private static final Comparator<ScoredSessionInput> CHRONOLOGICAL_COMPARATOR =
            Comparator.comparing(ScoredSessionInput::submittedAt)
                    .thenComparing(s -> s.sessionId().toString());

    public ComputedAnalytics compute(
            UUID ownerUserId,
            String canonicalExamCode,
            List<ScoredSessionInput> candidateSessions,
            List<ItemTimingObservation> itemTimings,
            List<TrapIncidenceObservation> trapIncidences,
            Integer activeTrapCountNow
    ) {
        Objects.requireNonNull(ownerUserId, "ownerUserId cannot be null");
        if (canonicalExamCode == null || !ExamProgressReport.SUPPORTED_EXAM_CODES.contains(canonicalExamCode)) {
            throw new IllegalArgumentException("Unsupported canonicalExamCode: " + canonicalExamCode);
        }

        // 1. Filter strictly by owner + canonical exam BEFORE selecting latest 20, then order chronologically ASC.
        List<ScoredSessionInput> latestWindow = (candidateSessions == null ? List.<ScoredSessionInput>of() : candidateSessions)
                .stream()
                .filter(s -> ownerUserId.equals(s.userId()) && canonicalExamCode.equals(s.examCode()))
                .sorted(LATEST_FIRST_COMPARATOR)
                .limit(MAX_MOCK_WINDOW)
                .sorted(CHRONOLOGICAL_COMPARATOR)
                .toList();

        int n = latestWindow.size();
        if (n == 0) {
            TrapRemediationDto emptyTrap = new TrapRemediationDto(
                    TrapSourceKind.SESSION_MATCHED_TRAP_INCIDENCE,
                    TrapDataStatus.INSUFFICIENT_DATA,
                    List.of(),
                    sanitizeActiveTrapCount(activeTrapCountNow),
                    null,
                    0,
                    ConqueredTrapsEvidenceStatus.UNAVAILABLE,
                    false
            );
            return new ComputedAnalytics(
                    List.of(),
                    null,
                    EvidenceStatus.EMPTY,
                    null,
                    List.of(),
                    List.of(),
                    emptyTrap,
                    0
            );
        }

        UUID sourceLastScoreId = latestWindow.get(n - 1).scoreId();
        EvidenceStatus evidenceStatus = resolveEvidenceStatus(n);

        // 2. Score Trajectory & Moving Average (MA3 over min(3, i + 1) real points, HALF_UP 2 decimals).
        List<ScoreTrajectoryPointDto> trajectory = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            ScoredSessionInput current = latestWindow.get(i);
            int startIdx = Math.max(0, i - MOVING_AVERAGE_WINDOW + 1);
            int count = i - startIdx + 1;
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = startIdx; j <= i; j++) {
                sum = sum.add(latestWindow.get(j).netScore());
            }
            BigDecimal ma3 = sum.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
            BigDecimal scaledNet = current.netScore().setScale(2, RoundingMode.HALF_UP);
            trajectory.add(new ScoreTrajectoryPointDto(
                    current.sessionId(),
                    current.submittedAt(),
                    scaledNet,
                    ma3,
                    current.scoreVersion()
            ));
        }

        BigDecimal deltaNet = n >= 2
                ? trajectory.get(n - 1).netScore().subtract(trajectory.get(0).netScore()).setScale(2, RoundingMode.HALF_UP)
                : null;

        Set<UUID> selectedSessionIds = new LinkedHashSet<>();
        for (ScoredSessionInput s : latestWindow) {
            selectedSessionIds.add(s.sessionId());
        }

        // 3. Topic-level Pacing over items belonging exclusively to the selected 20 sessions.
        List<PacingAnalysisDto> pacingByTopic = computePacing(selectedSessionIds, itemTimings);

        // 4. Trap Remediation Correlation (V061 observed session incidence vs net score).
        TrapRemediationDto trapRemediation = computeTrapRemediation(
                latestWindow,
                selectedSessionIds,
                trapIncidences,
                activeTrapCountNow
        );

        return new ComputedAnalytics(
                latestWindow,
                sourceLastScoreId,
                evidenceStatus,
                deltaNet,
                trajectory,
                pacingByTopic,
                trapRemediation,
                0
        );
    }

    public ExamProgressReport toReport(
            UUID snapshotId,
            UUID userId,
            String examCode,
            ComputedAnalytics computed,
            LocalDate asOfDate,
            Instant generatedAt,
            Instant expiresAt
    ) {
        return new ExamProgressReport(
                ExamProgressReport.SCHEMA_VERSION,
                snapshotId,
                userId,
                examCode,
                computed.selectedSessionsChronological().size(),
                computed.evidenceStatus(),
                computed.deltaNet(),
                computed.scoreTrajectory(),
                computed.pacingByTopic(),
                computed.trapRemediation(),
                computed.conqueredTrapsCount(),
                asOfDate,
                ExamProgressReport.ALGORITHM_VERSION,
                false,
                0,
                BigDecimal.ZERO,
                generatedAt,
                expiresAt
        );
    }

    private static EvidenceStatus resolveEvidenceStatus(int mockCount) {
        if (mockCount <= 0) {
            return EvidenceStatus.EMPTY;
        }
        if (mockCount == 1) {
            return EvidenceStatus.INSUFFICIENT_FOR_TREND;
        }
        if (mockCount == 2) {
            return EvidenceStatus.PARTIAL_TREND;
        }
        return EvidenceStatus.SUFFICIENT_EVIDENCE;
    }

    private record TopicKey(String topicCode, String subtopicCode, String skillId) implements Comparable<TopicKey> {
        @Override
        public int compareTo(TopicKey other) {
            int cmp = this.topicCode.compareTo(other.topicCode);
            if (cmp != 0) return cmp;
            cmp = Objects.compare(this.subtopicCode, other.subtopicCode, Comparator.nullsFirst(String::compareTo));
            if (cmp != 0) return cmp;
            return Objects.compare(this.skillId, other.skillId, Comparator.nullsFirst(String::compareTo));
        }
    }

    private static final class TopicAccumulator {
        int observedCount = 0;
        int excludedCount = 0;
        long sumActualSeconds = 0L;
        long sumExpectedSeconds = 0L;
    }

    private List<PacingAnalysisDto> computePacing(
            Set<UUID> selectedSessionIds,
            List<ItemTimingObservation> itemTimings
    ) {
        if (itemTimings == null || itemTimings.isEmpty()) {
            return List.of();
        }
        Map<TopicKey, TopicAccumulator> groups = new LinkedHashMap<>();
        List<ItemTimingObservation> filtered = itemTimings.stream()
                .filter(item -> selectedSessionIds.contains(item.sessionId()))
                .sorted(Comparator.comparing(ItemTimingObservation::topicCode)
                        .thenComparing(i -> i.subtopicCode() == null ? "" : i.subtopicCode())
                        .thenComparing(i -> i.skillId() == null ? "" : i.skillId())
                        .thenComparing(ItemTimingObservation::evaluationId))
                .toList();

        for (ItemTimingObservation item : filtered) {
            String normalizedTopic = item.topicCode().trim();
            if (normalizedTopic.isEmpty()) {
                continue;
            }
            String normalizedSubtopic = (item.subtopicCode() == null || item.subtopicCode().isBlank())
                    ? null
                    : item.subtopicCode().trim();
            String normalizedSkill = (item.skillId() == null || item.skillId().isBlank())
                    ? null
                    : item.skillId().trim();
            TopicKey key = new TopicKey(normalizedTopic, normalizedSubtopic, normalizedSkill);
            TopicAccumulator acc = groups.computeIfAbsent(key, k -> new TopicAccumulator());

            if (item.timeSpentSeconds() <= 0
                    || item.expectedTimeSeconds() == null
                    || item.expectedTimeSeconds() <= 0) {
                acc.excludedCount++;
            } else {
                acc.observedCount++;
                acc.sumActualSeconds += item.timeSpentSeconds();
                acc.sumExpectedSeconds += item.expectedTimeSeconds();
            }
        }

        List<PacingAnalysisDto> results = new ArrayList<>(groups.size());
        for (Map.Entry<TopicKey, TopicAccumulator> entry : groups.entrySet()) {
            TopicKey key = entry.getKey();
            TopicAccumulator acc = entry.getValue();
            if (acc.observedCount < MIN_PACING_OBSERVATIONS || acc.sumExpectedSeconds <= 0L) {
                results.add(new PacingAnalysisDto(
                        key.topicCode(),
                        key.subtopicCode(),
                        key.skillId(),
                        acc.observedCount,
                        acc.excludedCount,
                        null,
                        null,
                        null,
                        PacingClassification.INSUFFICIENT_DATA
                ));
                continue;
            }
            BigDecimal countBd = BigDecimal.valueOf(acc.observedCount);
            BigDecimal sumActualBd = BigDecimal.valueOf(acc.sumActualSeconds);
            BigDecimal sumExpectedBd = BigDecimal.valueOf(acc.sumExpectedSeconds);

            // Raw high-precision ratio drives classification; display values are rounded HALF_UP to 2 decimals.
            BigDecimal rawRatio = sumActualBd.divide(sumExpectedBd, 8, RoundingMode.HALF_UP);
            PacingClassification classification = classifyRawRatio(rawRatio);

            BigDecimal meanActual = sumActualBd.divide(countBd, 2, RoundingMode.HALF_UP);
            BigDecimal meanExpected = sumExpectedBd.divide(countBd, 2, RoundingMode.HALF_UP);
            BigDecimal displayRatio = rawRatio.setScale(2, RoundingMode.HALF_UP);

            results.add(new PacingAnalysisDto(
                    key.topicCode(),
                    key.subtopicCode(),
                    key.skillId(),
                    acc.observedCount,
                    acc.excludedCount,
                    meanActual,
                    meanExpected,
                    displayRatio,
                    classification
            ));
        }

        results.sort(Comparator.comparing(PacingAnalysisDto::topicCode)
                .thenComparing(p -> p.subtopicCode() == null ? "" : p.subtopicCode())
                .thenComparing(p -> p.skillId() == null ? "" : p.skillId()));
        return results;
    }

    public static PacingClassification classifyRawRatio(BigDecimal rawRatio) {
        Objects.requireNonNull(rawRatio, "rawRatio cannot be null");
        if (rawRatio.compareTo(FAST_UPPER_BOUND) <= 0) {
            return PacingClassification.FAST;
        }
        if (rawRatio.compareTo(ON_PACE_UPPER_BOUND) <= 0) {
            return PacingClassification.ON_PACE;
        }
        return PacingClassification.BOTTLENECK;
    }

    private TrapRemediationDto computeTrapRemediation(
            List<ScoredSessionInput> chronologicalSessions,
            Set<UUID> selectedSessionIds,
            List<TrapIncidenceObservation> trapIncidences,
            Integer activeTrapCountNow
    ) {
        Map<UUID, Integer> incidenceBySession = new LinkedHashMap<>();
        for (ScoredSessionInput session : chronologicalSessions) {
            incidenceBySession.put(session.sessionId(), 0);
        }

        if (trapIncidences != null) {
            Set<UUID> countedEvaluations = new LinkedHashSet<>();
            for (TrapIncidenceObservation obs : trapIncidences) {
                if (!selectedSessionIds.contains(obs.sessionId())) {
                    continue;
                }
                if ("RECURRING_MISTAKE_TRAP".equals(obs.attributionCategory())
                        && obs.matchedMistakeCode() != null
                        && !obs.matchedMistakeCode().isBlank()
                        && countedEvaluations.add(obs.itemEvaluationId())) {
                    incidenceBySession.computeIfPresent(obs.sessionId(), (k, v) -> v + 1);
                }
            }
        }

        List<TrapObservationPointDto> observations = new ArrayList<>(chronologicalSessions.size());
        for (ScoredSessionInput session : chronologicalSessions) {
            int count = incidenceBySession.getOrDefault(session.sessionId(), 0);
            observations.add(new TrapObservationPointDto(
                    session.sessionId(),
                    session.submittedAt(),
                    session.netScore().setScale(2, RoundingMode.HALF_UP),
                    count
            ));
        }

        // Distinct calendar dates check: Pearson requires >= 3 paired observations across >= 3 distinct dates.
        long distinctDates = observations.stream()
                .map(o -> o.submittedAt().toString().substring(0, 10))
                .distinct()
                .count();

        int sampleSize = observations.size();
        Integer sanitizedActiveNow = sanitizeActiveTrapCount(activeTrapCountNow);

        if (sampleSize < MIN_CORRELATION_OBSERVATIONS || distinctDates < MIN_CORRELATION_OBSERVATIONS) {
            return new TrapRemediationDto(
                    TrapSourceKind.SESSION_MATCHED_TRAP_INCIDENCE,
                    TrapDataStatus.INSUFFICIENT_DATA,
                    observations,
                    sanitizedActiveNow,
                    null,
                    sampleSize,
                    ConqueredTrapsEvidenceStatus.UNAVAILABLE,
                    false
            );
        }

        PearsonResult pearson = computePearson(observations);
        return new TrapRemediationDto(
                TrapSourceKind.SESSION_MATCHED_TRAP_INCIDENCE,
                pearson.status(),
                observations,
                sanitizedActiveNow,
                pearson.coefficient(),
                sampleSize,
                ConqueredTrapsEvidenceStatus.UNAVAILABLE,
                false
        );
    }

    public record PearsonResult(TrapDataStatus status, BigDecimal coefficient) {}

    public static PearsonResult computePearson(List<TrapObservationPointDto> observations) {
        if (observations == null || observations.size() < MIN_CORRELATION_OBSERVATIONS) {
            return new PearsonResult(TrapDataStatus.INSUFFICIENT_DATA, null);
        }
        int n = observations.size();
        double sumX = 0.0;
        double sumY = 0.0;
        for (TrapObservationPointDto pt : observations) {
            double x = pt.matchedTrapIncidenceCount();
            double y = pt.netScore().doubleValue();
            if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0.0) {
                return new PearsonResult(TrapDataStatus.INSUFFICIENT_DATA, null);
            }
            sumX += x;
            sumY += y;
        }
        double meanX = sumX / n;
        double meanY = sumY / n;

        double cov = 0.0;
        double varX = 0.0;
        double varY = 0.0;
        for (TrapObservationPointDto pt : observations) {
            double dx = pt.matchedTrapIncidenceCount() - meanX;
            double dy = pt.netScore().doubleValue() - meanY;
            cov += dx * dy;
            varX += dx * dx;
            varY += dy * dy;
        }

        if (varX <= VARIANCE_EPSILON || varY <= VARIANCE_EPSILON) {
            return new PearsonResult(TrapDataStatus.ZERO_VARIANCE, null);
        }

        double denom = Math.sqrt(varX * varY);
        if (!Double.isFinite(denom) || denom <= VARIANCE_EPSILON) {
            return new PearsonResult(TrapDataStatus.ZERO_VARIANCE, null);
        }

        double rawR = cov / denom;
        if (!Double.isFinite(rawR)) {
            return new PearsonResult(TrapDataStatus.ZERO_VARIANCE, null);
        }
        double clamped = Math.max(-1.0, Math.min(1.0, rawR));
        BigDecimal rounded = BigDecimal.valueOf(clamped).setScale(4, RoundingMode.HALF_UP);
        return new PearsonResult(TrapDataStatus.COMPUTED, rounded);
    }

    private static Integer sanitizeActiveTrapCount(Integer count) {
        if (count == null || count < 0) {
            return null;
        }
        return count;
    }
}
