package com.verifiedai.exam.application;

import com.verifiedai.mistake.MistakeTaxonomy;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Pure deterministic H-03 classification; consumes diagnosed evidence, never raw OCR inference. */
@Service
public class ExamPostAssessmentAttributionService {
    public enum Category { CONCEPTUAL_GAP, RECURRING_MISTAKE_TRAP, TIME_PRESSURE_SLIP, STRATEGIC_OMISSION }
    public record Input(String outcome, String selectedOption, String topicId, String subtopicId,
            String skillId, String examCode, Integer expectedSolveSeconds, int timeSpentSeconds,
            BigDecimal mastery, String diagnosedMistakeCode, Map<String, String> distractorMistakeMap,
            String trapMistakeCode, Map<String, BigDecimal> activeTraps) { }
    public record Attribution(String skillId, String itemOutcome, Category category,
            String matchedMistakeCode, String evidenceSource, int timeSpentSeconds,
            int expectedSolveSeconds, BigDecimal masterySnapshot, BigDecimal estimatedNetImpact,
            String recommendedActionType, String recommendedTargetRef, String rationaleTr,
            String rationaleEn, Map<String, String> metadata, int llmTokensUsed, boolean trainingEligible) { }

    public Attribution classify(Input in) {
        if ("CORRECT".equals(in.outcome())) return null;
        String skill = first(in.skillId(), in.subtopicId(), in.topicId());
        if (skill == null) throw new IllegalArgumentException("A canonical topic or skill is required");
        BigDecimal mastery = in.mastery() == null ? new BigDecimal("0.35") : in.mastery();
        if (mastery.signum() < 0 || mastery.compareTo(BigDecimal.ONE) > 0 || in.timeSpentSeconds() < 0)
            throw new IllegalArgumentException("Invalid mastery or elapsed time");
        int expected = in.expectedSolveSeconds() == null || in.expectedSolveSeconds() <= 0
                ? (in.examCode().contains("TYT") ? 105 : 135) : in.expectedSolveSeconds();
        boolean low = mastery.compareTo(new BigDecimal("0.45")) < 0;
        Category category;
        String code = null, evidence, action, target, tr, en;
        Map<String, String> metadata = Map.of();
        boolean omitted = "OMITTED".equals(in.outcome()) || "BLANK".equals(in.outcome())
                || "SKIPPED".equals(in.outcome()) || in.selectedOption() == null || in.selectedOption().isBlank();
        if (omitted) {
            category = Category.STRATEGIC_OMISSION;
            evidence = "OMISSION_DEFAULT";
            action = low ? "MICRO_LESSON" : "TRIAGE_STRATEGY_REVIEW";
            target = (low ? "microlesson:" : "triage:") + skill;
            metadata = Map.of("subReason", low ? "LOW_MASTERY_OMISSION" : "TIME_TRIAGE_OMISSION");
            tr = low ? "Boş bırakılan soru için temel kavramları pekiştir." : "Boş bırakılan soru için süre ve soru seçimini gözden geçir.";
            en = low ? "Review the core concepts behind this omitted item." : "Review pacing and question selection for this omitted item.";
        } else {
            if (!"WRONG".equals(in.outcome())) throw new IllegalArgumentException("Unsupported evaluation outcome");
            evidence = null;
            if (valid(in.diagnosedMistakeCode())) {
                code = in.diagnosedMistakeCode(); evidence = "SCRATCHPAD_STEP_DIAGNOSIS";
            } else if (in.distractorMistakeMap() != null && valid(in.distractorMistakeMap().get(in.selectedOption()))) {
                code = in.distractorMistakeMap().get(in.selectedOption()); evidence = "DISTRACTOR_TRAP_MAP";
            } else if (valid(in.trapMistakeCode()) && in.activeTraps() != null
                    && in.activeTraps().getOrDefault(in.trapMistakeCode(), BigDecimal.ZERO).compareTo(new BigDecimal("0.30")) >= 0) {
                code = in.trapMistakeCode(); evidence = "MISTAKE_DNA_PROFILE";
            }
            if (code != null) {
                category = Category.RECURRING_MISTAKE_TRAP; action = "TWIN_DRILL";
                target = "twin:" + skill + ":" + code;
                tr = "Doğrulanmış hata örüntüsünü ikiz soru çalışmasıyla pekiştir.";
                en = "Practice a twin problem addressing the evidenced mistake pattern.";
            } else if (low) {
                category = Category.CONCEPTUAL_GAP; evidence = "MASTERY_THRESHOLD";
                action = "MICRO_LESSON"; target = "microlesson:" + skill;
                tr = "Kavramsal temeli kısa bir mikro dersle güçlendir.";
                en = "Strengthen the conceptual foundation with a micro lesson.";
            } else {
                category = Category.TIME_PRESSURE_SLIP; evidence = "TIMING_HEURISTIC";
                action = "TIMED_SPEED_DRILL"; target = "speeddrill:" + skill;
                metadata = Map.of("subReason", in.timeSpentSeconds() > Math.round(expected * 1.35)
                        ? "EXCEEDED_EXPECTED_TIME" : "NON_TRAP_ATTENTION_FALLBACK");
                tr = "Süre ve işlem kontrolünü zamanlı alıştırmayla pekiştir.";
                en = "Practice pacing and calculation checks with a timed drill.";
            }
        }
        return new Attribution(skill, omitted ? "OMITTED" : "WRONG", category, code, evidence,
                in.timeSpentSeconds(), expected, mastery, new BigDecimal(omitted ? "1.000" : "1.250"),
                action, target, tr, en, metadata, 0, false);
    }
    private static boolean valid(String code) { return code != null && MistakeTaxonomy.CATEGORIES.containsKey(code); }
    private static String first(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }
}
