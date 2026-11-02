package com.verifiedai.tutoring.domain;

import com.fasterxml.jackson.databind.*;
import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.verifiedai.tutoring.domain.SocraticPolicy.*;
import static org.assertj.core.api.Assertions.*;

/** Structural synthetic regression only. No model, reference solution or expert teaching-quality labels. */
class SocraticEvaluationTest {
    final ObjectMapper json=new ObjectMapper().registerModule(timeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private static com.fasterxml.jackson.databind.module.SimpleModule timeModule() {
        // Same explicit ISO timestamp boundary used by the verification repositories;
        // do not assume a Jackson 2 JavaTime module exists in the Boot 4 runtime.
        var module=new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addSerializer(Instant.class,new JsonSerializer<Instant>() {
            @Override public void serialize(Instant value,com.fasterxml.jackson.core.JsonGenerator out,
                    SerializerProvider context) throws java.io.IOException {
                out.writeString(value.toString());
            }
        });
        return module;
    }
    @Test void syntheticPedagogicalCasesExerciseRealPolicyAndVersionedSchema() throws Exception {
        Path dataset=Path.of("../../evaluations/golden-datasets/tutoring/socratic-policy-v1.json");
        var root=json.readTree(Files.readString(dataset));
        assertThat(root.path("provenance").asText()).isEqualTo("SYNTHETIC");
        assertThat(root.path("cases").size()).isGreaterThanOrEqualTo(20);
        var records=new ArrayList<Map<String,Object>>();
        var uniqueIds=new HashSet<String>();
        for(var row:root.path("cases")) {
            assertThat(uniqueIds.add(row.path("id").asText())).isTrue();
            var before=new Progress(Phase.valueOf(row.path("previousPhase").asText()),row.path("unproductiveCount").asInt(),row.path("turnCount").asInt());
            var intent=Intent.valueOf(row.path("intent").asText());
            long start=System.nanoTime();
            var decision=decide(before,intent,Evidence.UNKNOWN);
            long nanos=System.nanoTime()-start;
            assertThat(decision.progress().phase().name()).as(row.path("id").asText()).isEqualTo(row.path("expectedPhase").asText());
            assertThat(decision.action().name()).isEqualTo(row.path("expectedAction").asText());
            requireTransition(before.phase(),decision.progress().phase(),Evidence.UNKNOWN);
            var learner=new TutorMessage("tutor-message-v1",UUID.randomUUID(),UUID.randomUUID(),TutorPolicy.Role.LEARNER,1,row.path("learnerText").asText(),Instant.EPOCH);
            var turn=TutorTurn.create(learner,before,intent,decision,row.path("language").asText(),Instant.EPOCH);
            VersionedJsonContract.validate("tutor-turn-v1",json.writeValueAsString(turn));
            assertThat(json.readTree(json.writeValueAsString(turn)).path("createdAt").textValue()).isEqualTo(Instant.EPOCH.toString());
            assertThat(turn.correctness()).isEqualTo("UNKNOWN"); assertThat(turn.referenceStatus()).isEqualTo("UNAVAILABLE");
            assertThat(turn.uncertain()).isTrue(); assertThat(turn.trainingEligible()).isFalse();
            for(var forbidden:row.path("forbidden")) assertThat(turn.content()).doesNotContain(forbidden.asText());
            records.add(Map.of("id",row.path("id").asText(),"legalTransition",true,"schemaValid",true,
                "leakageViolations",0,"falseCorrectnessClaims",0,"unverifiedCertaintyViolations",0,"policyLatencyNanos",nanos));
        }
        String target=System.getProperty("tutor.evaluation.report");
        if(target!=null) {
            var report=new LinkedHashMap<String,Object>();
            report.put("schemaVersion","socratic-synthetic-report-v1"); report.put("status","PASS");
            report.put("provenance","SYNTHETIC"); report.put("cases",records); report.put("executed",records.size());
            report.put("datasetSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(dataset))));
            report.put("legalTransitionRate",1.0); report.put("schemaValidityRate",1.0);
            report.put("answerLeakageViolations",0); report.put("falseCorrectnessClaims",0); report.put("unverifiedCertaintyViolations",0);
            report.put("sourceOwnershipEvidence","SocraticTurnIntegrationTest"); report.put("retryDuplicationEvidence","SocraticTurnIntegrationTest");
            report.put("inferenceCallsPerTurn",0); report.put("estimatedInferenceCostMicrosPerTurn",0);
            report.put("latencyScope","In-process policy only; request/network latency NOT_EVALUATED");
            report.put("liveModelEvaluation","NOT_EVALUATED"); report.put("pedagogicalProgressionQuality","NOT_EVALUATED");
            report.put("limitations",List.of("Structural synthetic checks do not measure teaching effectiveness.","No expert labels or live model route.","Only UNKNOWN evidence is supplied in application code."));
            Files.writeString(Path.of(target),json.writerWithDefaultPrettyPrinter().writeValueAsString(report)+"\n");
        }
    }
    @Test void turnSchemaRejectsForgedAuthorityLeakageMetadataAndMalformedPayloads() throws Exception {
        var before=Progress.initial();var decision=decide(before,Intent.UNKNOWN,Evidence.UNKNOWN);
        var learner=new TutorMessage("tutor-message-v1",UUID.randomUUID(),UUID.randomUUID(),TutorPolicy.Role.LEARNER,1,"Synthetic",Instant.EPOCH);
        var turn=TutorTurn.create(learner,before,Intent.UNKNOWN,decision,"en",Instant.EPOCH);
        var valid=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(turn);
        for(var field:List.of("correctness","role","policyVersion","provenance","language")) {
            var invalid=valid.deepCopy();invalid.put(field,"FORGED");
            assertThatThrownBy(()->VersionedJsonContract.validate("tutor-turn-v1",invalid.toString())).isInstanceOf(IllegalArgumentException.class);
        }
        var injected=valid.deepCopy();injected.put("finalAnswer","synthetic-secret");
        assertThatThrownBy(()->VersionedJsonContract.validate("tutor-turn-v1",injected.toString())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->VersionedJsonContract.validate("tutor-turn-v1","{invalid")).isInstanceOf(IllegalArgumentException.class);
        var uncertain=valid.deepCopy();uncertain.put("uncertain",false);
        assertThatThrownBy(()->VersionedJsonContract.validate("tutor-turn-v1",uncertain.toString())).isInstanceOf(IllegalArgumentException.class);
        // Arbitrary prose is never accepted as a model proposal: no model-output ingestion exists.
    }
}
