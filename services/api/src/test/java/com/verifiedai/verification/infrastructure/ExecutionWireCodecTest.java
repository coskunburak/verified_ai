package com.verifiedai.verification.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.solving.domain.model.canonical.MathStatement;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.model.MathematicalExecution.*;
import com.verifiedai.verification.infrastructure.http.ExecutionWireCodec;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.verifiedai.verification.PlanningTestData.number;
import static org.assertj.core.api.Assertions.*;

class ExecutionWireCodecTest {
    final ExecutionWireCodec codec = new ExecutionWireCodec();
    final Request request = new Request(UUID.randomUUID(), "wire", VerificationMethod.ARITHMETIC_EVALUATION,
        new MathStatement.Expression(number("0.3")), new MathStatement.Expression(number("0.3")), Map.of(), List.of());
    String response() {
        return """
            {"schemaVersion":"verification-execution-result-v1","executionId":"%s","correlationId":"wire",
             "method":"ARITHMETIC_EVALUATION","methodVersion":"v1","verifierVersion":"canonical-executor-v1",
             "outcome":"PROVEN","reasonCode":"EXACT_EQUAL","restrictionIds":[],
             "proof":{"descriptor":"EXACT_RATIONAL_COMPARISON","leftValue":{"numerator":"3","denominator":"10"},
             "rightValue":{"numerator":"3","denominator":"10"},"distinctRootCount":null}}
            """.formatted(request.executionId());
    }
    @Test void requestContainsOnlyApprovedTypedMathematics() throws Exception {
        var tree = new ObjectMapper().readTree(codec.request(request));
        assertThat(tree.path("original").path("expression").path("value").textValue()).isEqualTo("0.3");
        assertThat(tree.has("userId")).isFalse(); assertThat(tree.has("displayLatex")).isFalse();
        assertThat(codec.result(response().getBytes(java.nio.charset.StandardCharsets.UTF_8), request).outcome()).isEqualTo(Outcome.PROVEN);
    }
    @Test void mismatchedCorrelationVersionMethodAndProofAreRejected() {
        for (var corrupt : List.of(response().replace("\"wire\"", "\"other\""),
            response().replace("result-v1", "result-v9"), response().replace("ARITHMETIC_EVALUATION", "NUMERIC_SAMPLING"),
            response().replace("EXACT_RATIONAL_COMPARISON", "DOMAIN_CHECK"), response().replace("\"EXACT_EQUAL\"", "\"TIMEOUT\""))) {
            assertThatThrownBy(() -> codec.result(corrupt.getBytes(java.nio.charset.StandardCharsets.UTF_8), request))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void duplicateExtraTrailingAndOversizedResponsesAreRejected() {
        for (var corrupt : List.of(response().replace("\"outcome\":", "\"outcome\":\"DISPROVEN\",\"outcome\":"),
            response().replace("\"proof\":", "\"confidence\":1,\"proof\":"), response() + " {}", " ".repeat(16385))) {
            assertThatThrownBy(() -> codec.result(corrupt.getBytes(java.nio.charset.StandardCharsets.UTF_8), request))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
