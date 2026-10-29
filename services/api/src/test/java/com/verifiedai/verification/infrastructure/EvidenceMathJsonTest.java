package com.verifiedai.verification.infrastructure;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.solving.domain.model.canonical.MathExpression;
import com.verifiedai.verification.domain.model.PlanningSource;
import com.verifiedai.verification.infrastructure.persistence.EvidenceMathJson;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EvidenceMathJsonTest {
    private final ObjectMapper mapper = new ObjectMapper().registerModule(EvidenceMathJson.module())
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);

    record Envelope(List<PlanningSource.Restriction> restrictions, String followingField) { }

    private Envelope evidence() {
        var x = new MathExpression.Variable("x");
        var one = new MathExpression.Number(MathExpression.NumericType.INTEGER, "1");
        var left = new MathExpression.Binary(MathExpression.BinaryOperator.ADD, x, one);
        var right = new MathExpression.Function(MathExpression.FunctionName.EXP, List.of(x));
        return new Envelope(List.of(new PlanningSource.Restriction("bounded-x",
            PlanningSource.Relation.LESS_THAN, left, right)), "preserved-after-ast");
    }

    @Test void embeddedRestrictionTreesLeaveSiblingFieldsForTheEnclosingReader() throws Exception {
        var expected = evidence();
        String json = mapper.writeValueAsString(expected);
        assertThat(mapper.readValue(json, Envelope.class)).isEqualTo(expected);
        var tree = mapper.readTree(json);
        assertThat(tree.path("restrictions").get(0).path("left").path("kind").asText()).isEqualTo("BINARY");
        assertThat(tree.path("restrictions").get(0).path("right").path("kind").asText()).isEqualTo("FUNCTION");
    }

    @Test void documentTrailingTokensRemainForbidden() throws Exception {
        String json = mapper.writeValueAsString(evidence());
        assertThatThrownBy(() -> mapper.readValue(json + " {}", Envelope.class))
            .isInstanceOf(com.fasterxml.jackson.databind.exc.MismatchedInputException.class);
    }

    @Test void embeddedTreesStillRejectUnknownAndDuplicateFields() throws Exception {
        var tree = mapper.readTree(mapper.writeValueAsString(evidence()));
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree.path("restrictions").get(0).path("left"))
            .put("unexpected", true);
        assertThatThrownBy(() -> mapper.readValue(tree.toString(), Envelope.class))
            .isInstanceOf(com.fasterxml.jackson.databind.JsonMappingException.class);
        String json = mapper.writeValueAsString(evidence());
        assertThatThrownBy(() -> mapper.readValue(json.replace("\"kind\":\"BINARY\"",
            "\"kind\":\"BINARY\",\"kind\":\"BINARY\""), Envelope.class))
            .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }
}
