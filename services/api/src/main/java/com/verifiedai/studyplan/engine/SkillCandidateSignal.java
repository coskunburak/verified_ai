package com.verifiedai.studyplan.engine;

import java.math.BigDecimal;
import java.util.Objects;

public record SkillCandidateSignal(
        String skillCode,
        String dominantMistakeCode,
        BigDecimal mistakePropensity,
        int unresolvedTrapCount,
        BigDecimal currentRetention,
        BigDecimal halfLifeDays,
        BigDecimal independentMastery,
        String examCode,
        BigDecimal examWeight,
        String weakestPrerequisiteSkillCode,
        BigDecimal weakestPrerequisiteMastery
) {
    public SkillCandidateSignal {
        Objects.requireNonNull(skillCode, "skillCode cannot be null");
        if (mistakePropensity == null) {
            mistakePropensity = BigDecimal.ZERO;
        }
        if (currentRetention == null) {
            currentRetention = new BigDecimal("0.75");
        }
        if (halfLifeDays == null) {
            halfLifeDays = new BigDecimal("30.0");
        }
        if (independentMastery == null) {
            independentMastery = BigDecimal.ZERO;
        }
        if (examWeight == null) {
            examWeight = BigDecimal.ONE;
        }
        if (examCode == null || examCode.isBlank()) {
            examCode = "STANDARD_CURRICULUM";
        }
    }

    public static Builder builder(String skillCode) {
        return new Builder(skillCode);
    }

    public static class Builder {
        private final String skillCode;
        private String dominantMistakeCode;
        private BigDecimal mistakePropensity = BigDecimal.ZERO;
        private int unresolvedTrapCount = 0;
        private BigDecimal currentRetention = new BigDecimal("0.75");
        private BigDecimal halfLifeDays = new BigDecimal("30.0");
        private BigDecimal independentMastery = BigDecimal.ZERO;
        private String examCode = "STANDARD_CURRICULUM";
        private BigDecimal examWeight = BigDecimal.ONE;
        private String weakestPrerequisiteSkillCode;
        private BigDecimal weakestPrerequisiteMastery;

        public Builder(String skillCode) {
            this.skillCode = Objects.requireNonNull(skillCode, "skillCode cannot be null");
        }

        public Builder dominantMistake(String mistakeCode, BigDecimal propensity, int unresolvedCount) {
            this.dominantMistakeCode = mistakeCode;
            this.mistakePropensity = propensity != null ? propensity : BigDecimal.ZERO;
            this.unresolvedTrapCount = unresolvedCount;
            return this;
        }

        public Builder retention(BigDecimal currentRetention, BigDecimal halfLifeDays) {
            this.currentRetention = currentRetention;
            this.halfLifeDays = halfLifeDays;
            return this;
        }

        public Builder independentMastery(BigDecimal independentMastery) {
            this.independentMastery = independentMastery;
            return this;
        }

        public Builder exam(String examCode, BigDecimal examWeight) {
            this.examCode = examCode;
            this.examWeight = examWeight;
            return this;
        }

        public Builder weakestPrerequisite(String prerequisiteSkillCode, BigDecimal mastery) {
            this.weakestPrerequisiteSkillCode = prerequisiteSkillCode;
            this.weakestPrerequisiteMastery = mastery;
            return this;
        }

        public SkillCandidateSignal build() {
            return new SkillCandidateSignal(
                    skillCode,
                    dominantMistakeCode,
                    mistakePropensity,
                    unresolvedTrapCount,
                    currentRetention,
                    halfLifeDays,
                    independentMastery,
                    examCode,
                    examWeight,
                    weakestPrerequisiteSkillCode,
                    weakestPrerequisiteMastery
            );
        }
    }
}
