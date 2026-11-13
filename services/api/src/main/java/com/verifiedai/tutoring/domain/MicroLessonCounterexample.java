package com.verifiedai.tutoring.domain;

import java.util.Map;

public record MicroLessonCounterexample(
        String promptLatex,
        String commonTrapLatex,
        String whyTrapFailsExplanation,
        String verifiedStepLatex,
        String numericCounterexampleLatex,
        String sympyEquivalenceCheckExpr,
        String sympyTargetExpr,
        String sympyValidExpr,
        String sympyTrapExpr,
        Map<String, Object> counterexampleBindings,
        int estimatedSeconds
) {}
