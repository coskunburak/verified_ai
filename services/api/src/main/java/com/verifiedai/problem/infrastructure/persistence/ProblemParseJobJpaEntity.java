package com.verifiedai.problem.infrastructure.persistence;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.problem.domain.model.ProblemParseJobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "problem_parse_jobs")
public class ProblemParseJobJpaEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "problem_session_id", nullable = false)
    private UUID problemSessionId;

    @Column(name = "recognition_evidence_id", nullable = false)
    private UUID recognitionEvidenceId;

    @Column(name = "recognition_evidence_revision", nullable = false)
    private int recognitionEvidenceRevision;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String capability;

    @Column(name = "prompt_id", nullable = false)
    private String promptId;

    @Column(name = "prompt_version", nullable = false)
    private String promptVersion;

    @Column(name = "schema_version", nullable = false)
    private String schemaVersion;

    @Column(name = "route_policy_version", nullable = false)
    private String routePolicyVersion;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error_code")
    private String lastErrorCode;

    @Column(name = "last_failure_class")
    private String lastFailureClass;

    @Column(name = "review_required", nullable = false)
    private boolean reviewRequired;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Version
    private long version;

    protected ProblemParseJobJpaEntity() {
    }

    private ProblemParseJobJpaEntity(
        UUID id,
        UUID userId,
        UUID problemSessionId,
        UUID recognitionEvidenceId,
        int recognitionEvidenceRevision,
        String routePolicyVersion,
        String promptId,
        String promptVersion,
        String schemaVersion,
        int maxAttempts,
        Instant now
    ) {
        this.id = id;
        this.userId = userId;
        this.problemSessionId = problemSessionId;
        this.recognitionEvidenceId = recognitionEvidenceId;
        this.recognitionEvidenceRevision = recognitionEvidenceRevision;
        this.status = ProblemParseJobStatus.QUEUED.name();
        this.capability = AiCapability.PROBLEM_NORMALIZE.name();
        this.routePolicyVersion = routePolicyVersion;
        this.promptId = promptId;
        this.promptVersion = promptVersion;
        this.schemaVersion = schemaVersion;
        this.maxAttempts = maxAttempts;
        this.nextAttemptAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ProblemParseJobJpaEntity queued(
        UUID id,
        UUID userId,
        UUID problemSessionId,
        UUID recognitionEvidenceId,
        int recognitionEvidenceRevision,
        String routePolicyVersion,
        String promptId,
        String promptVersion,
        String schemaVersion,
        int maxAttempts,
        Instant now
    ) {
        return new ProblemParseJobJpaEntity(
            id,
            userId,
            problemSessionId,
            recognitionEvidenceId,
            recognitionEvidenceRevision,
            routePolicyVersion,
            promptId,
            promptVersion,
            schemaVersion,
            maxAttempts,
            now
        );
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public UUID problemSessionId() {
        return problemSessionId;
    }

    public UUID recognitionEvidenceId() {
        return recognitionEvidenceId;
    }

    public int recognitionEvidenceRevision() {
        return recognitionEvidenceRevision;
    }

    public String status() {
        return status;
    }

    public String capability() {
        return capability;
    }

    public String promptId() {
        return promptId;
    }

    public String promptVersion() {
        return promptVersion;
    }

    public String schemaVersion() {
        return schemaVersion;
    }

    public String routePolicyVersion() {
        return routePolicyVersion;
    }

    public int attemptCount() {
        return attemptCount;
    }

    public int maxAttempts() {
        return maxAttempts;
    }

    public String lastErrorCode() {
        return lastErrorCode;
    }

    public String lastFailureClass() {
        return lastFailureClass;
    }

    public boolean reviewRequired() {
        return reviewRequired;
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

    public boolean dueAt(Instant now) {
        return (ProblemParseJobStatus.QUEUED.name().equals(status) || ProblemParseJobStatus.FAILED_RETRYABLE.name().equals(status))
            && !nextAttemptAt.isAfter(now);
    }

    public int markRunning(Instant now) {
        status = ProblemParseJobStatus.RUNNING.name();
        attemptCount += 1;
        if (startedAt == null) {
            startedAt = now;
        }
        updatedAt = now;
        return attemptCount;
    }

    public void markSucceeded(boolean reviewRequired, Instant now) {
        status = ProblemParseJobStatus.SUCCEEDED.name();
        this.reviewRequired = reviewRequired;
        lastErrorCode = null;
        lastFailureClass = null;
        completedAt = now;
        updatedAt = now;
    }

    public void markUnsupported(Instant now) {
        status = ProblemParseJobStatus.UNSUPPORTED.name();
        reviewRequired = false;
        lastErrorCode = null;
        lastFailureClass = null;
        completedAt = now;
        updatedAt = now;
    }

    public void markFailure(String errorCode, String failureClass, boolean retryable, Instant nextAttemptAt, Instant now) {
        lastErrorCode = errorCode;
        lastFailureClass = failureClass;
        if (retryable && attemptCount < maxAttempts) {
            status = ProblemParseJobStatus.FAILED_RETRYABLE.name();
            this.nextAttemptAt = nextAttemptAt;
        } else {
            status = ProblemParseJobStatus.FAILED_TERMINAL.name();
            completedAt = now;
        }
        updatedAt = now;
    }

    public void recoverStuckRunning(String errorCode, Instant nextAttemptAt, Instant now) {
        lastErrorCode = errorCode;
        lastFailureClass = "TIMEOUT";
        if (attemptCount < maxAttempts) {
            status = ProblemParseJobStatus.FAILED_RETRYABLE.name();
            this.nextAttemptAt = nextAttemptAt;
        } else {
            status = ProblemParseJobStatus.FAILED_TERMINAL.name();
            completedAt = now;
        }
        updatedAt = now;
    }
}
