package com.verifiedai.problem.application.session;

import com.verifiedai.problem.domain.model.session.ProblemSessionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void selectedUserCorrectionCanRequireReviewWithoutAllowingBackgroundRegression() {
        assertThat(policy.canTransition(ProblemSessionStatus.PARSED, ProblemSessionStatus.REVIEW_REQUIRED)).isFalse();
        assertThatCode(() -> policy.requireUserCorrectionTransition(
            ProblemSessionStatus.PARSED, ProblemSessionStatus.REVIEW_REQUIRED)).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireUserCorrectionTransition(
            ProblemSessionStatus.REVIEW_REQUIRED, ProblemSessionStatus.PARSED)).doesNotThrowAnyException();
    }

    @Test
    void userCorrectionDoesNotEnableOtherTerminalStateRegressions() {
        assertThatThrownBy(() -> policy.requireUserCorrectionTransition(
            ProblemSessionStatus.PARSED, ProblemSessionStatus.PARSING)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy.requireUserCorrectionTransition(
            ProblemSessionStatus.COMPLETED, ProblemSessionStatus.REVIEW_REQUIRED)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> policy.requireUserCorrectionTransition(
            ProblemSessionStatus.CANCELLED, ProblemSessionStatus.REVIEW_REQUIRED)).isInstanceOf(IllegalStateException.class);
    }
}
