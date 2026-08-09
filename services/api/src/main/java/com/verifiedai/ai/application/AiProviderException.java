package com.verifiedai.ai.application;

public class AiProviderException extends RuntimeException {
    private final AiProviderFailureClass failureClass;
    private final boolean retryable;

    public AiProviderException(AiProviderFailureClass failureClass, boolean retryable, String message) {
        super(message);
        this.failureClass = failureClass;
        this.retryable = retryable;
    }

    public AiProviderFailureClass failureClass() {
        return failureClass;
    }

    public boolean retryable() {
        return retryable;
    }
}
