package com.verifiedai.exam.infrastructure.privacy;

import com.verifiedai.exam.application.ExamRepository;
import com.verifiedai.exam.domain.ExamModels.StudentExamTargetRecord;
import com.verifiedai.exam.domain.ExamModels.TargetStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void exportDeleteAndPurgeIncludeCandidateProfileAndSyllabusUploads() {
        var candidateRepo = mock(com.verifiedai.exam.application.CandidateProfileRepository.class);
        Instant now = Instant.parse("2026-11-15T10:00:00Z");

        when(examRepository.findTargetsByUserId(userId)).thenReturn(List.of());
        when(candidateRepo.findProfileByUserId(userId)).thenReturn(java.util.Optional.of(
                new com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileRecord(
                        UUID.randomUUID(),
                        userId,
                        UUID.randomUUID(),
                        "TR_YKS_TYT_MATH",
                        "2026.1",
                        null,
                        com.verifiedai.exam.domain.ExamModels.Jurisdiction.TR,
                        LocalDate.of(2027, 6, 20),
                        new BigDecimal("35.0"),
                        60,
                        420,
                        com.verifiedai.exam.domain.CandidateProfileModels.AccessibilityAccommodations.standardDefaults(),
                        new BigDecimal("0.25"),
                        15,
                        com.verifiedai.exam.domain.CandidateProfileModels.FeasibilityStatus.OPTIMAL,
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

        ExamPrivacyLifecycleContributor fullContributor = new ExamPrivacyLifecycleContributor(
                examRepository,
                candidateRepo,
                java.time.Clock.fixed(now, java.time.ZoneOffset.UTC)
        );
        Map<String, Object> exported = fullContributor.exportUserData(userId);
        assertThat(exported.get("category")).isEqualTo("exam");
        assertThat(exported.get("candidateProfile")).isNotNull();
        assertThat(exported.get("trainingEligible")).isEqualTo(false);
        assertThat(fullContributor.purgeExpiredSyllabusArtifacts(now)).isEqualTo(3);

        fullContributor.deleteUserData(userId, now);
        verify(candidateRepo, times(1)).deleteAllCandidateDataForUser(userId);
        verify(examRepository, times(1)).deleteTargetsByUserId(userId);
    }
}
