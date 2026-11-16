package com.verifiedai.exam.domain;

import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.ExamSectionSpec;
import com.verifiedai.exam.domain.ExamModels.ScoringEvaluationResult;
import com.verifiedai.exam.domain.ExamModels.ScoringFormulaType;
import com.verifiedai.exam.domain.ExamModels.ScoringParameters;
import com.verifiedai.exam.domain.ExamModels.SectionResponseCount;
import com.verifiedai.exam.domain.ExamModels.SectionWeightEntry;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Deterministic $0.00 LLM-cost exam scoring evaluator supporting:
 * <ul>
 *   <li>{@link ScoringFormulaType#CORRECT_MINUS_QUARTER_WRONG} (YKS TYT/AYT net score: C - p*W)</li>
 *   <li>{@link ScoringFormulaType#RAW_CORRECT_ONLY} (Digital SAT scaled linear mapping)</li>
 *   <li>{@link ScoringFormulaType#WEIGHTED_SECTION_RUBRIC} (AP/IB weighted section rubric)</li>
 * </ul>
 */
public final class ScoringFormulaPolicy {

    private ScoringFormulaPolicy() {
    }

    public static ScoringEvaluationResult evaluateAggregate(
            ExamDefinition definition,
            int correctCount,
            int wrongCount,
            int blankCount
    ) {
        Objects.requireNonNull(definition, "definition cannot be null");
        validateCounts(correctCount, wrongCount, blankCount, definition.totalQuestions());

        ScoringFormulaType formula = definition.scoringFormulaType();
        return switch (formula) {
            case CORRECT_MINUS_QUARTER_WRONG -> {
                BigDecimal penalty = definition.wrongPenaltyFactor() != null
                        ? definition.wrongPenaltyFactor()
                        : new BigDecimal("0.2500");
                BigDecimal rawNet = BigDecimal.valueOf(correctCount)
                        .subtract(penalty.multiply(BigDecimal.valueOf(wrongCount)))
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal bounded = clamp(rawNet, definition.minScore(), definition.maxScore());
                yield new ScoringEvaluationResult(
                        rawNet,
                        bounded,
                        definition.minScore(),
                        definition.maxScore(),
                        correctCount,
                        wrongCount,
                        blankCount,
                        formula,
                        0
                );
            }
            case RAW_CORRECT_ONLY -> {
                BigDecimal rawCorrect = BigDecimal.valueOf(correctCount).setScale(2, RoundingMode.HALF_UP);
                BigDecimal scaled = scaleLinear(
                        BigDecimal.valueOf(correctCount),
                        BigDecimal.valueOf(definition.totalQuestions()),
                        definition.scoringParameters(),
                        definition.minScore(),
                        definition.maxScore()
                );
                yield new ScoringEvaluationResult(
                        rawCorrect,
                        scaled,
                        definition.minScore(),
                        definition.maxScore(),
                        correctCount,
                        wrongCount,
                        blankCount,
                        formula,
                        0
                );
            }
            case WEIGHTED_SECTION_RUBRIC -> {
                BigDecimal ratio = BigDecimal.valueOf(correctCount)
                        .divide(BigDecimal.valueOf(definition.totalQuestions()), 6, RoundingMode.HALF_UP);
                BigDecimal scaled = scaleRubricFromRatio(
                        ratio,
                        definition.scoringParameters(),
                        definition.minScore(),
                        definition.maxScore()
                );
                yield new ScoringEvaluationResult(
                        BigDecimal.valueOf(correctCount).setScale(2, RoundingMode.HALF_UP),
                        scaled,
                        definition.minScore(),
                        definition.maxScore(),
                        correctCount,
                        wrongCount,
                        blankCount,
                        formula,
                        0
                );
            }
        };
    }

    public static ScoringEvaluationResult evaluateBySection(
            ExamDefinition definition,
            List<SectionResponseCount> sectionCounts
    ) {
        Objects.requireNonNull(definition, "definition cannot be null");
        Objects.requireNonNull(sectionCounts, "sectionCounts cannot be null");

        int totalCorrect = sectionCounts.stream().mapToInt(SectionResponseCount::correctCount).sum();
        int totalWrong = sectionCounts.stream().mapToInt(SectionResponseCount::wrongCount).sum();
        int totalBlank = sectionCounts.stream().mapToInt(SectionResponseCount::blankCount).sum();
        validateCounts(totalCorrect, totalWrong, totalBlank, definition.totalQuestions());

        if (definition.scoringFormulaType() != ScoringFormulaType.WEIGHTED_SECTION_RUBRIC) {
            return evaluateAggregate(definition, totalCorrect, totalWrong, totalBlank);
        }

        Map<String, ExamSectionSpec> sectionsByCode = definition.sections().stream()
                .collect(Collectors.toMap(ExamSectionSpec::sectionCode, s -> s, (a, b) -> a));
        Map<String, SectionResponseCount> countsByCode = sectionCounts.stream()
                .collect(Collectors.toMap(SectionResponseCount::sectionCode, s -> s, (a, b) -> a));

        List<SectionWeightEntry> configuredWeights = definition.scoringParameters() != null
                ? definition.scoringParameters().sectionWeights()
                : List.of();

        BigDecimal weightedRatioSum = BigDecimal.ZERO;
        BigDecimal totalConfiguredWeight = BigDecimal.ZERO;

        if (!configuredWeights.isEmpty()) {
            for (SectionWeightEntry weightEntry : configuredWeights) {
                ExamSectionSpec sectionSpec = sectionsByCode.get(weightEntry.sectionCode());
                SectionResponseCount count = countsByCode.get(weightEntry.sectionCode());
                if (sectionSpec == null || count == null) {
                    throw new ApiProblemException(
                            HttpStatus.BAD_REQUEST,
                            ApiErrorCode.REQUEST_VALIDATION_FAILED,
                            "Section counts must include all weighted exam sections: " + weightEntry.sectionCode(),
                            true,
                            "PROVIDE_ALL_SECTIONS"
                    );
                }
                int secTotal = count.correctCount() + count.wrongCount() + count.blankCount();
                if (secTotal != sectionSpec.questionCount()) {
                    throw new ApiProblemException(
                            HttpStatus.BAD_REQUEST,
                            ApiErrorCode.REQUEST_VALIDATION_FAILED,
                            "Section " + sectionSpec.sectionCode() + " total responses must equal " + sectionSpec.questionCount(),
                            true,
                            "FIX_SECTION_COUNTS"
                    );
                }
                BigDecimal sectionAccuracy = BigDecimal.valueOf(count.correctCount())
                        .divide(BigDecimal.valueOf(sectionSpec.questionCount()), 6, RoundingMode.HALF_UP);
                BigDecimal sectionWeight = BigDecimal.valueOf(weightEntry.weight());
                weightedRatioSum = weightedRatioSum.add(sectionAccuracy.multiply(sectionWeight));
                totalConfiguredWeight = totalConfiguredWeight.add(sectionWeight);
            }
        } else {
            weightedRatioSum = BigDecimal.valueOf(totalCorrect)
                    .divide(BigDecimal.valueOf(definition.totalQuestions()), 6, RoundingMode.HALF_UP);
            totalConfiguredWeight = BigDecimal.ONE;
        }

        BigDecimal normalizedRatio = totalConfiguredWeight.compareTo(BigDecimal.ZERO) > 0
                ? weightedRatioSum.divide(totalConfiguredWeight, 6, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal scaled = scaleRubricFromRatio(
                normalizedRatio,
                definition.scoringParameters(),
                definition.minScore(),
                definition.maxScore()
        );
        BigDecimal rawPoints = normalizedRatio
                .multiply(BigDecimal.valueOf(definition.totalQuestions()))
                .setScale(2, RoundingMode.HALF_UP);

        return new ScoringEvaluationResult(
                rawPoints,
                scaled,
                definition.minScore(),
                definition.maxScore(),
                totalCorrect,
                totalWrong,
                totalBlank,
                ScoringFormulaType.WEIGHTED_SECTION_RUBRIC,
                0
        );
    }

    private static BigDecimal scaleLinear(
            BigDecimal correct,
            BigDecimal totalQuestions,
            ScoringParameters params,
            BigDecimal minScore,
            BigDecimal maxScore
    ) {
        if (params != null && params.scaleMin() != null && params.scaleMax() != null) {
            BigDecimal low = BigDecimal.valueOf(params.scaleMin());
            BigDecimal high = BigDecimal.valueOf(params.scaleMax());
            BigDecimal ratio = correct.divide(totalQuestions, 6, RoundingMode.HALF_UP);
            BigDecimal scaled = low.add(ratio.multiply(high.subtract(low))).setScale(2, RoundingMode.HALF_UP);
            return clamp(scaled, minScore, maxScore);
        }
        return clamp(correct.setScale(2, RoundingMode.HALF_UP), minScore, maxScore);
    }

    private static BigDecimal scaleRubricFromRatio(
            BigDecimal normalizedRatio,
            ScoringParameters params,
            BigDecimal minScore,
            BigDecimal maxScore
    ) {
        BigDecimal low = (params != null && params.rubricScaleMin() != null)
                ? BigDecimal.valueOf(params.rubricScaleMin())
                : minScore;
        BigDecimal high = (params != null && params.rubricScaleMax() != null)
                ? BigDecimal.valueOf(params.rubricScaleMax())
                : maxScore;
        BigDecimal scaled = low.add(normalizedRatio.multiply(high.subtract(low))).setScale(2, RoundingMode.HALF_UP);
        return clamp(scaled, minScore, maxScore);
    }

    public static ScoringEvaluationResult evaluateAggregateForQuestionCount(
            ExamDefinition definition,
            int evaluatedQuestionCount,
            int correctCount,
            int wrongCount,
            int blankCount
    ) {
        Objects.requireNonNull(definition, "definition cannot be null");
        if (evaluatedQuestionCount < 1 || evaluatedQuestionCount > 200) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.REQUEST_VALIDATION_FAILED,
                    "evaluatedQuestionCount must be between 1 and 200",
                    true,
                    "FIX_RESPONSE_COUNTS"
            );
        }
        validateCounts(correctCount, wrongCount, blankCount, evaluatedQuestionCount);
        if (evaluatedQuestionCount == definition.totalQuestions()) {
            return evaluateAggregate(definition, correctCount, wrongCount, blankCount);
        }

        BigDecimal effectivePenalty = effectiveWrongPenaltyRatio(definition);
        BigDecimal adjustedMinScore = definition.scoringFormulaType() == ScoringFormulaType.CORRECT_MINUS_QUARTER_WRONG
                ? minPossibleNetScore(definition.scoringFormulaType(), effectivePenalty, evaluatedQuestionCount)
                : definition.minScore();
        BigDecimal adjustedMaxScore = definition.scoringFormulaType() == ScoringFormulaType.CORRECT_MINUS_QUARTER_WRONG
                ? maxPossibleNetScore(definition.scoringFormulaType(), evaluatedQuestionCount)
                : definition.maxScore();

        ExamDefinition adjusted = new ExamDefinition(
                definition.id(),
                definition.examCode(),
                definition.examVersion(),
                definition.jurisdiction(),
                definition.examFamily(),
                definition.titleEn(),
                definition.titleTr(),
                definition.status(),
                evaluatedQuestionCount,
                definition.durationMinutes(),
                definition.scoringFormulaType(),
                definition.wrongPenaltyFactor(),
                adjustedMinScore,
                adjustedMaxScore,
                definition.navigationPolicy(),
                definition.blueprintHash(),
                definition.effectiveFrom(),
                definition.deprecatedAt(),
                definition.supersededByVersion(),
                definition.sections(),
                definition.scoringParameters(),
                definition.topicWeights(),
                definition.trainingEligible(),
                definition.createdAt(),
                definition.updatedAt()
        );
        return evaluateAggregate(adjusted, correctCount, wrongCount, blankCount);
    }

    public static BigDecimal effectiveWrongPenaltyRatio(ExamDefinition definition) {
        if (definition == null) {
            return new BigDecimal("0.2500");
        }
        return switch (definition.scoringFormulaType()) {
            case CORRECT_MINUS_QUARTER_WRONG -> {
                BigDecimal configured = definition.wrongPenaltyFactor();
                yield configured != null
                        ? configured.setScale(4, RoundingMode.HALF_UP)
                        : new BigDecimal("0.2500");
            }
            case RAW_CORRECT_ONLY -> BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            case WEIGHTED_SECTION_RUBRIC -> {
                BigDecimal configured = definition.wrongPenaltyFactor();
                yield configured != null
                        ? configured.setScale(4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            }
        };
    }

    public static BigDecimal minPossibleNetScore(
            ScoringFormulaType formulaType,
            BigDecimal wrongPenaltyRatio,
            int totalQuestions
    ) {
        if (totalQuestions < 1) {
            throw new IllegalArgumentException("totalQuestions must be >= 1");
        }
        if (formulaType == ScoringFormulaType.RAW_CORRECT_ONLY) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal penalty = wrongPenaltyRatio == null ? BigDecimal.ZERO : wrongPenaltyRatio;
        return penalty.multiply(BigDecimal.valueOf(totalQuestions))
                .negate()
                .setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal maxPossibleNetScore(ScoringFormulaType formulaType, int totalQuestions) {
        if (totalQuestions < 1) {
            throw new IllegalArgumentException("totalQuestions must be >= 1");
        }
        return BigDecimal.valueOf(totalQuestions).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal computeNormalizedPercentageScore(
            BigDecimal netScore,
            BigDecimal maxPossibleNetScore
    ) {
        if (netScore == null || maxPossibleNetScore == null || maxPossibleNetScore.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal ratio = netScore
                .multiply(new BigDecimal("100"))
                .divide(maxPossibleNetScore, 4, RoundingMode.HALF_UP);
        return clamp(ratio, BigDecimal.ZERO, new BigDecimal("100.00"));
    }

    public static BigDecimal computeAccuracyPercentage(int correctCount, int wrongCount) {
        int attempted = correctCount + wrongCount;
        if (attempted <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal ratio = BigDecimal.valueOf(correctCount)
                .multiply(new BigDecimal("100"))
                .divide(BigDecimal.valueOf(attempted), 4, RoundingMode.HALF_UP);
        return clamp(ratio, BigDecimal.ZERO, new BigDecimal("100.00"));
    }

    private static BigDecimal clamp(BigDecimal value, BigDecimal minScore, BigDecimal maxScore) {
        BigDecimal result = value;
        if (minScore != null && result.compareTo(minScore) < 0) {
            result = minScore;
        }
        if (maxScore != null && result.compareTo(maxScore) > 0) {
            result = maxScore;
        }
        return result.setScale(2, RoundingMode.HALF_UP);
    }

    private static void validateCounts(int correct, int wrong, int blank, int totalQuestions) {
        if (correct < 0 || wrong < 0 || blank < 0 || (correct + wrong + blank) != totalQuestions) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.REQUEST_VALIDATION_FAILED,
                    "Response counts must be non-negative and sum to totalQuestions (" + totalQuestions + ")",
                    true,
                    "FIX_RESPONSE_COUNTS"
            );
        }
    }
}
