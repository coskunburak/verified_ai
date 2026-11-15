package com.verifiedai.exam.domain;

import java.util.Map;

public record AssemblyDistributionSummary(
        Map<String, Integer> topicCounts,
        Map<String, Integer> difficultyCounts,
        Map<String, Integer> difficultyRealizedBps,
        Map<String, Integer> cognitiveCounts,
        int maxDifficultyDeviationBps
) {
    public AssemblyDistributionSummary {
        topicCounts = topicCounts == null ? Map.of() : Map.copyOf(topicCounts);
        difficultyCounts = difficultyCounts == null ? Map.of() : Map.copyOf(difficultyCounts);
        difficultyRealizedBps = difficultyRealizedBps == null ? Map.of() : Map.copyOf(difficultyRealizedBps);
        cognitiveCounts = cognitiveCounts == null ? Map.of() : Map.copyOf(cognitiveCounts);
    }
}
