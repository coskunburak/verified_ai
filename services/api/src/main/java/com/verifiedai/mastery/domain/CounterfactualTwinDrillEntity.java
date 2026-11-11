package com.verifiedai.mastery.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "counterfactual_twin_drills")
public class CounterfactualTwinDrillEntity {

    @Id
    @Column(name = "twin_id", nullable = false, updatable = false)
    private UUID twinId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "source_attempt_id", nullable = false, updatable = false)
    private UUID sourceAttemptId;

    @Column(name = "curriculum_code", nullable = false, updatable = false, length = 64)
    private String curriculumCode;

    @Column(name = "target_skill_code", nullable = false, updatable = false, length = 128)
    private String targetSkillCode;

    @Column(name = "root_cause_skill_code", nullable = false, updatable = false, length = 128)
    private String rootCauseSkillCode;

    @Column(name = "problem_statement_latex", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String problemStatementLatex;

    @Column(name = "expected_canonical_expr", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String expectedCanonicalExpr;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "solution_steps_json", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String solutionStepsJson;

    @Column(name = "generator_seed", nullable = false, updatable = false, length = 64)
    private String generatorSeed;

    @Column(name = "verifier_proof_hash", nullable = false, updatable = false, length = 64)
    private String verifierProofHash;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "lock_until", nullable = false, updatable = false)
    private Instant lockUntil;

    @Column(name = "solution_revealed_at")
    private Instant solutionRevealedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "training_eligible", nullable = false, updatable = false)
    private boolean trainingEligible = false;

    protected CounterfactualTwinDrillEntity() {
    }

    public CounterfactualTwinDrillEntity(
            UUID twinId,
            UUID tenantId,
            UUID userId,
            UUID sourceAttemptId,
            String curriculumCode,
            String targetSkillCode,
            String rootCauseSkillCode,
            String problemStatementLatex,
            String expectedCanonicalExpr,
            String solutionStepsJson,
            String generatorSeed,
            String verifierProofHash,
            String status,
            Instant createdAt,
            Instant lockUntil,
            boolean trainingEligible) {
        if (trainingEligible) {
            throw new IllegalArgumentException("TERMINAL_INVARIANT_VIOLATION: trainingEligible must be false");
        }
        if (!lockUntil.equals(createdAt.plusSeconds(60))) {
            throw new IllegalArgumentException("TERMINAL_INVARIANT_VIOLATION: lockUntil must equal createdAt + 60s");
        }
        this.twinId = Objects.requireNonNull(twinId);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.userId = Objects.requireNonNull(userId);
        this.sourceAttemptId = Objects.requireNonNull(sourceAttemptId);
        this.curriculumCode = Objects.requireNonNull(curriculumCode);
        this.targetSkillCode = Objects.requireNonNull(targetSkillCode);
        this.rootCauseSkillCode = Objects.requireNonNull(rootCauseSkillCode);
        this.problemStatementLatex = Objects.requireNonNull(problemStatementLatex);
        this.expectedCanonicalExpr = Objects.requireNonNull(expectedCanonicalExpr);
        this.solutionStepsJson = Objects.requireNonNull(solutionStepsJson);
        this.generatorSeed = Objects.requireNonNull(generatorSeed);
        this.verifierProofHash = Objects.requireNonNull(verifierProofHash);
        this.status = Objects.requireNonNull(status);
        this.createdAt = createdAt;
        this.lockUntil = lockUntil;
        this.trainingEligible = false;
    }

    public void markSolutionRevealedAssisted(Instant now) {
        assertNonTerminal();
        this.status = "SOLUTION_REVEALED_ASSISTED";
        this.solutionRevealedAt = Objects.requireNonNull(now);
        this.completedAt = now;
    }

    public void markSolvedUnassisted(Instant now) {
        assertNonTerminal();
        this.status = "SOLVED_UNASSISTED";
        this.completedAt = Objects.requireNonNull(now);
    }

    public void markFailedUnassisted() {
        assertNonTerminal();
        this.status = "FAILED_UNASSISTED";
    }

    private void assertNonTerminal() {
        if ("SOLVED_UNASSISTED".equals(this.status)
                || "SOLUTION_REVEALED_ASSISTED".equals(this.status)
                || "EXPIRED".equals(this.status)) {
            throw new IllegalStateException("Cannot transition out of terminal twin status: " + this.status);
        }
    }

    public UUID getTwinId() { return twinId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }
    public UUID getSourceAttemptId() { return sourceAttemptId; }
    public String getCurriculumCode() { return curriculumCode; }
    public String getTargetSkillCode() { return targetSkillCode; }
    public String getRootCauseSkillCode() { return rootCauseSkillCode; }
    public String getProblemStatementLatex() { return problemStatementLatex; }
    public String getExpectedCanonicalExpr() { return expectedCanonicalExpr; }
    public String getSolutionStepsJson() { return solutionStepsJson; }
    public String getGeneratorSeed() { return generatorSeed; }
    public String getVerifierProofHash() { return verifierProofHash; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLockUntil() { return lockUntil; }
    public Instant getSolutionRevealedAt() { return solutionRevealedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public boolean isTrainingEligible() { return trainingEligible; }
}
