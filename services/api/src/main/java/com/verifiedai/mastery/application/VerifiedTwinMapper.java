package com.verifiedai.mastery.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.mastery.api.dto.VerifiedTwinChallengeResponse;
import com.verifiedai.mastery.domain.CounterfactualTwinDrillEntity;
import com.verifiedai.mastery.domain.CounterfactualTwinSubmissionEntity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;

public final class VerifiedTwinMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private VerifiedTwinMapper() {
    }

    public static VerifiedTwinChallengeResponse toIssuedResponse(CounterfactualTwinDrillEntity entity) {
        return new VerifiedTwinChallengeResponse(
                "verified-twin-v1",
                entity.getTwinId(),
                entity.getSourceAttemptId(),
                entity.getTargetSkillCode(),
                entity.getRootCauseSkillCode(),
                entity.getProblemStatementLatex(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getLockUntil(),
                null, // solutionSteps must be null while locked/issued
                null, // evaluation must be null while issued
                false
        );
    }

    public static VerifiedTwinChallengeResponse toEvaluatedResponse(
            CounterfactualTwinDrillEntity entity,
            CounterfactualTwinSubmissionEntity submission,
            BigDecimal updatedMasteryScore,
            String updatedMasteryState,
            boolean remediationCleared) {

        List<VerifiedTwinChallengeResponse.SolutionStepDto> steps = null;
        if ("SOLVED_UNASSISTED".equals(entity.getStatus()) || "SOLUTION_REVEALED_ASSISTED".equals(entity.getStatus())) {
            steps = parseSolutionSteps(entity.getSolutionStepsJson());
        }

        VerifiedTwinChallengeResponse.TwinEvaluationDto evaluation = new VerifiedTwinChallengeResponse.TwinEvaluationDto(
                submission.getEvidenceClassification(),
                submission.getRemediationCredit().setScale(4, RoundingMode.HALF_UP),
                updatedMasteryScore.setScale(4, RoundingMode.HALF_UP),
                updatedMasteryState,
                remediationCleared
        );

        return new VerifiedTwinChallengeResponse(
                "verified-twin-v1",
                entity.getTwinId(),
                entity.getSourceAttemptId(),
                entity.getTargetSkillCode(),
                entity.getRootCauseSkillCode(),
                entity.getProblemStatementLatex(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getLockUntil(),
                steps,
                evaluation,
                false
        );
    }

    private static List<VerifiedTwinChallengeResponse.SolutionStepDto> parseSolutionSteps(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<List<VerifiedTwinChallengeResponse.SolutionStepDto>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
