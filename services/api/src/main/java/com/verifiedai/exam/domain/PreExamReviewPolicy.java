package com.verifiedai.exam.domain;

import com.verifiedai.exam.domain.ExamModels.ExamTopicWeight;
import com.verifiedai.exam.domain.ExamReadinessCalculator.ActiveTrapSignal;
import com.verifiedai.exam.domain.ExamReadinessCalculator.SkillMasterySignal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure H-15 ranking. Scores are prioritization heuristics, not psychometric predictions. */
public final class PreExamReviewPolicy {
    public record CatalogKey(String skillCode, String mistakeCode) {}
    public record RankedTrap(ActiveTrapSignal signal, double priority) {}
    public record Selection(List<RankedTrap> traps, int excludedLowMasteryTopicCount) {
        public Selection { traps = List.copyOf(traps); }
    }

    public Selection select(Map<String, SkillMasterySignal> mastery,
                            List<ActiveTrapSignal> signals, List<ExamTopicWeight> blueprint,
                            Set<CatalogKey> approved) {
        Map<String, Double> weights = new HashMap<>();
        for (ExamTopicWeight item : blueprint) {
            if (item == null || item.skillId() == null || item.questionCountWeight() == null) continue;
            double weight = item.questionCountWeight().doubleValue();
            if (Double.isFinite(weight) && weight > 0) weights.merge(item.skillId(), weight, Double::sum);
        }
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (!Double.isFinite(total) || total <= 0) return new Selection(List.of(), 0);
        int excluded = (int) weights.keySet().stream().filter(skill -> {
            SkillMasterySignal m = mastery.get(skill);
            return m != null && Double.isFinite(m.masteryScore()) && m.masteryScore() >= 0
                    && m.masteryScore() < 0.5 && m.evidenceCount() > 0;
        }).count();
        Map<CatalogKey, RankedTrap> unique = new HashMap<>();
        for (ActiveTrapSignal signal : signals) {
            if (signal == null || signal.skillCode() == null || signal.mistakeCode() == null) continue;
            CatalogKey key = new CatalogKey(signal.skillCode(), signal.mistakeCode());
            SkillMasterySignal m = mastery.get(signal.skillCode());
            if (!approved.contains(key) || m == null || !signal.skillCode().equals(m.skillCode())
                    || m.evidenceCount() <= 0 || !Double.isFinite(m.masteryScore())
                    || m.masteryScore() < 0.5 || m.masteryScore() > 1
                    || !Double.isFinite(signal.posteriorRate()) || signal.posteriorRate() <= 0
                    || signal.posteriorRate() > 1 || signal.occurrenceCount() <= 0
                    || !weights.containsKey(signal.skillCode())) continue;
            double priority = signal.occurrenceCount() * (weights.get(signal.skillCode()) / total) * signal.posteriorRate();
            RankedTrap ranked = new RankedTrap(signal, priority);
            unique.merge(key, ranked, (a, b) -> compareDuplicate(a, b) <= 0 ? a : b);
        }
        List<RankedTrap> ranked = new ArrayList<>(unique.values());
        ranked.sort(Comparator.comparingDouble(RankedTrap::priority).reversed()
                .thenComparing(t -> t.signal().mistakeCode()).thenComparing(t -> t.signal().skillCode()));
        return new Selection(ranked.stream().limit(10).toList(), excluded);
    }

    private static int compareDuplicate(RankedTrap a, RankedTrap b) {
        int score = Double.compare(b.priority(), a.priority());
        if (score != 0) return score;
        int occurrences = Integer.compare(b.signal().occurrenceCount(), a.signal().occurrenceCount());
        if (occurrences != 0) return occurrences;
        return Comparator.nullsFirst(String::compareTo).compare(a.signal().trapFamilyCode(), b.signal().trapFamilyCode());
    }
}
