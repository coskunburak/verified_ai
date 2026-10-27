package com.verifiedai.solving.application.secondary;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.solving.SolverTestData;
import com.verifiedai.solving.application.SolutionCandidateValidator;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Offline synthetic contract cases only; never invokes a model or promotes a release. */
class SecondaryOfflineEvaluationTest {
    @Test void immutablePromptFixtureCasesProduceOnlyNonAuthoritativeEvidence() throws Exception {
        Path path=Path.of("evaluations/fixtures/secondary-solver/blind-v1.json");
        if(!Files.exists(path)) path=Path.of("../../evaluations/fixtures/secondary-solver/blind-v1.json");
        var fixtures=new ObjectMapper().readTree(Files.readString(path));
        assertThat(fixtures.path("realModelEvidence").asBoolean()).isFalse();
        assertThat(fixtures.path("promptSha256").asText()).isEqualTo(com.verifiedai.ai.application.PromptRegistry.defaults()
            .require("secondary-solver","v001").contentSha256());
        var source=SolverTestData.source();var validator=new SolutionCandidateValidator();var analyzer=new BlindAgreementAnalyzer();
        for(var c:fixtures.path("cases")) {
            if(c.has("primaryFinalAnswer")) {
                var first=SolverTestData.candidate(source);first.set("finalAnswer",c.path("primaryFinalAnswer"));
                var second=SolverTestData.candidate(source);second.set("finalAnswer",c.path("secondaryFinalAnswer"));
                var evidence=analyzer.compare(validator.validate(first.toString(),source,"LOCAL_FIXTURE"),
                    validator.validate(second.toString(),source,"LOCAL_FIXTURE"),"SYNTHETIC_SOURCE_BLIND_ONLY");
                assertThat(evidence.outcome().name()).as(c.path("id").asText()).isEqualTo(c.path("expected").asText());
                assertThat(evidence.mathematicalVerificationPerformed()).isFalse();
            } else {
                var payload=new BlindSecondaryRequestBuilder(validator).build(source).canonicalProblemJson();
                for(var field:c.path("forbiddenFields")) assertThat(payload).doesNotContain("\""+field.asText()+"\"");
            }
        }
    }
}
