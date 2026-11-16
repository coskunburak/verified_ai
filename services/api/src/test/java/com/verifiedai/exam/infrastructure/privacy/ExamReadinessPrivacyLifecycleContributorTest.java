package com.verifiedai.exam.infrastructure.privacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.verifiedai.exam.application.CandidateProfileRepository;
import com.verifiedai.exam.application.ExamRepository;
import com.verifiedai.exam.domain.ActiveTrapImpactItem;
import com.verifiedai.exam.domain.AssembledAssessmentRepository;
import com.verifiedai.exam.domain.ExamReadinessConfidenceTier;
import com.verifiedai.exam.domain.ExamReadinessProjection;
import com.verifiedai.exam.domain.ExamReadinessRationaleCode;
import com.verifiedai.exam.domain.ExamReadinessRepository;
import com.verifiedai.exam.domain.ExamReadinessTopicAllocation;
import com.verifiedai.exam.domain.SectionReadinessBreakdown;
import com.verifiedai.exam.infrastructure.persistence.ExamScoringRepository;
import com.verifiedai.exam.infrastructure.persistence.ExamSessionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * Category C GDPR / KVKK Article 17 lifecycle tests for Sprint 9.6 exam readiness
 * projections and topic allocations inside {@link ExamPrivacyLifecycleContributor}.
 */
class ExamReadinessPrivacyLifecycleContributorTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-16T12:00:00Z");
    private static final UUID USER_ID = UUID.fromString("96000000-0000-4000-8000-000000000c01");

    @Test
    @SuppressWarnings("unchecked")
    void exportsReadinessProjectionsAndAllocationsAndPurgesChildFirst() {
        ExamRepository examRepository = mock(ExamRepository.class);
        CandidateProfileRepository candidateRepo = mock(CandidateProfileRepository.class);
        AssembledAssessmentRepository assembledRepo = mock(AssembledAssessmentRepository.class);
        ExamSessionRepository sessionRepo = mock(ExamSessionRepository.class);
        ExamScoringRepository scoringRepo = mock(ExamScoringRepository.class);
        ExamReadinessRepository readinessRepo = mock(ExamReadinessRepository.class);

        UUID projId = UUID.fromString("96000000-0000-4000-8000-000000000c11");
        UUID allocId = UUID.fromString("96000000-0000-4000-8000-000000000c22");

        ExamReadinessTopicAllocation allocation = new ExamReadinessTopicAllocation(
                allocId,
                "TYT_MATH",
                "MATH.ALGEBRA.FACTORING",
                "topic.math.algebra.factoring",
                new BigDecimal("10.00"),
                new BigDecimal("0.5500"),
                new BigDecimal("0.8000"),
                new BigDecimal("1.320"),
                45,
                1,
                ExamReadinessRationaleCode.ACTIVE_TRAP_MITIGATION,
                false
        );

        ExamReadinessProjection projection = new ExamReadinessProjection(
                projId,
                USER_ID,
                "TR_YKS_TYT_MATH",
                null,
                null,
                40,
                new BigDecimal("0.2500"),
                new BigDecimal("18.50"),
                new BigDecimal("24.00"),
                new BigDecimal("29.50"),
                new BigDecimal("0.90"),
                new BigDecimal("30.00"),
                new BigDecimal("6.00"),
                new BigDecimal("1.50"),
                new BigDecimal("0.9200"),
                new BigDecimal("0.8500"),
                new BigDecimal("0.6000"),
                ExamReadinessConfidenceTier.ESTABLISHED,
                false,
                0,
                3,
                15,
                1,
                List.of(new ActiveTrapImpactItem("ARITHMETIC_SLIP", "exam.readiness.trap.arithmeticSlip",
                        new BigDecimal("0.3000"), new BigDecimal("1.50"), 1)),
                List.of(new SectionReadinessBreakdown("TYT_MATH", "TYT Mathematics", 40,
                        new BigDecimal("18.50"), new BigDecimal("24.00"), new BigDecimal("29.50"),
                        new BigDecimal("0.6500"), new BigDecimal("0.8500"), new BigDecimal("0.6000"))),
                List.of(allocation),
                "EXAM_READINESS_V1",
                false,
                0,
                BigDecimal.ZERO,
                FIXED_NOW
        );

        when(examRepository.findTargetsByUserId(USER_ID)).thenReturn(List.of());
        when(readinessRepo.findAllByUserId(USER_ID)).thenReturn(List.of(projection));
        when(readinessRepo.findTopicAllocationsByUserId(USER_ID)).thenReturn(List.of(allocation));
        when(readinessRepo.deleteByUserId(USER_ID)).thenReturn(2);
        when(scoringRepo.deleteByUserId(USER_ID)).thenReturn(1);
        when(sessionRepo.deleteByUserId(USER_ID)).thenReturn(1);
        when(assembledRepo.deleteByUserId(USER_ID)).thenReturn(1);
        when(candidateRepo.deleteAllCandidateDataForUser(USER_ID)).thenReturn(1);
        when(examRepository.deleteTargetsByUserId(USER_ID)).thenReturn(1);

        ExamPrivacyLifecycleContributor contributor = new ExamPrivacyLifecycleContributor(
                examRepository,
                candidateRepo,
                assembledRepo,
                sessionRepo,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC)
        );
        contributor.setExamScoringRepository(scoringRepo);
        contributor.setExamReadinessRepository(readinessRepo);

        Map<String, Object> exported = contributor.exportUserData(USER_ID);
        assertEquals(false, exported.get("trainingEligible"));
        assertEquals(0, exported.get("llmTokensUsed"));

        List<Map<String, Object>> exportedProjections = (List<Map<String, Object>>) exported.get("examReadinessProjections");
        assertEquals(1, exportedProjections.size());
        assertEquals(projId.toString(), exportedProjections.get(0).get("projectionId"));
        assertEquals("TR_YKS_TYT_MATH", exportedProjections.get(0).get("examCode"));
        assertEquals(false, exportedProjections.get(0).get("trainingEligible"));
        assertEquals(0, exportedProjections.get(0).get("llmTokensUsed"));
        assertFalse(Boolean.TRUE.equals(exportedProjections.get(0).get("trainingEligible")));

        List<Map<String, Object>> exportedAllocations = (List<Map<String, Object>>) exported.get("examReadinessTopicAllocations");
        assertEquals(1, exportedAllocations.size());
        assertEquals(allocId.toString(), exportedAllocations.get(0).get("allocationId"));
        assertEquals(false, exportedAllocations.get(0).get("trainingEligible"));

        contributor.deleteUserData(USER_ID, FIXED_NOW);

        InOrder inOrder = inOrder(readinessRepo, scoringRepo, sessionRepo, assembledRepo, candidateRepo, examRepository);
        inOrder.verify(readinessRepo).deleteByUserId(USER_ID);
        inOrder.verify(scoringRepo).deleteByUserId(USER_ID);
        inOrder.verify(sessionRepo).deleteByUserId(USER_ID);
        inOrder.verify(assembledRepo).deleteByUserId(USER_ID);
        inOrder.verify(candidateRepo).deleteAllCandidateDataForUser(USER_ID);
        inOrder.verify(examRepository).deleteTargetsByUserId(USER_ID);
    }
}
