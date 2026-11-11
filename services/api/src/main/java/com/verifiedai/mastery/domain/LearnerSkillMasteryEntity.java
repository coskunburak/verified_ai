package com.verifiedai.mastery.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "learner_skill_mastery")
@IdClass(LearnerSkillMasteryId.class)
public class LearnerSkillMasteryEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "skill_id", nullable = false, length = 96)
    private String skillCode;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "curriculum_code", length = 64)
    private String curriculumCode;

    @Column(name = "mastery_version", length = 32)
    private String masteryVersion = "bkt-lite-v2";

    @Column(name = "score", nullable = false)
    private double score = 0.2500;

    @Column(name = "half_life_days", nullable = false)
    private double halfLifeDays = 30.0;

    @Column(name = "evidence_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "confidence", nullable = false)
    private double confidence = 0.50;

    @Column(name = "mastery_state", length = 32)
    private String masteryState = "UNSEEN";

    @Column(name = "consecutive_failures", nullable = false)
    private int consecutiveFailures = 0;

    @Column(name = "direct_practice_count", nullable = false)
    private int directPracticeCount = 0;

    @Column(name = "last_assessed_at", nullable = false)
    private Instant lastAssessedAt = Instant.now();

    @Column(name = "training_eligible", nullable = false)
    private boolean trainingEligible = false;

    protected LearnerSkillMasteryEntity() {
    }

    public static LearnerSkillMasteryEntity createInitial(
            UUID tenantId, UUID userId, String curriculumCode, String skillCode, Instant now) {
        LearnerSkillMasteryEntity entity = new LearnerSkillMasteryEntity();
        entity.tenantId = Objects.requireNonNull(tenantId);
        entity.userId = Objects.requireNonNull(userId);
        entity.curriculumCode = Objects.requireNonNull(curriculumCode);
        entity.skillCode = Objects.requireNonNull(skillCode);
        entity.score = 0.2500;
        entity.halfLifeDays = 30.0;
        entity.attemptCount = 0;
        entity.confidence = 0.50;
        entity.masteryState = "UNSEEN";
        entity.consecutiveFailures = 0;
        entity.directPracticeCount = 0;
        entity.lastAssessedAt = Objects.requireNonNull(now);
        entity.trainingEligible = false;
        return entity;
    }

    public static LearnerSkillMasteryEntity rehydrateForTest(
            UUID tenantId,
            UUID userId,
            String curriculumCode,
            String skillCode,
            BigDecimal score,
            String masteryState,
            int attemptCount,
            int consecutiveFailures,
            int directPracticeCount) {
        LearnerSkillMasteryEntity entity = new LearnerSkillMasteryEntity();
        entity.tenantId = tenantId;
        entity.userId = userId;
        entity.curriculumCode = curriculumCode;
        entity.skillCode = skillCode;
        entity.score = score.doubleValue();
        entity.masteryState = masteryState;
        entity.attemptCount = attemptCount;
        entity.consecutiveFailures = consecutiveFailures;
        entity.directPracticeCount = directPracticeCount;
        entity.lastAssessedAt = Instant.now();
        entity.trainingEligible = false;
        return entity;
    }

    public void applyVerifiedUnassistedRemediation(BigDecimal credit, BigDecimal exitFloor, Instant now) {
        BigDecimal newScore = getMasteryScore().add(credit).min(BigDecimal.ONE).setScale(4, RoundingMode.HALF_UP);
        this.score = newScore.doubleValue();
        this.attemptCount++;
        this.directPracticeCount++;
        this.consecutiveFailures = 0;
        this.lastAssessedAt = Objects.requireNonNull(now);

        if (newScore.compareTo(exitFloor) >= 0) {
            if (newScore.compareTo(new BigDecimal("0.8500")) >= 0) {
                this.masteryState = "MASTERED";
            } else if (newScore.compareTo(new BigDecimal("0.4000")) >= 0) {
                this.masteryState = "PRACTICING";
            } else {
                this.masteryState = "EMERGING";
            }
        }
    }

    public void recordFailure(Instant now) {
        this.consecutiveFailures++;
        this.attemptCount++;
        this.directPracticeCount++;
        this.lastAssessedAt = Objects.requireNonNull(now);
        if (this.consecutiveFailures >= 2) {
            this.masteryState = "AT_RISK_REMEDIATION_REQUIRED";
        }
    }

    public UUID getTenantId() { return tenantId; }
    public UUID getUserId() { return userId; }
    public String getCurriculumCode() { return curriculumCode; }
    public String getSkillCode() { return skillCode; }
    public BigDecimal getMasteryScore() {
        return BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP);
    }
    public String getMasteryState() { return masteryState; }
    public int getAttemptCount() { return attemptCount; }
    public int getConsecutiveFailures() { return consecutiveFailures; }
    public int getDirectPracticeCount() { return directPracticeCount; }
    public Instant getLastAssessedAt() { return lastAssessedAt; }
    public boolean isTrainingEligible() { return trainingEligible; }
}
