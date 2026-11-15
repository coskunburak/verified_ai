package com.verifiedai.exam.infrastructure.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.verifiedai.exam.application.CandidateProfileRepository;
import com.verifiedai.exam.application.ExamRepository;
import com.verifiedai.exam.domain.AppliedAccommodationSnapshot;
import com.verifiedai.exam.domain.AssembledAssessment;
import com.verifiedai.exam.domain.AssembledAssessmentItem;
import com.verifiedai.exam.domain.AssembledAssessmentRepository;
import com.verifiedai.exam.domain.AssembledAssessmentStatus;
import com.verifiedai.exam.domain.AssemblyDistributionSummary;
import com.verifiedai.exam.domain.AssessmentBlueprintMode;
import com.verifiedai.exam.domain.CandidateProfileModels.AccessibilityAccommodations;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileRecord;
import com.verifiedai.exam.domain.CandidateProfileModels.FeasibilityStatus;
import com.verifiedai.exam.domain.CognitiveTaxonomyLevel;
import com.verifiedai.exam.domain.ExamModels.Jurisdiction;
import com.verifiedai.exam.domain.ExamModels.StudentExamTargetRecord;
import com.verifiedai.exam.domain.ExamModels.TargetStatus;
import com.verifiedai.exam.domain.ExamSession;
import com.verifiedai.exam.domain.ExamSessionStatus;
import com.verifiedai.exam.domain.QuestionDifficultyBand;
import com.verifiedai.exam.domain.QuestionItemFormat;
import com.verifiedai.exam.infrastructure.persistence.ExamSessionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ExamPrivacyLifecycleContributorTest {

    private ExamRepository examRepository;
    private ExamPrivacyLifecycleContributor contributor;
    private final UUID userId = UUID.fromString("91000000-0000-4000-8000-000000000111");

    @BeforeEach
    void setUp() {
        examRepository = mock(ExamRepository.class);
        contributor = new ExamPrivacyLifecycleContributor(examRepository);
    }

    @Test
    void categoryMatchesExamAndDeleteCompletesUnderSla() {
        assertThat(contributor.category()).isEqualTo("exam");

        long start = System.currentTimeMillis();
        when(examRepository.deleteTargetsByUserId(userId)).thenReturn(2);

        contributor.deleteUserData(userId, Instant.now());

        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed).isLessThan(500L);
        verify(examRepository, times(1)).deleteTargetsByUserId(userId);
    }

    @Test
    void exportUserDataIsNonStigmatizingAndNonTraining() {
        StudentExamTargetRecord record = new StudentExamTargetRecord(
                UUID.randomUUID(),
                userId,
                UUID.randomUUID(),
                "TR_YKS_TYT_MATH",
                "2026.1",
                "c1cb8c26245fdee1b53c164a94e35a6cecbde8bae6ece948e60f577df53e1391",
                new BigDecimal("34.50"),
                LocalDate.of(2027, 6, 15),
                1,
                TargetStatus.ACTIVE,
                true,
                "exam-export-test-001",
                "{}",
                null,
                0,
                false,
                Instant.parse("2026-11-14T10:00:00Z"),
                Instant.parse("2026-11-14T10:00:00Z")
        );
        when(examRepository.findTargetsByUserId(userId)).thenReturn(List.of(record));

        Map<String, Object> exported = contributor.exportUserData(userId);

        assertThat(exported.get("category")).isEqualTo("exam");
        assertThat(exported.get("studentTargetCount")).isEqualTo(1);
        assertThat(exported.get("llmTokensUsed")).isEqualTo(0);
        assertThat(exported.get("trainingEligible")).isEqualTo(false);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> targets = (List<Map<String, Object>>) exported.get("studentTargets");
        assertThat(targets).hasSize(1);
        assertThat(targets.getFirst().get("examCode")).isEqualTo("TR_YKS_TYT_MATH");
        assertThat(targets.getFirst().get("trainingEligible")).isEqualTo(false);

        String dumped = exported.toString().toLowerCase();
        for (String banned : List.of("failurerate", "errorrate", "weaknessscore", "incompetence", "zayıf yön")) {
            assertThat(dumped).doesNotContain(banned);
        }
    }

    @Test
    void exportDeleteAndPurgeIncludeCandidateProfileSyllabusUploadsAndAssembledAssessmentsInFkSafeOrder() {
        var candidateRepo = mock(CandidateProfileRepository.class);
        var assembledRepo = mock(AssembledAssessmentRepository.class);
        Instant now = Instant.parse("2026-11-15T10:00:00Z");

        when(examRepository.findTargetsByUserId(userId)).thenReturn(List.of());
        when(candidateRepo.findProfileByUserId(userId)).thenReturn(Optional.of(
                new CandidateProfileRecord(
                        UUID.randomUUID(),
                        userId,
                        UUID.randomUUID(),
                        "TR_YKS_TYT_MATH",
                        "2026.1",
                        null,
                        Jurisdiction.TR,
                        LocalDate.of(2027, 6, 20),
                        new BigDecimal("35.0"),
                        60,
                        420,
                        AccessibilityAccommodations.standardDefaults(),
                        new BigDecimal("0.25"),
                        15,
                        FeasibilityStatus.OPTIMAL,
                        "key-privacy",
                        "candidate-profile-v1",
                        false,
                        now,
                        now
                )
        ));
        when(candidateRepo.findHorizonHistoryByUserId(userId, 100)).thenReturn(List.of());
        when(candidateRepo.findSyllabusUploadsByUserId(userId, 100)).thenReturn(List.of());
        when(candidateRepo.purgeExpiredSyllabusUploads(now)).thenReturn(3);

        UUID assessmentId = UUID.randomUUID();
        AssembledAssessment assembled = new AssembledAssessment(
                assessmentId,
                userId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "BP-TYT-MATH-DIAG-2026-V1",
                "TYT Mathematics Diagnostic Baseline",
                AssessmentBlueprintMode.DIAGNOSTIC_BASELINE,
                UUID.fromString("91000000-0000-4000-8000-000000000101"),
                1,
                "seed-privacy-test",
                "4040404040404040404040404040404040404040404040404040404040404040",
                5,
                900,
                new BigDecimal("1.25"),
                1125,
                new AppliedAccommodationSnapshot(new BigDecimal("1.25"), false, false, false),
                new AssemblyDistributionSummary(Map.of(), Map.of(), Map.of(), Map.of(), 0),
                AssembledAssessmentStatus.ASSEMBLED,
                now,
                now.plusSeconds(86400),
                false,
                List.of(
                        new AssembledAssessmentItem(
                                UUID.randomUUID(),
                                assessmentId,
                                UUID.randomUUID(),
                                "TYT-MATH-2026-INT-001",
                                1,
                                "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                                null,
                                QuestionItemFormat.SINGLE_CHOICE_MCQ,
                                QuestionDifficultyBand.EASY,
                                CognitiveTaxonomyLevel.RECALL,
                                "Evaluate (-18) + 27 - (-9)",
                                null,
                                null,
                                false,
                                List.of("A", "B", "C", "D", "E"),
                                List.of(),
                                new BigDecimal("1.00"),
                                60,
                                false
                        )
                )
        );
        when(assembledRepo.findByUserId(userId, 100)).thenReturn(List.of(assembled));
        when(assembledRepo.deleteByUserId(userId)).thenReturn(1);

        ExamPrivacyLifecycleContributor fullContributor = new ExamPrivacyLifecycleContributor(
                examRepository,
                candidateRepo,
                assembledRepo,
                Clock.fixed(now, ZoneOffset.UTC)
        );
        Map<String, Object> exported = fullContributor.exportUserData(userId);
        assertThat(exported.get("category")).isEqualTo("exam");
        assertThat(exported.get("candidateProfile")).isNotNull();
        assertThat(exported.get("assembledAssessmentCount")).isEqualTo(1);
        assertThat(exported.get("trainingEligible")).isEqualTo(false);
        assertThat(fullContributor.purgeExpiredSyllabusArtifacts(now)).isEqualTo(3);

        fullContributor.deleteUserData(userId, now);

        InOrder order = inOrder(assembledRepo, candidateRepo, examRepository);
        order.verify(assembledRepo).deleteByUserId(userId);
        order.verify(candidateRepo).deleteAllCandidateDataForUser(userId);
        order.verify(examRepository).deleteTargetsByUserId(userId);
    }

    @Test
    void exportAndDeleteIncludeTimedMockExamSessionsAndScratchpadsInChildFirstOrder() {
        var candidateRepo = mock(CandidateProfileRepository.class);
        var assembledRepo = mock(AssembledAssessmentRepository.class);
        var sessionRepo = mock(ExamSessionRepository.class);
        Instant now = Instant.parse("2026-11-15T10:00:00Z");

        when(examRepository.findTargetsByUserId(userId)).thenReturn(List.of());
        when(candidateRepo.findProfileByUserId(userId)).thenReturn(Optional.empty());
        when(candidateRepo.findHorizonHistoryByUserId(userId, 100)).thenReturn(List.of());
        when(candidateRepo.findSyllabusUploadsByUserId(userId, 100)).thenReturn(List.of());
        when(assembledRepo.findByUserId(userId, 100)).thenReturn(List.of());

        UUID sessionId = UUID.randomUUID();
        ExamSession session = new ExamSession(
                sessionId,
                userId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "TR_YKS_TYT_MATH",
                1,
                ExamSessionStatus.SUBMITTED,
                "FREE_NAVIGATION",
                true,
                new AppliedAccommodationSnapshot(new BigDecimal("1.00"), false, false, false),
                900,
                new BigDecimal("1.00"),
                900,
                120,
                now,
                now.plusSeconds(900),
                now.plusSeconds(1020),
                now,
                null,
                now.plusSeconds(300),
                "USER_INITIATED",
                "submit-privacy-1",
                1,
                1,
                0,
                0,
                0,
                0L,
                null,
                false,
                now,
                now.plusSeconds(300),
                List.of()
        );
        when(sessionRepo.findByUserId(userId)).thenReturn(List.of(session));
        when(sessionRepo.deleteByUserId(userId)).thenReturn(1);

        ExamPrivacyLifecycleContributor contributorWithSessions = new ExamPrivacyLifecycleContributor(
                examRepository,
                candidateRepo,
                assembledRepo,
                sessionRepo,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        Map<String, Object> exported = contributorWithSessions.exportUserData(userId);
        assertThat(exported.get("examSessionCount")).isEqualTo(1);
        assertThat(exported.get("examSessions")).isInstanceOf(List.class);
        assertThat(exported.get("trainingEligible")).isEqualTo(false);

        contributorWithSessions.deleteUserData(userId, now);

        InOrder order = inOrder(sessionRepo, assembledRepo, candidateRepo, examRepository);
        order.verify(sessionRepo).deleteByUserId(userId);
        order.verify(assembledRepo).deleteByUserId(userId);
        order.verify(candidateRepo).deleteAllCandidateDataForUser(userId);
        order.verify(examRepository).deleteTargetsByUserId(userId);
    }
}
