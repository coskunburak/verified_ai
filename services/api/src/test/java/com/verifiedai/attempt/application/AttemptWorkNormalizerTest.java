package com.verifiedai.attempt.application;

import com.verifiedai.attempt.domain.*;
import com.verifiedai.problem.application.canonicalization.CanonicalManualStepNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AttemptWorkNormalizerTest {
    final AttemptWorkNormalizer normalizer=new AttemptWorkNormalizer(new CanonicalManualStepNormalizer());
    final AttemptSourceIdentity source=new AttemptSourceIdentity(UUID.randomUUID(),1,UUID.randomUUID(),1);
    AttemptWorkInput manual(List<AttemptWorkInput.Step> steps) { return new AttemptWorkInput("attempt-structured-work-request-v1",source,AttemptWorkInput.Engine.MANUAL,null,null,null,null,List.of(),steps); }
    AttemptWorkInput.Step step(String text,AttemptWorkInput.Role role,boolean confirmed) { return new AttemptWorkInput.Step(UUID.randomUUID(),text,role,List.of(),confirmed); }
    @Test void syntheticMathGoldensPreserveSyntaxWithoutProvingClaims() throws Exception {
        try(var stream=getClass().getResourceAsStream("/schemas/fixtures/attempt-structured-work-golden.json")) {
            assertThat(stream).isNotNull(); var cases=new ObjectMapper().readTree(stream);
            for(var fixture:cases.path("cases")) {
                var input=manual(List.of(step(fixture.path("text").asText(),AttemptWorkInput.Role.INTERMEDIATE,fixture.path("confirmed").asBoolean())));
                var actual=normalizer.normalize(input,Set.of("x")).getFirst();
                assertThat(actual.originalText()).as(fixture.path("id").asText()).isEqualTo(fixture.path("text").asText());
                assertThat(actual.reviewedText()).isEqualTo(actual.originalText());
                assertThat(actual.normalizationStatus().name()).isEqualTo(fixture.path("status").asText());
                for(var token:fixture.path("contains")) assertThat(actual.normalizedStatement()).contains(token.asText());
            }
        }
    }
    @Test void orderAndFinalIntentAreExplicitAndIdsSurviveReorder() {
        var a=step("x=4",AttemptWorkInput.Role.INTERMEDIATE,true); var b=step("4",AttemptWorkInput.Role.FINAL_ANSWER,true);
        var work=normalizer.normalize(manual(List.of(b,a)),Set.of("x"));
        assertThat(work).extracting(AttemptStructuredWork.Step::id).containsExactly(b.id(),a.id());
        assertThat(work).extracting(AttemptStructuredWork.Step::index).containsExactly(1,2);
        assertThat(work.getLast().role()).isEqualTo(AttemptWorkInput.Role.INTERMEDIATE);
        assertThatThrownBy(()->manual(List.of(b,step("5",AttemptWorkInput.Role.FINAL_ANSWER,true)))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void correctionSegmentationAndUncertaintyNeverOverwriteTranscription() {
        UUID id=UUID.randomUUID(); var raw=new AttemptWorkInput.Observation(id,"1/l = O",new java.math.BigDecimal("0.2"),null);
        var first=new AttemptWorkInput.Step(UUID.randomUUID(),"x=4",AttemptWorkInput.Role.INTERMEDIATE,List.of(id),false);
        var second=new AttemptWorkInput.Step(UUID.randomUUID(),"4",AttemptWorkInput.Role.FINAL_ANSWER,List.of(id),true);
        var input=new AttemptWorkInput("attempt-structured-work-request-v1",source,AttemptWorkInput.Engine.NATIVE_VISION,UUID.randomUUID(),null,null,null,List.of(raw),List.of(first,second));
        var result=normalizer.normalize(input,Set.of("x"));
        assertThat(input.observations().getFirst().rawText()).isEqualTo("1/l = O");
        assertThat(result.getFirst().originalText()).isNull(); assertThat(result.getFirst().normalizedStatement()).isNull();
        assertThat(result.getFirst().reviewStatus()).isEqualTo(AttemptStructuredWork.ReviewStatus.REQUIRED);
        assertThat(result.getLast().normalizationStatus()).isEqualTo(AttemptStructuredWork.NormalizationStatus.NORMALIZED);
        assertThatThrownBy(()->input.observations().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void noSilentObservationLossForgedAuthorityOrUnicodeOverflow() {
        var steps=java.util.stream.IntStream.range(0,12).mapToObj(i->step("x="+i,AttemptWorkInput.Role.INTERMEDIATE,true)).toList();
        assertThat(normalizer.normalize(manual(steps),Set.of("x"))).hasSize(12);
        assertThatThrownBy(()->manual(java.util.stream.Stream.concat(steps.stream(),steps.stream().limit(1)).toList())).isInstanceOf(IllegalArgumentException.class);
        for(String text:List.of("", " ", "x\0", "😀".repeat(129), "\uD800"))
            assertThatThrownBy(()->step(text,AttemptWorkInput.Role.INTERMEDIATE,true)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new AttemptWorkInput.Box(java.math.BigDecimal.ONE,java.math.BigDecimal.ZERO,java.math.BigDecimal.ONE,java.math.BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new AttemptWorkInput("attempt-structured-work-request-v1",source,AttemptWorkInput.Engine.MANUAL,UUID.randomUUID(),null,null,null,List.of(),List.of(steps.getFirst()))).isInstanceOf(IllegalArgumentException.class);
        var raw=new AttemptWorkInput.Observation(UUID.randomUUID(),"x",null,null);
        assertThatThrownBy(()->new AttemptWorkInput("attempt-structured-work-request-v1",source,AttemptWorkInput.Engine.NATIVE_VISION,UUID.randomUUID(),null,null,null,List.of(raw),List.of(steps.getFirst()))).isInstanceOf(IllegalArgumentException.class);
    }
}
