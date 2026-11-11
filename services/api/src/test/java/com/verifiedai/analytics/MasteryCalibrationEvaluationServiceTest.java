package com.verifiedai.analytics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MasteryCalibrationEvaluationServiceTest {

    private LearnerMasteryCalibrationRepository repository;
    private MasteryCalibrationEvaluationService service;

    private final UUID userId = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private final UUID attemptId = UUID.fromString("66666666-7777-8888-9999-000000000000");
    private final String skillCode = "ALG_LIN_EQ_01";

    @BeforeEach
    void setUp() {
        repository = mock(LearnerMasteryCalibrationRepository.class);
        service = new MasteryCalibrationEvaluationService(repository);
    }

    @Test
    @DisplayName("recordCalibrationObservation is idempotent when record already exists")
    void recordCalibrationObservationIdempotent() {
        LearnerMasteryCalibrationRecordEntity existing = new LearnerMasteryCalibrationRecordEntity(
            userId,
            attemptId,
            skillCode,
            new BigDecimal("0.7500"),
            (short) 1,
            new BigDecimal("0.06250"),
            Instant.now()
        );

        when(repository.findByAttemptIdAndCanonicalSkillCode(attemptId, skillCode))
            .thenReturn(Optional.of(existing));

        LearnerMasteryCalibrationRecordEntity result = service.recordCalibrationObservation(
            userId,
            attemptId,
            skillCode,
            new BigDecimal("0.7500"),
            1
        );

        assertThat(result).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("recordCalibrationObservation computes residual and persists when new")
    void recordCalibrationObservationPersistsNew() {
        when(repository.findByAttemptIdAndCanonicalSkillCode(attemptId, skillCode))
            .thenReturn(Optional.empty());

        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        LearnerMasteryCalibrationRecordEntity result = service.recordCalibrationObservation(
            userId,
            attemptId,
            skillCode,
            new BigDecimal("0.8000"),
            1
        );

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getAttemptId()).isEqualTo(attemptId);
        assertThat(result.getCanonicalSkillCode()).isEqualTo(skillCode);
        assertThat(result.getPredictedRetentionProb()).isEqualTo(new BigDecimal("0.8000"));
        assertThat(result.getObservedIndependentOutcome()).isEqualTo((short) 1);
        // (0.8 - 1)^2 = 0.04000
        assertThat(result.getBrierResidual()).isEqualTo(new BigDecimal("0.04000"));

        ArgumentCaptor<LearnerMasteryCalibrationRecordEntity> captor =
            ArgumentCaptor.forClass(LearnerMasteryCalibrationRecordEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getBrierResidual()).isEqualTo(new BigDecimal("0.04000"));
    }

    @Test
    @DisplayName("getCalibrationSummary correctly aggregates cohort metrics and per-skill slices")
    void getCalibrationSummaryAggregation() {
        Instant now = Instant.now();
        List<LearnerMasteryCalibrationRecordEntity> records = List.of(
            new LearnerMasteryCalibrationRecordEntity(
                userId, UUID.randomUUID(), "ALG_QUAD_EQ_01", new BigDecimal("0.7000"), (short) 1, new BigDecimal("0.09000"), now
            ),
            new LearnerMasteryCalibrationRecordEntity(
                userId, UUID.randomUUID(), "ALG_LIN_EQ_01", new BigDecimal("0.8000"), (short) 1, new BigDecimal("0.04000"), now
            ),
            new LearnerMasteryCalibrationRecordEntity(
                userId, UUID.randomUUID(), "ALG_LIN_EQ_01", new BigDecimal("0.8000"), (short) 0, new BigDecimal("0.64000"), now
            )
        );

        when(repository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
            .thenReturn(records);

        MasteryCalibrationSummaryDto summary = service.getCalibrationSummary(null, 30);

        assertThat(summary.sampleSize()).isEqualTo(3);
        assertThat(summary.canonicalSkillCodeFilter()).isNull();
        assertThat(summary.windowDays()).isEqualTo(30);
        assertThat(summary.reliabilityBins()).hasSize(10);
        // Slices sorted by canonicalSkillCode ascending: ALG_LIN_EQ_01, then ALG_QUAD_EQ_01
        assertThat(summary.skillSlices()).hasSize(2);
        assertThat(summary.skillSlices().get(0).canonicalSkillCode()).isEqualTo("ALG_LIN_EQ_01");
        assertThat(summary.skillSlices().get(0).sampleSize()).isEqualTo(2);
        assertThat(summary.skillSlices().get(1).canonicalSkillCode()).isEqualTo("ALG_QUAD_EQ_01");
        assertThat(summary.skillSlices().get(1).sampleSize()).isEqualTo(1);
    }
}
