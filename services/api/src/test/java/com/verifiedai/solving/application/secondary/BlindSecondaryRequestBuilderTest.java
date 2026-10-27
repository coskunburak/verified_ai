package com.verifiedai.solving.application.secondary;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.solving.SolverTestData;
import com.verifiedai.solving.application.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class BlindSecondaryRequestBuilderTest {
    final SolutionCandidateValidator validator=new SolutionCandidateValidator();
    final BlindSecondaryRequestBuilder builder=new BlindSecondaryRequestBuilder(validator);
    @Test void canonicalOnlyRequestCannotContainPrimaryArtifacts() {
        var source=SolverTestData.source();
        var primary=SolverTestData.candidate(source);
        primary.put("method","PRIMARY_METHOD_SECRET");primary.put("reasoning","PRIMARY_REASONING_SECRET");
        primary.put("rawOutput","PRIMARY_RAW_SECRET");primary.put("transcript","PRIMARY_TRANSCRIPT_SECRET");
        primary.put("finalAnswer","PRIMARY_ANSWER_SECRET");primary.put("steps","PRIMARY_STEPS_SECRET");
        var request=builder.build(source);
        assertThat(request.canonicalProblemJson()).isEqualTo(validator.serialize(validator.parse(source.canonicalJson())))
            .doesNotContain("PRIMARY_","finalAnswer","reasoning","rawOutput","transcript");
        assertThat(request.canonicalProblemId()).isEqualTo(source.canonicalProblemId());
        assertThat(request.problemParseId()).isEqualTo(source.problemParseId());
        assertThat(request.problemParseRevision()).isEqualTo(source.problemParseRevision());
    }
    @Test void contaminatedRootIsRejectedRatherThanForwarded() {
        var source=SolverTestData.source();var root=(ObjectNode)validator.parse(source.canonicalJson());
        for(String field:new String[]{"finalAnswer","steps","method","reasoning","rawOutput","transcript","comparison"}) {
            var contaminated=root.deepCopy();contaminated.put(field,"SECRET");
            var input=new SolveSource(source.canonicalProblemId(),source.userId(),source.problemSessionId(),source.problemParseId(),
                source.problemParseRevision(),1,source.problemType(),source.taskType(),contaminated.toString());
            assertThatThrownBy(()->builder.build(input)).isInstanceOf(CandidateRejected.class);
        }
    }
    @Test void publicBuildBoundaryAcceptsOnlyCanonicalSourceAndHasNoPrimaryStore() {
        var method=Arrays.stream(BlindSecondaryRequestBuilder.class.getDeclaredMethods()).filter(m->m.getName().equals("build")).findFirst().orElseThrow();
        assertThat(method.getParameterTypes()).containsExactly(SolveSource.class);
        assertThat(Arrays.stream(BlindSecondaryRequestBuilder.class.getDeclaredFields()).map(f->f.getType().getName()))
            .noneMatch(n->n.contains("SolveStore") || n.contains("PrimarySolve") || n.contains("SolveView"));
    }
    @Test void reviewRequiredSourceCannotBecomeSecondaryInput() {
        var s=SolverTestData.source();var root=(ObjectNode)validator.parse(s.canonicalJson());
        ((ObjectNode)root.path("display")).put("reviewRequired",true);
        var bad=new SolveSource(s.canonicalProblemId(),s.userId(),s.problemSessionId(),s.problemParseId(),1,1,s.problemType(),s.taskType(),root.toString());
        assertThatThrownBy(()->builder.build(bad)).isInstanceOf(CandidateRejected.class);
    }
}
