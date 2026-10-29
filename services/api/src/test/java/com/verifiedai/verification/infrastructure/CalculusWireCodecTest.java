package com.verifiedai.verification.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.verification.infrastructure.http.CalculusWireCodec;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CalculusWireCodecTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CalculusWireCodec codec = new CalculusWireCodec();
    private ObjectNode request() throws Exception {
        return (ObjectNode) mapper.readTree(Files.readString(Path.of("../../packages/test-fixtures/verification/calculus-v1/cases.json"))).get(0).path("request");
    }
    private ObjectNode result(ObjectNode request) {
        var result = mapper.createObjectNode();
        result.put("schemaVersion", "calculus-execution-result-v1").put("methodVersion", "v1")
            .put("verifierVersion", "bounded-calculus-v1").put("outcome", "PROVEN").put("reasonCode", "EXACT_DERIVATIVE");
        for (var name : List.of("executionId", "correlationId", "scope", "method")) result.set(name, request.get(name));
        result.putArray("restrictionIds"); result.putNull("diagnostic");
        result.putObject("proof").put("descriptor", "DERIVATIVE_IDENTITY").put("scope", "OPEN_INTERVAL")
            .putNull("leftValue").putNull("rightValue");
        return result;
    }
    @Test void everySharedTypedRequestRoundTripsWithoutScopeChanges() throws Exception {
        var cases = mapper.readTree(Files.readString(Path.of("../../packages/test-fixtures/verification/calculus-v1/cases.json")));
        for (var fixture : cases) {
            var input = fixture.path("request");
            assertThat(mapper.readTree(codec.request(codec.request(mapper.writeValueAsBytes(input))))).isEqualTo(input);
        }
    }
    @Test void scopeUuidIsEmittedAsCanonicalTextInsteadOfNativeBinary() throws Exception {
        var input = request();
        var typed = codec.request(mapper.writeValueAsBytes(input));
        var encoded = mapper.readTree(codec.request(typed));
        assertThat(encoded.path("scope").path("identity").isTextual()).isTrue();
        assertThat(encoded.path("scope").path("identity").textValue()).isEqualTo(typed.scope().identity().toString());
        assertThat(encoded.path("executionId").textValue()).isEqualTo(typed.executionId().toString());
        assertThat(encoded.path("interval").path("lower").isNull()).isTrue();
        assertThat(encoded.path("interval").path("upper").isNull()).isTrue();
        assertThat(encoded.path("approach").isNull()).isTrue();
        assertThat(encoded.path("integralScope").isNull()).isTrue();
    }
    @Test void validDerivativeResponseBindsScopeAndExactDescriptor() throws Exception {
        var p = request(); var typed = codec.request(mapper.writeValueAsBytes(p));
        var response = codec.result(mapper.writeValueAsBytes(result(p)), typed);
        assertThat(response.scope()).isEqualTo(typed.scope());
        assertThat(response.proof().descriptor().name()).isEqualTo("DERIVATIVE_IDENTITY");
    }
    @Test void incompatibleVersionsAndMissingTaskMetadataAreRejected() throws Exception {
        var p = request();
        p.put("schemaVersion", "verification-execution-v1");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.request(mapper.writeValueAsBytes(p)));
        p.put("schemaVersion", "calculus-execution-v1"); p.put("method", "FINITE_LIMIT");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.request(mapper.writeValueAsBytes(p)));
        p.put("method", "INTEGRAL_DIFFERENTIATION_BACKCHECK");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.request(mapper.writeValueAsBytes(p)));
    }
    @Test void sourceCandidateVersionsAndScopeMismatchFailClosed() throws Exception {
        var p = request(); var typed = codec.request(mapper.writeValueAsBytes(p));
        var response = result(p).deepCopy();
        ((ObjectNode) response.path("scope")).put("candidateRevision", 2);
        assertThatIllegalArgumentException().isThrownBy(() -> codec.result(mapper.writeValueAsBytes(response), typed));
        ((ObjectNode) p.path("scope")).put("sourceVersion", "canonical-problem-v1");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.request(mapper.writeValueAsBytes(p)));
    }
    @Test void methodProofOutcomeAndAlgorithmContradictionsAreRejected() throws Exception {
        var p = request(); var typed = codec.request(mapper.writeValueAsBytes(p));
        for (var field : List.of("schemaVersion", "methodVersion", "verifierVersion", "outcome", "reasonCode", "method")) {
            var response = result(p); response.put(field, switch (field) {
                case "outcome" -> "DISPROVEN";
                case "reasonCode" -> "TIMEOUT";
                case "method" -> "FINITE_LIMIT";
                default -> "unapproved-v99";
            });
            assertThatIllegalArgumentException().isThrownBy(() -> codec.result(mapper.writeValueAsBytes(response), typed));
        }
    }
    @Test void numericalAgreementCannotAcquireExactProof() throws Exception {
        var p = request(); var typed = codec.request(mapper.writeValueAsBytes(p));
        var response = result(p);
        response.put("reasonCode", "NUMERICAL_DIAGNOSTIC");
        response.putObject("diagnostic").put("policy", "calculus-binary64-v1").put("attempted", 16).put("evaluated", 16).put("agreeing", 16);
        assertThatIllegalArgumentException().isThrownBy(() -> codec.result(mapper.writeValueAsBytes(response), typed));
    }
    @Test void unknownFieldsDuplicateKeysOversizedResponsesAndNumericCoercionsAreRejected() throws Exception {
        var p = request(); var typed = codec.request(mapper.writeValueAsBytes(p));
        ((ObjectNode) p.path("original")).put("code", "untrusted input");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.request(mapper.writeValueAsBytes(p)));
        assertThatIllegalArgumentException().isThrownBy(() -> codec.result(new byte[16385], typed));
        assertThatIllegalArgumentException().isThrownBy(() -> codec.request("{\"method\":\"FINITE_LIMIT\",\"method\":\"FINITE_LIMIT\"}".getBytes(StandardCharsets.UTF_8)));
        var response = result(request());
        ((ObjectNode) response.path("scope")).put("sourceRevision", "1");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.result(mapper.writeValueAsBytes(response), typed));
    }
    @Test void restrictionReferencesAndUndeclaredVariablesCannotBeChanged() throws Exception {
        var p = request(); var typed = codec.request(mapper.writeValueAsBytes(p));
        var response = result(p); response.putArray("restrictionIds").add("unmatched");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.result(mapper.writeValueAsBytes(response), typed));
        p.putObject("candidate").put("kind", "VARIABLE").put("symbol", "t");
        assertThatIllegalArgumentException().isThrownBy(() -> codec.request(mapper.writeValueAsBytes(p)));
    }
}
