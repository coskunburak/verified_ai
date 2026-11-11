package com.verifiedai.mastery.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TwinSubmissionRequest(
        @NotNull
        @Pattern(regexp = "^(SUBMIT_ANSWER|REVEAL_SOLUTION)$", message = "Action must be SUBMIT_ANSWER or REVEAL_SOLUTION")
        String action,

        @Size(max = 512, message = "Submitted answer latex cannot exceed 512 characters")
        String submittedAnswerLatex,

        Boolean forceEarlyForfeit
) {
}
