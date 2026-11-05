package com.verifiedai.attempt.domain;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class AttemptAnalysisPolicyTest {
    @Test void transitionsDoNotRewritePublishedEvidence() {
        assertThat(AttemptAnalysisPolicy.canTransition(AttemptStatus.SUBMITTED,AttemptStatus.ANALYZED)).isTrue();
        assertThat(AttemptAnalysisPolicy.canTransition(AttemptStatus.SUBMITTED,AttemptStatus.STALE_SOURCE)).isTrue();
        assertThat(AttemptAnalysisPolicy.canTransition(AttemptStatus.ANALYZED,AttemptStatus.SUBMITTED)).isFalse();
    }
    @Test void boundsAndIdempotencyAreMandatory() {
        assertThatThrownBy(()->AttemptAnalysisPolicy.submission(null,List.of("x=2"))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->AttemptAnalysisPolicy.submission("synthetic-key",List.of())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->AttemptAnalysisPolicy.submission("synthetic-key",Collections.nCopies(13,"x=2"))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->AttemptAnalysisPolicy.submission("synthetic-key",List.of("a".repeat(257)))).isInstanceOf(RuntimeException.class);
    }
    @Test void uncertaintyNeverBecomesFirstError() {
        var id=UUID.randomUUID();var result=AttemptAnalysisResult.unavailable(id,3,AttemptAnalysisResult.Reason.TIMEOUT);
        assertThat(result.firstWrongStepIndex()).isNull(); assertThat(result.outcome()).isEqualTo(AttemptOutcome.NEEDS_REVIEW);
        assertThat(result.steps().get(1).verdict()).isEqualTo(StepVerdict.NOT_ASSESSED);
        assertThatThrownBy(()->result.validateFor(UUID.randomUUID(),3)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsFalseProgressAndForgedWitness() {
        var id=UUID.randomUUID();var uncertainty=AttemptAnalysisResult.unavailable(id,1,AttemptAnalysisResult.Reason.UNSUPPORTED_MATH);
        assertThatThrownBy(()->new AttemptAnalysisResult(uncertainty.schemaVersion(),uncertainty.verifierVersion(),uncertainty.taxonomyVersion(),
            id,id.toString(),AttemptOutcome.INCOMPLETE_VALID_SO_FAR,null,"REAL_POLYNOMIAL",uncertainty.steps())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new CounterexampleEvidence("attempt-witness-v1","EQUATION_TRUTH",Map.of(),"2","4","EQUATION_EQUIVALENCE",true,
            "bounded-attempt-v1","DIFFERENT_TRUTH")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void differentRationalSpellingsDoNotCreateCounterexamples() {
        for(var pair:List.of(List.of("1/2","2/4"),List.of("-1/2","-2/4"),List.of("0","-0/3"),List.of("2","8/4"))) {
            assertThatThrownBy(()->expressionWitness(pair.getFirst(),pair.getLast())).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(expressionWitness("1/2","2/3").claimRefuted()).isEqualTo("EXPRESSION_IDENTITY");
        // Differences smaller than double precision must remain distinguishable.
        assertThat(expressionWitness("1","100000000000000000001/100000000000000000000")).isNotNull();
    }
    private CounterexampleEvidence expressionWitness(String before,String after) {
        return new CounterexampleEvidence("attempt-witness-v1","EXPRESSION_VALUES",Map.of(),before,after,
            "EXPRESSION_IDENTITY",true,"bounded-attempt-v1","UNEQUAL_VALUES");
    }
    @Test void aggregateRejectsForgedAssessmentTrainingAndOrder() {
        var now=java.time.Instant.now(); var source=new AttemptSourceIdentity(UUID.randomUUID(),1,UUID.randomUUID(),1);
        var id=UUID.randomUUID(); var problem=UUID.randomUUID();
        var saved=new StudentAttempt("student-attempt-v2",id,problem,source,AttemptSourceType.MANUAL,"manual-canonical-v1",
            AttemptStatus.SUBMITTED,List.of(),null,now,now,false,null," 4 ");
        assertThat(saved.finalAnswer()).isEqualTo(" 4 "); assertThat(saved.legacyRepresentation().steps()).hasSize(1);
        assertThatThrownBy(()->new StudentAttempt("student-attempt-v2",id,problem,source,AttemptSourceType.MANUAL,"manual-canonical-v1",
            AttemptStatus.ANALYZED,List.of(),null,now,now,false,null,"4")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new StudentAttempt("student-attempt-v2",id,problem,source,AttemptSourceType.MANUAL,"manual-canonical-v1",
            AttemptStatus.SUBMITTED,List.of(),null,now,now,true,null,"4")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new StudentAttempt("student-attempt-v1",id,problem,source,AttemptSourceType.MANUAL,"manual-canonical-v1",
            AttemptStatus.SUBMITTED,List.of(new StudentAttemptStep(2,"x=4",null)),null,now,now,false)).isInstanceOf(IllegalArgumentException.class);
        for(var terminal:List.of(AttemptStatus.ANALYZED,AttemptStatus.STALE_SOURCE))
            for(var target:AttemptStatus.values()) assertThat(AttemptAnalysisPolicy.canTransition(terminal,target)).isFalse();
    }

}
