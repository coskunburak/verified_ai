package com.verifiedai.problem.application;

record CanonicalMathLimits(
    int maxExpressionLength,
    int maxAstNodes,
    int maxAstDepth,
    int maxExponentMagnitude,
    int maxNumericDigits,
    int maxFunctionNestingDepth
) {
    static final CanonicalMathLimits V1 = new CanonicalMathLimits(512, 120, 32, 12, 64, 8);
}
