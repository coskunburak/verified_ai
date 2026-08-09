package com.verifiedai.problem.domain.port;

public final class ProblemAssetStorageUnavailableException extends RuntimeException {
    public ProblemAssetStorageUnavailableException(String message) {
        super(message);
    }

    public ProblemAssetStorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
