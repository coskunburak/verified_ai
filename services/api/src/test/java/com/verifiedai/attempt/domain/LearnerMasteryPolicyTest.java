package com.verifiedai.attempt.domain;
import static org.assertj.core.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
class LearnerMasteryPolicyTest {
    @Test void independentEvidenceHasMoreWeightThanAssistedEvidence() {
        Instant now=Instant.parse("2026-01-01T00:00:00Z");
        var independent=LearnerMasteryPolicy.observe(null,true,false,now);
        var assisted=LearnerMasteryPolicy.observe(null,true,true,now);
        assertThat(independent.score()).isGreaterThan(assisted.score());
        assertThat(independent.halfLife()).isGreaterThan(assisted.halfLife());
        assertThat(independent.count()).isEqualTo(1);
    }
    @Test void wrongEvidenceDoesNotIncreaseMasteryAndBoundsArePreserved() {
        Instant now=Instant.parse("2026-01-01T00:00:00Z");
        var state=new LearnerMasteryPolicy.State(.7,8,4,.5,now);
        var wrong=LearnerMasteryPolicy.observe(state,false,false,now);
        assertThat(wrong.score()).isLessThan(state.score());
        assertThat(wrong.halfLife()).isEqualTo(4);
        for(int i=0;i<100;i++) wrong=LearnerMasteryPolicy.observe(wrong,false,false,now);
        assertThat(wrong.score()).isBetween(.05,.99);
        assertThat(wrong.halfLife()).isBetween(1.0,90.0);
    }
}
