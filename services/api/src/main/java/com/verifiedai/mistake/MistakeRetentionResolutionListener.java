package com.verifiedai.mistake;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.verifiedai.mistake.MistakeRecurrenceState.*;

/** Replay over proven same-hazard exposures and immutable twin completions. */
@Component
@ConditionalOnProperty(name = "verifiedai.mastery.decay-reassessment-enabled", havingValue = "true")
public class MistakeRetentionResolutionListener {
    private final LearnerMistakeDnaService dna;

    public MistakeRetentionResolutionListener(LearnerMistakeDnaService dna) {
        this.dna = dna;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void completed(TwinCompleted event) {
        dna.refresh(event.owner());
    }

    public record TwinCompleted(UUID owner) { }
    public record Twin(UUID id, String code, Instant at, boolean independent, boolean correct) { }
    public interface TwinSource { List<Twin> twins(UUID owner); }
    public record Transition(MistakeRecurrenceState from, MistakeRecurrenceState to,
        UUID firstDrill, UUID pendingDrill, UUID checkDrill, UUID classificationId, UUID causalAttempt,
        Instant pendingAt, Instant eventAt) { }
    public record Result(MistakeRecurrenceState state, Instant dueAt, List<Transition> transitions) { }
    private record Event(Instant at, UUID id, MistakeDnaEvidenceSource.Evidence exposure, Twin twin) { }
    static final Duration DELAY = Duration.ofDays(7);

    public static Result replay(String code, List<MistakeDnaEvidenceSource.Evidence> exposures, List<Twin> twins,
                                Instant now, MistakeRecurrenceState baseline) {
        var events = new ArrayList<Event>();
        for (var e : exposures) {
            if (e.verifiedExposure() && !e.superseded() && e.skillEvidenceId() != null
                && MistakeTaxonomy.SKILLS.get(code).equals(e.exposureSkill()) && !e.observedAt().isAfter(now))
                events.add(new Event(e.observedAt(), e.classificationId(), e, null));
        }
        for (var twin : twins) {
            if (code.equals(twin.code()) && !twin.at().isAfter(now))
                events.add(new Event(twin.at(), twin.id(), null, twin));
        }
        events.sort(Comparator.comparing(Event::at).thenComparing(e -> e.twin() != null)
            .thenComparing(e -> e.id().toString()));
        var observations = new ArrayList<EmpiricalBayesMistakeRateCalculator.Observation>();
        var transitions = new ArrayList<Transition>();
        var seen = new HashMap<UUID,Event>();
        MistakeRecurrenceState state = null;
        Twin first = null, pending = null;
        MistakeDnaEvidenceSource.Evidence causal = null;
        for (var event : events) {
            var prior = seen.putIfAbsent(event.id(), event);
            if (prior != null) {
                if (!prior.equals(event)) throw new IllegalArgumentException("Conflicting retention evidence identity");
                continue;
            }
            if (event.exposure() != null) {
                var e = event.exposure();
                boolean error = code.equals(e.decision().code());
                observations.add(new EmpiricalBayesMistakeRateCalculator.Observation(e.evaluationId(), event.at(), error));
                if (!error) continue;
                causal = e;
                if (state == RESOLVED_PENDING_RETENTION || state == STABLE_RESOLVED) {
                    transitions.add(new Transition(state, RELAPSED, first.id(), pending.id(), null,
                        causal.classificationId(), causal.attemptId(), pending.at(), event.at()));
                    state = RELAPSED;
                }
                first = null;
                pending = null;
                continue;
            }
            var twin = event.twin();
            if (causal == null || state == STABLE_RESOLVED) continue;
            if (state == RESOLVED_PENDING_RETENTION) {
                if (twin.independent() && twin.correct() && !event.at().isBefore(pending.at().plus(DELAY))) {
                    transitions.add(new Transition(state, STABLE_RESOLVED, first.id(), pending.id(), twin.id(),
                        causal.classificationId(), causal.attemptId(), pending.at(), event.at()));
                    state = STABLE_RESOLVED;
                }
                continue;
            }
            if (state == null) {
                var rate = new EmpiricalBayesMistakeRateCalculator().calculate(observations, event.at());
                if (MistakeRecurrenceState.project(rate) != RECURRING_PATTERN) continue;
            }
            state = IN_REMEDIATION;
            if (!twin.independent() || !twin.correct()) {
                first = null;
            } else if (first == null) {
                first = twin;
            } else {
                pending = twin;
                transitions.add(new Transition(state, RESOLVED_PENDING_RETENTION, first.id(), pending.id(), null,
                    causal.classificationId(), causal.attemptId(), pending.at(), event.at()));
                state = RESOLVED_PENDING_RETENTION;
            }
        }
        return new Result(state == null ? baseline : state,
            state == RESOLVED_PENDING_RETENTION ? pending.at().plus(DELAY) : null, List.copyOf(transitions));
    }
}
