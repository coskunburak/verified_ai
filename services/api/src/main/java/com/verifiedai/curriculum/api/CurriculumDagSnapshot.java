package com.verifiedai.curriculum.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CurriculumDagSnapshot(
        String curriculumCode,
        List<String> topologicallySortedSkillCodes,
        Map<String, String> skillTitles,
        Map<String, List<String>> predecessors,
        List<Edge> edges
) {
    public CurriculumDagSnapshot {
        Objects.requireNonNull(curriculumCode);
        Objects.requireNonNull(topologicallySortedSkillCodes);
        Objects.requireNonNull(skillTitles);
        Objects.requireNonNull(predecessors);
        Objects.requireNonNull(edges);
    }

    public record Edge(String fromSkillCode, String toSkillCode, BigDecimal weight) {
        public Edge {
            Objects.requireNonNull(fromSkillCode);
            Objects.requireNonNull(toSkillCode);
            Objects.requireNonNull(weight);
        }
    }

    public List<String> predecessorsOf(String skillCode) {
        return predecessors.getOrDefault(skillCode, List.of());
    }

    public String titleOf(String skillCode) {
        return skillTitles.getOrDefault(skillCode, skillCode);
    }
}
