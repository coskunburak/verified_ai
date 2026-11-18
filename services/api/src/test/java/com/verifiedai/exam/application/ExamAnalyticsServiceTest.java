package com.verifiedai.exam.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.verifiedai.exam.config.ExamProperties;
import com.verifiedai.exam.domain.ExamAnalyticsPolicy;
import com.verifiedai.exam.domain.ExamAnalyticsPolicy.ScoredSessionInput;
import com.verifiedai.exam.domain.ExamProgressReport;
import com.verifiedai.exam.domain.ExamProgressReport.EvidenceStatus;
import com.verifiedai.exam.domain.ExamReadinessCalculator.ActiveTrapSignal;
import com.verifiedai.exam.infrastructure.persistence.ExamAnalyticsSnapshotRepository;
import com.verifiedai.exam.infrastructure.persistence.ExamAnalyticsSnapshotRepository.SnapshotRow;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class ExamAnalyticsServiceTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void disabledFeatureRejectsBeforeReadingAnyLearnerData() {
        ExamProperties props = new ExamProperties();
        props.setAnalyticsEnabled(false);
        var repo = mock(ExamAnalyticsSnapshotRepository.class);
        var signals = mock(ExamReadinessSignalReader.class);
        var service = new ExamAnalyticsService(props, repo, signals, new ExamAnalyticsPolicy(), mapper, Clock.systemUTC(), new SimpleMeterRegistry());

        var failure = assertThrows(ExamAnalyticsService.Failure.class,
                () -> service.getProgressReport(UUID.randomUUID(), "TR_YKS_TYT_MATH"));
        assertEquals(403, failure.status);
        assertEquals("FEATURE_DISABLED", failure.code);
        verifyNoInteractions(repo, signals);
    }

    @Test
    void bolaForeignOwnerTargetRejectedAtServiceBoundary() {
        ExamProperties props = new ExamProperties();
        props.setAnalyticsEnabled(true);
        var repo = mock(ExamAnalyticsSnapshotRepository.class);
        var signals = mock(ExamReadinessSignalReader.class);
        var metrics = new SimpleMeterRegistry();
        var service = new ExamAnalyticsService(props, repo, signals, new ExamAnalyticsPolicy(), mapper, Clock.systemUTC(), metrics);

        UUID authenticated = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        var failure = assertThrows(ExamAnalyticsService.Failure.class,
                () -> service.getProgressReport(authenticated, stranger, "TR_YKS_TYT_MATH"));
        assertEquals(403, failure.status);
        assertEquals("EXAM_ACCESS_DENIED", failure.code);
        assertEquals(1.0, metrics.counter("exam.analytics.bola.denied").count());
        verifyNoInteractions(repo, signals);
    }

    @Test
    void rejectsUnsupportedExamCodesIncludingSatAndEmptyValues() {
        ExamProperties props = new ExamProperties();
        props.setAnalyticsEnabled(true);
        var repo = mock(ExamAnalyticsSnapshotRepository.class);
        var signals = mock(ExamReadinessSignalReader.class);
        var service = new ExamAnalyticsService(props, repo, signals, new ExamAnalyticsPolicy(), mapper, Clock.systemUTC(), new SimpleMeterRegistry());

        UUID user = UUID.randomUUID();
        assertEquals(400, assertThrows(ExamAnalyticsService.Failure.class, () -> service.getProgressReport(user, "INTL_SAT_MATH")).status);
        assertEquals(400, assertThrows(ExamAnalyticsService.Failure.class, () -> service.getProgressReport(user, "SAT_MATH")).status);
        assertEquals(400, assertThrows(ExamAnalyticsService.Failure.class, () -> service.getProgressReport(user, " ")).status);
        assertEquals(400, assertThrows(ExamAnalyticsService.Failure.class, () -> service.getProgressReport(user, null)).status);
        verifyNoInteractions(repo, signals);
    }

    @Test
    void cacheMissComputesAndUpsertsWhileCacheHitSkipsRecomputationAndEmitsTelemetryAfterCommit() {
        ExamProperties props = new ExamProperties();
        props.setAnalyticsEnabled(true);
        var repo = mock(ExamAnalyticsSnapshotRepository.class);
        var signals = mock(ExamReadinessSignalReader.class);
        var metrics = new SimpleMeterRegistry();
        Instant now = Instant.parse("2026-11-18T10:00:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        var service = new ExamAnalyticsService(props, repo, signals, new ExamAnalyticsPolicy(), mapper, fixedClock, metrics);

        UUID user = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-11-10T09:00:00Z");
        ScoredSessionInput score = new ScoredSessionInput(
                UUID.randomUUID(), UUID.randomUUID(), user, "TR_YKS_TYT_MATH",
                new BigDecimal("22.50"), 1, t1, t1
        );
        when(repo.findLatestScoredSessionsByUserAndExam(user, "TR_YKS_TYT_MATH", 20)).thenReturn(List.of(score));
        when(repo.findItemTimingsForSessions(eq(user), anyList())).thenReturn(List.of());
        when(repo.findTrapIncidencesForSessions(eq(user), anyList())).thenReturn(List.of());
        when(signals.loadActiveTrapSignals(user)).thenReturn(List.of(new ActiveTrapSignal("TRAP_1", "SIGN_ERROR", "SK.1", 0.4, 2)));
        when(repo.findFreshSnapshot(eq(user), eq("TR_YKS_TYT_MATH"), anyString(), eq(ExamProgressReport.ALGORITHM_VERSION), any(LocalDate.class), eq(now)))
                .thenReturn(Optional.empty());
        when(repo.upsertSnapshot(any(), anyString(), eq(score.scoreId()))).thenAnswer(inv -> {
            ExamProgressReport r = inv.getArgument(0);
            String fp = inv.getArgument(1);
            return new SnapshotRow(
                    r.snapshotId(), r.userId(), r.examCode(), r.mockCount(),
                    r.scoreTrajectory(), r.pacingByTopic(), r.trapRemediation(),
                    r.conqueredTrapsCount(), r.asOfDate(), fp, r.algorithmVersion(),
                    score.scoreId(), r.schemaVersion(), r.generatedAt(), r.generatedAt(), r.expiresAt()
            );
        });

        TransactionSynchronizationManager.initSynchronization();
        try {
            ExamProgressReport first = service.getProgressReport(user, "YKS_TYT");
            assertEquals("TR_YKS_TYT_MATH", first.examCode());
            assertEquals(1, first.mockCount());
            assertEquals(EvidenceStatus.INSUFFICIENT_FOR_TREND, first.evidenceStatus());
            assertFalse(first.trainingEligible());
            assertEquals(0, first.llmTokensUsed());
            assertEquals(BigDecimal.ZERO, first.inferenceCostUsd());
            assertEquals(1, first.trapRemediation().activeTrapCountNow());
            verify(repo).lockOwnerExam(user, "TR_YKS_TYT_MATH");
            verify(repo, times(1)).upsertSnapshot(any(), anyString(), eq(score.scoreId()));

            TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCommit());
            assertEquals(1.0, metrics.counter("exam_analytics_viewed").count());
            assertEquals(1.0, metrics.counter("exam.analytics.cache", "result", "miss").count());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void fingerprintChangesOnRegradeAndAsOfDateRollover() {
        ExamProperties props = new ExamProperties();
        props.setAnalyticsEnabled(true);
        var service = new ExamAnalyticsService(
                props,
                mock(ExamAnalyticsSnapshotRepository.class),
                mock(ExamReadinessSignalReader.class),
                new ExamAnalyticsPolicy(),
                mapper,
                Clock.systemUTC(),
                new SimpleMeterRegistry()
        );
        UUID user = UUID.randomUUID();
        UUID scoreId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant t1 = Instant.parse("2026-11-10T09:00:00Z");
        ScoredSessionInput v1 = new ScoredSessionInput(scoreId, sessionId, user, "TR_YKS_AYT_MATH", new BigDecimal("20.00"), 1, t1, t1);
        ScoredSessionInput v2Regraded = new ScoredSessionInput(scoreId, sessionId, user, "TR_YKS_AYT_MATH", new BigDecimal("21.25"), 2, t1, t1.plusSeconds(60));

        String fp1 = service.computeSourceFingerprint(user, "TR_YKS_AYT_MATH", LocalDate.of(2026, 11, 18), List.of(v1), List.of(), List.of(), List.of());
        String fpRegraded = service.computeSourceFingerprint(user, "TR_YKS_AYT_MATH", LocalDate.of(2026, 11, 18), List.of(v2Regraded), List.of(), List.of(), List.of());
        String fpNextDay = service.computeSourceFingerprint(user, "TR_YKS_AYT_MATH", LocalDate.of(2026, 11, 19), List.of(v1), List.of(), List.of(), List.of());

        assertNotEquals(fp1, fpRegraded);
        assertNotEquals(fp1, fpNextDay);
        assertEquals(64, fp1.length());
    }
}
