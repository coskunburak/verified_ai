package com.verifiedai.mastery;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.verifiedai.mastery.LearningEvidenceType.*;

class EvidenceStratifiedMasteryPolicyTest {
    private final EvidenceStratifiedMasteryPolicy policy=new EvidenceStratifiedMasteryPolicy();
    private static final Instant AT=Instant.parse("2026-01-01T00:00:00Z");
    private MasteryEvidenceSource.Evidence evidence(int id,LearningEvidenceType type,boolean correct,boolean twin,Instant at) {
        var uuid=new UUID(0,id);
        return new MasteryEvidenceSource.Evidence(uuid,uuid,uuid,uuid,uuid,"MATH.ARITHMETIC.FRACTIONS",at,type,correct,twin,"synthetic-v1");
    }
    private EvidenceStratifiedMasteryPolicy.State next(LearningEvidenceType type,boolean correct,boolean twin) {
        return policy.observe(policy.initial(),evidence(1,type,correct,twin,AT));
    }
    @Test void correctIndependentUsesRatifiedBayesAndTransition() {
        var s=next(INDEPENDENT,true,false);
        assertThat(s.score()).isEqualTo(.72666667); assertThat(s.halfLife()).isEqualTo(3.6);
        assertThat(s.confidence()).isEqualTo(.18126925); assertThat(s.independentPasses()).isEqualTo(1);
    }
    @Test void incorrectIndependentUsesBayesianCausalUpdate() {
        assertThat(next(INDEPENDENT,false,false).score()).isEqualTo(.06660377);
        assertThat(next(INDEPENDENT,false,false).halfLife()).isEqualTo(1.65);
    }
    @Test void correctHintUsesAdjustedSlipGuessAndLearning() {
        assertThat(next(HINT_ASSISTED,true,false).score()).isEqualTo(.52171429);
        assertThat(next(HINT_ASSISTED,true,false).independent()).isZero();
        assertThat(next(HINT_ASSISTED,true,false).confidence()).isEqualTo(.07688365);
    }
    @Test void incorrectHintDoesNotReceiveCorrectLearningTransition() {
        assertThat(next(HINT_ASSISTED,false,false).score()).isEqualTo(.06660377);
    }
    @Test void revealedCorrectIsFailedRetrievalWithNoPositiveTransition() {
        assertThat(next(SOLUTION_REVEALED_ASSISTED,true,false).score()).isEqualTo(.05);
        assertThat(next(SOLUTION_REVEALED_ASSISTED,true,false).confidence()).isZero();
    }
    @Test void unsupportedIsIdentityIncludingAllCountsConfidenceAndTime() {
        var prior=next(INDEPENDENT,true,false);
        assertThat(policy.observe(prior,evidence(2,UNSUPPORTED,true,true,AT.plusSeconds(999)))).isSameAs(prior);
    }
    @Test void verifiedIndependentTwinHasStrongerTransitionButHintTwinDoesNot() {
        assertThat(next(INDEPENDENT,true,true).score()).isEqualTo(.74666667);
        assertThat(next(HINT_ASSISTED,true,true)).isEqualTo(next(HINT_ASSISTED,true,false));
    }
    @Test void competenceUsesEligibleTotalDenominatorAnd28DayHalfLife() {
        var independent=evidence(1,INDEPENDENT,true,false,AT);
        var hints=evidence(2,HINT_ASSISTED,true,false,AT);
        assertThat(policy.project(List.of(independent),AT).independentCompetence()).isEqualTo(.4375);
        assertThat(policy.project(List.of(independent,hints),AT).independentCompetence()).isEqualTo(.35);
        var later=policy.project(List.of(independent),AT.plus(Duration.ofDays(28)));
        assertThat(later.independentCompetence()).isEqualTo(.35714286);
        assertThat(policy.project(List.of(hints),AT).independentCompetence()).isEqualTo(.1875);
        assertThat(policy.project(List.of(evidence(4,UNSUPPORTED,true,false,AT)),AT).independentCompetence()).isEqualTo(.25);
        assertThat(policy.project(List.of(evidence(3,INDEPENDENT,false,false,AT)),AT).independentCompetence()).isEqualTo(.1875);
    }
    @Test void repeatedDeliveryStableOrderingAndFutureBoundaryAreDeterministic() {
        var first=evidence(1,INDEPENDENT,true,false,AT); var second=evidence(2,INDEPENDENT,false,false,AT);
        assertThat(policy.project(List.of(second,first,first),AT)).isEqualTo(policy.project(List.of(first,second),AT));
        assertThat(policy.project(List.of(first),AT.minusNanos(1)).steps()).isEmpty();
        assertThat(policy.project(List.of(first),AT).steps()).hasSize(1);
        assertThatThrownBy(()->policy.project(List.of(first,evidence(1,HINT_ASSISTED,true,false,AT)),AT)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void spacedRetrievalAndRetentionUseDocumentedBoundary() {
        var s=next(INDEPENDENT,true,false);
        var boundary=AT.plusSeconds(155520); // exactly half of 3.6 days
        assertThat(policy.observe(s,evidence(2,INDEPENDENT,true,false,boundary)).halfLife()).isEqualTo(7.29);
        assertThat(policy.observe(s,evidence(2,INDEPENDENT,true,false,boundary.minusNanos(1))).halfLife()).isEqualTo(4.32);
        assertThat(policy.retention(s,AT.plusSeconds(311040))).isCloseTo(.38833334,within(.00000001));
    }
    @Test void assistedEvidenceNeverInventsMasteredAndPreservesEarnedHighScore() {
        var s=policy.initial();
        for(int i=1;i<=30;i++) s=policy.observe(s,evidence(i,HINT_ASSISTED,true,false,AT));
        assertThat(s.score()).isEqualTo(.78); assertThat(s.status()).isEqualTo("PRACTICING");
        var independent=policy.observe(next(INDEPENDENT,true,false),evidence(2,INDEPENDENT,true,false,AT));
        assertThat(independent.status()).isEqualTo("MASTERED");
        assertThat(policy.observe(independent,evidence(3,HINT_ASSISTED,true,false,AT)).score()).isEqualTo(independent.score());
        assertThat(policy.observe(independent,evidence(3,HINT_ASSISTED,false,false,AT)).score()).isLessThan(independent.score());
        var fabricated=new EvidenceStratifiedMasteryPolicy.State(.95,3,0,10,10,0,.5,false,AT,AT);
        assertThat(fabricated.status()).isEqualTo("PRACTICING");
    }
    @Test void gPed01AllScoresAndEvidenceStrataRespectBoundsAndAntiCrutch() {
        for(int i=5;i<=99;i++) for(var type:LearningEvidenceType.values()) for(boolean correct:List.of(true,false)) {
            double score=i/100.0;
            var prior=new EvidenceStratifiedMasteryPolicy.State(score,3,5,0,0,3,.5,false,AT,AT);
            var next=policy.observe(prior,evidence(1,type,correct,false,AT));
            assertThat(next.score()).isBetween(.05,.99);
            if(type==UNSUPPORTED) assertThat(next).isSameAs(prior);
            if(type==SOLUTION_REVEALED_ASSISTED) assertThat(next.score()).isLessThanOrEqualTo(score);
            if(type==HINT_ASSISTED && correct) assertThat(next.score()).isLessThanOrEqualTo(Math.max(score,.78));
        }
        assertThat(EvidenceStratifiedMasteryPolicy.VERSION).isEqualTo("bkt-lite-v2");
    }
}
