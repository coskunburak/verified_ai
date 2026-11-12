package com.verifiedai.studyplan.engine;

import com.verifiedai.studyplan.domain.RecommendationRationaleCode;
import com.verifiedai.studyplan.domain.RecommendationType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public record ScoredRecommendation(
        String targetSkillCode,
        String targetMistakeCode,
        RecommendationType recommendationType,
        BigDecimal priorityScore,
        RecommendationRationaleCode rationaleCode,
        Map<String, Object> rationaleParams,
        String rationaleParamsJson
) {
    public ScoredRecommendation {
        Objects.requireNonNull(targetSkillCode, "targetSkillCode cannot be null");
        Objects.requireNonNull(recommendationType, "recommendationType cannot be null");
        Objects.requireNonNull(priorityScore, "priorityScore cannot be null");
        Objects.requireNonNull(rationaleCode, "rationaleCode cannot be null");
        Objects.requireNonNull(rationaleParams, "rationaleParams cannot be null");
        Objects.requireNonNull(rationaleParamsJson, "rationaleParamsJson cannot be null");
        rationaleParams = Collections.unmodifiableMap(rationaleParams);
    }
}
