package com.verifiedai.solving.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.problem.application.solving.SolveSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import static org.assertj.core.api.Assertions.*;

class SolutionCandidateContractTest {
    @TestFactory Stream<DynamicTest> syntheticContractFixtures() throws Exception {
        var manifest=new ObjectMapper().readTree(Files.readString(Path.of("../../evaluations/fixtures/solver/cases.json")));
        return java.util.stream.StreamSupport.stream(manifest.path("cases").spliterator(),false).map(c->
            DynamicTest.dynamicTest(c.path("id").asText(),()->{
                var s=c.path("source");var source=new SolveSource(UUID.fromString(c.path("canonicalProblemId").asText()),
                    UUID.randomUUID(),UUID.randomUUID(),UUID.fromString(c.path("problemParseId").asText()),
                    c.path("problemParseRevision").asInt(),1,s.path("problemType").asText(),s.path("taskType").asText(),s.toString());
                var validator=new SolutionCandidateValidator();
                if(c.path("semanticAccepted").asBoolean())
                    assertThat(validator.validate(c.path("output").toString(),source,"LOCAL_FIXTURE")).isNotNull();
                else assertThatThrownBy(()->validator.validate(c.path("output").toString(),source,"LOCAL_FIXTURE"))
                    .isInstanceOf(CandidateRejected.class);
            }));
    }
}
