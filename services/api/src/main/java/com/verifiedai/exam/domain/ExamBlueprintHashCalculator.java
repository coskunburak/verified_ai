package com.verifiedai.exam.domain;

import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.ExamTopicWeight;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic SHA-256 blueprint hash generator for versioned exam definitions.
 */
public final class ExamBlueprintHashCalculator {

    private ExamBlueprintHashCalculator() {
    }

    public static String computeHash(ExamDefinition definition) {
        Objects.requireNonNull(definition, "definition cannot be null");
        String canonical = canonicalRepresentation(definition);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    static String canonicalRepresentation(ExamDefinition definition) {
        StringBuilder sb = new StringBuilder();
        sb.append("examCode=").append(definition.examCode()).append('\n');
        sb.append("examVersion=").append(definition.examVersion()).append('\n');
        sb.append("jurisdiction=").append(definition.jurisdiction().name()).append('\n');
        sb.append("examFamily=").append(definition.examFamily().name()).append('\n');
        sb.append("totalQuestions=").append(definition.totalQuestions()).append('\n');
        sb.append("durationMinutes=").append(definition.durationMinutes()).append('\n');
        sb.append("scoringFormulaType=").append(definition.scoringFormulaType().name()).append('\n');
        sb.append("wrongPenaltyFactor=").append(formatScale(definition.wrongPenaltyFactor(), 4)).append('\n');
        sb.append("minScore=").append(formatScale(definition.minScore(), 2)).append('\n');
        sb.append("maxScore=").append(formatScale(definition.maxScore(), 2)).append('\n');
        sb.append("navigationPolicy=").append(definition.navigationPolicy().name());

        List<ExamTopicWeight> sortedWeights = definition.topicWeights().stream()
                .sorted(Comparator.comparing(ExamTopicWeight::sectionCode)
                        .thenComparing(ExamTopicWeight::skillId))
                .toList();

        for (ExamTopicWeight weight : sortedWeights) {
            sb.append('\n')
                    .append("weight=")
                    .append(weight.sectionCode()).append('|')
                    .append(weight.skillId()).append('|')
                    .append(weight.domainCode()).append('|')
                    .append(formatScale(weight.questionCountWeight(), 4)).append('|')
                    .append(weight.minQuestions()).append('|')
                    .append(weight.maxQuestions());
        }
        return sb.toString();
    }

    private static String formatScale(BigDecimal value, int scale) {
        BigDecimal safe = value == null ? BigDecimal.ZERO : value;
        return safe.setScale(scale, RoundingMode.HALF_UP).toPlainString();
    }
}
