package com.verifiedai.solving.domain.model.canonical;

import com.verifiedai.solving.CanonicalTestData;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CanonicalSolutionTest {
    CanonicalSolution base=CanonicalTestData.solution();
    Step step(String id,int order,List<String> dependencies) {
        return new Step(CanonicalSolution.stepIdentity(base.id(),id),id,order,Category.TRANSFORM,base.finalStatement(),"Untrusted explanation",dependencies);
    }
    void construct(Step... steps) { CanonicalTestData.copy(base,base.version(),List.of(steps)); }
    @Test void deterministicIdentityIsRoleAndVersionSpecific() {
        var origin=base.lineage().origin();
        assertThat(CanonicalSolution.identity(origin,base.version())).isEqualTo(base.id());
        assertThat(CanonicalSolution.identity(new Origin(Role.SECONDARY,origin.jobId()),base.version())).isNotEqualTo(base.id());
        assertThat(CanonicalSolution.identity(origin,new Version("canonical-solution-v1","candidate-normalizer-v2"))).isNotEqualTo(base.id());
    }
    @Test void immutableOrderedGraphRetainsDependencyOrder() {
        var source=new ArrayList<>(List.of(step("a",1,List.of()),step("b",2,List.of("a")),step("c",3,List.of("b","a"))));
        var solution=CanonicalTestData.copy(base,base.version(),source); source.clear();
        assertThat(solution.steps()).hasSize(3);assertThat(solution.steps().getLast().dependsOn()).containsExactly("b","a");
        assertThatThrownBy(()->solution.steps().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(()->solution.steps().getLast().dependsOn().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void graphRejectsDuplicateIdsOrdinalsMissingSelfForwardAndCycles() {
        assertThatThrownBy(()->construct(step("a",1,List.of()),step("a",2,List.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->construct(step("a",1,List.of()),step("b",1,List.of()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->construct(step("a",2,List.of()))).isInstanceOf(IllegalArgumentException.class);
        for(var deps:List.of(List.of("missing"),List.of("a"),List.of("b")))
            assertThatThrownBy(()->construct(step("a",1,deps),step("b",2,List.of("a")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->step("b",2,List.of("a","a"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void sourceRoleRequiresExactParentPresenceAndPositiveRevisions() {
        var l=base.lineage();
        assertThatThrownBy(()->new Lineage(l.userId(),l.sessionId(),l.canonicalProblemId(),l.parseId(),0,1,l.origin(),null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new Lineage(l.userId(),l.sessionId(),l.canonicalProblemId(),l.parseId(),1,1,l.origin(),UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->new Lineage(l.userId(),l.sessionId(),l.canonicalProblemId(),l.parseId(),1,1,new Origin(Role.SECONDARY,UUID.randomUUID()),null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void explanationIsBoundedAndIndependentOfMathAndNoVerdictExists() {
        assertThat(step("a",1,List.of()).claim()).isEqualTo(base.finalStatement());
        assertThatThrownBy(()->new Step(UUID.randomUUID(),"a",1,Category.CONCLUDE,base.finalStatement(),"x".repeat(1001),List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(Arrays.stream(CanonicalSolution.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName))
            .doesNotContain("verified","verificationStatus","confidence");
    }
}
