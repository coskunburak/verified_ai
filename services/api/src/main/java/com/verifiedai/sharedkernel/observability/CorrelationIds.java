package com.verifiedai.sharedkernel.observability;

import org.slf4j.MDC;

public final class CorrelationIds {

    private CorrelationIds() {
    }

    public static String current() {
        String correlationId = MDC.get("correlationId");
        return correlationId == null || correlationId.isBlank() ? "unavailable" : correlationId;
    }
}

