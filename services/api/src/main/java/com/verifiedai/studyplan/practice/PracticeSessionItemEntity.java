package com.verifiedai.studyplan.practice;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "practice_session_items")
public class PracticeSessionItemEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private PracticeSessionEntity session;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "ordinal_index", nullable = false)
    private int ordinalIndex;

    @Enumerated(EnumType.STRING)
    @Column(name = "slot_role", nullable = false, length = 32)
    private PracticeSlotRole slotRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_source", nullable = false, length = 32)
    private PracticeQuestionSource questionSource;

    @Column(name = "source_question_id", nullable = false, length = 128)
    private String sourceQuestionId;

    @Column(name = "skill_id", nullable = false, length = 128)
    private String skillId;

    @Column(name = "trap_family", length = 64)
    private String trapFamily;

    @Column(name = "target_difficulty", nullable = false, precision = 5, scale = 4)
    private BigDecimal targetDifficulty;

    @Column(name = "item_difficulty", nullable = false, precision = 5, scale = 4)
    private BigDecimal itemDifficulty;

    @Column(name = "zpd_lower_bound", nullable = false, precision = 5, scale = 4)
    private BigDecimal zpdLowerBound;

    @Column(name = "zpd_upper_bound", nullable = false, precision = 5, scale = 4)
    private BigDecimal zpdUpperBound;

    @Column(name = "prompt_latex", nullable = false, columnDefinition = "TEXT")
    private String promptLatex;

    @Column(name = "prompt_instruction_tr", nullable = false, length = 512)
    private String promptInstructionTr;

    @Column(name = "prompt_instruction_en", nullable = false, length = 512)
    private String promptInstructionEn;

    @Column(name = "canonical_solution_expr", nullable = false, length = 512)
    private String canonicalSolutionExpr;

    @Column(name = "trap_distractor_expr", length = 512)
    private String trapDistractorExpr;

    @Column(name = "sympy_verification_hash", nullable = false, length = 64)
    private String sympyVerificationHash;

    @Column(name = "is_twin_verification", nullable = false)
    private boolean isTwinVerification = false;

    @Column(name = "independent_lock_seconds", nullable = false)
    private int independentLockSeconds = 60;

    @Column(name = "unlocked_at")
    private Instant unlockedAt;

    @Column(name = "hints_unlocked_count", nullable = false)
    private int hintsUnlockedCount = 0;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_status", nullable = false, length = 32)
    private PracticeItemStatus itemStatus = PracticeItemStatus.PENDING;

    @Column(name = "submitted_answer_latex", length = 512)
    private String submittedAnswerLatex;

    @Column(name = "detected_trap_triggered", nullable = false)
    private boolean detectedTrapTriggered = false;

    @Column(name = "verification_duration_ms")
    private Long verificationDurationMs;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PracticeSessionItemEntity() {
    }

    public PracticeSessionItemEntity(
            UUID id,
            PracticeSessionEntity session,
            UUID userId,
            int ordinalIndex,
            PracticeSlotRole slotRole,
            PracticeQuestionSource questionSource,
            String sourceQuestionId,
            String skillId,
            String trapFamily,
            BigDecimal targetDifficulty,
            BigDecimal itemDifficulty,
            BigDecimal zpdLowerBound,
            BigDecimal zpdUpperBound,
            String promptLatex,
            String promptInstructionTr,
            String promptInstructionEn,
            String canonicalSolutionExpr,
            String trapDistractorExpr,
            String sympyVerificationHash,
            boolean isTwinVerification,
            int independentLockSeconds,
            Instant unlockedAt,
            Instant createdAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.session = session;
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.ordinalIndex = ordinalIndex;
        this.slotRole = Objects.requireNonNull(slotRole, "slotRole cannot be null");
        this.questionSource = Objects.requireNonNull(questionSource, "questionSource cannot be null");
        this.sourceQuestionId = Objects.requireNonNull(sourceQuestionId, "sourceQuestionId cannot be null");
        this.skillId = Objects.requireNonNull(skillId, "skillId cannot be null");
        this.trapFamily = trapFamily;
        this.targetDifficulty = Objects.requireNonNull(targetDifficulty, "targetDifficulty cannot be null");
        this.itemDifficulty = Objects.requireNonNull(itemDifficulty, "itemDifficulty cannot be null");
        this.zpdLowerBound = Objects.requireNonNull(zpdLowerBound, "zpdLowerBound cannot be null");
        this.zpdUpperBound = Objects.requireNonNull(zpdUpperBound, "zpdUpperBound cannot be null");
        this.promptLatex = Objects.requireNonNull(promptLatex, "promptLatex cannot be null");
        this.promptInstructionTr = Objects.requireNonNull(promptInstructionTr, "promptInstructionTr cannot be null");
        this.promptInstructionEn = Objects.requireNonNull(promptInstructionEn, "promptInstructionEn cannot be null");
        this.canonicalSolutionExpr = Objects.requireNonNull(canonicalSolutionExpr, "canonicalSolutionExpr cannot be null");
        this.trapDistractorExpr = trapDistractorExpr;
        this.sympyVerificationHash = Objects.requireNonNull(sympyVerificationHash, "sympyVerificationHash cannot be null");
        this.isTwinVerification = isTwinVerification;
        this.independentLockSeconds = independentLockSeconds;
        this.unlockedAt = unlockedAt;
        this.itemStatus = PracticeItemStatus.PENDING;
        this.hintsUnlockedCount = 0;
        this.attemptCount = 0;
        this.detectedTrapTriggered = false;
        this.createdAt = createdAt != null ? createdAt : Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public PracticeSessionEntity getSession() {
        return session;
    }

    public void setSession(PracticeSessionEntity session) {
        this.session = session;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getOrdinalIndex() {
        return ordinalIndex;
    }

    public PracticeSlotRole getSlotRole() {
        return slotRole;
    }

    public PracticeQuestionSource getQuestionSource() {
        return questionSource;
    }

    public String getSourceQuestionId() {
        return sourceQuestionId;
    }

    public String getSkillId() {
        return skillId;
    }

    public String getTrapFamily() {
        return trapFamily;
    }

    public BigDecimal getTargetDifficulty() {
        return targetDifficulty;
    }

    public BigDecimal getItemDifficulty() {
        return itemDifficulty;
    }

    public BigDecimal getZpdLowerBound() {
        return zpdLowerBound;
    }

    public BigDecimal getZpdUpperBound() {
        return zpdUpperBound;
    }

    public String getPromptLatex() {
        return promptLatex;
    }

    public String getPromptInstructionTr() {
        return promptInstructionTr;
    }

    public String getPromptInstructionEn() {
        return promptInstructionEn;
    }

    public String getCanonicalSolutionExpr() {
        return canonicalSolutionExpr;
    }

    public String getTrapDistractorExpr() {
        return trapDistractorExpr;
    }

    public String getSympyVerificationHash() {
        return sympyVerificationHash;
    }

    public boolean isTwinVerification() {
        return isTwinVerification;
    }

    public int getIndependentLockSeconds() {
        return independentLockSeconds;
    }

    public Instant getUnlockedAt() {
        return unlockedAt;
    }

    public void setUnlockedAt(Instant unlockedAt) {
        this.unlockedAt = unlockedAt;
    }

    public int getHintsUnlockedCount() {
        return hintsUnlockedCount;
    }

    public void incrementHintsUnlocked() {
        this.hintsUnlockedCount++;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void incrementAttemptCount() {
        this.attemptCount++;
    }

    public PracticeItemStatus getItemStatus() {
        return itemStatus;
    }

    public void setItemStatus(PracticeItemStatus itemStatus) {
        this.itemStatus = itemStatus;
    }

    public String getSubmittedAnswerLatex() {
        return submittedAnswerLatex;
    }

    public void setSubmittedAnswerLatex(String submittedAnswerLatex) {
        this.submittedAnswerLatex = submittedAnswerLatex;
    }

    public boolean isDetectedTrapTriggered() {
        return detectedTrapTriggered;
    }

    public void setDetectedTrapTriggered(boolean detectedTrapTriggered) {
        this.detectedTrapTriggered = detectedTrapTriggered;
    }

    public Long getVerificationDurationMs() {
        return verificationDurationMs;
    }

    public void setVerificationDurationMs(Long verificationDurationMs) {
        this.verificationDurationMs = verificationDurationMs;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
