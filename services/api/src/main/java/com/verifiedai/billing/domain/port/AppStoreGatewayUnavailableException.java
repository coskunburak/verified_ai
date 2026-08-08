package com.verifiedai.billing.domain.port;

public final class AppStoreGatewayUnavailableException extends RuntimeException {
    public AppStoreGatewayUnavailableException(String message) {
        super(message);
    }

    public AppStoreGatewayUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
