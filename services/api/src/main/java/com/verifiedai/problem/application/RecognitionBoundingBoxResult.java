package com.verifiedai.problem.application;

import java.math.BigDecimal;

public record RecognitionBoundingBoxResult(
    BigDecimal x,
    BigDecimal y,
    BigDecimal width,
    BigDecimal height
) {
}
