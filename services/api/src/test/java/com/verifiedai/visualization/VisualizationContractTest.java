package com.verifiedai.visualization;
import com.verifiedai.visualization.domain.VisualizationContract;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class VisualizationContractTest {
    private final ObjectMapper mapper=new ObjectMapper();
    String fixture(String name) throws Exception {
        try(var in=getClass().getResourceAsStream("/schemas/fixtures/math-visualization-"+name+"-v1.json")) {
            return new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
        }
    }
    @Test void sharedFixtureAndExactApproximateSeparation() throws Exception {
        var request=mapper.readTree(fixture("request"));
        var result=VisualizationContract.result(fixture("result"),request);
        assertThat(result.path("sampleSemantics").asText()).isEqualTo("APPROXIMATE_DISPLAY_ONLY");
        assertThat(result.has("verificationStatus")).isFalse();
        VisualizationContract.request(fixture("source"));
    }
    @Test void unknownFieldsSourceSubstitutionAndInvalidSamplesFailClosed() throws Exception {
        var request=mapper.readTree(fixture("request"));
        var result=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(fixture("result"));
        result.put("verificationStatus","VERIFIED");
        assertThatThrownBy(()->VisualizationContract.result(result.toString(),request)).isInstanceOf(IllegalArgumentException.class);
        result.remove("verificationStatus"); result.put("correlationId","other");
        assertThatThrownBy(()->VisualizationContract.result(result.toString(),request)).isInstanceOf(IllegalArgumentException.class);
        result.put("correlationId",request.path("correlationId").asText());
        ((com.fasterxml.jackson.databind.node.ObjectNode)result.path("segments").get(0).path("points").get(0)).put("y",1001);
        assertThatThrownBy(()->VisualizationContract.result(result.toString(),request)).isInstanceOf(IllegalArgumentException.class);
    }
}
