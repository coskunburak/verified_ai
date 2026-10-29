package com.verifiedai.verification.application;

import com.verifiedai.problem.application.solving.SolveSourceAccess;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.solving.application.canonical.CanonicalSolutions;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.MathematicalExecution.*;
import com.verifiedai.verification.domain.model.VerificationPlan.Availability;
import com.verifiedai.verification.domain.policy.VerificationPlanner;
import com.verifiedai.verification.domain.repository.VerificationRunRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Transactional(propagation = Propagation.NEVER)
public class VerificationExecutionService implements VerificationExecution {
    private final VerificationPlanning planning;
    private final CanonicalSolutions solutions;
    private final SolveSourceAccess sources;
    private final VerificationSourceReader reader;
    private final VerificationPlanner planner;
    private final MathematicalExecutor executor;
    private final VerificationRunRepository repository;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final MeterRegistry metrics;

    public VerificationExecutionService(VerificationPlanning planning, CanonicalSolutions solutions, SolveSourceAccess sources,
        VerificationSourceReader reader, VerificationPlanner planner, MathematicalExecutor executor,
        VerificationRunRepository repository, TransactionTemplate transactions, Clock clock, MeterRegistry metrics) {
        this.planning = planning; this.solutions = solutions; this.sources = sources; this.reader = reader;
        this.planner = planner; this.executor = executor; this.repository = repository; this.clock = clock; this.metrics = metrics;
        tx = new TransactionTemplate(transactions.getTransactionManager());
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW); tx.setTimeout(5);
    }
    private CalculusExecutor calculusExecutor;
    @org.springframework.beans.factory.annotation.Autowired
    void calculusExecutor(CalculusExecutor executor) { calculusExecutor=executor; }
    public Publication execute(UUID user, UUID session, UUID solutionId, UUID key) {
        Objects.requireNonNull(key);
        var selection = planning.plan(user, session, solutionId); // Owner, parent, review, and version checks; no caller transaction.
        if (selection.plan() == null) return new Publication(State.BLOCKED, null);
        var plan = selection.plan();
        if (plan.methods().stream().noneMatch(s -> s.availability() == Availability.AVAILABLE))
            return new Publication(State.BLOCKED, null);
        var read = solutions.historical(user, session, solutionId); // Complete BEFORE any owner lock.
        if (read.state() != CanonicalSolutions.ReadState.GENERATED_UNVERIFIED || read.solution() == null)
            return new Publication(State.STALE_SOURCE, null);
        var solution = read.solution();
        var prepared = tx.execute(status -> {
            var source = reader.readForExecution(sources.current(user, session));
            var fresh = planner.plan(source, solution);
            if (fresh.plan() == null || !fresh.plan().equals(plan)) return null;
            return new Prepared(source, repository.find(user, session, key).orElse(null));
        });
        if (prepared == null) return new Publication(State.STALE_SOURCE, null);
        if (prepared.cached() != null) return reuse(prepared.cached(), plan, solutionId);
        UUID executionId = UUID.nameUUIDFromBytes(("verification-execution-v1/" + user + "/" + session + "/" + key)
            .getBytes(StandardCharsets.UTF_8));
        String correlation = executionId.toString();
        if (prepared.source().calculus()!=null)
            return executeCalculus(user,session,key,solution,plan,prepared.source(),executionId,correlation);
        var results = new ArrayList<Result>();
        for (var method : plan.methods()) {
            var request = new Request(executionId, correlation, method.method(), prepared.source().statements().getFirst(),
                solution.finalStatement(), prepared.source().variables(), prepared.source().restrictions());
            Result result;
            long start = System.nanoTime();
            if (method.availability() != Availability.AVAILABLE) {
                result = new Result(method.method(), "v1", MathematicalExecution.VERIFIER_VERSION, Outcome.BLOCKED, Reason.DISABLED,
                    request.restrictions().stream().map(PlanningSource.Restriction::id).toList(), null);
            } else {
                try {
                    result = executor.execute(request);
                    Objects.requireNonNull(result).validateFor(request);
                } catch (IllegalArgumentException | NullPointerException e) {
                    result = Result.failed(request, Reason.INVALID_RESPONSE);
                } catch (RuntimeException e) {
                    result = Result.failed(request, Reason.NETWORK_FAILURE);
                }
            }
            metrics.counter("verification.execution.outcomes", "method", method.method().name(), "outcome", result.outcome().name(),
                "reason", result.reasonCode().name()).increment();
            metrics.timer("verification.execution.latency", "method", method.method().name())
                .record(System.nanoTime() - start, java.util.concurrent.TimeUnit.NANOSECONDS);
            results.add(result);
        }
        // No canonical read (REQUIRES_NEW) or HTTP call is permitted below this owner lock.
        return tx.execute(status -> {
            PlanningSource current;
            try { current = reader.readForExecution(sources.current(user, session)); }
            catch (VerificationSourceReader.SourceRejected e) { return new Publication(State.STALE_SOURCE, null); }
            catch (ApiProblemException e) {
                if (e.code() != ApiErrorCode.SOLVE_SOURCE_NOT_READY) throw e;
                return new Publication(State.STALE_SOURCE, null);
            }
            var fresh = planner.plan(current, solution);
            if (fresh.plan() == null || !fresh.plan().equals(plan)) {
                metrics.counter("verification.execution.publication", "state", "STALE_SOURCE").increment();
                return new Publication(State.STALE_SOURCE, null);
            }
            var existing = repository.find(user, session, key);
            if (existing.isPresent()) return reuse(existing.get(), plan, solutionId);
            var now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            var evidence = results.stream().map(result -> new VerificationEvidence(
                VerificationEvidence.identity(executionId, result.method()), executionId, plan.id(), plan.policyVersion(),
                plan.capabilityVersion(), current.identity(), solutionId, solution.version(), result.method().purpose(),
                current.variables(), result, correlation, now)).toList();
            var run = new VerificationRun(executionId, key, plan.id(), plan.policyVersion(), plan.capabilityVersion(),
                current.identity(), solutionId, solution.version(), correlation, now, evidence);
            var stored = repository.publish(run);
            metrics.counter("verification.execution.publication", "state", "PUBLISHED").increment();
            return new Publication(State.PUBLISHED, stored);
        });
    }
    public History history(UUID user, UUID session, UUID solutionId, UUID key) {
        // Ownership is reauthorized even for explicitly historical evidence.
        var owned = solutions.historical(user, session, solutionId);
        if (owned.solution() == null) return null;
        return tx.execute(status -> {
            sources.lockOwner(user, session);
            var stored = repository.find(user, session, key).filter(r -> r.solutionId().equals(solutionId));
            if (stored.isEmpty()) return null;
            boolean current;
            try { current = CanonicalSolution.Version.supported(owned.solution().version())
                && VerificationPlanner.matches(reader.read(sources.current(user, session)).identity(), owned.solution()); }
            catch (ApiProblemException e) {
                if (e.code() != ApiErrorCode.SOLVE_SOURCE_NOT_READY) throw e;
                current = false;
            }
            return new History(stored.get(), current);
        });
    }
    private Publication executeCalculus(UUID user, UUID session, UUID key, CanonicalSolution solution,
        VerificationPlan plan, PlanningSource source, UUID executionId, String correlation) {
        if (calculusExecutor==null || !VerificationPlanner.CALCULUS_POLICY.equals(plan.policyVersion())
            || plan.methods().size()!=1) return new Publication(State.BLOCKED,null);
        var selected=plan.methods().getFirst().method();
        var method=switch(selected) {
            case DERIVATIVE_BACKCHECK -> CalculusExecution.Method.DERIVATIVE_BACKCHECK;
            case INTEGRAL_DIFFERENTIATION_BACKCHECK -> CalculusExecution.Method.INTEGRAL_DIFFERENTIATION_BACKCHECK;
            case FINITE_LIMIT -> CalculusExecution.Method.FINITE_LIMIT;
            default -> throw new IllegalArgumentException("Not a calculus method");
        };
        var scope=CalculusEvidence.scope(source.identity(),solution.id());
        var metadata=source.calculus();
        String variable=source.variables().keySet().iterator().next();
        var request=new CalculusExecution.Request(executionId,correlation,scope,method,variable,PlanningSource.Domain.REAL,
            metadata.interval(),((com.verifiedai.solving.domain.model.canonical.MathStatement.Expression)source.statements().getFirst()).expression(),
            ((com.verifiedai.solving.domain.model.canonical.MathStatement.Expression)solution.finalStatement()).expression(),source.restrictions(),
            metadata.approach(),metadata.integralScope());
        CalculusExecution.Result computed;
        long started=System.nanoTime();
        try { computed=Objects.requireNonNull(calculusExecutor.execute(request)); computed.validateFor(request); }
        catch (RuntimeException e) {
            computed=new CalculusExecution.Result(executionId,correlation,scope,method,"v1",CalculusExecution.ALGORITHM,
                Outcome.EXECUTION_FAILED,CalculusExecution.Reason.INVALID_RESPONSE,
                source.restrictions().stream().map(PlanningSource.Restriction::id).toList(),null,null);
        }
        final var result=computed;
        metrics.counter("verification.calculus.execution.outcomes","method",method.name(),"outcome",result.outcome().name(),
            "reason",result.reasonCode().name(),"algorithm",CalculusExecution.ALGORITHM,"capability","calculus-capabilities-v1").increment();
        metrics.counter("verification.execution.outcomes","method",method.name(),"outcome",result.outcome().name(),
            "reason",result.reasonCode().name()).increment();
        metrics.timer("verification.execution.latency","method",method.name()).record(System.nanoTime()-started,java.util.concurrent.TimeUnit.NANOSECONDS);
        return tx.execute(status->{
            PlanningSource current;
            try { current=reader.readForExecution(sources.current(user,session)); }
            catch (VerificationSourceReader.SourceRejected e) { return new Publication(State.STALE_SOURCE,null); }
            catch (ApiProblemException e) { if(e.code()!=ApiErrorCode.SOLVE_SOURCE_NOT_READY) throw e; return new Publication(State.STALE_SOURCE,null); }
            var fresh=planner.plan(current,solution);
            if (fresh.plan()==null || !fresh.plan().equals(plan) || !current.equals(source)) {
                metrics.counter("verification.execution.publication","state","STALE_SOURCE").increment();
                return new Publication(State.STALE_SOURCE,null);
            }
            var previous=repository.find(user,session,key);
            if (previous.isPresent()) return reuse(previous.get(),plan,solution.id());
            var now=clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
            var evidence=new VerificationEvidence(VerificationEvidence.identity(executionId,selected),executionId,plan.id(),
                plan.policyVersion(),plan.capabilityVersion(),current.identity(),solution.id(),solution.version(),selected.purpose(),
                current.variables(),null,correlation,now,CalculusEvidence.of(request,result));
            var run=new VerificationRun(executionId,key,plan.id(),plan.policyVersion(),plan.capabilityVersion(),current.identity(),
                solution.id(),solution.version(),correlation,now,List.of(evidence));
            var stored=repository.publish(run);
            metrics.counter("verification.execution.publication","state","PUBLISHED").increment();
            return new Publication(State.PUBLISHED,stored);
        });
    }
    private Publication reuse(VerificationRun stored, VerificationPlan plan, UUID solution) {
        if (!stored.planId().equals(plan.id()) || !stored.solutionId().equals(solution)) return new Publication(State.BLOCKED, null);
        return new Publication(State.REUSED, stored);
    }
    private record Prepared(PlanningSource source, VerificationRun cached) { }
}
