package com.verifiedai.attempt.api;

import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AttemptWorkRequestTest {
    String fixture() throws Exception {
        try(var stream=getClass().getResourceAsStream("/schemas/fixtures/attempt-structured-work-request-v1.json")) {
            return new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    @Test void strictRequestRejectsUnknownDuplicateCoercedAndAuthorityFields() throws Exception {
        String valid=fixture(); assertThat(AttemptWorkRequest.parse(VersionedJsonContract.parse(valid)).steps()).hasSize(2);
        for(String field:java.util.List.of("provider","normalizationVersion","trainingEligible","ownerId","verified")) {
            var bad=(ObjectNode)VersionedJsonContract.parse(valid); bad.put(field,"forged");
            assertThatThrownBy(()->AttemptWorkRequest.parse(bad)).isInstanceOf(RuntimeException.class);
        }
        assertThatThrownBy(()->VersionedJsonContract.parse(valid.replace("\"engine\": \"MANUAL\"","\"engine\": \"MANUAL\", \"engine\": \"MANUAL\""))).isInstanceOf(IllegalArgumentException.class);
        var bad=(ObjectNode)VersionedJsonContract.parse(valid); ((ObjectNode)bad.path("steps").get(0)).put("confirmed","true");
        assertThatThrownBy(()->AttemptWorkRequest.parse(bad)).isInstanceOf(RuntimeException.class);
    }
    @Test void missingExplicitNullableKeysAndInvalidIdsReject() throws Exception {
        var bad=(ObjectNode)VersionedJsonContract.parse(fixture()); bad.remove("captureId");
        assertThatThrownBy(()->AttemptWorkRequest.parse(bad)).isInstanceOf(RuntimeException.class);
        var uuid=(ObjectNode)VersionedJsonContract.parse(fixture()); ((ObjectNode)uuid.path("source")).put("canonicalId","1-1-1-1-1");
        assertThatThrownBy(()->AttemptWorkRequest.parse(uuid)).isInstanceOf(RuntimeException.class);
    }
    @Test void sharedNativeGatewayAndNegativeFixturesMatchRuntimePolicy() throws Exception {
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        for(String name:java.util.List.of("native","gateway")) {
            try(var stream=getClass().getResourceAsStream("/schemas/fixtures/attempt-structured-work-"+name+"-v1.json")) {
                assertThat(AttemptWorkRequest.parse(mapper.readTree(stream)).observations()).hasSize(1);
            }
        }
        try(var stream=getClass().getResourceAsStream("/schemas/fixtures/attempt-structured-work-invalid-v1.json")) {
            for(var fixture:mapper.readTree(stream).path("cases"))
                assertThatThrownBy(()->AttemptWorkRequest.parse(fixture.path("input"))).as(fixture.path("name").asText()).isInstanceOf(RuntimeException.class);
        }
    }
}
