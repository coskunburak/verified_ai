package com.verifiedai.mastery.application;

import java.util.Objects;

public record TwinSubmissionCommand(
        String action,
        String submittedAnswerLatex,
        boolean forceEarlyForfeit
) {
    public TwinSubmissionCommand {
        Objects.requireNonNull(action, "action cannot be null");
    }
}
