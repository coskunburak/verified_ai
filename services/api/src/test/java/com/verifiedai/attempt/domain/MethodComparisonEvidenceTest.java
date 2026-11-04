package com.verifiedai.attempt.domain;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class MethodComparisonEvidenceTest {
    private final ObjectMapper mapper=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private ObjectNode fixture() throws Exception {
        try(var input=getClass().getResourceAsStream("/schemas/fixtures/method-comparison-public-v2.json")) {
            return (ObjectNode)mapper.readTree(input);
        }
    }
    @Test void typedPublicFixtureAndHistoricalV1RemainReadable() throws Exception {
        var publicValue=fixture(); VersionedJsonContract.validate("method-comparison-public-v2",publicValue.toString());
        var evidence=mapper.treeToValue(publicValue.path("evidence"),MethodComparisonEvidence.class);
        evidence.validateFor(evidence.executionId(),evidence.source());
        assertThat(evidence.methods()).hasSize(3);
        var legacy=new AttemptLearningEvidence.Method("FACTORING",evidence.methods().getFirst().steps(),"verified-method-v1");
        assertThat(mapper.readValue(mapper.writeValueAsString(legacy),AttemptLearningEvidence.Method.class)).isEqualTo(legacy);
    }
    @Test void strictNestedAuthorityAndMalformedMathReject() throws Exception {
        var value=fixture(); ((ObjectNode)value.path("evidence").path("methods").get(0)).put("studentVerified",true);
        assertThatThrownBy(()->VersionedJsonContract.validate("method-comparison-public-v2",value.toString())).isInstanceOf(IllegalArgumentException.class);
        var bad=fixture(); ((ObjectNode)bad.path("evidence").path("methods").get(0).path("steps").get(0)).put("relation","FORGED");
        assertThatThrownBy(()->VersionedJsonContract.validate("method-comparison-public-v2",bad.toString())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void duplicateMethodsCompactIndicesAndWrongSourceReject() throws Exception {
        var value=fixture(); var raw=(ObjectNode)value.path("evidence");
        var evidence=mapper.treeToValue(raw,MethodComparisonEvidence.class);
        assertThatThrownBy(()->evidence.validateFor(UUID.randomUUID(),evidence.source())).isInstanceOf(IllegalArgumentException.class);
        var other=evidence.source().deepCopy(); ((ObjectNode)other).put("domainBasis","UNRESOLVED");
        assertThatThrownBy(()->evidence.validateFor(evidence.executionId(),other)).isInstanceOf(IllegalArgumentException.class);
        ((ObjectNode)raw.path("methods").get(0).path("comparison")).putArray("compactStepIndices").add(3);
        assertThatThrownBy(()->mapper.treeToValue(raw,MethodComparisonEvidence.class)).isInstanceOf(Exception.class);
        var duplicate=(ObjectNode)fixture().path("evidence");
        ((com.fasterxml.jackson.databind.node.ArrayNode)duplicate.path("methods")).set(1,duplicate.path("methods").get(0));
        assertThatThrownBy(()->mapper.treeToValue(duplicate,MethodComparisonEvidence.class)).isInstanceOf(Exception.class);
    }
}
