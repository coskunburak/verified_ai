package com.verifiedai.problem.infrastructure.persistence;

import com.verifiedai.problem.domain.model.ProblemAssetSource;
import com.verifiedai.problem.domain.model.ProblemSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "problem_sessions")
public class ProblemSessionJpaEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String status;

    @Column(name = "input_mode", nullable = false)
    private String inputMode;

    @Column(name = "current_parse_id")
    private UUID currentParseId;
    @Column(name = "problem_id")
    private UUID problemId;
    @Column(name = "solve_job_id")
    private UUID solveJobId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    protected ProblemSessionJpaEntity() {
    }

    private ProblemSessionJpaEntity(UUID id, UUID userId, ProblemAssetSource inputMode, Instant now) {
        this.id = id;
        this.userId = userId;
        this.status = ProblemSessionStatus.CREATED.name();
        this.inputMode = inputMode.name();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ProblemSessionJpaEntity create(UUID id, UUID userId, ProblemAssetSource inputMode, Instant now) {
        return new ProblemSessionJpaEntity(id, userId, inputMode, now);
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public String status() {
        return status;
    }

    public String inputMode() {
        return inputMode;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public long version() {
        return version;
    }

    public UUID currentParseId() {
        return currentParseId;
    }

    public void selectParse(UUID parseId, Instant now) {
        this.currentParseId = parseId;
        this.updatedAt = now;
    }

    public void markAssetUploaded(Instant now) {
        if (ProblemSessionStatus.CREATED.name().equals(status) || ProblemSessionStatus.FAILED.name().equals(status)) {
            status = ProblemSessionStatus.ASSET_UPLOADED.name();
            updatedAt = now;
        }
    }

    public void markParsing(Instant now) {
        status = ProblemSessionStatus.PARSING.name();
        updatedAt = now;
    }

    public void markParsed(Instant now) {
        status = ProblemSessionStatus.PARSED.name();
        updatedAt = now;
    }

    public void markReviewRequired(Instant now) {
        status = ProblemSessionStatus.REVIEW_REQUIRED.name();
        updatedAt = now;
    }

    public void markFailed(Instant now) {
        status = ProblemSessionStatus.FAILED.name();
        updatedAt = now;
    }
}
