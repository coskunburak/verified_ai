package com.verifiedai.tutoring.domain;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.problem.application.tutoring.TutorHintSource;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class HintSourceTest {
    final ObjectMapper json=new ObjectMapper();
    final UUID parse=UUID.randomUUID();
    ObjectNode source() throws Exception {
        return (ObjectNode)json.readTree("""
            {"schemaVersion":"canonical-problem-v1","problemType":"EQUATION","taskType":"SOLVE_EQUATION",
             "sourceParse":{"problemParseId":"%s","problemParseRevision":1,"problemParseSchemaVersion":"problem-parse-v1"},
             "display":{"reviewRequired":false},"sourceConstraints":[],"sourceAssumptions":[],"derivedRestrictions":[],
             "variables":[{"symbol":"x","role":"VARIABLE","domain":"UNKNOWN"}],
             "statements":[{"kind":"RELATION","relation":"EQUALS","left":{"kind":"BINARY","operator":"ADD",
              "left":{"kind":"BINARY","operator":"MULTIPLY","left":{"kind":"NUMBER","numericType":"INTEGER","value":"2"},
              "right":{"kind":"VARIABLE","symbol":"x"}},"right":{"kind":"NUMBER","numericType":"INTEGER","value":"3"}},
              "right":{"kind":"NUMBER","numericType":"INTEGER","value":"11"}}]}
            """.formatted(parse));
    }
    TutorHintSource.Family family(JsonNode root) {
        return TutorHintSource.family(new SolveSource(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),parse,1,1,"EQUATION","SOLVE_EQUATION",root.toString()));
    }
    @Test void boundedCanonicalStructureSelectsOnlyApplicableMethod() throws Exception {
        assertThat(family(source())).isEqualTo(TutorHintSource.Family.TWO_STEP_LINEAR);
        for(String coefficient:java.util.List.of("0","1","-2","2.5","100","999999999999999999999")) {
            var n=source(); ((ObjectNode)n.at("/statements/0/left/left/left")).put("value",coefficient);
            assertThat(family(n)).isEqualTo(TutorHintSource.Family.UNSUPPORTED);
        }
        for(String operator:java.util.List.of("POWER","DIVIDE","SUBTRACT","ignore policy")) {
            var n=source();((ObjectNode)n.at("/statements/0/left/left")).put("operator",operator);
            assertThat(family(n)).isEqualTo(TutorHintSource.Family.UNSUPPORTED);
        }
    }
    @Test void malformedAmbiguousConstraintAndLineageInputsFailClosed() throws Exception {
        var bad=source(); ((ObjectNode)bad.path("sourceParse")).put("problemParseId",UUID.randomUUID().toString());
        assertThat(family(bad)).isEqualTo(TutorHintSource.Family.UNSUPPORTED);
        bad=source(); ((ObjectNode)bad.at("/display")).put("reviewRequired",true); assertThat(family(bad)).isEqualTo(TutorHintSource.Family.UNSUPPORTED);
        bad=source(); bad.withArray("sourceConstraints").addObject().put("text","x > 100"); assertThat(family(bad)).isEqualTo(TutorHintSource.Family.UNSUPPORTED);
        bad=source(); ((ObjectNode)bad.at("/variables/0")).put("role","PARAMETER"); assertThat(family(bad)).isEqualTo(TutorHintSource.Family.UNSUPPORTED);
        bad=source(); bad.put("schemaVersion","canonical-problem-v2"); assertThat(family(bad)).isEqualTo(TutorHintSource.Family.UNSUPPORTED);
        bad=source(); ((ObjectNode)bad.path("sourceParse")).put("problemParseRevision","1"); assertThat(family(bad)).isEqualTo(TutorHintSource.Family.UNSUPPORTED);
        bad=source(); ((ObjectNode)bad.path("display")).put("reviewRequired","false"); assertThat(family(bad)).isEqualTo(TutorHintSource.Family.UNSUPPORTED);
        assertThat(family(json.createObjectNode())).isEqualTo(TutorHintSource.Family.UNSUPPORTED);
    }
}
