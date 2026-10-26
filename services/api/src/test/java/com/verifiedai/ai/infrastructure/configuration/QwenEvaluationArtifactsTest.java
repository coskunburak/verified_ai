package com.verifiedai.ai.infrastructure.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.ai.application.*;
import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.solving.application.*;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class QwenEvaluationArtifactsTest {
    @Test void manifestMatchesPinnedConfigurationAndImmutablePrompt() throws Exception {
        var m=new ObjectMapper().readTree(Files.readAllBytes(Path.of("../../infra/local/qwen-model-release.json")));
        assertThat(m.path("revision").asText()).isEqualTo(VllmSolverProperties.REVISION);
        assertThat(m.path("servedModelId").asText()).isEqualTo(VllmSolverProperties.MODEL);
        assertThat(m.path("prompt").path("sha256").asText()).isEqualTo(PromptRegistry.defaults().require("solver","v002").contentSha256());
        assertThat(m.path("productionApproved").asBoolean()).isFalse();
        assertThat(m.path("weightsSha256").isNull()).isTrue();
        assertThat(m.path("quantizationTarget").path("status").asText()).startsWith("BLOCKED");
    }
    @Test void syntheticCorpusSeparatesSupportedSourcesFromFutureScopeAndReview() throws Exception {
        var root=new ObjectMapper().readTree(Files.readAllBytes(Path.of("../../evaluations/fixtures/solver/qwen-smoke-v1.json")));
        var validator=new SolutionCandidateValidator();int supported=0,rejected=0;
        for(var item:root.path("cases")) {
            var c=item.path("source");
            var s=new SolveSource(UUID.fromString(item.path("canonicalProblemId").asText()),UUID.randomUUID(),UUID.randomUUID(),
                UUID.fromString(item.path("problemParseId").asText()),1,1,c.path("problemType").asText(),c.path("taskType").asText(),c.toString());
            assertThat(item.path("origin").asText()).isEqualTo("PROJECT_AUTHORED_SYNTHETIC");
            assertThat(item.path("trainingEligibility").asText()).isEqualTo("NOT_ELIGIBLE");
            if("SOURCE_REJECTED".equals(item.path("expectedDisposition").asText())) {
                assertThatThrownBy(()->validator.sourceDocument(s)).isInstanceOf(CandidateRejected.class);rejected++;
            } else { assertThatCode(()->validator.sourceDocument(s)).doesNotThrowAnyException();supported++; }
        }
        assertThat(supported).isEqualTo(11);assertThat(rejected).isEqualTo(7);
    }
}
