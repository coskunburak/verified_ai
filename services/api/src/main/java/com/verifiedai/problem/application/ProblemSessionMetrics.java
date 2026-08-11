package com.verifiedai.problem.application;

import com.verifiedai.problem.domain.model.ProblemSessionNextAction;
import com.verifiedai.problem.domain.model.ProblemSessionStage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
class ProblemSessionMetrics {
    private final MeterRegistry meterRegistry;

    ProblemSessionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    void lifecycleTransition(String from, String to) {
        counter("problem.session.lifecycle.transition.total", "from", from, "to", to).increment();
    }

    void historyLoaded(String outcome) {
        counter("problem.session.history.load.total", "outcome", outcome).increment();
    }

    void historyLatency(long nanos) {
        timer("problem.session.history.load.latency").record(nanos, TimeUnit.NANOSECONDS);
    }

    void detailLoaded(String outcome) {
        counter("problem.session.detail.load.total", "outcome", outcome).increment();
    }

    void detailLatency(long nanos) {
        timer("problem.session.detail.load.latency").record(nanos, TimeUnit.NANOSECONDS);
    }

    void recoveryPlanned(ProblemSessionStage stage, ProblemSessionNextAction nextAction) {
        counter(
            "problem.session.recovery.plan.total",
            "stage",
            stage.name(),
            "next_action",
            nextAction.name()
        ).increment();
    }

    void ambiguousLineage() {
        counter("problem.session.recovery.ambiguous.total").increment();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }

    private Timer timer(String name) {
        return Timer.builder(name).register(meterRegistry);
    }
}
