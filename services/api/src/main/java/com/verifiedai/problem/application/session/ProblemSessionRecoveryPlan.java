package com.verifiedai.problem.application.session;

import com.verifiedai.problem.domain.model.session.ProblemSessionNextAction;
import com.verifiedai.problem.domain.model.session.ProblemSessionStage;

public record ProblemSessionRecoveryPlan(
    ProblemSessionStage stage,
    ProblemSessionNextAction nextAction,
    boolean retryable,
    boolean reviewRequired,
    String failureCode,
    ProblemSessionActiveJob activeJob
) {
}
