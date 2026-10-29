package com.verifiedai.verification.application;

import com.verifiedai.verification.domain.model.MathematicalExecution.*;

/** Transport-independent port; failures return non-proof method outcomes. */
public interface MathematicalExecutor {
    Result execute(Request request);
}
