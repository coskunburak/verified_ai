package com.verifiedai.studyplan.rebalance;

import com.verifiedai.exam.events.CandidateGoalUpdatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Modulith boundary listener translating candidate goal, study horizon, and
 * syllabus confirmation updates into structured study-plan telemetry without
 * introducing cross-module cycles.
 */
@Component
public class CandidateGoalRebalanceListener {

    private static final Logger log = LoggerFactory.getLogger(CandidateGoalRebalanceListener.class);
    private final Counter goalSyncSignalCounter;

    public CandidateGoalRebalanceListener(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new);
        this.goalSyncSignalCounter = registry.counter("verifiedai_studyplan_candidate_goal_sync_total");
    }

    @EventListener
    public void onCandidateGoalUpdated(CandidateGoalUpdatedEvent event) {
        goalSyncSignalCounter.increment();
        log.info(
                "Study plan received candidate goal update examCode={} version={} trigger={} feasibility={} requiredDaily={}",
                event.examCode(),
                event.pinnedExamVersion(),
                event.triggerType(),
                event.feasibilityStatus(),
                event.requiredDailyMinutes()
        );
    }
}
