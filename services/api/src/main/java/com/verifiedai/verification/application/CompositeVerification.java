package com.verifiedai.verification.application;

import com.verifiedai.problem.application.solving.SolveSourceAccess;
import com.verifiedai.solving.application.canonical.CanonicalSolutions;
import com.verifiedai.verification.domain.model.CompositeEvidenceAssessment;
import com.verifiedai.verification.domain.model.NumericDiagnostic;
import com.verifiedai.verification.domain.policy.CompositeEvidencePolicy;
import com.verifiedai.verification.domain.policy.VerificationPlanner;
import com.verifiedai.verification.domain.repository.VerificationRunRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Owner-authorized current projection; no mathematical calls, writes or product status assignment. */
@Service
@Transactional(propagation = Propagation.NEVER)
public class CompositeVerification {
    private final CanonicalSolutions solutions;
    private final VerificationPlanning planning;
    private final SolveSourceAccess sources;
    private final VerificationSourceReader reader;
    private final VerificationPlanner planner;
    private final VerificationRunRepository runs;
    private final TransactionTemplate tx;
    private final MeterRegistry metrics;
    public CompositeVerification(CanonicalSolutions solutions, VerificationPlanning planning, SolveSourceAccess sources, VerificationSourceReader reader,
        VerificationPlanner planner, VerificationRunRepository runs, TransactionTemplate transactions, MeterRegistry metrics) {
        this.planning = planning; this.solutions = solutions; this.sources = sources; this.reader = reader; this.planner = planner;
        this.runs = runs; this.metrics = metrics;
        tx = new TransactionTemplate(transactions.getTransactionManager());
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW); tx.setTimeout(5);
    }
    public Optional<CompositeEvidenceAssessment> current(UUID user, UUID session, UUID solutionId, UUID key) {
        return current(user, session, solutionId, key, null);
    }
    public Optional<CompositeEvidenceAssessment> current(UUID user, UUID session, UUID solutionId, UUID key, NumericDiagnostic.Evidence diagnostic) {
        long started = System.nanoTime();
        // Finish canonical and parent-precondition reads before acquiring the source/owner lock.
        var read = solutions.historical(user, session, solutionId);
        if (read.state() != CanonicalSolutions.ReadState.GENERATED_UNVERIFIED || read.solution() == null) return Optional.empty();
        var selection = planning.plan(user, session, solutionId);
        if (selection.plan() == null) return Optional.empty();
        var assessed = tx.execute(status -> {
            var source = reader.readForExecution(sources.current(user, session));
            var plan = planner.plan(source, read.solution()).plan();
            if (plan == null || !plan.equals(selection.plan())) return Optional.<CompositeEvidenceAssessment>empty();
            var run = runs.find(user, session, key);
            if (run.isEmpty() || !run.get().solutionId().equals(solutionId) || !run.get().planId().equals(plan.id()))
                return Optional.<CompositeEvidenceAssessment>empty();
            return Optional.of(new CompositeEvidencePolicy(planner).assess(source, read.solution(), plan, run.get(), diagnostic));
        });
        assessed.ifPresent(result -> {
            metrics.counter("verification.composite.assessments", "category", result.category().name(),
                "domain", result.domainCoverage().name(), "proof", Boolean.toString(result.exactProofPresent()),
                "contradiction", Boolean.toString(result.exactContradictionPresent())).increment();
            metrics.summary("verification.composite.unsupported").record(result.unsupported().size());
            metrics.summary("verification.composite.failed").record(result.failed().size());
            metrics.counter("verification.composite.numeric", "state", result.diagnostic() == null ? "NOT_PERFORMED" : result.diagnostic().status().name()).increment();
            if (result.reasons().contains(CompositeEvidenceAssessment.Reason.NUMERIC_CONFLICT))
                metrics.counter("verification.composite.numeric_conflicts").increment();
            metrics.timer("verification.composite.latency").record(System.nanoTime() - started, java.util.concurrent.TimeUnit.NANOSECONDS);
        });
        return assessed;
    }
}
