package com.verifiedai.problem.application.canonicalization;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.calculus.CalculusIntegrationData;
import com.verifiedai.problem.application.solving.CalculusSourceDocument;
import com.verifiedai.problem.infrastructure.persistence.entity.ProblemParseJpaEntity;
import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CalculusSourceContractTest {
    @Test void allThreeTasksBuildFromAcceptedUserRevisionsWithOriginalAstAndSourceLineage() {
        for (var example : CalculusIntegrationData.cases()) {
            var parse=example.path("parse"); var id=UUID.randomUUID();
            var row=ProblemParseJpaEntity.fromUserCorrection(id,UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),
                UUID.randomUUID(),1,2,"SUPPORTED",null,false,"problem-parse-v2",parse.toString(),"contract-key",
                "a".repeat(64),"OTHER","{\"fields\":[\"CALCULUS_METADATA\"]}","parse-correction-v1",Instant.now());
            var canonical=new CanonicalProblemBuilder().buildCalculus(row);
            assertThat(canonical.path("sourceParse").path("problemParseId").asText()).isEqualTo(id.toString());
            assertThat(canonical.path("calculus")).isEqualTo(parse.path("calculus"));
            assertThat(canonical.path("acceptedParse")).isEqualTo(parse);
            VersionedJsonContract.validate("canonical-problem-v2",canonical.toString());
        }
    }
    @Test void invalidDomainDirectionIntegralScopeRationalsSymbolsAndUnknownFieldsAreRejected() {
        var derivative=(ObjectNode)CalculusIntegrationData.cases().getFirst().path("parse").deepCopy();
        var bad=derivative.deepCopy(); ((ObjectNode)bad.path("calculus").path("variable")).put("domain","UNKNOWN"); rejected(bad);
        bad=derivative.deepCopy(); ((ObjectNode)bad.path("calculus").path("original").path("left")).put("symbol","y"); rejected(bad);
        bad=derivative.deepCopy(); ((ObjectNode)bad.path("calculus").path("interval")).set("lower",
            CalculusIntegrationData.JSON.createObjectNode().put("numerator","2").put("denominator","2")); rejected(bad);
        bad=derivative.deepCopy(); ((ObjectNode)bad.path("calculus")).put("integralScope","DEFINITE"); rejected(bad);
        bad=derivative.deepCopy(); bad.put("untrusted",true); rejected(bad);
        bad=derivative.deepCopy(); bad.withArray("variables").addObject().put("symbol","y").put("role","VARIABLE").putArray("sourceBlockIds").add("block-1"); rejected(bad);
        var limit=(ObjectNode)CalculusIntegrationData.cases().get(3).path("parse").deepCopy();
        ((ObjectNode)limit.path("calculus")).putNull("approach"); rejected(limit);
        limit=(ObjectNode)CalculusIntegrationData.cases().get(3).path("parse").deepCopy();
        ((ObjectNode)limit.path("calculus").path("approach")).remove("direction"); rejected(limit);
    }
    @Test void exactBoundsAndEachExplicitDirectionAreAcceptedButEmptyIntervalsAreRejected() {
        for (String direction : List.of("LEFT","RIGHT","TWO_SIDED")) {
            var parse=(ObjectNode)CalculusIntegrationData.cases().get(3).path("parse").deepCopy();
            ((ObjectNode)parse.path("calculus").path("approach")).put("direction",direction);
            var interval=(ObjectNode)parse.path("calculus").path("interval");
            interval.set("lower",CalculusIntegrationData.JSON.createObjectNode().put("numerator","0").put("denominator","1"));
            interval.set("upper",CalculusIntegrationData.JSON.createObjectNode().put("numerator","2").put("denominator","1"));
            CalculusSourceDocument.parse(parse.toString());
            interval.set("lower",interval.path("upper")); rejected(parse);
        }
    }
    @Test void duplicateKeysDepthAndWrongVersionsFailBeforeCanonicalization() {
        String raw=CalculusIntegrationData.cases().getFirst().path("parse").toString();
        assertThatIllegalArgumentException().isThrownBy(()->CalculusSourceDocument.parse(raw.replaceFirst("\\{","{\"schemaVersion\":\"problem-parse-v2\",")));
        assertThatIllegalArgumentException().isThrownBy(()->CalculusSourceDocument.parse(raw.replace("problem-parse-v2","problem-parse-v1")));
        assertThatIllegalArgumentException().isThrownBy(()->VersionedJsonContract.parse("[".repeat(90)+"0"+"]".repeat(90)));
        assertThatIllegalArgumentException().isThrownBy(()->VersionedJsonContract.parse(" ".repeat(65537)));
    }
    private static void rejected(ObjectNode parse) {
        assertThatIllegalArgumentException().isThrownBy(()->CalculusSourceDocument.parse(parse.toString()));
    }
}
