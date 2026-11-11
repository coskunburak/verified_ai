package com.verifiedai.mastery;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record VerifiedAttemptSnapshot(
        UUID attemptId,
        UUID tenantId,
        UUID userId,
        String curriculumCode,
        String targetSkillCode,
        String rootCauseSkillCode,
        Map<String, Object> canonicalProblemAst,
        boolean hasDiagnosedMistake
) {
    public VerifiedAttemptSnapshot {
        Objects.requireNonNull(attemptId, "attemptId cannot be null");
        Objects.requireNonNull(tenantId, "tenantId cannot be null");
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(curriculumCode, "curriculumCode cannot be null");
        Objects.requireNonNull(targetSkillCode, "targetSkillCode cannot be null");
        Objects.requireNonNull(rootCauseSkillCode, "rootCauseSkillCode cannot be null");
        Objects.requireNonNull(canonicalProblemAst, "canonicalProblemAst cannot be null");
    }
}
