package com.verifiedai.verification.application;

import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.problem.application.solving.SolveSourceAccess;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.solving.application.canonical.CanonicalSolutions;
import com.verifiedai.solving.application.canonical.CanonicalSolutions.Read;
import com.verifiedai.solving.application.canonical.CanonicalSolutions.ReadState;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import com.verifiedai.verification.domain.model.PlanningSource.Identity;
import com.verifiedai.verification.domain.model.VerificationPlan.*;
import com.verifiedai.verification.domain.policy.VerificationPlanner;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(propagation = Propagation.NEVER)
public class VerificationPlanningService implements VerificationPlanning {
    private final CanonicalSolutions solutions;
    private final SolveSourceAccess sources;
    private final VerificationSourceReader reader;
    private final VerificationPlanner planner;
    private final MeterRegistry metrics;
    private final TransactionTemplate tx;
    public VerificationPlanningService(CanonicalSolutions solutions, SolveSourceAccess sources,
        VerificationSourceReader reader, VerificationPlanner planner, MeterRegistry metrics, TransactionTemplate transactions) {
        this.solutions = solutions; this.sources = sources; this.reader = reader; this.planner = planner; this.metrics = metrics;
        tx = new TransactionTemplate(transactions.getTransactionManager());
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        tx.setTimeout(5);
    }
    public Result plan(UUID user, UUID session, UUID solutionId) {
        Objects.requireNonNull(user); Objects.requireNonNull(session); Objects.requireNonNull(solutionId);
        long start = System.nanoTime();
        metrics.counter("verification.planning.requested", "policy", VerificationPlanner.POLICY_VERSION).increment();
        String outcome = "FAILED";
        try {
            // Canonical reads lock the same owner in their own REQUIRES_NEW transaction.
            // Finish them BEFORE acquiring source locks, otherwise the suspended transaction deadlocks itself.
            var read = solutions.historical(user, session, solutionId);
            Read parent = null;
            if (read.solution() != null && read.solution().lineage().origin().role() == Role.SECONDARY) {
                UUID parentId = CanonicalSolution.identity(new Origin(Role.PRIMARY, read.solution().lineage().parentPrimaryJobId()), Version.CURRENT);
                try { parent = solutions.historical(user, session, parentId); }
                catch (ApiProblemException e) {
                    if (e.code() != ApiErrorCode.RESOURCE_FORBIDDEN) throw e;
                    // A non-materialized parent is a missing prerequisite, not invented primary evidence.
                }
            }
            Read primary = parent;
            Result result = tx.execute(status -> finish(user, session, solutionId, read, primary));
            outcome = result.state().name(); record(result);
            return result;
        } catch (ApiProblemException e) {
            outcome = "ACCESS_REJECTED";
            metrics.counter("verification.planning.rejected", "reason", outcome).increment();
            throw e;
        } finally {
            metrics.timer("verification.planning.latency", "outcome", outcome, "policy", VerificationPlanner.POLICY_VERSION)
                .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }
    private Result finish(UUID user, UUID session, UUID requested, Read read, Read parent) {
        SolveSource source;
        try { source = sources.current(user, session); }
        catch (ApiProblemException e) {
            if (e.code() != ApiErrorCode.SOLVE_SOURCE_NOT_READY) throw e;
            return Result.rejected(State.SOURCE_UNAVAILABLE, Reason.SOURCE_UNAVAILABLE);
        }
        var identity = new Identity(source.userId(), source.problemSessionId(), source.canonicalProblemId(),
            source.problemParseId(), source.problemParseRevision(), source.canonicalRevision());
        // Recheck requested owner too, even if an upstream adapter violates its documented contract.
        if (!user.equals(identity.userId()) || !session.equals(identity.sessionId()) || !requested.equals(read.solutionId()))
            return Result.rejected(State.STALE_SOURCE, Reason.LINEAGE_MISMATCH);
        if (read.solution() != null && (!requested.equals(read.solution().id()) || !VerificationPlanner.matches(identity, read.solution())))
            return Result.rejected(State.STALE_SOURCE, Reason.LINEAGE_MISMATCH);
        Result rejected = readRejection(read.state());
        if (rejected != null) return rejected;
        if (read.solution() == null) return Result.rejected(State.NOT_MATERIALIZED, Reason.NOT_MATERIALIZED);
        if (read.solution().lineage().origin().role() == Role.SECONDARY) {
            if (parent == null || parent.state() != ReadState.GENERATED_UNVERIFIED || parent.solution() == null
                || !Version.CURRENT.equals(parent.solution().version()) || !VerificationPlanner.matches(identity, parent.solution())
                || !parent.solution().ambiguities().isEmpty() || !parent.solution().unresolvedIssues().isEmpty()
                || parent.solution().lineage().origin().role() != Role.PRIMARY
                || !parent.solution().lineage().origin().jobId().equals(read.solution().lineage().parentPrimaryJobId()))
                return Result.rejected(State.REVIEW_REQUIRED, Reason.PARENT_NOT_CURRENT);
        }
        try { return planner.plan(reader.read(source), read.solution()); }
        catch (VerificationSourceReader.SourceRejected e) {
            return Result.rejected(e.reason() == Reason.LINEAGE_MISMATCH ? State.STALE_SOURCE : State.UNSUPPORTED, e.reason());
        }
    }
    private static Result readRejection(ReadState state) {
        return switch (state) {
            case GENERATED_UNVERIFIED -> null;
            case REVIEW_REQUIRED -> Result.rejected(State.REVIEW_REQUIRED, Reason.UNRESOLVED_SOLUTION);
            case NOT_MATERIALIZED -> Result.rejected(State.NOT_MATERIALIZED, Reason.NOT_MATERIALIZED);
            case SOURCE_UNAVAILABLE -> Result.rejected(State.SOURCE_UNAVAILABLE, Reason.SOURCE_UNAVAILABLE);
            case HISTORICAL_SOURCE -> Result.rejected(State.STALE_SOURCE, Reason.HISTORICAL_SOURCE);
            case HISTORICAL_VERSION -> Result.rejected(State.UNSUPPORTED, Reason.HISTORICAL_VERSION);
            case UNSUPPORTED_REPRESENTATION -> Result.rejected(State.UNSUPPORTED, Reason.UNSUPPORTED_REPRESENTATION);
        };
    }
    private void record(Result result) {
        metrics.counter("verification.planning.completed", "state", result.state().name(), "policy", VerificationPlanner.POLICY_VERSION).increment();
        if (result.plan() == null) {
            metrics.counter("verification.planning.rejected", "reason", result.reason().name()).increment(); return;
        }
        metrics.summary("verification.planning.selected_methods", "policy", VerificationPlanner.POLICY_VERSION).record(result.plan().methods().size());
        for (var selection : result.plan().methods()) {
            if (selection.availability() != Availability.AVAILABLE)
                metrics.counter("verification.planning.unavailable", "method", selection.method().name(), "reason", selection.reason().name()).increment();
        }
    }
}
