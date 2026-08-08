package com.verifiedai.profile.infrastructure.persistence;

import com.verifiedai.profile.domain.model.EducationLevel;
import com.verifiedai.profile.domain.model.ExplanationDepth;
import com.verifiedai.profile.domain.model.OnboardingStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learning_profiles")
public class LearningProfileJpaEntity {
    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    private String educationLevel;
    private String preferredLanguage;
    private String explanationDepth;
    private Integer dailyStudyMinutes;
    private String timezone;
    private String goalContext;

    @Column(nullable = false)
    private String onboardingStatus;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    protected LearningProfileJpaEntity() {
    }

    private LearningProfileJpaEntity(UUID userId, Instant now) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.onboardingStatus = OnboardingStatus.IN_PROGRESS.name();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static LearningProfileJpaEntity create(UUID userId, Instant now) {
        return new LearningProfileJpaEntity(userId, now);
    }

    public void update(
        EducationLevel educationLevel,
        String preferredLanguage,
        ExplanationDepth explanationDepth,
        Integer dailyStudyMinutes,
        String timezone,
        String goalContext,
        OnboardingStatus onboardingStatus,
        Instant now
    ) {
        this.educationLevel = educationLevel == null ? null : educationLevel.name();
        this.preferredLanguage = preferredLanguage;
        this.explanationDepth = explanationDepth == null ? null : explanationDepth.name();
        this.dailyStudyMinutes = dailyStudyMinutes;
        this.timezone = timezone;
        this.goalContext = goalContext;
        this.onboardingStatus = onboardingStatus.name();
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String educationLevel() {
        return educationLevel;
    }

    public String preferredLanguage() {
        return preferredLanguage;
    }

    public String explanationDepth() {
        return explanationDepth;
    }

    public Integer dailyStudyMinutes() {
        return dailyStudyMinutes;
    }

    public String timezone() {
        return timezone;
    }

    public String goalContext() {
        return goalContext;
    }

    public String onboardingStatus() {
        return onboardingStatus;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Long version() {
        return version;
    }
}
