package com.verifiedai.exam.domain;

import com.verifiedai.curriculum.application.CurriculumTaxonomyCatalog;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.ExamStatus;
import com.verifiedai.exam.domain.ExamModels.ExamTopicWeight;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Pure domain validator enforcing:
 * <ol>
 *   <li>Weight conservation: {@code |sum(questionCountWeight) - totalQuestions| <= 1e-4}</li>
 *   <li>Question bounds: {@code 0 <= minQuestions <= questionCountWeight <= maxQuestions <= totalQuestions}</li>
 *   <li>Curriculum taxonomy validity via {@link CurriculumTaxonomyCatalog}</li>
 *   <li>Deterministic SHA-256 blueprint hash integrity and ACTIVE version checks</li>
 *   <li>Target score within exam {@code [minScore, maxScore]} range</li>
 * </ol>
 */
@Component
public class ExamBlueprintValidator {

    public static final BigDecimal WEIGHT_CONSERVATION_TOLERANCE = new BigDecimal("0.0001");

    private final CurriculumTaxonomyCatalog taxonomyCatalog;

    public ExamBlueprintValidator(CurriculumTaxonomyCatalog taxonomyCatalog) {
        this.taxonomyCatalog = Objects.requireNonNull(taxonomyCatalog, "taxonomyCatalog cannot be null");
    }

    public void validateBlueprint(ExamDefinition definition) {
        Objects.requireNonNull(definition, "definition cannot be null");

        if (definition.topicWeights().isEmpty()) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_WEIGHT_CONSERVATION_VIOLATION,
                    "Exam blueprint must define at least one topic weight entry",
                    false,
                    "REVIEW_EXAM_BLUEPRINT"
            );
        }

        BigDecimal totalQuestionsDecimal = BigDecimal.valueOf(definition.totalQuestions());
        BigDecimal weightSum = BigDecimal.ZERO;

        for (ExamTopicWeight weight : definition.topicWeights()) {
            validateCurriculumMapping(weight);

            BigDecimal qWeight = weight.questionCountWeight();
            BigDecimal minQ = BigDecimal.valueOf(weight.minQuestions());
            BigDecimal maxQ = BigDecimal.valueOf(weight.maxQuestions());

            if (qWeight == null
                    || weight.minQuestions() < 0
                    || qWeight.compareTo(minQ) < 0
                    || qWeight.compareTo(maxQ) > 0
                    || maxQ.compareTo(totalQuestionsDecimal) > 0) {
                throw new ApiProblemException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.EXAM_WEIGHT_CONSERVATION_VIOLATION,
                        "Topic weight bounds violated for skill " + weight.skillId(),
                        false,
                        "REVIEW_TOPIC_WEIGHTS"
                );
            }
            weightSum = weightSum.add(qWeight);
        }

        BigDecimal delta = weightSum.subtract(totalQuestionsDecimal).abs();
        if (delta.compareTo(WEIGHT_CONSERVATION_TOLERANCE) > 0) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_WEIGHT_CONSERVATION_VIOLATION,
                    "Topic weight sum (" + weightSum + ") does not conserve totalQuestions (" + definition.totalQuestions() + ")",
                    false,
                    "FIX_WEIGHT_CONSERVATION"
            );
        }

        String recomputedHash = ExamBlueprintHashCalculator.computeHash(definition);
        if (!recomputedHash.equalsIgnoreCase(definition.blueprintHash())) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_VERSION_CONFLICT,
                    "Exam blueprint hash integrity check failed for " + definition.examCode() + "@" + definition.examVersion(),
                    false,
                    "REFRESH_EXAM_CATALOG"
            );
        }
    }

    public void validateTargetBindingEligibility(
            ExamDefinition definition,
            String expectedBlueprintHash,
            BigDecimal targetScore
    ) {
        validateBlueprint(definition);

        if (definition.status() != ExamStatus.ACTIVE) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_VERSION_CONFLICT,
                    "Cannot bind target to non-ACTIVE exam version " + definition.examCode() + "@" + definition.examVersion(),
                    true,
                    "SELECT_ACTIVE_EXAM_VERSION"
            );
        }

        if (expectedBlueprintHash != null && !expectedBlueprintHash.isBlank()
                && !definition.blueprintHash().equalsIgnoreCase(expectedBlueprintHash.trim())) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_VERSION_CONFLICT,
                    "Exam blueprint hash mismatch against active version",
                    true,
                    "REFRESH_EXAM_BLUEPRINT"
            );
        }

        if (targetScore == null
                || targetScore.compareTo(definition.minScore()) < 0
                || targetScore.compareTo(definition.maxScore()) > 0) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_TARGET_SCORE_OUT_OF_RANGE,
                    "Target score must be between " + definition.minScore() + " and " + definition.maxScore(),
                    true,
                    "ADJUST_TARGET_SCORE"
            );
        }
    }

    private void validateCurriculumMapping(ExamTopicWeight weight) {
        String skillId = weight.skillId();
        String domainCode = weight.domainCode();
        if (skillId == null || !taxonomyCatalog.isActiveSkill(skillId)) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_CURRICULUM_SKILL_UNKNOWN,
                    "Exam topic weight references unknown or retired curriculum skill: " + skillId,
                    false,
                    "USE_ACTIVE_CURRICULUM_SKILL"
            );
        }
        String canonicalDomain = taxonomyCatalog.topicForSkill(skillId);
        if (!Objects.equals(canonicalDomain, domainCode)) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_CURRICULUM_SKILL_UNKNOWN,
                    "Exam topic weight domain_code mismatch for skill " + skillId + " (expected " + canonicalDomain + ")",
                    false,
                    "ALIGN_CURRICULUM_DOMAIN"
            );
        }
    }
}
