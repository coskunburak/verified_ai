package com.verifiedai.solving.application.secondary;

import com.fasterxml.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.verifiedai.ai.application.*;
import com.verifiedai.billing.application.CapabilityAccessPolicy;
import com.verifiedai.problem.application.solving.*;
import com.verifiedai.sharedkernel.error.*;
import com.verifiedai.solving.application.*;
import com.verifiedai.solving.domain.model.*;
import com.verifiedai.solving.domain.model.secondary.*;
import com.verifiedai.solving.infrastructure.persistence.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable internal evidence workflow. No public endpoint or correctness authority. */
@Service
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class SecondarySolveService {
    private final SolveSourceAccess sources;
    private final SolveStore primary;
    private final SecondarySolveStore store;
    private final CapabilityAccessPolicy access;
    private final AiRoutePlanner routes;
    private final AiModelGateway gateway;
    private final SolutionCandidateValidator validator;
    private final BlindSecondaryRequestBuilder blind;
    private final BlindAgreementAnalyzer analyzer;
    private final TransactionTemplate tx;
    private final JsonMapper mapper;
    private final Clock clock;
    private final MeterRegistry metrics;
    private final SecondaryEscalationPolicy policy;
    private final boolean enabled;
    private final long maxCost;
    private final int concurrency;
    private final Locale locale;
    public SecondarySolveService(SolveSourceAccess sources,SolveStore primary,SecondarySolveStore store,
        CapabilityAccessPolicy access,AiRoutePlanner routes,AiModelGateway gateway,SolutionCandidateValidator validator,
        BlindSecondaryRequestBuilder blind,BlindAgreementAnalyzer analyzer,TransactionTemplate tx,JsonMapper mapper,
        Clock clock,MeterRegistry metrics,@Value("${app.solving.secondary.enabled:false}") boolean enabled,
        @Value("${app.solving.secondary.sample-basis-points:0}") int sample,
        @Value("${app.solving.secondary.max-cost-micros:0}") long maxCost,
        @Value("${app.solving.secondary.max-concurrency:2}") int concurrency,
        @Value("${app.solving.language:tr}") String language) {
        this.sources=sources;this.primary=primary;this.store=store;this.access=access;this.routes=routes;
        this.gateway=gateway;this.validator=validator;this.blind=blind;this.analyzer=analyzer;this.tx=tx;
        this.mapper=mapper;this.clock=clock;this.metrics=metrics;this.enabled=enabled;
        this.policy=new SecondaryEscalationPolicy(sample);
        if(maxCost<0 || concurrency<1 || concurrency>10 || !Set.of("tr","en").contains(language))
            throw new IllegalArgumentException("Invalid secondary execution bounds");
        this.maxCost=maxCost;this.concurrency=concurrency;this.locale=Locale.forLanguageTag(language);
    }
    public String policyVersion() { return policy.version(); }

    /** Polling the durable primary table is the integration/outbox boundary; restarts cannot lose a trigger. */
    public void runDueJobs(int requested) {
        int limit=Math.min(Math.max(requested,1),10);
        var recovered=tx.execute(s->store.recover(clock.instant()));
        if(recovered!=null) recovered.forEach((state,count)->
            metrics.counter("solving.secondary.outcomes","state",state.name()).increment(count));
        for(UUID parent:store.undecided(policy.version(),limit)) {
            try {
                SolveJob p=primary.get(parent).orElse(null);
                if(p!=null) evaluate(p.userId(),p.sessionId(),p.id());
            } catch(RuntimeException e) { workerFailure(); }
        }
        if(!enabled) return;
        for(UUID id:store.due(limit)) {
            try { run(id); } catch(RuntimeException e) { workerFailure(); }
        }
    }

    public SecondaryJob evaluate(UUID user,UUID session,UUID primaryId) {
        return tx.execute(s->{
            sources.lockOwner(user,session);
            SolveJob p=ownedPrimary(user,session,primaryId);
            var existing=store.forPrimary(primaryId,policy.version());
            if(existing.isPresent()) return existing.get();
            if(!Set.of(SolveState.GENERATED_UNVERIFIED,SolveState.REVIEW_REQUIRED).contains(p.state())) throw forbidden();
            var artifact=primary.candidate(primaryId).orElseThrow(SecondarySolveService::forbidden);
            SolveSource source=null;
            SolutionCandidate candidate=null;
            boolean current=false,supported=false,entitled=true;
            try {
                source=sources.current(user,session);current=matches(p,source);
                blind.build(source);supported=true;
                candidate=validator.validate((String)artifact.get("artifact"),source,
                    validator.parse((String)artifact.get("provenance")).path("provider").asText());
            } catch(ApiProblemException | CandidateRejected e) { /* Explicit conservative decision below. */ }
            try { access.requireBasicSolve(user); } catch(ApiProblemException e) { entitled=false; }
            AiRoutePlan plan=null;
            String primaryModel=validator.parse((String)artifact.get("provenance")).path("model").asText();
            // Risk selection precedes route lookup, so skipped solves cause no provider/health work.
            boolean issues=candidate!=null && (!candidate.ambiguities().isEmpty() || !candidate.unresolvedIssues().isEmpty());
            var initial=policy.decide(new SecondaryEscalationPolicy.Inputs(p.canonicalId(),current,supported,candidate!=null,
                issues,enabled,true,true));
            String routeFailure=null;
            if(initial.outcome()==SecondaryEscalationPolicy.Outcome.ESCALATE && entitled) {
                try { plan=routes.routePlan(context(source),user); }
                catch(AiProviderException e) { routeFailure=e.failureClass().name(); }
            }
            boolean approved=plan!=null && !primaryModel.isBlank() && !primaryModel.equals(plan.primary().model())
                && plan.fallbackChain().isEmpty() && plan.maxAttempts()==1
                && "secondary-solver".equals(plan.promptId()) && "v001".equals(plan.promptVersion())
                && "solution-candidate-v1".equals(plan.schemaVersion());
            boolean budget=plan!=null && routes.attemptCeiling(plan,plan.primary())<=Math.min(maxCost,plan.maxCostMicros());
            var decision=policy.decide(new SecondaryEscalationPolicy.Inputs(p.canonicalId(),current,supported,candidate!=null,
                issues,enabled,approved,budget));
            if(!entitled && initial.outcome()==SecondaryEscalationPolicy.Outcome.ESCALATE)
                decision=new SecondaryEscalationPolicy.Decision(policy.version(),SecondaryEscalationPolicy.Outcome.BLOCKED,"ENTITLEMENT_BLOCKED");
            else if("BUDGET_EXCEEDED".equals(routeFailure))
                decision=new SecondaryEscalationPolicy.Decision(policy.version(),SecondaryEscalationPolicy.Outcome.BLOCKED,"SECONDARY_BUDGET_UNAVAILABLE");
            if(decision.outcome()==SecondaryEscalationPolicy.Outcome.ESCALATE && store.previouslyScheduled(primaryId))
                decision=new SecondaryEscalationPolicy.Decision(policy.version(),SecondaryEscalationPolicy.Outcome.BLOCKED,"SECONDARY_ALREADY_SCHEDULED");
            SecondaryState state=switch(decision.outcome()) {
                case SKIP -> SecondaryState.NOT_REQUIRED;
                case BLOCKED -> "SOURCE_STALE".equals(decision.reason())?SecondaryState.STALE:SecondaryState.POLICY_BLOCKED;
                case INSUFFICIENT_EVIDENCE -> SecondaryState.INSUFFICIENT_EVIDENCE;
                case ESCALATE -> SecondaryState.QUEUED;
            };
            var job=store.insert(p,decision,state,approved?encode(plan):null,clock.instant());
            metrics.counter("solving.secondary.decisions","policy",policy.version(),"decision",decision.outcome().name(),
                "reason",decision.reason()).increment();
            return job;
        });
    }

    public void run(UUID id) {
        if(!enabled) return;
        SecondaryJob job=store.get(id).orElse(null);
        if(job==null || job.state()!=SecondaryState.QUEUED) return;
        SolveSource source=tx.execute(s->{
            sources.lockOwner(job.userId(),job.sessionId());
            if(!store.claim(id,clock.instant(),concurrency)) return null;
            try {
                if(!policy.version().equals(job.policyVersion())) {
                    store.finish(id,SecondaryState.POLICY_BLOCKED,"POLICY_VERSION_CHANGED",null,clock.instant());return null;
                }
                var current=sources.current(job.userId(),job.sessionId());
                var p=ownedPrimary(job.userId(),job.sessionId(),job.primaryJobId());
                if(!matches(job,current) || !Set.of(SolveState.GENERATED_UNVERIFIED,SolveState.REVIEW_REQUIRED).contains(p.state())) {
                    store.finish(id,SecondaryState.STALE,"SOURCE_CHANGED",null,clock.instant());return null;
                }
                access.requireBasicSolve(job.userId());blind.build(current);return current;
            } catch(ApiProblemException | CandidateRejected e) {
                store.finish(id,SecondaryState.POLICY_BLOCKED,"SOURCE_OR_ENTITLEMENT_BLOCKED",null,clock.instant());return null;
            }
        });
        if(source==null || !store.running(id)) return;
        long started=System.nanoTime();
        AiRoutePlan plan;
        try {
            plan=routes.routePlan(context(source),job.userId());
            if(!plan.equals(decodePlan(job.routeJson()))) throw new AiProviderException(AiProviderFailureClass.POLICY_BLOCKED,false,"Pinned route changed");
        } catch(AiProviderException e) {
            tx.executeWithoutResult(s->store.finish(id,SecondaryState.POLICY_BLOCKED,"ROUTE_CHANGED_OR_UNAVAILABLE",null,clock.instant()));return;
        }
        SolveJob parent=primary.get(job.primaryJobId()).orElse(null);
        if(parent==null) return; // Account/source deletion already cascaded the job.
        long ceiling=Math.min(maxCost,plan.maxCostMicros());
        var command=new AiExecutionCommand(AiCapability.SOLVE,blind.build(source),
            new AiExecutionContext(job.operationId(),job.userId(),job.sessionId(),id.toString(),null,id.toString(),job.deadline(),
                Map.of("logicalPrimaryOperationId",parent.operationId().toString(),"routeRole","SECONDARY_SOLVER")),
            context(source),"application/json","canonical-problem-v1",plan.promptId(),plan.promptVersion(),plan.schemaVersion(),ceiling);
        // Gateway independently repeats route checks, durable reservation, deadlines and provider-health guards.
        AiExecutionResult result=gateway.execute(command);
        SolutionCandidate candidate=null;
        BlindAgreementAnalyzer.Evidence agreement=null;
        SecondaryState proposed;String failure=null;
        if(result.succeeded()) {
            try {
                if(!(result.output() instanceof AiSolveResult) || result.provenance()==null
                    || !plan.primary().provider().equals(result.provenance().provider())
                    || !plan.primary().model().equals(result.provenance().model()) || result.fallbackUsed())
                    throw new CandidateRejected("SECONDARY_PROVENANCE_MISMATCH");
                candidate=validator.validate(result.output().rawOutputJson(),source,result.provenance().provider());
                var row=primary.candidate(job.primaryJobId()).orElseThrow(()->new CandidateRejected("PRIMARY_MISSING"));
                JsonNode provenance=validator.parse((String)row.get("provenance"));
                if(provenance.path("model").asText().equals(result.provenance().model())) throw new CandidateRejected("MODEL_IDENTITY_NOT_SEPARATE");
                var first=validator.validate((String)row.get("artifact"),source,provenance.path("provider").asText());
                agreement=analyzer.compare(first,candidate,"LOCAL_FIXTURE".equals(result.provenance().provider())
                    ?"SYNTHETIC_SOURCE_BLIND_ONLY":"APPROVED_DISTINCT_MODEL_SHARED_FAILURE_RISK");
                proposed=SecondaryState.GENERATED_UNVERIFIED;
            } catch(CandidateRejected e) { proposed=SecondaryState.TERMINAL_FAILURE;failure=e.code(); }
        } else {
            failure=result.failureClass()==null?"PROVIDER_FAILED":result.failureClass().name();
            proposed=failureState(result);
        }
        final SolutionCandidate accepted=candidate;
        final BlindAgreementAnalyzer.Evidence evidence=agreement;
        final SecondaryState terminal=proposed;
        final String code=failure;
        SecondaryState completed=tx.execute(s->{
            sources.lockOwner(job.userId(),job.sessionId());
            boolean current;
            try { current=matches(job,sources.current(job.userId(),job.sessionId())); }
            catch(ApiProblemException e) { current=false; }
            SecondaryState end=current?terminal:SecondaryState.STALE;
            if(!store.finish(id,end,current?code:"SOURCE_CHANGED",result.ledgerRecordId(),clock.instant())) return null;
            // Late/stale/cancelled candidates cannot be promoted as current corroboration.
            if(current && accepted!=null && evidence!=null)
                store.evidence(id,validator.serialize(accepted),validator.serialize(result.provenance()),validator.serialize(evidence),clock.instant());
            return end;
        });
        if(completed!=null) {
            metrics.counter("solving.secondary.outcomes","state",completed.name()).increment();
            io.micrometer.core.instrument.Timer.builder("solving.secondary.latency")
                .publishPercentileHistogram().register(metrics).record(Duration.ofNanos(System.nanoTime()-started));
            if(result.usage()!=null) {
                metrics.summary("solving.secondary.committed_cost_micros").record(result.usage().estimatedCostMicros());
                if(result.output()!=null && result.output().usage()!=null) {
                    var usage=result.output().usage();
                    if(usage.inputTokens()!=null) metrics.summary("solving.secondary.input_tokens").record(usage.inputTokens());
                    if(usage.outputTokens()!=null) metrics.summary("solving.secondary.output_tokens").record(usage.outputTokens());
                }
            }
            if(result.status()==AiExecutionStatus.BLOCKED_BUDGET)
                metrics.counter("solving.secondary.blocked_budget").increment();
            if(evidence!=null && completed==SecondaryState.GENERATED_UNVERIFIED)
                metrics.counter("solving.secondary.agreement","outcome",evidence.outcome().name()).increment();
        }
    }
    public record View(UUID jobId,SecondaryState state,String policyVersion,String decision,String reason,JsonNode agreement) { }
    public View get(UUID user,UUID session,UUID id) {
        return tx.execute(s->{
            sources.lockOwner(user,session);var job=owned(user,session,id);
            boolean current;
            try { current=matches(job,sources.current(user,session)); } catch(ApiProblemException e) { current=false; }
            return new View(id,current?job.state():SecondaryState.STALE,job.policyVersion(),job.decision(),job.reason(),
                current?store.agreement(id).map(validator::parse).orElse(null):null);
        });
    }
    public View cancel(UUID user,UUID session,UUID id) {
        tx.executeWithoutResult(s->{sources.lockOwner(user,session);owned(user,session,id);store.cancel(id,clock.instant());});
        return get(user,session,id);
    }
    private AiRouteContext context(SolveSource source) {
        return new AiRouteContext(AiCapability.SOLVE,source.problemType(),source.taskType(),null,null,false,"LOW",null,null,
            locale,Duration.ofSeconds(30),maxCost,null,Map.of(),null,null,"NO_TRAINING_BY_DEFAULT",AiRouteContext.RouteRole.SECONDARY_SOLVER);
    }
    private SolveJob ownedPrimary(UUID user,UUID session,UUID id) {
        return primary.get(id).filter(j->j.userId().equals(user)&&j.sessionId().equals(session)).orElseThrow(SecondarySolveService::forbidden);
    }
    private SecondaryJob owned(UUID user,UUID session,UUID id) {
        return store.get(id).filter(j->j.userId().equals(user)&&j.sessionId().equals(session)).orElseThrow(SecondarySolveService::forbidden);
    }
    private static boolean matches(SolveJob j,SolveSource s) {
        return j.canonicalId().equals(s.canonicalProblemId()) && j.parseId().equals(s.problemParseId()) && j.parseRevision()==s.problemParseRevision();
    }
    private static boolean matches(SecondaryJob j,SolveSource s) {
        return j.canonicalId().equals(s.canonicalProblemId()) && j.parseId().equals(s.problemParseId())
            && j.parseRevision()==s.problemParseRevision() && j.canonicalRevision()==s.canonicalRevision();
    }
    private static SecondaryState failureState(AiExecutionResult r) {
        if(r.failureClass()==null || Set.of(AiProviderFailureClass.TIMEOUT,AiProviderFailureClass.DEADLINE_EXCEEDED,
            AiProviderFailureClass.UNKNOWN,AiProviderFailureClass.LEDGER_UNAVAILABLE).contains(r.failureClass())) return SecondaryState.OUTCOME_UNKNOWN;
        if(Set.of(AiProviderFailureClass.POLICY_BLOCKED,AiProviderFailureClass.CONFIGURATION_DISABLED,
            AiProviderFailureClass.BUDGET_EXCEEDED).contains(r.failureClass())) return SecondaryState.POLICY_BLOCKED;
        return r.retryable()?SecondaryState.RETRYABLE_FAILURE:SecondaryState.TERMINAL_FAILURE;
    }
    private String encode(AiRoutePlan plan) {
        try { return mapper.writeValueAsString(plan); } catch(Exception e) { throw new IllegalStateException("Cannot pin secondary route"); }
    }
    private AiRoutePlan decodePlan(String json) {
        try { return mapper.readValue(json,AiRoutePlan.class); } catch(Exception e) { throw new IllegalStateException("Invalid pinned secondary route"); }
    }
    private void workerFailure() { metrics.counter("solving.secondary.worker.failure").increment(); }
    private static ApiProblemException forbidden() {
        return new ApiProblemException(HttpStatus.NOT_FOUND,ApiErrorCode.RESOURCE_FORBIDDEN,"Secondary evidence unavailable",false,"REVIEW");
    }
}
