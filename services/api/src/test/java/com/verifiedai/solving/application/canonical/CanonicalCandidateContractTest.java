package com.verifiedai.solving.application.canonical;

import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.solving.CanonicalTestData;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import static org.assertj.core.api.Assertions.*;

class CanonicalCandidateContractTest {
    @TestFactory Stream<DynamicTest> everyExistingAcceptedV1FixtureRetainsExactMath() throws Exception {
        var codec=new CanonicalMathCodec();
        var cases=new com.fasterxml.jackson.databind.ObjectMapper().readTree(Files.readString(Path.of("../../evaluations/fixtures/solver/cases.json")));
        return java.util.stream.StreamSupport.stream(cases.path("cases").spliterator(),false)
            .filter(c->c.path("semanticAccepted").asBoolean()).map(c->DynamicTest.dynamicTest(c.path("id").asText(),()->{
                var s=c.path("source");
                var source=new SolveSource(UUID.fromString(c.path("canonicalProblemId").asText()),UUID.randomUUID(),UUID.randomUUID(),
                    UUID.fromString(c.path("problemParseId").asText()),c.path("problemParseRevision").asInt(),1,s.path("problemType").asText(),s.path("taskType").asText(),s.toString());
                var output=c.path("output");
                var a=CanonicalTestData.artifact(source,Role.PRIMARY,UUID.randomUUID(),null,output.toString());
                var mapped=CanonicalTestData.mapper().map(a,source,Version.CURRENT,CanonicalTestData.NOW);
                assertThat(codec.json(mapped.finalStatement())).isEqualTo(output.path("finalAnswer"));
                assertThat(mapped.steps()).hasSize(output.path("steps").size());
                for(int i=0;i<mapped.steps().size();i++) {
                    assertThat(codec.json(mapped.steps().get(i).claim())).isEqualTo(output.path("steps").get(i).path("claim"));
                    assertThat(mapped.steps().get(i).explanation()).isEqualTo(output.path("steps").get(i).path("explanation").asText());
                }
            }));
    }
}
