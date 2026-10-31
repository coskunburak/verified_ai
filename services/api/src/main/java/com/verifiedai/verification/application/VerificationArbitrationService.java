package com.verifiedai.verification.application;

import com.verifiedai.problem.application.solving.SolveSourceAccess;
import com.verifiedai.sharedkernel.error.*;
import com.verifiedai.solving.application.SecondaryCorroborationQuery;
import com.verifiedai.solving.application.canonical.CanonicalSolutions;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.VerificationDecision.*;
import com.verifiedai.verification.domain.policy.*;
import com.verifiedai.verification.domain.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(propagation = Propagation.NEVER)
public class VerificationArbitrationService {
    private final CanonicalSolutions solutions;
    private final SolveSourceAccess sources;
    private final VerificationSourceReader reader;
    private final VerificationPlanner planner;
    private final VerificationRunRepository runs;
    private final VerificationDecisionRepository decisions;
    private final SecondaryCorroborationQuery secondary;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final boolean enabled, promotion;
    public VerificationArbitrationService(CanonicalSolutions solutions, SolveSourceAccess sources,
        VerificationSourceReader reader, VerificationPlanner planner, VerificationRunRepository runs,
        VerificationDecisionRepository decisions, SecondaryCorroborationQuery secondary, TransactionTemplate transactions,
        Clock clock, MeterRegistry metrics, @Value("${app.verification.arbitration-enabled:false}") boolean enabled,
        @Value("${app.verification.arbitration-owner-accepted:false}") boolean accepted,
        @Value("${app.environment:local}") String environment) {
        if (enabled && !Set.of("local", "test").contains(environment)) throw new IllegalArgumentException("Arbitration production release is not approved");
        this.solutions = solutions; this.sources = sources; this.reader = reader; this.planner = planner;
        this.runs = runs; this.decisions = decisions; this.secondary = secondary; this.clock = clock; this.metrics = metrics;
        this.enabled = enabled; this.promotion = enabled && accepted;
        tx = new TransactionTemplate(transactions.getTransactionManager());
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW); tx.setTimeout(5);
    }
    public VerificationDecision evaluate(UUID user, UUID session, UUID solutionId, UUID runKey, UUID key) {
        Objects.requireNonNull(runKey); Objects.requireNonNull(key);
        long start = System.nanoTime();
        metrics.counter("verification.arbitration.requests", "operation", "EVALUATE").increment();
        // Canonical's REQUIRES_NEW read must finish before an owner lock is acquired.
        var read = solutions.historical(user, session, solutionId);
        if (!enabled) throw unavailable();
        if (read.state() != CanonicalSolutions.ReadState.GENERATED_UNVERIFIED || read.solution() == null) throw unavailable();
        var solution = read.solution();
        var prepared = tx.execute(s -> prepare(user, session, solution, runKey));
        var cached = tx.execute(s -> {
            if (!prepare(user, session, solution, runKey).equals(prepared)) throw unavailable();
            return decisions.find(user, session, key).orElse(null);
        });
        if (cached != null) return reuse(cached, solutionId, prepared);
        var result = new VerificationPolicy(planner).decide(prepared.source(), solution, prepared.plan(), prepared.run(),
            prepared.secondary(), key, clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS), promotion, prepared.attempts());
        var published = tx.execute(s -> {
            var fresh = prepare(user, session, solution, runKey);
            if (!fresh.equals(prepared)) {
                metrics.counter("verification.arbitration.rejected", "reason", "STALE_LINEAGE").increment();
                throw unavailable();
            }
            var previous = decisions.find(user, session, key);
            return previous.isPresent() ? reuse(previous.get(), solutionId, fresh) : decisions.publish(result);
        });
        metrics.counter("verification.arbitration.decisions", "status", published.status().name()).increment();
        for (var reason : published.reasons()) metrics.counter("verification.arbitration.reasons", "reason", reason.name()).increment();
        metrics.summary("verification.arbitration.unchecked").record(published.unchecked().size());
        metrics.summary("verification.arbitration.contradictions").record(published.contradictions().size());
        metrics.summary("verification.arbitration.uncertainty").record(published.uncertainty().size());
        metrics.counter("verification.arbitration.recovery", "action", published.recovery().name()).increment();
        metrics.timer("verification.arbitration.latency").record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
        return published;
    }
    /** Missing, stale, disabled and changed secondary snapshots never display a cached current verdict. */
    public Optional<VerificationDecision> currentForJob(UUID user, UUID session, UUID job) {
        metrics.counter("verification.arbitration.requests", "operation", "READ").increment();
        var current = solutions.current(user, session);
        if (!enabled || current.state() != CanonicalSolutions.ReadState.GENERATED_UNVERIFIED) return Optional.empty();
        var solution = current.solutions().stream().filter(s -> s.lineage().origin().role() == CanonicalSolution.Role.PRIMARY
            && s.lineage().origin().jobId().equals(job)).findFirst();
        if (solution.isEmpty()) return Optional.empty();
        return tx.execute(s -> {
            sources.lockOwner(user, session);
            var stored = decisions.latest(user, session, solution.get().id());
            if (stored.isEmpty()) return Optional.<VerificationDecision>empty();
            try {
                var fresh = prepare(user, session, solution.get(), stored.get().runRequestKey());
                var d = stored.get();
                if (!d.policyVersion().equals(VerificationDecision.POLICY) || !d.source().equals(fresh.source().identity())
                    || !d.planId().equals(fresh.plan().id()) || !d.secondary().equals(fresh.secondary())
                    || d.status() == Status.VERIFIED && !promotion) return Optional.<VerificationDecision>empty();
                // Revalidate stored evidence without publishing or dispatching work.
                var check = new VerificationPolicy(planner).decide(fresh.source(), solution.get(), fresh.plan(), fresh.run(),
                    fresh.secondary(), d.requestKey(), d.createdAt(), promotion, fresh.attempts());
                if (d.recovery() == Recovery.RETRY_VERIFICATION && (fresh.attempts() >= VerificationRecoveryPolicy.MAX_ATTEMPTS
                    || !clock.instant().isBefore(fresh.run().createdAt().plusSeconds(300)))) return Optional.<VerificationDecision>empty();
                return check.equals(d) ? Optional.of(d) : Optional.<VerificationDecision>empty();
            } catch (ApiProblemException e) {
                if (e.code() != ApiErrorCode.SOLVE_SOURCE_NOT_READY && e.code() != ApiErrorCode.VERIFICATION_UNAVAILABLE) throw e;
                return Optional.<VerificationDecision>empty();
            } catch (VerificationSourceReader.SourceRejected | IllegalArgumentException e) { return Optional.<VerificationDecision>empty(); }
        });
    }
    private Prepared prepare(UUID user, UUID session, CanonicalSolution solution, UUID runKey) {
        var source = reader.readForExecution(sources.current(user, session));
        if (solution.lineage().origin().role() != CanonicalSolution.Role.PRIMARY
            || !secondary.currentPrimary(user, session, solution.lineage().origin().jobId())) throw unavailable();
        var plan = planner.plan(source, solution).plan();
        if (plan == null) throw unavailable();
        var run = runs.find(user, session, runKey).orElseThrow(VerificationArbitrationService::unavailable);
        if (!run.solutionId().equals(solution.id()) || !run.source().equals(source.identity()) || !run.planId().equals(plan.id())) throw unavailable();
        if (runs.latest(user, session, solution.id()).filter(latest -> latest.id().equals(run.id())).isEmpty()) throw unavailable();
        var signal = secondary.snapshot(solution);
        return new Prepared(source, plan, run, new SecondarySnapshot(signal.references(), signal.signals().stream()
            .map(v -> SecondarySignal.valueOf(v.name())).toList()), decisions.attempts(user, session, solution.id()));
    }
    private VerificationDecision reuse(VerificationDecision d, UUID solution, Prepared prepared) {
        if (!d.solutionId().equals(solution) || !d.runId().equals(prepared.run().id()) || !d.policyVersion().equals(VerificationDecision.POLICY)) {
            metrics.counter("verification.arbitration.rejected", "reason", "IDEMPOTENCY_CONFLICT").increment();
            throw new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.IDEMPOTENCY_KEY_REUSED, "Decision key belongs to another scope", false, "REVIEW");
        }
        if (!d.source().equals(prepared.source().identity()) || !d.planId().equals(prepared.plan().id())
            || !d.secondary().equals(prepared.secondary()) || d.status() == Status.VERIFIED && !promotion) throw unavailable();
        return d;
    }
    private static ApiProblemException unavailable() {
        return new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.VERIFICATION_UNAVAILABLE,
            "Current verification prerequisites unavailable", false, "REVIEW");
    }
    private record Prepared(PlanningSource source, VerificationPlan plan, VerificationRun run, SecondarySnapshot secondary, int attempts) { }
}
