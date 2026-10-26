package com.verifiedai.ai.application;

/** Durable commitments, independent of reported usage estimates. */
public interface AiSpendGuard {
    void reserve(AiExecutionCommand command, AiRoutePlan plan, AiRouteTarget target,
        int attempt, long ceilingMicros, long requestLimitMicros);
    void report(AiExecutionCommand command, int attempt, AiUsage usage, AiProviderFailureClass failure);

    static AiSpendGuard denyPaid() {
        return new AiSpendGuard() {
            public void reserve(AiExecutionCommand command, AiRoutePlan plan, AiRouteTarget target,
                int attempt, long ceilingMicros, long requestLimitMicros) {
                if (ceilingMicros != 0) throw new AiProviderException(AiProviderFailureClass.BUDGET_EXCEEDED,
                    false, "Durable budget authority required");
            }
            public void report(AiExecutionCommand command, int attempt, AiUsage usage, AiProviderFailureClass failure) { }
        };
    }
}
