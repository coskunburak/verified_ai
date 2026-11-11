package com.verifiedai.mastery.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VerifiedTwinChallengeResponse(
        String schemaVersion,
        UUID twinId,
        UUID sourceAttemptId,
        String targetSkillCode,
        String rootCauseSkillCode,
        String problemStatementLatex,
        String status,
        Instant createdAt,
        Instant lockUntil,
        List<SolutionStepDto> solutionSteps,
        TwinEvaluationDto evaluation,
        boolean trainingEligible
) {
    public record SolutionStepDto(
            int stepIndex,
            String explanation,
            String latexExpression
    ) {}

    public record TwinEvaluationDto(
            String evidenceClassification,
            BigDecimal remediationCredit,
            BigDecimal updatedMasteryScore,
            String updatedMasteryState,
            boolean remediationCleared
    ) {}
}
