package com.verifiedai.billing.domain.port;

public final class AppStoreVerificationFailedException extends RuntimeException {
    public AppStoreVerificationFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    public AppStoreVerificationFailedException(String message) {
        super(message);
    }
}
