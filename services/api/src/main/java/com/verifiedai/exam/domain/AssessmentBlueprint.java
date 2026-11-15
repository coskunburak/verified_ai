package com.verifiedai.exam.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

public record AssessmentBlueprint(
        UUID id,
        UUID examDefinitionId,
        int examVersionNumber,
        UUID examSectionId,
        String blueprintCode,
        String title,
        String description,
        AssessmentBlueprintMode blueprintMode,
        int totalQuestionCount,
        int baseDurationSeconds,
        int easyTargetBps,
        int mediumTargetBps,
        int hardTargetBps,
        int expertTargetBps,
        int difficultyToleranceBps,
        int recallTargetBps,
        int comprehensionTargetBps,
        int applicationTargetBps,
        int analysisTargetBps,
        int evaluationTargetBps,
        int allowRecentlySeenCooldownDays,
        boolean shuffleQuestions,
        boolean shuffleOptions,
        AssessmentBlueprintStatus status,
        String blueprintHash,
        boolean trainingEligible,
        List<BlueprintTopicRule> topicRules,
        Instant createdAt,
        Instant updatedAt
) {
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    public AssessmentBlueprint {
        Objects.requireNonNull(examDefinitionId, "examDefinitionId is required");
        Objects.requireNonNull(blueprintMode, "blueprintMode is required");
        Objects.requireNonNull(status, "status is required");

        if (examVersionNumber < 1) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_VERSION_MISMATCH,
                    "examVersionNumber must be >= 1",
                    true,
                    "Specify a positive examVersionNumber matching the active exam definition."
            );
        }
        if (blueprintCode == null || blueprintCode.trim().length() < 3) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                    "blueprintCode must be at least 3 characters",
                    true,
                    "Provide a valid blueprintCode."
            );
        }
        blueprintCode = blueprintCode.trim();
        if (title == null || title.trim().length() < 3) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                    "Blueprint title is required",
                    true,
                    "Provide a descriptive blueprint title."
            );
        }
        title = title.trim();
        if (totalQuestionCount < 5 || totalQuestionCount > 200) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                    "totalQuestionCount must be between 5 and 200",
                    true,
                    "Configure totalQuestionCount between 5 and 200."
            );
        }
        if (baseDurationSeconds < 300 || baseDurationSeconds > 21600) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                    "baseDurationSeconds must be between 300 and 21600",
                    true,
                    "Configure baseDurationSeconds between 300 (5 min) and 21600 (6 hours)."
            );
        }
        if (easyTargetBps < 0 || mediumTargetBps < 0 || hardTargetBps < 0 || expertTargetBps < 0
                || easyTargetBps + mediumTargetBps + hardTargetBps + expertTargetBps != 10000) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                    "Difficulty target basis points must sum to exactly 10000",
                    true,
                    "Adjust easy, medium, hard, and expert target basis points so their sum equals 10000."
            );
        }
        if (difficultyToleranceBps < 0 || difficultyToleranceBps > 2000) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                    "difficultyToleranceBps must be between 0 and 2000",
                    true,
                    "Specify difficultyToleranceBps between 0 and 2000."
            );
        }
        if (recallTargetBps < 0 || comprehensionTargetBps < 0 || applicationTargetBps < 0
                || analysisTargetBps < 0 || evaluationTargetBps < 0
                || recallTargetBps + comprehensionTargetBps + applicationTargetBps + analysisTargetBps + evaluationTargetBps != 10000) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                    "Cognitive taxonomy target basis points must sum to exactly 10000",
                    true,
                    "Adjust recall, comprehension, application, analysis, and evaluation target basis points to sum to 10000."
            );
        }
        if (allowRecentlySeenCooldownDays < 0 || allowRecentlySeenCooldownDays > 365) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                    "allowRecentlySeenCooldownDays must be between 0 and 365",
                    true,
                    "Specify allowRecentlySeenCooldownDays between 0 and 365."
            );
        }
        if (topicRules == null || topicRules.isEmpty()) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                    "Assessment blueprint requires at least one topic rule",
                    true,
                    "Provide one or more topic distribution rules summing to 10000 bps."
            );
        }

        topicRules = topicRules.stream()
                .sorted(Comparator.comparingInt(BlueprintTopicRule::ruleOrder))
                .toList();

        Set<String> seenTopics = new HashSet<>();
        Set<Integer> seenOrders = new HashSet<>();
        int weightSum = 0;
        int minSum = 0;
        int maxSum = 0;
        for (BlueprintTopicRule rule : topicRules) {
            if (!seenTopics.add(rule.topicCode())) {
                throw new ApiProblemException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                        "Duplicate topicCode in blueprint topic rules: " + rule.topicCode(),
                        true,
                        "Ensure each topicCode appears at most once in the blueprint."
                );
            }
            if (!seenOrders.add(rule.ruleOrder())) {
                throw new ApiProblemException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                        "Duplicate ruleOrder in blueprint topic rules: " + rule.ruleOrder(),
                        true,
                        "Ensure each topic rule has a distinct ruleOrder."
                );
            }
            weightSum += rule.targetWeightBps();
            minSum += rule.minQuestionCount();
            maxSum += rule.maxQuestionCount();
        }

        if (weightSum != 10000) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                    "Topic rule targetWeightBps must sum to 10000, found " + weightSum,
                    true,
                    "Adjust topic rule targetWeightBps values so their sum equals 10000."
            );
        }
        if (totalQuestionCount < minSum || totalQuestionCount > maxSum) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_BLUEPRINT_DISTRIBUTION_INVALID,
                    "totalQuestionCount (" + totalQuestionCount + ") is outside topic rule [minSum=" + minSum + ", maxSum=" + maxSum + "] bounds",
                    true,
                    "Ensure sum(minQuestionCount) <= totalQuestionCount <= sum(maxQuestionCount)."
            );
        }

        if (blueprintHash == null || !SHA256_PATTERN.matcher(blueprintHash).matches()) {
            blueprintHash = computeBlueprintHash(
                    blueprintCode,
                    examDefinitionId,
                    examVersionNumber,
                    blueprintMode,
                    totalQuestionCount,
                    baseDurationSeconds,
                    easyTargetBps,
                    mediumTargetBps,
                    hardTargetBps,
                    expertTargetBps,
                    difficultyToleranceBps,
                    recallTargetBps,
                    comprehensionTargetBps,
                    applicationTargetBps,
                    analysisTargetBps,
                    evaluationTargetBps,
                    topicRules
            );
        }
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    public AssessmentBlueprint activate(Instant now) {
        return new AssessmentBlueprint(
                id,
                examDefinitionId,
                examVersionNumber,
                examSectionId,
                blueprintCode,
                title,
                description,
                blueprintMode,
                totalQuestionCount,
                baseDurationSeconds,
                easyTargetBps,
                mediumTargetBps,
                hardTargetBps,
                expertTargetBps,
                difficultyToleranceBps,
                recallTargetBps,
                comprehensionTargetBps,
                applicationTargetBps,
                analysisTargetBps,
                evaluationTargetBps,
                allowRecentlySeenCooldownDays,
                shuffleQuestions,
                shuffleOptions,
                AssessmentBlueprintStatus.ACTIVE,
                blueprintHash,
                false,
                topicRules,
                createdAt,
                now == null ? Instant.now() : now
        );
    }

    public static String computeBlueprintHash(
            String blueprintCode,
            UUID examDefinitionId,
            int examVersionNumber,
            AssessmentBlueprintMode blueprintMode,
            int totalQuestionCount,
            int baseDurationSeconds,
            int easyTargetBps,
            int mediumTargetBps,
            int hardTargetBps,
            int expertTargetBps,
            int difficultyToleranceBps,
            int recallTargetBps,
            int comprehensionTargetBps,
            int applicationTargetBps,
            int analysisTargetBps,
            int evaluationTargetBps,
            List<BlueprintTopicRule> topicRules
    ) {
        String rulesCanonical = topicRules == null ? "" : topicRules.stream()
                .sorted(Comparator.comparingInt(BlueprintTopicRule::ruleOrder))
                .map(r -> r.ruleOrder() + ":" + r.topicCode() + ":" + r.targetWeightBps() + ":" + r.minQuestionCount() + ":" + r.maxQuestionCount())
                .collect(Collectors.joining(";"));
        String payload = String.join("|",
                blueprintCode == null ? "" : blueprintCode.trim(),
                String.valueOf(examDefinitionId),
                String.valueOf(examVersionNumber),
                String.valueOf(blueprintMode),
                String.valueOf(totalQuestionCount),
                String.valueOf(baseDurationSeconds),
                easyTargetBps + "," + mediumTargetBps + "," + hardTargetBps + "," + expertTargetBps + "," + difficultyToleranceBps,
                recallTargetBps + "," + comprehensionTargetBps + "," + applicationTargetBps + "," + analysisTargetBps + "," + evaluationTargetBps,
                rulesCanonical
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
