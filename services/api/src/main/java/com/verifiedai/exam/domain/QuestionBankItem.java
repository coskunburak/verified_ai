package com.verifiedai.exam.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;

public record QuestionBankItem(
        UUID id,
        UUID examDefinitionId,
        int examVersionNumber,
        UUID examSectionId,
        String questionCode,
        String topicCode,
        String subtopicCode,
        QuestionItemFormat itemFormat,
        QuestionDifficultyBand difficultyBand,
        BigDecimal difficultyBValue,
        BigDecimal discriminationAValue,
        BigDecimal guessingCValue,
        CognitiveTaxonomyLevel cognitiveLevel,
        String stemMarkdown,
        String stemLatex,
        String altTextDescription,
        boolean hasVisualDiagram,
        BigDecimal numericCorrectAnswer,
        BigDecimal numericTolerance,
        Map<String, Object> structuredRubric,
        String solutionExplanationMarkdown,
        int estimatedTimeSeconds,
        BigDecimal maxPoints,
        BigDecimal negativeMarkingWeight,
        QuestionItemStatus status,
        String contentSha256,
        long exposureCount,
        boolean trainingEligible,
        List<QuestionOption> options,
        QuestionProvenanceRecord provenance,
        Instant createdAt,
        Instant updatedAt
) {
    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final BigDecimal MIN_B = new BigDecimal("-3.00");
    private static final BigDecimal MAX_B = new BigDecimal("3.00");
    private static final BigDecimal MIN_A = new BigDecimal("0.20");
    private static final BigDecimal MAX_A = new BigDecimal("2.50");
    private static final BigDecimal MIN_C = new BigDecimal("0.00");
    private static final BigDecimal MAX_C = new BigDecimal("0.35");

    public QuestionBankItem {
        Objects.requireNonNull(examDefinitionId, "examDefinitionId is required");
        Objects.requireNonNull(itemFormat, "itemFormat is required");
        Objects.requireNonNull(difficultyBand, "difficultyBand is required");
        Objects.requireNonNull(cognitiveLevel, "cognitiveLevel is required");
        Objects.requireNonNull(status, "status is required");

        if (examVersionNumber < 1) {
            throw new IllegalArgumentException("examVersionNumber must be >= 1");
        }
        if (questionCode == null || questionCode.trim().length() < 3) {
            throw new IllegalArgumentException("questionCode must be at least 3 characters");
        }
        questionCode = questionCode.trim();
        if (topicCode == null || topicCode.trim().isEmpty()) {
            throw new IllegalArgumentException("topicCode is required");
        }
        topicCode = topicCode.trim();

        difficultyBValue = difficultyBValue == null ? BigDecimal.ZERO : difficultyBValue;
        discriminationAValue = discriminationAValue == null ? new BigDecimal("1.00") : discriminationAValue;
        guessingCValue = guessingCValue == null ? new BigDecimal("0.20") : guessingCValue;

        if (difficultyBValue.compareTo(MIN_B) < 0 || difficultyBValue.compareTo(MAX_B) > 0) {
            throw new IllegalArgumentException("difficultyBValue must be between -3.00 and 3.00");
        }
        if (discriminationAValue.compareTo(MIN_A) < 0 || discriminationAValue.compareTo(MAX_A) > 0) {
            throw new IllegalArgumentException("discriminationAValue must be between 0.20 and 2.50");
        }
        if (guessingCValue.compareTo(MIN_C) < 0 || guessingCValue.compareTo(MAX_C) > 0) {
            throw new IllegalArgumentException("guessingCValue must be between 0.00 and 0.35");
        }
        if (stemMarkdown == null || stemMarkdown.trim().length() < 10) {
            throw new IllegalArgumentException("stemMarkdown must have at least 10 characters");
        }
        stemMarkdown = stemMarkdown.trim();
        if (solutionExplanationMarkdown == null || solutionExplanationMarkdown.trim().length() < 10) {
            throw new IllegalArgumentException("solutionExplanationMarkdown must have at least 10 characters");
        }
        solutionExplanationMarkdown = solutionExplanationMarkdown.trim();
        if (estimatedTimeSeconds < 15 || estimatedTimeSeconds > 1800) {
            throw new IllegalArgumentException("estimatedTimeSeconds must be between 15 and 1800");
        }
        maxPoints = maxPoints == null ? new BigDecimal("1.00") : maxPoints;
        if (maxPoints.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("maxPoints must be > 0");
        }
        negativeMarkingWeight = negativeMarkingWeight == null ? new BigDecimal("0.2500") : negativeMarkingWeight;
        if (negativeMarkingWeight.compareTo(BigDecimal.ZERO) < 0 || negativeMarkingWeight.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("negativeMarkingWeight must be between 0.0000 and 1.0000");
        }

        // Visual diagram accessibility invariant
        if (hasVisualDiagram && (altTextDescription == null || altTextDescription.trim().length() < 10)) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_QUESTION_INVALID_OPTIONS,
                    "Visual diagram items require non-empty accessible altTextDescription (>= 10 characters)",
                    true,
                    "Provide a descriptive altTextDescription of at least 10 characters when hasVisualDiagram is true."
            );
        }

        structuredRubric = structuredRubric == null ? Map.of() : Map.copyOf(structuredRubric);
        options = options == null
                ? List.of()
                : options.stream()
                        .sorted(Comparator.comparingInt(QuestionOption::displayOrder))
                        .toList();

        validateFormatAndOptions(itemFormat, options, numericCorrectAnswer, numericTolerance);

        if (status == QuestionItemStatus.PUBLISHED) {
            validateProvenanceForPublication(provenance);
        }

        if (contentSha256 == null || !SHA256_PATTERN.matcher(contentSha256).matches()) {
            contentSha256 = computeContentSha256(questionCode, topicCode, itemFormat, stemMarkdown, solutionExplanationMarkdown, options);
        }
        if (exposureCount < 0) {
            throw new IllegalArgumentException("exposureCount must be >= 0");
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

    public QuestionBankItem publish(Instant now) {
        validateProvenanceForPublication(this.provenance);
        return new QuestionBankItem(
                id,
                examDefinitionId,
                examVersionNumber,
                examSectionId,
                questionCode,
                topicCode,
                subtopicCode,
                itemFormat,
                difficultyBand,
                difficultyBValue,
                discriminationAValue,
                guessingCValue,
                cognitiveLevel,
                stemMarkdown,
                stemLatex,
                altTextDescription,
                hasVisualDiagram,
                numericCorrectAnswer,
                numericTolerance,
                structuredRubric,
                solutionExplanationMarkdown,
                estimatedTimeSeconds,
                maxPoints,
                negativeMarkingWeight,
                QuestionItemStatus.PUBLISHED,
                contentSha256,
                exposureCount,
                false,
                options,
                provenance,
                createdAt,
                now == null ? Instant.now() : now
        );
    }

    private static void validateFormatAndOptions(
            QuestionItemFormat itemFormat,
            List<QuestionOption> options,
            BigDecimal numericCorrectAnswer,
            BigDecimal numericTolerance
    ) {
        if (itemFormat == QuestionItemFormat.SINGLE_CHOICE_MCQ || itemFormat == QuestionItemFormat.MULTI_SELECT_MCQ) {
            if (options.size() < 4 || options.size() > 5) {
                throw new ApiProblemException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.EXAM_QUESTION_INVALID_OPTIONS,
                        "Multiple-choice questions require 4 or 5 options",
                        true,
                        "Provide 4 or 5 distinct options (A..D or A..E) for MCQ items."
                );
            }
            Set<String> seenKeys = new HashSet<>();
            Set<Integer> seenOrders = new HashSet<>();
            int correctCount = 0;
            for (QuestionOption opt : options) {
                if (!seenKeys.add(opt.optionKey())) {
                    throw new ApiProblemException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            ApiErrorCode.EXAM_QUESTION_INVALID_OPTIONS,
                            "Duplicate optionKey in question options: " + opt.optionKey(),
                            true,
                            "Ensure each option has a unique optionKey (A..E)."
                    );
                }
                if (!seenOrders.add(opt.displayOrder())) {
                    throw new ApiProblemException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            ApiErrorCode.EXAM_QUESTION_INVALID_OPTIONS,
                            "Duplicate displayOrder in question options: " + opt.displayOrder(),
                            true,
                            "Ensure each option has a unique displayOrder (1..5)."
                    );
                }
                if (opt.isCorrect()) {
                    correctCount++;
                }
            }
            if (itemFormat == QuestionItemFormat.SINGLE_CHOICE_MCQ && correctCount != 1) {
                throw new ApiProblemException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.EXAM_QUESTION_INVALID_OPTIONS,
                        "SINGLE_CHOICE_MCQ must have exactly 1 correct option, found " + correctCount,
                        true,
                        "Mark exactly one option as isCorrect=true for SINGLE_CHOICE_MCQ."
                );
            }
            if (itemFormat == QuestionItemFormat.MULTI_SELECT_MCQ && correctCount < 2) {
                throw new ApiProblemException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.EXAM_QUESTION_INVALID_OPTIONS,
                        "MULTI_SELECT_MCQ must have at least 2 correct options, found " + correctCount,
                        true,
                        "Mark at least two options as isCorrect=true for MULTI_SELECT_MCQ."
                );
            }
        } else if (itemFormat == QuestionItemFormat.NUMERIC_GRID_IN) {
            if (numericCorrectAnswer == null || numericTolerance == null || numericTolerance.compareTo(BigDecimal.ZERO) < 0) {
                throw new ApiProblemException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ApiErrorCode.EXAM_QUESTION_INVALID_OPTIONS,
                        "NUMERIC_GRID_IN requires non-null numericCorrectAnswer and numericTolerance >= 0",
                        true,
                        "Provide numericCorrectAnswer and a non-negative numericTolerance for NUMERIC_GRID_IN."
                );
            }
        }
    }

    public static void validateProvenanceForPublication(QuestionProvenanceRecord provenance) {
        if (provenance == null || !provenance.isSmeVerified()) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.EXAM_QUESTION_PROVENANCE_UNVERIFIED,
                    "Question item cannot be published without an SME-verified provenance record",
                    true,
                    "Ensure provenance verificationStatus is VERIFIED with distinct authorId and reviewerId before publishing."
            );
        }
    }

    public static String computeContentSha256(
            String questionCode,
            String topicCode,
            QuestionItemFormat itemFormat,
            String stemMarkdown,
            String solutionExplanationMarkdown,
            List<QuestionOption> options
    ) {
        String optionsCanonical = options == null ? "" : options.stream()
                .sorted(Comparator.comparingInt(QuestionOption::displayOrder))
                .map(o -> o.optionKey() + ":" + o.contentMarkdown() + ":" + o.isCorrect())
                .collect(Collectors.joining(";"));
        String payload = String.join("|",
                questionCode == null ? "" : questionCode.trim(),
                topicCode == null ? "" : topicCode.trim(),
                String.valueOf(itemFormat),
                stemMarkdown == null ? "" : stemMarkdown.trim(),
                solutionExplanationMarkdown == null ? "" : solutionExplanationMarkdown.trim(),
                optionsCanonical
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
