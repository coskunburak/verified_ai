package com.verifiedai.exam.infrastructure.privacy;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.verifiedai.exam.application.ExamRepository;
import com.verifiedai.exam.infrastructure.persistence.ExamAnalyticsSnapshotRepository;
import com.verifiedai.exam.infrastructure.persistence.ExamScoringRepository;
import com.verifiedai.exam.infrastructure.persistence.PreExamStudySessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExamAnalyticsPrivacyLifecycleIntegrationTest {

    @Test
    void exportsAnalyticsSnapshotsDeletesChildFirstBeforeScoringAndPurgesExpiredRows() {
        UUID user = UUID.fromString("99000000-0000-4000-8000-000000000099");
        Instant now = Instant.parse("2026-11-18T12:00:00Z");

        var exams = mock(ExamRepository.class);
        var analyticsRepo = mock(ExamAnalyticsSnapshotRepository.class);
        var preExamRepo = mock(PreExamStudySessionRepository.class);
        var scoringRepo = mock(ExamScoringRepository.class);

        var contributor = new ExamPrivacyLifecycleContributor(
                exams,
                null,
                null,
                null,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        contributor.setExamAnalyticsSnapshotRepository(analyticsRepo);
        contributor.setPreExamStudySessionRepository(preExamRepo);
        contributor.setExamScoringRepository(scoringRepo);

        when(exams.findTargetsByUserId(user)).thenReturn(List.of());
        when(scoringRepo.findLatestScoresByUserId(user, 200)).thenReturn(List.of());
        when(scoringRepo.findRegradeAuditsByUserId(user)).thenReturn(List.of());
        when(preExamRepo.exportByUserId(user)).thenReturn(List.of());
        when(preExamRepo.exportEventsByUserId(user)).thenReturn(List.of());

        List<Map<String, Object>> exported = List.of(Map.of(
                "exam_code", "TR_YKS_TYT_MATH",
                "training_eligible", false,
                "llm_tokens_used", 0
        ));
        when(analyticsRepo.exportByUserId(user)).thenReturn(exported);

        Map<String, Object> fullExport = contributor.exportUserData(user);
        assertEquals(exported, fullExport.get("examAnalyticsSnapshots"));
        assertEquals(false, fullExport.get("trainingEligible"));
        assertEquals(0, fullExport.get("llmTokensUsed"));

        contributor.deleteUserData(user, now);
        var order = inOrder(analyticsRepo, preExamRepo, scoringRepo, exams);
        order.verify(analyticsRepo).deleteByUserId(user);
        order.verify(preExamRepo).deleteByUserId(user);
        order.verify(scoringRepo).deleteByUserId(user);
        order.verify(exams).deleteTargetsByUserId(user);

        contributor.purgeExpiredPreExamSnapshots();
        verify(analyticsRepo).purgeExpired(now);
    }
}
