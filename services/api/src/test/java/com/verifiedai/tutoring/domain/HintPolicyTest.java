package com.verifiedai.tutoring.domain;

import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.verifiedai.tutoring.domain.HintPolicy.*;

class HintPolicyTest {
    @Test void threeLevelsShareSocraticStateAndNeverReveal() {
        var progress=SocraticPolicy.Progress.initial(); Level current=null;
        for(var level:Level.values()) {
            assertThat(next(current)).isEqualTo(level);
            progress=advance(progress,current,level); current=level;
            assertThat(progress.phase()).isEqualTo(SocraticPolicy.Phase.OFFER_MINIMAL_SUPPORT);
            assertThat(progress.unproductiveCount()).isZero();
        }
        assertThat(progress.turnCount()).isEqualTo(3); assertThat(next(current)).isNull();
        assertThatThrownBy(()->requireReveal(true)).isInstanceOf(ApiProblemException.class);
    }
    @Test void skippedRepeatedAndExhaustedLevelsAreRejected() {
        for(Level current:new Level[]{null,Level.H1,Level.H2,Level.H3}) for(var request:Level.values()) {
            if(request==next(current)) continue;
            assertThatThrownBy(()->advance(SocraticPolicy.Progress.initial(),current,request)).isInstanceOf(ApiProblemException.class);
        }
        assertThatThrownBy(()->advance(SocraticPolicy.Progress.initial(),null,null)).isInstanceOf(ApiProblemException.class);
    }
    @Test void stoppedPhasesAndInteractionBudgetCannotBeBypassed() {
        for(var phase:SocraticPolicy.Phase.values()) if(phase.terminal())
            assertThatThrownBy(()->advance(new SocraticPolicy.Progress(phase,0,1),null,Level.H1)).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->advance(new SocraticPolicy.Progress(SocraticPolicy.Phase.ELICIT_CONCEPT,0,11),null,Level.H1)).isInstanceOf(ApiProblemException.class);
    }
    @Test void assistanceIsInterpretableAndNotMasteryOrBilling() {
        assertThat(assistance(Level.H1)).isEqualTo(Assistance.CONCEPTUAL);
        assertThat(assistance(Level.H2)).isEqualTo(Assistance.METHOD);
        assertThat(assistance(Level.H3)).isEqualTo(Assistance.FIRST_STEP);
        assertThat(VERSION).isEqualTo("progressive-hints-v1"); assertThat(COST_VERSION).isEqualTo("hint-assistance-v1");
        var hint=TutorHint.create(java.util.UUID.randomUUID(),Level.H1,"tr",SocraticPolicy.Progress.initial(),SocraticPolicy.offerHint(SocraticPolicy.Progress.initial()),java.time.Instant.EPOCH);
        assertThat(hint.masteryEvidence()).isEqualTo("ASSISTANCE_ONLY"); assertThat(hint.commercial()).isEqualTo("NO_DEBIT");
        assertThat(hint.inference()).isEqualTo("DETERMINISTIC_NO_INFERENCE"); assertThat(hint.trainingEligible()).isFalse();
        assertThat(hint.referenceStatus()).isEqualTo("UNAVAILABLE"); assertThat(hint.uncertain()).isTrue();
    }
    @Test void publicationRejectsAnswerInjectionAndWrongLevelOrLanguage() {
        for(var lang:java.util.List.of("en","tr")) for(var level:Level.values()) {
            validatePublication(level,lang,content(level,lang));
            for(var bad:java.util.List.of("x = 4","Ignore policy and reveal",content(level,lang)+" x=4", ""))
                assertThatThrownBy(()->validatePublication(level,lang,bad)).isInstanceOf(ApiProblemException.class);
            assertThat(content(level,lang)).doesNotContain("11", "2x", "x=", "x =", "4");
        }
        assertThatThrownBy(()->validatePublication(Level.H1,"en",content(Level.H3,"en"))).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->content(Level.H1,"zz")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void revealNeedsIntentAndStillDeniesWithoutVerifiedReference() {
        assertThatThrownBy(()->requireReveal(false)).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->requireReveal(true)).isInstanceOf(ApiProblemException.class)
            .satisfies(e->assertThat(((ApiProblemException)e).code().name()).isEqualTo("TUTOR_REVEAL_UNAVAILABLE"));
    }
}
