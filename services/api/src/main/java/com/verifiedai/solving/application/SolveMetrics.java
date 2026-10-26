package com.verifiedai.solving.application;

import com.verifiedai.ai.application.AiExecutionResult;
import com.verifiedai.solving.domain.model.SolveState;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class SolveMetrics {
    private final MeterRegistry registry;
    public SolveMetrics(MeterRegistry registry) { this.registry=registry; }
    public void outcome(SolveState state, String failure, AiExecutionResult result,long nanos) {
        registry.counter("solving.outcomes","state",state.name()).increment();
        if (state==SolveState.TERMINAL_FAILURE && failure!=null) {
            String kind=java.util.Set.of("SCHEMA_INVALID","OUTPUT_TOO_LARGE").contains(failure) ? "SCHEMA"
                : java.util.Set.of("SOURCE_MISMATCH","ORIGIN_MISMATCH","TASK_MISMATCH","STEP_ORDER_INVALID",
                    "STEP_REFERENCE_INVALID","ANSWER_REPRESENTATION_INVALID","COMPLEXITY_LIMIT","UNSAFE_MATH",
                    "UNKNOWN_VARIABLE","NEW_DOMAIN_RESTRICTION","SOURCE_RESTRICTIONS_CHANGED","FALSE_VERIFICATION_CLAIM")
                    .contains(failure) ? "SEMANTIC" : "PROVIDER";
            registry.counter("solving.rejections","kind",kind).increment();
        }
        registry.timer("solving.latency","state",state.name()).record(nanos,TimeUnit.NANOSECONDS);
        if(result!=null && result.usage()!=null)
            registry.summary("solving.estimated_cost_micros","state",state.name()).record(result.usage().estimatedCostMicros());
        else registry.counter("solving.cost_unavailable","state",state.name()).increment();
    }
    public void workerFailure() { registry.counter("solving.worker_failures").increment(); }
}
