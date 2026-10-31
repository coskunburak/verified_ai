package com.verifiedai.verification.application;

import com.verifiedai.verification.domain.model.NumericDiagnostic;

/** Optional internal diagnostic port, not a planner-approved proof executor. */
public interface NumericExecutor {
    NumericDiagnostic.Result diagnose(NumericDiagnostic.Request request);
}
