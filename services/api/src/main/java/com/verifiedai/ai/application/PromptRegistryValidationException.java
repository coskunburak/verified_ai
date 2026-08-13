package com.verifiedai.ai.application;

public final class PromptRegistryValidationException
    extends RuntimeException {

    private final PromptRegistryFailureCode code;

    public PromptRegistryValidationException(
        PromptRegistryFailureCode code,
        String message
    ) {
        super(message);
        this.code = code;
    }

    public PromptRegistryFailureCode code() {
        return code;
    }
}
