package com.verifiedai.studyplan.practice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record ZpdWindowDto(
        @JsonProperty("lowerBound") BigDecimal lowerBound,
        @JsonProperty("upperBound") BigDecimal upperBound,
        @JsonProperty("targetDifficulty") BigDecimal targetDifficulty
) {
}
