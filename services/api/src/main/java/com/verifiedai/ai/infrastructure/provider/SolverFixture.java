package com.verifiedai.ai.infrastructure.provider;

import com.verifiedai.ai.application.AiSolveResult;

/** Test-source extension only. No production implementation or mathematical performance claim. */
public interface SolverFixture {
    AiSolveResult execute(AiProviderRequest request);
}
