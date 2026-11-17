package com.verifiedai.exam.application;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.verifiedai.exam.application.ExamPostAssessmentAttributionService.Category.*;

class ExamPostAssessmentAttributionServiceTest {
    private final ExamPostAssessmentAttributionService service = new ExamPostAssessmentAttributionService();
    private static final String CODE = "CALC_CHAIN_RULE_OMITTED";
    private ExamPostAssessmentAttributionService.Input input(String outcome, String option, String mastery,
            String diagnosis, Map<String,String> distractors, Map<String,BigDecimal> traps) {
        return new ExamPostAssessmentAttributionService.Input(outcome, option, "topic", null, null,
                "TYT", null, 200, mastery == null ? null : new BigDecimal(mastery), diagnosis, distractors, CODE, traps);
    }
    @Test void omissionWinsOverTrapAndLowMastery() {
        var a = service.classify(input("OMITTED", null, "0.2", CODE, Map.of("A",CODE), Map.of()));
        assertEquals(STRATEGIC_OMISSION,a.category()); assertEquals("MICRO_LESSON",a.recommendedActionType());
        assertEquals(new BigDecimal("1.000"),a.estimatedNetImpact());
        assertEquals("LOW_MASTERY_OMISSION",a.metadata().get("subReason"));
        assertEquals(0,a.llmTokensUsed()); assertFalse(a.trainingEligible());
    }
    @Test void scratchpadWinsOverDistractorAndLowMastery() {
        var a=service.classify(input("WRONG","A","0.2",CODE,Map.of("A","ALG_SIGN_FLIP_DISTRIBUTE"),Map.of()));
        assertEquals(RECURRING_MISTAKE_TRAP,a.category()); assertEquals(CODE,a.matchedMistakeCode());
        assertEquals("SCRATCHPAD_STEP_DIAGNOSIS",a.evidenceSource());
    }
    @Test void absentOrInvalidScratchpadFallsBackToDistractorThenDna() {
        assertEquals("DISTRACTOR_TRAP_MAP", service.classify(input("WRONG","A","0.8",null,Map.of("A",CODE),Map.of())).evidenceSource());
        assertEquals("DISTRACTOR_TRAP_MAP", service.classify(input("WRONG","A","0.8","invented",Map.of("A",CODE),Map.of())).evidenceSource());
        assertEquals("MISTAKE_DNA_PROFILE", service.classify(input("WRONG","A","0.8",null,Map.of(),Map.of(CODE,new BigDecimal("0.30")))).evidenceSource());
        assertEquals(TIME_PRESSURE_SLIP, service.classify(input("WRONG","A","0.8",null,Map.of(),Map.of(CODE,new BigDecimal("0.2999")))).category());
    }
    @Test void missingMasteryAndSkillUseDefaults() {
        var a=service.classify(input("WRONG","A",null,null,Map.of(),Map.of()));
        assertEquals(CONCEPTUAL_GAP,a.category()); assertEquals("topic",a.skillId());
        assertEquals(105,a.expectedSolveSeconds()); assertEquals(new BigDecimal("0.35"),a.masterySnapshot());
    }
    @Test void masteryBoundaryUsesTimingFallbackAndCorrectIsExcluded() {
        assertEquals(TIME_PRESSURE_SLIP,service.classify(input("WRONG","A","0.45",null,Map.of(),Map.of())).category());
        assertEquals("TRIAGE_STRATEGY_REVIEW",service.classify(input("BLANK",null,"0.45",null,Map.of(),Map.of())).recommendedActionType());
        assertNull(service.classify(input("CORRECT","A","0.9",null,Map.of(),Map.of())));
    }
}
