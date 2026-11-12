package com.verifiedai.studyplan.practice;

import java.math.BigDecimal;

public record CuratedPracticeQuestion(
        String questionId,
        String skillId,
        String trapFamily,
        BigDecimal difficulty,
        String promptLatex,
        String instructionTr,
        String instructionEn,
        String canonicalSolutionExpr,
        String trapDistractorExpr,
        String sympyVerificationHash
) {
}
