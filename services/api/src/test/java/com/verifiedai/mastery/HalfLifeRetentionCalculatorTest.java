package com.verifiedai.mastery;

import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.verifiedai.mastery.LearningEvidenceType.*;

class HalfLifeRetentionCalculatorTest {
    private final EvidenceStratifiedMasteryPolicy policy = new EvidenceStratifiedMasteryPolicy();
    private final Instant at = Instant.parse("2026-03-29T00:00:00Z");

    private EvidenceStratifiedMasteryPolicy.State state(double m, double h) {
        return new EvidenceStratifiedMasteryPolicy.State(m,h,3,0,0,3,.5,false,at,at);
    }
    private MasteryEvidenceSource.Evidence observation(LearningEvidenceType type, boolean correct, Instant when) {
        var id = new UUID(0,1);
        return new MasteryEvidenceSource.Evidence(id,id,id,id,id,"MATH.TEST",when,type,correct,false,"synthetic");
    }

    @Test void halfLifeMeasuresElapsedUtcTimeWithoutMutatingMastery() {
        var state = state(.85,2);
        assertThat(policy.retention(state, at.minusSeconds(1))).isEqualTo(.85);
        assertThat(policy.retention(state, at.plus(Duration.ofDays(2)))).isEqualTo(.45);
        assertThat(policy.retention(state, at.plus(Duration.ofDays(4)))).isEqualTo(.25);
        assertThat(policy.retention(state, Instant.MAX)).isEqualTo(.05);
        assertThat(state.score()).isEqualTo(.85);
        assertThat(policy.retention(policy.initial(), at)).isEqualTo(.25);
    }

    @Test void boundedHalfLifeAndAssistancePreserveTheV2RetrievalContract() {
        assertThat(policy.observe(state(.80,60),observation(INDEPENDENT,true,at.plus(Duration.ofDays(30)))).halfLife()).isEqualTo(90);
        assertThat(policy.observe(state(.80,1),observation(INDEPENDENT,false,at)).halfLife()).isEqualTo(1);
        assertThat(policy.observe(state(.80,4),observation(HINT_ASSISTED,true,at)).halfLife()).isEqualTo(4);
        assertThat(policy.observe(state(.80,4),observation(SOLUTION_REVEALED_ASSISTED,true,at)).halfLife()).isEqualTo(2.2);
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY, 91})
            assertThatIllegalArgumentException().isThrownBy(() -> policy.retention(state(.8,invalid), at));
        assertThatIllegalArgumentException().isThrownBy(() -> policy.retention(state(Double.NaN,4), at));
    }
}
