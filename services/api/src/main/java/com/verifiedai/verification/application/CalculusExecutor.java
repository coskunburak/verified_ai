package com.verifiedai.verification.application;

import com.verifiedai.verification.domain.model.CalculusExecution;

/** Narrow typed port. Readiness is authenticated and checked outside transactions. */
public interface CalculusExecutor {
    boolean calculusReady();
    CalculusExecution.Result execute(CalculusExecution.Request request);
}
