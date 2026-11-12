package com.verifiedai.studyplan.practice;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "practice_sessions")
public class PracticeSessionEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "source_recommendation_id")
    private UUID sourceRecommendationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_mode", nullable = false, length = 32)
    private PracticeSessionMode sessionMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private PracticeSessionStatus status = PracticeSessionStatus.IN_PROGRESS;

    @Column(name = "target_skill_id", nullable = false, length = 128)
    private String targetSkillId;

    @Column(name = "active_trap_family", length = 64)
    private String activeTrapFamily;

    @Column(name = "learner_mastery_snapshot", nullable = false, precision = 5, scale = 4)
    private BigDecimal learnerMasterySnapshot;

    @Column(name = "total_items", nullable = false)
    private int totalItems;

    @Column(name = "completed_items", nullable = false)
    private int completedItems = 0;

    @Column(name = "verified_correct_items", nullable = false)
    private int verifiedCorrectItems = 0;

    @Column(name = "trap_closure_achieved", nullable = false)
    private boolean trapClosureAchieved = false;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "abandoned_at")
    private Instant abandonedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("ordinalIndex ASC")
    private List<PracticeSessionItemEntity> items = new ArrayList<>();

    protected PracticeSessionEntity() {
    }

    public PracticeSessionEntity(
            UUID id,
            UUID userId,
            UUID sourceRecommendationId,
            PracticeSessionMode sessionMode,
            String targetSkillId,
            String activeTrapFamily,
            BigDecimal learnerMasterySnapshot,
            int totalItems,
            Instant startedAt
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.userId = Objects.requireNonNull(userId, "userId cannot be null");
        this.sourceRecommendationId = sourceRecommendationId;
        this.sessionMode = Objects.requireNonNull(sessionMode, "sessionMode cannot be null");
        this.status = PracticeSessionStatus.IN_PROGRESS;
        this.targetSkillId = Objects.requireNonNull(targetSkillId, "targetSkillId cannot be null");
        this.activeTrapFamily = activeTrapFamily;
        this.learnerMasterySnapshot = Objects.requireNonNull(learnerMasterySnapshot, "learnerMasterySnapshot cannot be null");
        this.totalItems = totalItems;
        this.completedItems = 0;
        this.verifiedCorrectItems = 0;
        this.trapClosureAchieved = false;
        this.startedAt = startedAt != null ? startedAt : Instant.now();
        this.updatedAt = this.startedAt;
    }

    public void addItem(PracticeSessionItemEntity item) {
        item.setSession(this);
        this.items.add(item);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getSourceRecommendationId() {
        return sourceRecommendationId;
    }

    public PracticeSessionMode getSessionMode() {
        return sessionMode;
    }

    public PracticeSessionStatus getStatus() {
        return status;
    }

    public void setStatus(PracticeSessionStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getTargetSkillId() {
        return targetSkillId;
    }

    public String getActiveTrapFamily() {
        return activeTrapFamily;
    }

    public BigDecimal getLearnerMasterySnapshot() {
        return learnerMasterySnapshot;
    }

    public int getTotalItems() {
        return totalItems;
    }

    public int getCompletedItems() {
        return completedItems;
    }

    public void setCompletedItems(int completedItems) {
        this.completedItems = completedItems;
        this.updatedAt = Instant.now();
    }

    public int getVerifiedCorrectItems() {
        return verifiedCorrectItems;
    }

    public void setVerifiedCorrectItems(int verifiedCorrectItems) {
        this.verifiedCorrectItems = verifiedCorrectItems;
        this.updatedAt = Instant.now();
    }

    public boolean isTrapClosureAchieved() {
        return trapClosureAchieved;
    }

    public void setTrapClosureAchieved(boolean trapClosureAchieved) {
        this.trapClosureAchieved = trapClosureAchieved;
        this.updatedAt = Instant.now();
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
        this.updatedAt = Instant.now();
    }

    public Instant getAbandonedAt() {
        return abandonedAt;
    }

    public void setAbandonedAt(Instant abandonedAt) {
        this.abandonedAt = abandonedAt;
        this.updatedAt = Instant.now();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public List<PracticeSessionItemEntity> getItems() {
        return items;
    }
}
