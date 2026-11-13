package com.verifiedai.tutoring.domain;

public record MicroLessonCoreRule(
        String ruleTitle,
        String conciseExplanation,
        String canonicalLatexFormula,
        String sympyCheckExpr,
        int estimatedSeconds
) {}
