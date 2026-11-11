package com.verifiedai.mastery.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "counterfactual_twin_submissions")
public class CounterfactualTwinSubmissionEntity {

    @Id
    @Column(name = "submission_id", nullable = false, updatable = false)
    private UUID submissionId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "twin_id", nullable = false, updatable = false)
    private UUID twinId;

    @Column(name = "action_type", nullable = false, updatable = false, length = 32)
    private String actionType;

    @Column(name = "submitted_answer_latex", updatable = false, columnDefinition = "TEXT")
    private String submittedAnswerLatex;

    @Column(name = "is_mathematically_equal", nullable = false, updatable = false)
    private boolean mathematicallyEqual;

    @Column(name = "evidence_classification", nullable = false, updatable = false, length = 32)
    private String evidenceClassification;

    @Column(name = "remediation_credit", nullable = false, updatable = false, precision = 5, scale = 4)
    private BigDecimal remediationCredit;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt;

    @Column(name = "training_eligible", nullable = false, updatable = false)
    private boolean trainingEligible = false;

    protected CounterfactualTwinSubmissionEntity() {
    }

    public static CounterfactualTwinSubmissionEntity assistedReveal(
            UUID submissionId, UUID tenantId, UUID userId, UUID twinId, Instant now) {
        CounterfactualTwinSubmissionEntity entity = new CounterfactualTwinSubmissionEntity();
        entity.submissionId = Objects.requireNonNull(submissionId);
        entity.tenantId = Objects.requireNonNull(tenantId);
        entity.userId = Objects.requireNonNull(userId);
        entity.twinId = Objects.requireNonNull(twinId);
        entity.actionType = "REVEAL_SOLUTION";
        entity.submittedAnswerLatex = null;
        entity.mathematicallyEqual = false;
        entity.evidenceClassification = "SOLUTION_REVEALED_ASSISTED";
        entity.remediationCredit = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        entity.submittedAt = Objects.requireNonNull(now);
        entity.trainingEligible = false;
        return entity;
    }

    public static CounterfactualTwinSubmissionEntity answerEvaluation(
            UUID submissionId,
            UUID tenantId,
            UUID userId,
            UUID twinId,
            String submittedAnswerLatex,
            boolean isCorrect,
            BigDecimal credit,
            Instant now) {
        CounterfactualTwinSubmissionEntity entity = new CounterfactualTwinSubmissionEntity();
        entity.submissionId = Objects.requireNonNull(submissionId);
        entity.tenantId = Objects.requireNonNull(tenantId);
        entity.userId = Objects.requireNonNull(userId);
        entity.twinId = Objects.requireNonNull(twinId);
        entity.actionType = "SUBMIT_ANSWER";
        entity.submittedAnswerLatex = Objects.requireNonNull(submittedAnswerLatex);
        entity.mathematicallyEqual = isCorrect;
        entity.evidenceClassification = isCorrect ? "VERIFIED_UNASSISTED_SUCCESS" : "UNASSISTED_INCORRECT";
        entity.remediationCredit = isCorrect
                ? credit.setScale(4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        entity.submittedAt = Objects.requireNonNull(now);
        entity.trainingEligible = false;
        return entity;
    }

    public UUID getSubmissionId() { return submissionId; }
    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }
    public UUID getTwinId() { return twinId; }
    public String getActionType() { return actionType; }
    public String getSubmittedAnswerLatex() { return submittedAnswerLatex; }
    public String getEvidenceClassification() { return evidenceClassification; }
    public BigDecimal getRemediationCredit() { return remediationCredit; }
    public boolean isMathematicallyEqual() { return mathematicallyEqual; }
    public Instant getSubmittedAt() { return submittedAt; }
    public boolean isTrainingEligible() { return trainingEligible; }
}
