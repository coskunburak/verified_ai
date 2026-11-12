package com.verifiedai.studyplan.engine;

import com.verifiedai.studyplan.domain.RecommendationRationaleCode;
import com.verifiedai.studyplan.domain.RecommendationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NextBestActionRecommendationEngineTest {

    private NextBestActionRecommendationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new NextBestActionRecommendationEngine();
    }

    @Test
    @DisplayName("INV-COACH-001: Exactly computes 52.5000 score with BigDecimal precision")
    void testExactFormulaCalculation() {
        // P_{u,m} = 80.0, R_{u,s}(t) = 0.45, w_{E,s} = 0.60, I_{u,s} = 0.50
        SkillCandidateSignal signal = SkillCandidateSignal.builder("MATH.ALG.QUADRATICS.FACTORING")
                .dominantMistake("SIGN_DISTRIBUTION_TRAP", new BigDecimal("80.0000"), 3)
                .retention(new BigDecimal("0.4500"), new BigDecimal("14.00"))
                .independentMastery(new BigDecimal("0.5000"))
                .exam("AYT_MAT_2026", new BigDecimal("0.6000"))
                .build();

        ScoredRecommendation scored = engine.evaluateCandidate(signal);

        // Expected:
        // Mistake = 0.45 * 80.0 = 36.0000
        // Retention = 0.30 * (0.75 - 0.45) * 100 = 9.0000
        // ROI = 0.25 * (0.60 * (1 - 0.50) * 100) = 7.5000
        // Total = 52.5000
        assertThat(scored.priorityScore()).isEqualByComparingTo(new BigDecimal("52.5000"));
        assertThat(scored.recommendationType()).isEqualTo(RecommendationType.MISTAKE_REPAIR);
        assertThat(scored.rationaleCode()).isEqualTo(RecommendationRationaleCode.ACTIVE_RECURRING_TRAP);
        assertThat(scored.targetSkillCode()).isEqualTo("MATH.ALG.QUADRATICS.FACTORING");
        assertThat(scored.targetMistakeCode()).isEqualTo("SIGN_DISTRIBUTION_TRAP");
    }

    @Test
    @DisplayName("INV-COACH-002: Prerequisite bottleneck overrides target skill when prerequisite mastery < 0.50")
    void testPrerequisiteBottleneckOverride() {
        SkillCandidateSignal signal = SkillCandidateSignal.builder("MATH.CALC.DERIVATIVES.CHAIN_RULE")
                .dominantMistake(null, BigDecimal.ZERO, 0)
                .retention(new BigDecimal("0.8000"), new BigDecimal("30.00"))
                .independentMastery(new BigDecimal("0.3000"))
                .exam("AYT_MAT_2026", new BigDecimal("0.8500"))
                .weakestPrerequisite("MATH.CALC.LIMITS.BASIC", new BigDecimal("0.4200"))
                .build();

        ScoredRecommendation scored = engine.evaluateCandidate(signal);

        // Target skill should be overridden to prerequisite
        assertThat(scored.targetSkillCode()).isEqualTo("MATH.CALC.LIMITS.BASIC");
        assertThat(scored.recommendationType()).isEqualTo(RecommendationType.PREREQUISITE_REPAIR);
        assertThat(scored.rationaleCode()).isEqualTo(RecommendationRationaleCode.PREREQUISITE_REPAIR);
        assertThat(scored.targetMistakeCode()).isNull();

        assertThat(scored.rationaleParams()).containsEntry("prerequisiteSkillCode", "MATH.CALC.LIMITS.BASIC");
        assertThat(scored.rationaleParams()).containsEntry("blockedSkillCode", "MATH.CALC.DERIVATIVES.CHAIN_RULE");
        assertThat(scored.rationaleParams().get("currentMastery")).isEqualTo(0.42);
        assertThat(scored.rationaleParams().get("requiredMasteryThreshold")).isEqualTo(0.5);
    }

    @Test
    @DisplayName("Retention component is exactly 0.0000 when current retention >= 0.75")
    void testRetentionContributionZeroWhenAboveThreshold() {
        SkillCandidateSignal signal = SkillCandidateSignal.builder("MATH.TRIG.IDENTITIES")
                .dominantMistake(null, BigDecimal.ZERO, 0)
                .retention(new BigDecimal("0.7500"), new BigDecimal("30.00"))
                .independentMastery(new BigDecimal("0.6000"))
                .exam("AYT_MAT_2026", new BigDecimal("0.5000"))
                .build();

        ScoredRecommendation scored = engine.evaluateCandidate(signal);

        // Mistake = 0
        // Retention = 0.30 * max(0, 0.75 - 0.75) * 100 = 0.0000
        // ROI = 0.25 * (0.50 * (1 - 0.60) * 100) = 5.0000
        // Total = 5.0000
        assertThat(scored.priorityScore()).isEqualByComparingTo(new BigDecimal("5.0000"));
        assertThat(scored.recommendationType()).isEqualTo(RecommendationType.HIGH_ROI_TOPIC);
        assertThat(scored.rationaleCode()).isEqualTo(RecommendationRationaleCode.HIGH_EXAM_NET_ROI);
    }

    @Test
    @DisplayName("INV-COACH-003: Ranking and tie-breaking is 100% deterministic across 100 runs")
    void testDeterministicTieBreakingAcross100Runs() {
        List<SkillCandidateSignal> candidates = List.of(
                SkillCandidateSignal.builder("SKILL_C")
                        .dominantMistake(null, BigDecimal.ZERO, 0)
                        .retention(new BigDecimal("0.5000"), new BigDecimal("10.00"))
                        .independentMastery(new BigDecimal("0.4000"))
                        .exam("AYT", new BigDecimal("0.5000"))
                        .build(),
                SkillCandidateSignal.builder("SKILL_A")
                        .dominantMistake("TRAP_1", new BigDecimal("50.0000"), 2)
                        .retention(new BigDecimal("0.6000"), new BigDecimal("10.00"))
                        .independentMastery(new BigDecimal("0.4000"))
                        .exam("AYT", new BigDecimal("0.5000"))
                        .build(),
                SkillCandidateSignal.builder("SKILL_B")
                        .dominantMistake(null, BigDecimal.ZERO, 0)
                        .retention(new BigDecimal("0.8000"), new BigDecimal("10.00"))
                        .independentMastery(new BigDecimal("0.2000"))
                        .exam("AYT", new BigDecimal("0.8000"))
                        .weakestPrerequisite("SKILL_PREREQ", new BigDecimal("0.3500"))
                        .build()
        );

        List<String> firstRunCodes = engine.rankCandidates(candidates, 5).stream()
                .map(ScoredRecommendation::targetSkillCode)
                .toList();

        for (int i = 0; i < 100; i++) {
            List<SkillCandidateSignal> shuffled = new ArrayList<>(candidates);
            java.util.Collections.shuffle(shuffled);
            List<String> currentRunCodes = engine.rankCandidates(shuffled, 5).stream()
                    .map(ScoredRecommendation::targetSkillCode)
                    .toList();
            assertThat(currentRunCodes)
                    .as("Run " + i + " must produce identical ranking order")
                    .isEqualTo(firstRunCodes);
        }
    }

    @Test
    @DisplayName("Deduplication: Keeps highest priority score when multiple skills share the same target prerequisite")
    void testDeduplicationForSharedPrerequisite() {
        List<SkillCandidateSignal> candidates = List.of(
                SkillCandidateSignal.builder("ADVANCED_SKILL_1")
                        .dominantMistake(null, BigDecimal.ZERO, 0)
                        .retention(new BigDecimal("0.5000"), new BigDecimal("10.00"))
                        .independentMastery(new BigDecimal("0.2000"))
                        .exam("AYT", new BigDecimal("0.8000"))
                        .weakestPrerequisite("FOUNDATION_MATH", new BigDecimal("0.3000"))
                        .build(),
                SkillCandidateSignal.builder("ADVANCED_SKILL_2")
                        .dominantMistake(null, BigDecimal.ZERO, 0)
                        .retention(new BigDecimal("0.7000"), new BigDecimal("10.00"))
                        .independentMastery(new BigDecimal("0.5000"))
                        .exam("AYT", new BigDecimal("0.4000"))
                        .weakestPrerequisite("FOUNDATION_MATH", new BigDecimal("0.3000"))
                        .build()
        );

        List<ScoredRecommendation> ranked = engine.rankCandidates(candidates, 5);

        // Both point to FOUNDATION_MATH, should only have 1 active recommendation for FOUNDATION_MATH
        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).targetSkillCode()).isEqualTo("FOUNDATION_MATH");
        assertThat(ranked.get(0).recommendationType()).isEqualTo(RecommendationType.PREREQUISITE_REPAIR);
    }
}
