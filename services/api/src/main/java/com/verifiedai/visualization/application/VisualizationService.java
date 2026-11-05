package com.verifiedai.visualization.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.verifiedai.problem.application.solving.*;
import com.verifiedai.problem.application.attempt.AttemptSourceDocument;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import com.verifiedai.verification.application.VerificationSourceReader;
import com.verifiedai.visualization.domain.VisualizationContract;
import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;
import com.verifiedai.sharedkernel.error.*;
import com.verifiedai.sharedkernel.ratelimit.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class VisualizationService {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(VisualizationService.class);
    private final SolveSourceAccess sources;
    private final VerificationSourceReader reader;
    private final CanonicalMathCodec codec;
    private final VisualizationVerifier verifier;
    private final TransactionTemplate tx;
    private final RateLimiter limiter;
    private final MeterRegistry metrics;
    private final boolean enabled;
    public VisualizationService(SolveSourceAccess sources,VerificationSourceReader reader,CanonicalMathCodec codec,
        VisualizationVerifier verifier,PlatformTransactionManager transactions,RateLimiter limiter,MeterRegistry metrics,
        @Value("${verifiedai.visualization.enabled:false}") boolean enabled) {
        this.sources=sources; this.reader=reader; this.codec=codec; this.verifier=verifier; this.limiter=limiter; this.metrics=metrics; this.enabled=enabled;
        tx=new TransactionTemplate(transactions); tx.setTimeout(5);
    }
    public JsonNode context(UUID owner,UUID problem) {
        return tx.execute(t->{
            var current=sources.current(owner,problem);
            var result=codec.object().put("schemaVersion","math-visualization-context-v1").put("enabled",enabled);
            result.set("source",identity(current));
            VersionedJsonContract.validate("math-visualization-context-v1",result.toString()); return result;
        });
    }
    public JsonNode visualize(UUID owner,UUID problem,String body) {
        long started=System.nanoTime(); String outcome="UNAVAILABLE";
        try {
            final JsonNode query;
            try { query=VisualizationContract.request(body); } catch(IllegalArgumentException ex) { throw error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY); }
            if(!limiter.check(new RateLimitPolicy("visualization",20,Duration.ofMinutes(1),true),owner.toString()).allowed())
                throw error(ApiErrorCode.RATE_LIMIT_EXCEEDED,HttpStatus.TOO_MANY_REQUESTS);
            var request=tx.execute(t->{
                var current=sources.current(owner,problem);
                if(!enabled) throw error(ApiErrorCode.VISUALIZATION_UNAVAILABLE,HttpStatus.SERVICE_UNAVAILABLE);
                if(!identity(current).equals(query.path("source"))) throw stale();
                JsonNode source;
                try { source=source(current); }
                catch(IllegalArgumentException | VerificationSourceReader.SourceRejected ex) { throw error(ApiErrorCode.VISUALIZATION_UNAVAILABLE,HttpStatus.SERVICE_UNAVAILABLE); }
                var viewport=query.path("viewport");
                String parameters=query.path("type").asText()+":"+query.path("resolution").asInt();
                for(String axis:List.of("xMin","xMax","yMin","yMax")) parameters+=":"+Double.toString(viewport.path(axis).asDouble()==0 ? 0 : viewport.path(axis).asDouble());
                UUID execution=UUID.nameUUIDFromBytes(("math-visualization-policy-v1:"+owner+":"+problem+":"+identity(current)+":"+parameters+":"+source)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                var r=codec.object().put("schemaVersion","math-visualization-request-v1").put("executionId",execution.toString()).put("correlationId",execution.toString());
                r.set("source",source); r.set("identity",query.path("source"));
                for(String field:List.of("type","resolution")) r.set(field,query.path(field));
                var normalizedViewport=r.putObject("viewport");
                for(String axis:List.of("xMin","xMax","yMin","yMax")) normalizedViewport.put(axis,viewport.path(axis).asDouble());
                VersionedJsonContract.validate("math-visualization-request-v1",r.toString()); return r;
            });
            JsonNode result;
            try { result=VisualizationContract.result(verifier.execute(UUID.fromString(request.path("executionId").asText()),request.toString()),request); }
            catch(IllegalArgumentException | IllegalStateException ex) {
                metrics.counter("visualization.worker.failure","reason",ex instanceof VisualizationVerifier.Failure failure ? failure.reason().name() : "INVALID_RESPONSE").increment();
                throw error(ApiErrorCode.VISUALIZATION_UNAVAILABLE,HttpStatus.SERVICE_UNAVAILABLE);
            }
            var response=tx.execute(t->{
                // Reacquire account/session fences only after the HTTP call has ended.
                if(!identity(sources.current(owner,problem)).equals(query.path("source"))) throw stale();
                var r=codec.object().put("schemaVersion","math-visualization-public-v1").put("problemId",problem.toString())
                    .put("freshness","CURRENT_AT_PUBLICATION").put("trainingEligible",false);
                r.set("source",query.path("source")); r.set("result",result);
                VersionedJsonContract.validate("math-visualization-public-v1",r.toString()); return r;
            });
            outcome=result.path("status").asText();
            metrics.counter("visualization.reason","reason",result.path("reasonCode").asText()).increment();
            return response;
        } catch(ApiProblemException ex) { outcome=ex.code().name(); throw ex; }
        catch(org.springframework.dao.DataAccessException ex) { throw error(ApiErrorCode.VISUALIZATION_UNAVAILABLE,HttpStatus.SERVICE_UNAVAILABLE); }
        finally {
            metrics.counter("visualization.requests","outcome",outcome).increment();
            io.micrometer.core.instrument.Timer.builder("visualization.duration").publishPercentileHistogram()
                .serviceLevelObjectives(Duration.ofMillis(250),Duration.ofSeconds(1),Duration.ofSeconds(5)).register(metrics)
                .record(System.nanoTime()-started,java.util.concurrent.TimeUnit.NANOSECONDS);
            LOG.info("visualization request={} outcome={} elapsed_ms={}",
                com.verifiedai.sharedkernel.observability.CorrelationIds.current(),outcome,(System.nanoTime()-started)/1_000_000);
        }
    }
    private JsonNode identity(SolveSource s) {
        return codec.object().put("canonicalId",s.canonicalProblemId().toString()).put("canonicalRevision",s.canonicalRevision())
            .put("parseId",s.problemParseId().toString()).put("parseRevision",s.problemParseRevision());
    }
    private JsonNode source(SolveSource current) {
        var result=codec.object(); var variables=result.putArray("variables");
        if("canonical-problem-v3".equals(codec.parse(current.canonicalJson()).path("schemaVersion").asText())) {
            var root=AttemptSourceDocument.read(current); AttemptSourceDocument.variables(root);
            for(var v:root.path("variables")) variables.addObject().put("symbol",v.path("symbol").asText()).put("domain",v.path("domain").asText());
            result.set("original",root.path("original")); result.set("restrictions",root.path("restrictions"));
        } else {
            var parsed=reader.readForExecution(current);
            if(parsed.reviewRequired()||parsed.statements().size()!=1||parsed.variables().size()!=1||!parsed.assumptionIds().isEmpty()||parsed.calculus()!=null)
                throw new IllegalArgumentException("Unsupported canonical source");
            result.set("original",codec.json(parsed.statements().getFirst()));
            parsed.variables().forEach((v,d)->variables.addObject().put("symbol",v).put("domain",d.name()));
            var restrictions=result.putArray("restrictions");
            for(var r:parsed.restrictions()) {
                if(parsed.derivedRestrictionIds().contains(r.id())) continue;
                var item=restrictions.addObject().put("id",r.id()).put("relation",r.relation().name());
                item.set("left",codec.json(r.left())); item.set("right",codec.json(r.right()));
            }
        }
        boolean explicit=true;
        for(var v:variables) explicit &= "REAL".equals(v.path("domain").asText());
        result.put("domainBasis",explicit?"SOURCE_EXPLICIT":"UNRESOLVED"); return result;
    }
    private static ApiProblemException stale() { return error(ApiErrorCode.VISUALIZATION_STALE_SOURCE,HttpStatus.CONFLICT); }
    public static ApiProblemException error(ApiErrorCode code,HttpStatus status) {
        return new ApiProblemException(status,code,"Visualization unavailable",status==HttpStatus.SERVICE_UNAVAILABLE,"RETRY");
    }
}
