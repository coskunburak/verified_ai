package com.verifiedai.problem.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.problem.domain.model.ProblemSessionStatus;
import org.junit.jupiter.api.Test;

class ProblemSessionLifecyclePolicyTest {
    private final ProblemSessionLifecyclePolicy policy = new ProblemSessionLifecyclePolicy();

    @Test
    void permitsExplicitProblemSessionRecoveryTransitionsOnly() {
        assertThat(policy.canTransition(ProblemSessionStatus.CREATED, ProblemSessionStatus.ASSET_UPLOADED)).isTrue();
        assertThat(policy.canTransition(ProblemSessionStatus.ASSET_UPLOADED, ProblemSessionStatus.PARSING)).isTrue();
        assertThat(policy.canTransition(ProblemSessionStatus.PARSING, ProblemSessionStatus.REVIEW_REQUIRED)).isTrue();
        assertThat(policy.canTransition(ProblemSessionStatus.REVIEW_REQUIRED, ProblemSessionStatus.PARSED)).isTrue();
        assertThat(policy.canTransition(ProblemSessionStatus.FAILED, ProblemSessionStatus.PARSING)).isTrue();
    }

    @Test
    void rejectsRegressionsFromTerminalSolveReadyStatus() {
        assertThat(policy.canTransition(ProblemSessionStatus.PARSED, ProblemSessionStatus.PARSING)).isFalse();

        assertThatThrownBy(() -> policy.requireTransition(ProblemSessionStatus.PARSED, ProblemSessionStatus.ASSET_UPLOADED))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Illegal problem session transition PARSED -> ASSET_UPLOADED");
    }
}
