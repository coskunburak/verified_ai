package com.verifiedai.problem.application;

import com.verifiedai.problem.domain.model.ProblemSessionNextAction;
import com.verifiedai.problem.domain.model.ProblemSessionStage;

public record ProblemSessionRecoveryPlan(
    ProblemSessionStage stage,
    ProblemSessionNextAction nextAction,
    boolean retryable,
    boolean reviewRequired,
    String failureCode,
    ProblemSessionActiveJob activeJob
) {
}
