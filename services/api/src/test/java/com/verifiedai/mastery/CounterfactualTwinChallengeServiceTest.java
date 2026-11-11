package com.verifiedai.mastery;

import com.verifiedai.mastery.api.dto.VerifiedTwinChallengeResponse;
import com.verifiedai.mastery.application.CounterfactualTwinChallengeService;
import com.verifiedai.mastery.application.MathVerifierTwinPort;
import com.verifiedai.mastery.application.TwinSolutionLockedException;
import com.verifiedai.mastery.application.TwinSubmissionCommand;
import com.verifiedai.mastery.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CounterfactualTwinChallengeServiceTest {

    @Mock private MasteryStateLockStore lockStore;
    @Mock private AttemptQueryPort attemptQueryPort;
    @Mock private MathVerifierTwinPort mathVerifierTwinPort;
    @Mock private CounterfactualTwinRepository twinRepository;
    @Mock private CounterfactualTwinSubmissionRepository submissionRepository;
    @Mock private LearnerSkillMasteryRepository masteryRepository;

    private final Instant createdAt = Instant.parse("2026-11-11T12:00:00Z");
    private final Instant within60sWindow = Instant.parse("2026-11-11T12:00:25Z");
    private CounterfactualTwinChallengeService service;

    @BeforeEach
    void setUp() {
        when(lockStore.lock(any())).thenReturn(() -> {});
        Clock clockAt25s = Clock.fixed(within60sWindow, ZoneOffset.UTC);
        service = new CounterfactualTwinChallengeService(
                lockStore, attemptQueryPort, mathVerifierTwinPort,
                twinRepository, submissionRepository, masteryRepository, clockAt25s
        );
    }

    @Test
    void revealSolutionBefore60sWithoutForfeitThrows423Locked() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID twinId = UUID.randomUUID();

        CounterfactualTwinDrillEntity issuedTwin = createIssuedTwin(tenantId, userId, twinId);
        when(twinRepository.findByTenantIdAndUserIdAndTwinId(tenantId, userId, twinId))
                .thenReturn(Optional.of(issuedTwin));

        TwinSubmissionCommand prematureReveal = new TwinSubmissionCommand("REVEAL_SOLUTION", null, false);

        assertThatThrownBy(() -> service.submitOrRevealTwin(tenantId, userId, twinId, prematureReveal))
                .isInstanceOf(TwinSolutionLockedException.class);
    }

    @Test
    void earlyForfeitRevealGrantsZeroCreditAndDoesNotClearAtRiskState() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID twinId = UUID.randomUUID();

        CounterfactualTwinDrillEntity issuedTwin = createIssuedTwin(tenantId, userId, twinId);
        LearnerSkillMasteryEntity atRiskMastery = LearnerSkillMasteryEntity.rehydrateForTest(
                tenantId, userId, "MATH_09", "ALG_LIN_ISO_01",
                new BigDecimal("0.4200"), "AT_RISK_REMEDIATION_REQUIRED", 3, 2, 3
        );

        when(twinRepository.findByTenantIdAndUserIdAndTwinId(tenantId, userId, twinId))
                .thenReturn(Optional.of(issuedTwin));
        when(masteryRepository.findByTenantIdAndUserIdAndSkillCode(tenantId, userId, "ALG_LIN_ISO_01"))
                .thenReturn(Optional.of(atRiskMastery));

        TwinSubmissionCommand forfeitCommand = new TwinSubmissionCommand("REVEAL_SOLUTION", null, true);
        VerifiedTwinChallengeResponse res = service.submitOrRevealTwin(tenantId, userId, twinId, forfeitCommand);

        assertThat(res.status()).isEqualTo("SOLUTION_REVEALED_ASSISTED");
        assertThat(res.evaluation().evidenceClassification()).isEqualTo("SOLUTION_REVEALED_ASSISTED");
        assertThat(res.evaluation().remediationCredit()).isEqualByComparingTo("0.0000");
        assertThat(res.evaluation().remediationCleared()).isFalse();
        assertThat(res.solutionSteps()).isNotEmpty();
    }

    @Test
    void unassistedCorrectAnswerGrants015CreditAndClearsRemediationWhenAboveExitFloor() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID twinId = UUID.randomUUID();

        CounterfactualTwinDrillEntity issuedTwin = createIssuedTwin(tenantId, userId, twinId);
        LearnerSkillMasteryEntity atRiskMastery = LearnerSkillMasteryEntity.rehydrateForTest(
                tenantId, userId, "MATH_09", "ALG_LIN_ISO_01",
                new BigDecimal("0.4500"), "AT_RISK_REMEDIATION_REQUIRED", 3, 2, 3
        );

        when(twinRepository.findByTenantIdAndUserIdAndTwinId(tenantId, userId, twinId))
                .thenReturn(Optional.of(issuedTwin));
        when(masteryRepository.findByTenantIdAndUserIdAndSkillCode(tenantId, userId, "ALG_LIN_ISO_01"))
                .thenReturn(Optional.of(atRiskMastery));
        when(mathVerifierTwinPort.verifySymbolicEquivalence("x = 4", "4")).thenReturn(true);

        TwinSubmissionCommand solveCommand = new TwinSubmissionCommand("SUBMIT_ANSWER", "x = 4", false);
        VerifiedTwinChallengeResponse res = service.submitOrRevealTwin(tenantId, userId, twinId, solveCommand);

        assertThat(res.status()).isEqualTo("SOLVED_UNASSISTED");
        assertThat(res.evaluation().evidenceClassification()).isEqualTo("VERIFIED_UNASSISTED_SUCCESS");
        assertThat(res.evaluation().remediationCredit()).isEqualByComparingTo("0.1500");
        assertThat(res.evaluation().updatedMasteryScore()).isEqualByComparingTo("0.6000");
        assertThat(res.evaluation().remediationCleared()).isTrue();
    }

    private CounterfactualTwinDrillEntity createIssuedTwin(UUID tenantId, UUID userId, UUID twinId) {
        return new CounterfactualTwinDrillEntity(
                twinId, tenantId, userId, UUID.randomUUID(),
                "MATH_09", "ALG_LIN_EQ_02", "ALG_LIN_ISO_01",
                "4x + 3 = 19", "4",
                "[{\"stepIndex\":1,\"explanation\":\"Subtract 3\",\"latexExpression\":\"4x = 16\"}]",
                "seed_abc", "a".repeat(64),
                "ISSUED", createdAt, createdAt.plusSeconds(60), false
        );
    }
}
