package com.verifiedai.studyplan.privacy;

import com.verifiedai.studyplan.domain.LearnerRecommendationEntity;
import com.verifiedai.studyplan.domain.LearnerRecommendationRepository;
import com.verifiedai.studyplan.domain.RecommendationRationaleCode;
import com.verifiedai.studyplan.domain.RecommendationStatus;
import com.verifiedai.studyplan.domain.RecommendationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StudyPlanPrivacyLifecycleContributorTest {

    private LearnerRecommendationRepository repository;
    private StudyPlanPrivacyLifecycleContributor contributor;

    private final UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        repository = mock(LearnerRecommendationRepository.class);
        contributor = new StudyPlanPrivacyLifecycleContributor(repository);
    }

    @Test
    @DisplayName("Category matches 'study_plan'")
    void testCategory() {
        assertThat(contributor.category()).isEqualTo("study_plan");
    }

    @Test
    @DisplayName("deleteUserData executes single-query purge under 500ms SLA")
    void testDeleteUserDataSla() {
        long start = System.currentTimeMillis();
        when(repository.deleteAllByUserId(userId)).thenReturn(3);

        contributor.deleteUserData(userId, Instant.now());

        long elapsed = System.currentTimeMillis() - start;
        assertThat(elapsed).isLessThan(500);
        verify(repository, times(1)).deleteAllByUserId(userId);
    }

    @Test
    @DisplayName("exportUserData produces clean map with zero stigmatizing terms")
    void testExportUserDataCleanLexicon() {
        LearnerRecommendationEntity entity = new LearnerRecommendationEntity(
                UUID.randomUUID(),
                userId,
                RecommendationType.MISTAKE_REPAIR,
                "MATH.ALG.FACTORING",
                "SIGN_TRAP",
                new BigDecimal("65.0000"),
                RecommendationRationaleCode.ACTIVE_RECURRING_TRAP,
                "{\"skillCode\":\"MATH.ALG.FACTORING\",\"mistakeCode\":\"SIGN_TRAP\",\"mistakePropensity\":65.0,\"unresolvedTrapCount\":2}",
                RecommendationStatus.ACTIVE,
                Instant.now().plusSeconds(86400),
                Instant.now()
        );

        when(repository.findAllByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(entity));

        Map<String, Object> exported = contributor.exportUserData(userId);

        assertThat(exported.get("category")).isEqualTo("study_plan");
        assertThat(exported.get("recordCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> recs = (List<Map<String, Object>>) exported.get("recommendations");
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).get("targetSkillCode")).isEqualTo("MATH.ALG.FACTORING");
        assertThat(recs.get(0).get("recommendationType")).isEqualTo("MISTAKE_REPAIR");
        assertThat(recs.get(0).get("priorityScore")).isEqualTo(65.0);

        // Non-stigmatizing audit on exported content
        String exportedString = exported.toString();
        List<String> bannedTerms = List.of(
                "failureRate", "errorRate", "failure_rate", "error_rate",
                "weaknessScore", "weakness_score", "incompetent", "incompetence",
                "başarısızlık oranı", "hata oranı", "zayıf yön"
        );
        for (String term : bannedTerms) {
            assertThat(exportedString.toLowerCase()).doesNotContain(term.toLowerCase());
        }
    }
}
