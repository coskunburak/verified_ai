package com.verifiedai.problem.application.session;

import com.verifiedai.problem.domain.model.session.ProblemSessionNextAction;
import com.verifiedai.problem.domain.model.session.ProblemSessionStage;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ProblemSessionMetrics {
    private final MeterRegistry meterRegistry;

    public ProblemSessionMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void lifecycleTransition(String from, String to) {
        counter("problem.session.lifecycle.transition.total", "from", from, "to", to).increment();
    }

    public void historyLoaded(String outcome) {
        counter("problem.session.history.load.total", "outcome", outcome).increment();
    }

    public void historyLatency(long nanos) {
        timer("problem.session.history.load.latency").record(nanos, TimeUnit.NANOSECONDS);
    }

    public void detailLoaded(String outcome) {
        counter("problem.session.detail.load.total", "outcome", outcome).increment();
    }

    public void detailLatency(long nanos) {
        timer("problem.session.detail.load.latency").record(nanos, TimeUnit.NANOSECONDS);
    }

    public void recoveryPlanned(ProblemSessionStage stage, ProblemSessionNextAction nextAction) {
        counter(
            "problem.session.recovery.plan.total",
            "stage",
            stage.name(),
            "next_action",
            nextAction.name()
        ).increment();
    }

    public void ambiguousLineage() {
        counter("problem.session.recovery.ambiguous.total").increment();
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(meterRegistry);
    }

    private Timer timer(String name) {
        return Timer.builder(name).register(meterRegistry);
    }
}
