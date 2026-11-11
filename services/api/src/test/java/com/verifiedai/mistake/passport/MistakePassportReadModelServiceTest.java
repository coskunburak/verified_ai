package com.verifiedai.mistake.passport;

import com.verifiedai.mistake.passport.dto.MistakePassportResponseDto;
import com.verifiedai.mistake.passport.dto.MistakePassportResponseDto.*;
import com.verifiedai.mistake.passport.repository.MistakePassportProjectionRepository;
import com.verifiedai.mistake.passport.service.MistakePassportReadModelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MistakePassportReadModelServiceTest {

    private MistakePassportProjectionRepository repository;
    private MistakePassportReadModelService service;

    private final UUID learnerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        repository = mock(MistakePassportProjectionRepository.class);
        service = new MistakePassportReadModelService(repository);
    }

    @Test
    @DisplayName("Aggregates summary metrics exclusively over conceptual traps and isolates ARITH_BASIC_COMPUTATION")
    void partitionsConceptualAndArithmeticTrapsCorrectly() {
        ExamTrapItemDto trap1 = createTrapItem(
            UUID.randomUUID(),
            "ALG_SIGN_DISTRIBUTION",
            "CONCEPTUAL_STRUCTURAL",
            TrapLifecycleStatus.RECURRING_PATTERN,
            new BigDecimal("0.0000"),
            new BigDecimal("0.8000"),
            new BigDecimal("0.7000"),
            new BigDecimal("0.6000"),
            new BigDecimal("0.2000")
        );

        ExamTrapItemDto trap2 = createTrapItem(
            UUID.randomUUID(),
            "INEQ_NEGATIVE_MULT_NO_FLIP",
            "CONCEPTUAL_STRUCTURAL",
            TrapLifecycleStatus.STABLE_RESOLVED,
            new BigDecimal("1.0000"),
            new BigDecimal("1.0000"),
            new BigDecimal("0.9000"),
            new BigDecimal("0.8000"),
            new BigDecimal("0.0000")
        );

        ExamTrapItemDto trap3 = createTrapItem(
            UUID.randomUUID(),
            "CALC_CHAIN_RULE_OMITTED",
            "CONCEPTUAL_STRUCTURAL",
            TrapLifecycleStatus.RESOLVED_PENDING_RETENTION,
            new BigDecimal("1.0000"),
            new BigDecimal("0.6000"),
            new BigDecimal("0.5000"),
            new BigDecimal("0.4000"),
            new BigDecimal("0.4000")
        );

        ExamTrapItemDto slip = createTrapItem(
            UUID.randomUUID(),
            "ARITH_BASIC_COMPUTATION",
            "ARITHMETIC_SLIP",
            TrapLifecycleStatus.RECURRING_PATTERN,
            new BigDecimal("0.0000"),
            new BigDecimal("0.4000"),
            new BigDecimal("0.3000"),
            new BigDecimal("0.2000"),
            new BigDecimal("0.6000")
        );

        when(repository.fetchPassportItemsByLearnerId(learnerId))
            .thenReturn(List.of(trap1, trap2, trap3, slip));

        MistakePassportResponseDto response = service.getLearnerMistakePassport(learnerId, null);

        assertThat(response.schemaVersion()).isEqualTo("mistake-passport.v1");
        assertThat(response.learnerId()).isEqualTo(learnerId);

        PassportSummaryMetrics summary = response.summaryMetrics();
        assertThat(summary.conqueredExamTrapsCount()).isEqualTo(1); // trap2 only
        assertThat(summary.activeExamTrapsCount()).isEqualTo(1);    // trap1 only
        assertThat(summary.pendingRetentionVerificationCount()).isEqualTo(1); // trap3 only

        // Mean independent transfer rate across conceptual traps: (0.8 + 1.0 + 0.6) / 3 = 0.8000
        assertThat(summary.independentTransferRate()).isEqualByComparingTo("0.8000");
        // Mean posterior mastery: (0.7 + 0.9 + 0.5) / 3 = 0.7000
        assertThat(summary.meanPosteriorMastery()).isEqualByComparingTo("0.7000");
        // Mean retention: (0.6 + 0.8 + 0.4) / 3 = 0.6000
        assertThat(summary.meanRetentionProbability()).isEqualByComparingTo("0.6000");

        assertThat(response.conceptualTraps()).containsExactly(trap1, trap2, trap3);
        assertThat(response.arithmeticSlips()).containsExactly(slip);
    }

    @Test
    @DisplayName("Applies lifecycle status filter while preserving summary metric calculations across all conceptual traps")
    void appliesLifecycleFilterCorrectly() {
        ExamTrapItemDto trap1 = createTrapItem(
            UUID.randomUUID(), "ALG_SIGN_DISTRIBUTION", "CONCEPTUAL_STRUCTURAL",
            TrapLifecycleStatus.RECURRING_PATTERN,
            BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO
        );

        ExamTrapItemDto trap2 = createTrapItem(
            UUID.randomUUID(), "INEQ_NEGATIVE_MULT_NO_FLIP", "CONCEPTUAL_STRUCTURAL",
            TrapLifecycleStatus.STABLE_RESOLVED,
            BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO
        );

        when(repository.fetchPassportItemsByLearnerId(learnerId))
            .thenReturn(List.of(trap1, trap2));

        MistakePassportResponseDto filteredResponse = service.getLearnerMistakePassport(
            learnerId,
            TrapLifecycleStatus.STABLE_RESOLVED
        );

        assertThat(filteredResponse.summaryMetrics().conqueredExamTrapsCount()).isEqualTo(1);
        assertThat(filteredResponse.summaryMetrics().activeExamTrapsCount()).isEqualTo(1);
        assertThat(filteredResponse.conceptualTraps()).containsExactly(trap2);
        assertThat(filteredResponse.arithmeticSlips()).isEmpty();
    }

    private ExamTrapItemDto createTrapItem(
            UUID id,
            String code,
            String family,
            TrapLifecycleStatus status,
            BigDecimal outcome,
            BigDecimal transfer,
            BigDecimal mastery,
            BigDecimal retention,
            BigDecimal scaffold
    ) {
        return new ExamTrapItemDto(
            id,
            code,
            family,
            "SKILL.NODE",
            "trap." + code.toLowerCase() + ".title",
            "trap." + code.toLowerCase() + ".coaching_framing",
            status,
            1,
            0,
            Instant.now(),
            null,
            null,
            new SkillFiveMetricsSnapshot(outcome, transfer, mastery, retention, scaffold),
            null,
            null
        );
    }
}
