package com.verifiedai.tutoring.domain;

public record MicroLessonQuickCheckOption(
        String optionId,
        String latexOrText,
        String rationale,
        boolean isCorrect
) {}
