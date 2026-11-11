package com.verifiedai.analytics;

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

class AnalyticsPrivacyLifecycleContributorTest {

    private LearnerMasteryCalibrationRepository repository;
    private AnalyticsPrivacyLifecycleContributor contributor;

    private final UUID learnerAId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @BeforeEach
    void setUp() {
        repository = mock(LearnerMasteryCalibrationRepository.class);
        contributor = new AnalyticsPrivacyLifecycleContributor(repository);
    }

    @Test
    @DisplayName("Contributor category is 'analytics'")
    void category() {
        assertThat(contributor.category()).isEqualTo("analytics");
    }

    @Test
    @DisplayName("deleteUserData invokes indexed bulk deletion (< 500ms SLA)")
    void deleteUserDataSingleQueryPurge() {
        long start = System.currentTimeMillis();

        when(repository.deleteAllByUserId(learnerAId)).thenReturn(5);

        contributor.deleteUserData(learnerAId, Instant.now());

        long duration = System.currentTimeMillis() - start;
        assertThat(duration).isLessThan(500);
        verify(repository, times(1)).deleteAllByUserId(learnerAId);
    }

    @Test
    @DisplayName("exportUserData formats calibration records into safe map")
    void exportUserData() {
        LearnerMasteryCalibrationRecordEntity record = new LearnerMasteryCalibrationRecordEntity(
            learnerAId,
            UUID.randomUUID(),
            "ALG_LIN_EQ_01",
            new BigDecimal("0.8500"),
            (short) 1,
            new BigDecimal("0.02250"),
            Instant.now()
        );

        when(repository.findByUserIdOrderByCreatedAtDesc(learnerAId))
            .thenReturn(List.of(record));

        Map<String, Object> exported = contributor.exportUserData(learnerAId);

        assertThat(exported.get("category")).isEqualTo("analytics");
        assertThat(exported.get("recordCount")).isEqualTo(1);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) exported.get("records");
        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("canonicalSkillCode")).isEqualTo("ALG_LIN_EQ_01");
        assertThat(records.get(0).get("predictedRetentionProb")).isEqualTo("0.8500");
        assertThat(records.get(0).get("observedIndependentOutcome")).isEqualTo(1);
        assertThat(records.get(0).get("brierResidual")).isEqualTo("0.02250");
    }
}
