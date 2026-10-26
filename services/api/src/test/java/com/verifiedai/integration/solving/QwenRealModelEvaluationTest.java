package com.verifiedai.integration.solving;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.ai.application.*;
import com.verifiedai.ai.infrastructure.configuration.VllmSolverProperties;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.solving.SolverSourceFixture;
import com.verifiedai.solving.application.*;
import com.verifiedai.solving.domain.model.SolveState;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

/** Deliberately disabled in normal Maven/regression runs. Synthetic data only. */
@EnabledIfEnvironmentVariable(named="QWEN_OWNER_RUN",matches="APPROVED_SYNTHETIC_EVALUATION")
@ActiveProfiles({"test","qwen-evaluation"})
class QwenRealModelEvaluationTest extends PostgresIntegrationTestSupport {
    @Autowired PrimarySolveService solves;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired AiModelGateway gateway;
    @Autowired SolutionCandidateValidator validator;
    @Autowired VllmSolverProperties config;
    @Autowired JdbcTemplate jdbc;
    final ObjectMapper mapper=new ObjectMapper();

    @Test void durableSmoke() throws Exception {
        Path report=report("qwen-durable-smoke.json");
        var fixture=new SolverSourceFixture(jdbc);UUID user=fixture.insertUser();
        UUID session=fixture.insertSupportedParse(user,SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
        canonical.canonicalize(user,session);
        var job=solves.start(new SolveCommand(user,session,"qwen-smoke-"+session,null));
        assertThat(solves.start(new SolveCommand(user,session,"qwen-smoke-"+session,null)).jobId()).isEqualTo(job.jobId());
        long start=System.nanoTime();solves.run(job.jobId());solves.run(job.jobId());
        var result=solves.get(user,session,job.jobId());
        ObjectNode evidence=metadata();evidence.put("state",result.state().name());evidence.put("failure",result.failureCode());
        evidence.put("latencyMs",Duration.ofNanos(System.nanoTime()-start).toMillis());
        if(result.candidate()!=null) evidence.set("candidate",result.candidate());
        evidence.set("attempts",mapper.valueToTree(jdbc.queryForList("""
            select state, reserved_micros, reported_micros, input_tokens, output_tokens, pricing_version
            from ai_spend_attempts where operation_id=(select operation_id from solve_jobs where id=?)
            """,job.jobId())));
        Files.writeString(report,evidence.toPrettyString()+"\n",StandardOpenOption.CREATE_NEW);
        assertThat(result.state()).isEqualTo(SolveState.GENERATED_UNVERIFIED);
        assertThat(result.model()).isEqualTo(VllmSolverProperties.MODEL);
        assertThat(result.verificationNotice()).contains("has not been performed");
        assertThat(jdbc.queryForObject("select count(*) from ai_spend_attempts where operation_id=(select operation_id from solve_jobs where id=?)",
            Integer.class,job.jobId())).isEqualTo(1);
        assertThat(result.candidate().path("finalAnswer").path("right").path("value").asText()).isEqualTo("1");
    }

    @Test void canonicalBenchmark() throws Exception {
        Path output=report("qwen-attempts.jsonl");
        Path corpus=Path.of("../../evaluations/fixtures/solver/qwen-smoke-v1.json");
        byte[] bytes=Files.readAllBytes(corpus);
        var cases=mapper.readTree(bytes).path("cases");
        int max=Integer.parseInt(Objects.requireNonNull(System.getenv("QWEN_MAX_CASES"),"Owner must bound case count"));
        if(max<1 || max>240) throw new IllegalArgumentException("Case ceiling must be 1..240");
        var fixture=new SolverSourceFixture(jdbc);UUID user=fixture.insertUser();
        // Real user/session FKs for the existing spend ledger; corpus inputs remain isolated synthetic snapshots.
        UUID session=fixture.insertSupportedParse(user,SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
        String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        try(var writer=Files.newBufferedWriter(output,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
            int count=0;
            for(var item:cases) {
                if(count++>=max) break;
                if(!"PROJECT_AUTHORED_SYNTHETIC".equals(item.path("origin").asText())) throw new IllegalArgumentException("Synthetic corpus only");
                var c=item.path("source");
                var source=new SolveSource(UUID.fromString(item.path("canonicalProblemId").asText()),user,session,
                    UUID.fromString(item.path("problemParseId").asText()),item.path("problemParseRevision").asInt(),1,
                    c.path("problemType").asText(),c.path("taskType").asText(),c.toString());
                ObjectNode row=metadata();row.put("caseId",item.path("id").asText());row.put("slice",item.path("slice").asText());
                row.put("corpusSha256",digest);row.put("expectedDisposition",item.path("expectedDisposition").asText());
                row.put("referenceReview",item.path("referenceReview").asText());row.put("locale",item.path("locale").asText());
                row.set("referenceAnswer",item.path("referenceAnswer"));
                long started=System.nanoTime();
                try {validator.sourceDocument(source);} catch(CandidateRejected rejected) {
                    row.put("status","SOURCE_REJECTED");row.put("failure",rejected.code());row.put("attemptCount",0);
                    row.put("latencyMs",Duration.ofNanos(System.nanoTime()-started).toMillis());write(writer,row);continue;
                }
                var context=new AiRouteContext(AiCapability.SOLVE,source.problemType(),source.taskType(),null,null,false,"LOW",false,null,
                    Locale.forLanguageTag(item.path("locale").asText()),Duration.ofSeconds(30),config.accounting().attemptAllocationMicros(),
                    null,Map.of(),null,"LOCAL_TEST","ZERO_RETENTION",AiRouteContext.RouteRole.PRIMARY_SOLVER);
                var command=new AiExecutionCommand(AiCapability.SOLVE,new AiSolveRequest(source.canonicalProblemId(),source.problemParseId(),1,source.canonicalJson()),
                    new AiExecutionContext(UUID.randomUUID(),user,session,"offline-evaluation",null,UUID.randomUUID().toString(),
                        Instant.now().plusSeconds(30),Map.of()),context,"application/json","canonical-problem-v1","solver","v002",
                    "solution-candidate-v1",config.accounting().attemptAllocationMicros());
                var result=gateway.execute(command);
                row.put("status",result.status().name());row.put("failure",result.failureClass()==null?null:result.failureClass().name());
                row.put("attemptCount",result.attemptCount());row.set("committedUsage",mapper.valueToTree(result.usage()));
                if(result.succeeded()) {
                    row.set("reportedUsage",mapper.valueToTree(result.output().usage()));
                    row.set("provenance",mapper.valueToTree(result.provenance()));
                    try {
                        var candidate=validator.validate(result.output().rawOutputJson(),source,result.provenance().provider());
                        row.set("candidate",mapper.readTree(validator.serialize(candidate)));row.put("semanticAccepted",true);
                    } catch(CandidateRejected rejected) {
                        row.put("semanticAccepted",false);row.put("failure",rejected.code());
                        // No raw/rejected payload or hidden reasoning is persisted.
                    }
                }
                row.put("latencyMs",Duration.ofNanos(System.nanoTime()-started).toMillis());write(writer,row);
                // Stop on operational uncertainty; do not exhaust the budget on a broken endpoint.
                if(!result.succeeded()) break;
            }
        }
    }

    private ObjectNode metadata() {
        ObjectNode n=mapper.createObjectNode().put("evidenceVersion","qwen-evaluation-v1").put("model",VllmSolverProperties.MODEL)
            .put("verificationPerformed",false).put("runtimeImageDigest",config.release().runtimeImageDigest())
            .put("deploymentEvidenceSha256",config.release().deploymentEvidenceSha256()).put("approval",config.evaluationApproval())
            .put("structuredOutputs",config.limits().structuredOutputs());
        n.put("promptSha256",PromptRegistry.defaults().require("solver","v002").contentSha256());
        n.put("schemaSha256",OutputSchemaRegistry.defaults().require("solution-candidate-v1").contentSha256());
        n.put("pricingVersion",config.accounting().pricingVersion());
        n.put("allocationMicros",config.accounting().attemptAllocationMicros());
        n.put("createdAt",Instant.now().toString());return n;
    }
    private Path report(String name) throws Exception {
        String folder=Objects.requireNonNull(System.getenv("QWEN_REPORT_DIR"),"Owner must choose a fresh evidence directory");
        Path root=Path.of("../../.generated/qwen-evaluation").toAbsolutePath().normalize();
        Path path=Path.of(folder).toAbsolutePath().normalize();
        if(!path.startsWith(root)) throw new IllegalArgumentException("Evidence must stay in .generated/qwen-evaluation");
        Files.createDirectories(path);Path file=path.resolve(name);
        if(Files.exists(file)) throw new IllegalStateException("Evidence already exists; choose a new run directory");
        return file;
    }
    private void write(java.io.BufferedWriter writer,ObjectNode row) throws Exception {
        writer.write(row.toString());writer.newLine();writer.flush();
    }
}
