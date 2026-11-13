package com.verifiedai.studyplan.coach.dto;

public record ColdStartDemoCardDto(
        String title,
        String samplePromptLatex,
        String correctStepLatex,
        String focusStepLatex,
        String hintText
) {
}
