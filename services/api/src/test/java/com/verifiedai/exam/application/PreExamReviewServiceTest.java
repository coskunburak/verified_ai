package com.verifiedai.exam.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.verifiedai.exam.config.ExamProperties;
import com.verifiedai.exam.domain.*;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.infrastructure.persistence.PreExamStudySessionRepository;
import com.verifiedai.exam.infrastructure.persistence.PreExamStudySessionRepository.Snapshot;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

class PreExamReviewServiceTest {
    @Test void disabledFeatureNeverReadsStudentEvidence() {
        var flags=new ExamProperties(); var exams=mock(ExamRepository.class);
        var profiles=mock(CandidateProfileRepository.class); var signals=mock(ExamReadinessSignalReader.class);
        var sessions=mock(PreExamStudySessionRepository.class); var mapper=JsonMapper.builder().build();
        @SuppressWarnings("unchecked") ObjectProvider<Clock> clocks=mock(ObjectProvider.class);
        when(clocks.getIfAvailable(any())).thenReturn(Clock.systemUTC());
        var service=new PreExamReviewService(flags,exams,profiles,signals,sessions,new PreExamCatalog(mapper),mapper,clocks,new SimpleMeterRegistry());
        assertEquals(403,assertThrows(PreExamReviewService.Failure.class,()->service.review(UUID.randomUUID(),"TR_YKS_TYT_MATH")).status);
        verifyNoInteractions(exams,profiles,signals,sessions);
    }
    @Test void sparseEvidenceSnapshotIsPrivateAndFreshReplayDoesNotInsertAgain() {
        var flags=new ExamProperties(); flags.setPreExamModeEnabled(true);
        var exams=mock(ExamRepository.class);
        Instant fixtureTime=Instant.parse("2026-11-18T09:00:00Z");
        var definition=new ExamDefinition(
                UUID.fromString("98000000-0000-4000-8000-000000000001"),
                "TR_YKS_TYT_MATH", "synthetic-v1", ExamModels.Jurisdiction.TR, ExamModels.ExamFamily.YKS,
                "Synthetic mathematics", "Sentetik matematik", ExamModels.ExamStatus.ACTIVE,
                40, 60, ExamModels.ScoringFormulaType.CORRECT_MINUS_QUARTER_WRONG,
                new BigDecimal("0.25"), BigDecimal.ZERO, new BigDecimal("40"),
                ExamModels.NavigationPolicy.FREE_JUMP, "synthetic-blueprint", fixtureTime, null, null,
                List.of(), null, List.of(), false, fixtureTime, fixtureTime);
        when(exams.findLatestByCode("TR_YKS_TYT_MATH",false)).thenReturn(Optional.of(definition));
        var profiles=mock(CandidateProfileRepository.class); var signals=mock(ExamReadinessSignalReader.class);
        UUID user=UUID.randomUUID(); when(profiles.findProfileByUserId(user)).thenReturn(Optional.empty());
        when(signals.loadSkillMasterySignals(user)).thenReturn(Map.of()); when(signals.loadActiveTrapSignals(user)).thenReturn(List.of());
        var sessions=mock(PreExamStudySessionRepository.class); when(sessions.findFresh(eq(user),anyString(),anyString(),any())).thenReturn(Optional.empty());
        var mapper=JsonMapper.builder().build(); var metrics=new SimpleMeterRegistry();
        @SuppressWarnings("unchecked") ObjectProvider<Clock> clocks=mock(ObjectProvider.class);
        Instant now=Instant.parse("2026-11-18T09:00:00Z"); when(clocks.getIfAvailable(any())).thenReturn(Clock.fixed(now,ZoneOffset.UTC));
        var service=new PreExamReviewService(flags,exams,profiles,signals,sessions,new PreExamCatalog(mapper),mapper,clocks,metrics);
        TransactionSynchronizationManager.initSynchronization();
        try {
            Snapshot first=service.review(user,"TR_YKS_TYT_MATH");
            var json=mapper.readTree(first.reviewJson());
            assertEquals("NO_TARGET_DATE",json.path("windowStatus").asText());
            assertTrue(json.path("hoursRemaining").isNull());
            assertEquals("INSUFFICIENT_EVIDENCE",json.path("evidenceStatus").asText());
            assertEquals(0,json.path("activeTraps").size()); assertEquals(0,json.path("highYieldFormulas").size());
            assertEquals(2,json.path("genericReferences").size());
            assertFalse(json.path("trainingEligible").asBoolean()); assertEquals(0,json.path("llmTokensUsed").asInt());
            assertNotEquals(first.reviewEtag(),first.sheetEtag());
            when(sessions.findFresh(user,"TR_YKS_TYT_MATH",first.sourceFingerprint(),now)).thenReturn(Optional.of(first));
            assertEquals(first,service.review(user,"TR_YKS_TYT_MATH"));
            verify(sessions,times(1)).insert(any());
            assertEquals(1,TransactionSynchronizationManager.getSynchronizations().size());
            TransactionSynchronizationManager.getSynchronizations().forEach(s->s.afterCommit());
            assertEquals(1.0,metrics.counter("pre_exam_mode_activated").count());
        } finally { TransactionSynchronizationManager.clearSynchronization(); }
    }
}
