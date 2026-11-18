package com.verifiedai.exam.domain;

import static org.junit.jupiter.api.Assertions.*;
import com.verifiedai.exam.domain.ExamModels.ExamTopicWeight;
import com.verifiedai.exam.domain.ExamReadinessCalculator.ActiveTrapSignal;
import com.verifiedai.exam.domain.ExamReadinessCalculator.SkillMasterySignal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class PreExamReviewPolicyTest {
    private final PreExamReviewPolicy policy = new PreExamReviewPolicy();
    private static final String SKILL = "MATH.ALGEBRA.FACTORING";
    private SkillMasterySignal mastery(double score) {
        return new SkillMasterySignal(SKILL, score, 1, 1, 3, Instant.parse("2026-11-18T00:00:00Z"));
    }
    private ExamTopicWeight weight() {
        return new ExamTopicWeight(null, null, "MATH", SKILL, "MATH.ALGEBRA",
                BigDecimal.ONE, 0, 10, null, 0);
    }
    private ActiveTrapSignal signal(String code) { return new ActiveTrapSignal(code, "ALGEBRA", SKILL, 0.5, 2); }
    private Set<PreExamReviewPolicy.CatalogKey> keys(String... codes) {
        Set<PreExamReviewPolicy.CatalogKey> result = new HashSet<>();
        for (String code : codes) result.add(new PreExamReviewPolicy.CatalogKey(SKILL, code));
        return result;
    }
    @Test void exactMasteryFloorAndMissingEvidence() {
        var signals = List.of(signal("SYNTHETIC_A"));
        assertEquals(1, policy.select(Map.of(SKILL, mastery(0.5)), signals, List.of(weight()), keys("SYNTHETIC_A")).traps().size());
        var low = policy.select(Map.of(SKILL, mastery(0.4999)), signals, List.of(weight()), keys("SYNTHETIC_A"));
        assertTrue(low.traps().isEmpty());
        assertEquals(1, low.excludedLowMasteryTopicCount());
        assertTrue(policy.select(Map.of(), signals, List.of(weight()), keys("SYNTHETIC_A")).traps().isEmpty());
        assertTrue(policy.select(Map.of(SKILL, mastery(Double.NaN)), signals, List.of(weight()), keys("SYNTHETIC_A")).traps().isEmpty());
    }
    @Test void rejectsUnapprovedAndInvalidSignals() {
        var bad = List.of(new ActiveTrapSignal("SYNTHETIC_A", "ALGEBRA", SKILL, Double.NaN, 3),
                new ActiveTrapSignal("SYNTHETIC_A", "ALGEBRA", SKILL, 0.8, -1), signal("UNKNOWN"));
        assertTrue(policy.select(Map.of(SKILL, mastery(0.8)), bad, List.of(weight()), keys("SYNTHETIC_A")).traps().isEmpty());
    }
    @Test void tiesTopTenAndDuplicateOrderAreStable() {
        List<ActiveTrapSignal> signals = new ArrayList<>();
        Set<PreExamReviewPolicy.CatalogKey> approved = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            String code = String.format(Locale.ROOT, "SYNTHETIC_%02d", i);
            signals.add(signal(code)); approved.addAll(keys(code));
        }
        signals.add(signal("SYNTHETIC_00"));
        var first = policy.select(Map.of(SKILL, mastery(0.8)), signals, List.of(weight()), approved);
        Collections.reverse(signals);
        assertEquals(first, policy.select(Map.of(SKILL, mastery(0.8)), signals, List.of(weight()), approved));
        assertEquals(10, first.traps().size());
        assertEquals("SYNTHETIC_00", first.traps().getFirst().signal().mistakeCode());
    }
}
