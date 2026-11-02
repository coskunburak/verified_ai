package com.verifiedai.tutoring.domain;

import com.fasterxml.jackson.databind.*;
import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Executable deterministic disclosure regression; no live provider or expert effectiveness claim. */
class HintEvaluationTest {
    final ObjectMapper json=new ObjectMapper().registerModule(timeModule());
    private static com.fasterxml.jackson.databind.module.SimpleModule timeModule() {
        var module=new com.fasterxml.jackson.databind.module.SimpleModule();
        module.addSerializer(Instant.class,new JsonSerializer<Instant>() {
            @Override public void serialize(Instant value,com.fasterxml.jackson.core.JsonGenerator out,SerializerProvider context) throws java.io.IOException { out.writeString(value.toString()); }
        }); return module;
    }
    @Test void syntheticHintCasesExecutePublicationPolicyAndVersionedSchema() throws Exception {
        var dataset=Path.of("../../evaluations/golden-datasets/tutoring/progressive-hints-v1.json");
        var source=json.readTree(Files.readString(dataset)); var records=new ArrayList<Map<String,Object>>(); var ids=new HashSet<String>();
        assertThat(source.path("provenance").asText()).isEqualTo("SYNTHETIC");
        assertThat(source.path("trainingEligibility").asText()).isEqualTo("NOT_ELIGIBLE");
        assertThat(source.path("cases").size()).isGreaterThanOrEqualTo(18);
        for(var row:source.path("cases")) {
            String id=row.path("id").asText(); assertThat(ids.add(id)).isTrue();
            if(row.path("kind").asText().equals("PUBLICATION")) {
                var level=HintPolicy.Level.valueOf(row.path("level").asText()); var lang=row.path("language").asText(); var text=row.path("content").asText();
                if(row.path("expected").asText().equals("ACCEPT")) {
                    HintPolicy.validatePublication(level,lang,text);
                    var previous=new SocraticPolicy.Progress(SocraticPolicy.Phase.ELICIT_CONCEPT,0,level.ordinal());
                    var progress=HintPolicy.advance(previous,level==HintPolicy.Level.H1?null:HintPolicy.Level.values()[level.ordinal()-1],level);
                    var hint=TutorHint.create(UUID.randomUUID(),level,lang,previous,progress,Instant.EPOCH);
                    assertThat(hint.content()).isEqualTo(text);
                    VersionedJsonContract.validate("tutor-hint-v1",json.writeValueAsString(hint));
                    assertThat(hint.uncertain()).isTrue(); assertThat(hint.referenceStatus()).isEqualTo("UNAVAILABLE");
                } else assertThatThrownBy(()->HintPolicy.validatePublication(level,lang,text)).as(id).isInstanceOf(ApiProblemException.class);
            } else {
                assertThatThrownBy(()->HintPolicy.requireReveal(row.path("confirmed").asBoolean())).as(id).isInstanceOf(ApiProblemException.class)
                    .satisfies(e->assertThat(((ApiProblemException)e).code().name()).isEqualTo(row.path("expected").asText().equals("UNAVAILABLE") ? "TUTOR_REVEAL_UNAVAILABLE" : "REQUEST_VALIDATION_FAILED"));
            }
            records.add(Map.of("id",id,"outcome","PASS"));
        }
        String target=System.getProperty("hint.evaluation.report");
        if(target!=null) {
            var report=new LinkedHashMap<String,Object>(); report.put("status","PASS"); report.put("provenance","SYNTHETIC");
            report.put("cases",records);report.put("executed",records.size());
            report.put("datasetSha256",HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(dataset))));
            report.put("answerLeakageViolations",0); report.put("inferenceCalls",0);report.put("pedagogicalProgressionQuality","NOT_EVALUATED");
            report.put("liveModelQuality","NOT_EVALUATED");
            Files.writeString(Path.of(target),json.writerWithDefaultPrettyPrinter().writeValueAsString(report)+"\n");
        }
    }
    @Test void hintSchemaRejectsHiddenAnswersForgedCostAndMalformedArtifacts() throws Exception {
        var before=SocraticPolicy.Progress.initial();
        var hint=TutorHint.create(UUID.randomUUID(),HintPolicy.Level.H1,"en",before,SocraticPolicy.offerHint(before),Instant.EPOCH);
        var valid=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(hint);
        for(var field:List.of("policyVersion","costPolicyVersion","assistance","masteryEvidence","inference","commercial","referenceStatus")) {
            var bad=valid.deepCopy();bad.put(field,"FORGED");
            assertThatThrownBy(()->VersionedJsonContract.validate("tutor-hint-v1",bad.toString())).isInstanceOf(IllegalArgumentException.class);
        }
        var hidden=valid.deepCopy();hidden.put("finalAnswer","x=4");
        assertThatThrownBy(()->VersionedJsonContract.validate("tutor-hint-v1",hidden.toString())).isInstanceOf(IllegalArgumentException.class);
        var mislabeled=valid.deepCopy();mislabeled.put("level","H3");
        assertThatThrownBy(()->VersionedJsonContract.validate("tutor-hint-v1",mislabeled.toString())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->VersionedJsonContract.validate("tutor-hint-v1","{invalid")).isInstanceOf(IllegalArgumentException.class);
    }
}
