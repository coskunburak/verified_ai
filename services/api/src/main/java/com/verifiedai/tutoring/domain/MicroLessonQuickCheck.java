package com.verifiedai.tutoring.domain;

import java.util.List;

public record MicroLessonQuickCheck(
        String questionPrompt,
        String questionLatex,
        List<MicroLessonQuickCheckOption> options,
        String correctOptionId,
        int estimatedSeconds
) {}
