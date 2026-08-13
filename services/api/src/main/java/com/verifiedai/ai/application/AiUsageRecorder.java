package com.verifiedai.ai.application;

public interface AiUsageRecorder {

    void reserve(
        AiUsageRecord record
    );

    void complete(
        AiUsageRecord record
    );

    static AiUsageRecorder noOp() {
        return new AiUsageRecorder() {

            @Override
            public void reserve(
                AiUsageRecord record
            ) {
                // Intentional no-op for isolated tests.
            }

            @Override
            public void complete(
                AiUsageRecord record
            ) {
                // Intentional no-op for isolated tests.
            }
        };
    }
}
