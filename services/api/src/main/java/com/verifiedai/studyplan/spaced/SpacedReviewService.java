package com.verifiedai.studyplan.spaced;

import com.verifiedai.billing.application.CapabilityAccessPolicy;
import com.verifiedai.billing.application.LearningOsCapability;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.studyplan.lifecycle.StudyPlanLifecycleMessagingService;
import com.verifiedai.studyplan.spaced.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class SpacedReviewService {
    private CapabilityAccessPolicy access;
    @org.springframework.beans.factory.annotation.Value("${verifiedai.billing.learning-os-tiers-enabled:false}")
    private String learningOsFlag;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setAccess(CapabilityAccessPolicy access) {
        this.access = access;
    }

    private StudyPlanLifecycleMessagingService lifecycleMessagingService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setLifecycleMessagingService(StudyPlanLifecycleMessagingService lifecycleMessagingService) {
        this.lifecycleMessagingService = lifecycleMessagingService;
    }

    private boolean tierGuardsEnabled() {
        return "true".equalsIgnoreCase(learningOsFlag);
    }

    private void requireAccess(UUID userId, LearningOsCapability capability) {
        if (access == null) {
            throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.ENTITLEMENT_SYNC_FAILED,
                "Entitlement verification unavailable", true, "RETRY");
        }
        access.require(userId, capability);
    }

    private boolean hasAccess(UUID userId, LearningOsCapability capability) {
        if (access == null) {
            throw new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE, ApiErrorCode.ENTITLEMENT_SYNC_FAILED,
                "Entitlement verification unavailable", true, "RETRY");
        }
        return access.check(userId, capability);
    }


    private final SpacedReviewQueueRepository repository;
    private final SpacedRepetitionScheduler scheduler;
    private final JdbcClient jdbcClient;

    public SpacedReviewService(
            SpacedReviewQueueRepository repository,
            SpacedRepetitionScheduler scheduler,
            JdbcClient jdbcClient
    ) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler cannot be null");
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
    }

    @Transactional(noRollbackFor = ApiProblemException.class)
    public SpacedQueueResponse getDailySpacedQueue(UUID userId, Instant now) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Instant evaluationTime = now != null ? now : Instant.now();

        if (tierGuardsEnabled()) {
            ApiProblemException syncError = null;
            boolean allowed;
            try {
                allowed = hasAccess(userId, LearningOsCapability.DAILY_COACH_MISSION);
            } catch (ApiProblemException ex) {
                if (ex.code() == ApiErrorCode.ENTITLEMENT_SYNC_FAILED) {
                    allowed = false;
                    syncError = ex;
                } else {
                    throw ex;
                }
            }
            if (!allowed) {
                List<SpacedReviewQueueEntity> servedEntities = repository.findByUserIdAndStatusIn(
                        userId,
                        List.of(SpacedQueueStatus.SERVED)
                );
                if (servedEntities.isEmpty()) {
                    if (syncError != null) {
                        throw syncError;
                    }
                    requireAccess(userId, LearningOsCapability.DAILY_COACH_MISSION);
                }
                return selectAndBuildQueueResponse(userId, servedEntities, evaluationTime, false);
            }
        }

        // 1. Sync / populate pending queue from persistence or discovered items
        syncEligibleQueueItems(userId, evaluationTime);

        // 2. Fetch all active due items for user
        List<SpacedReviewQueueEntity> activeEntities = repository.findByUserIdAndStatusIn(
                userId,
                List.of(SpacedQueueStatus.PENDING, SpacedQueueStatus.SERVED)
        );

        return selectAndBuildQueueResponse(userId, activeEntities, evaluationTime, true);
    }

    private SpacedQueueResponse selectAndBuildQueueResponse(
            UUID userId,
            List<SpacedReviewQueueEntity> entities,
            Instant evaluationTime,
            boolean mutateState
    ) {
        // 3. Convert to candidates and recalculate current retention
        List<SpacedReviewCandidate> candidates = new ArrayList<>();
        for (SpacedReviewQueueEntity entity : entities) {
            BigDecimal currentRetention = entity.getEstimatedRetention();
            if (entity.getTriggerType() == SpacedTriggerType.RETENTION_DECAY) {
                currentRetention = scheduler.computeCurrentRetention(
                        new BigDecimal("0.8500"),
                        entity.getHalfLifeDays(),
                        entity.getCreatedAt(),
                        evaluationTime
                );
                if (mutateState) {
                    entity.setEstimatedRetention(currentRetention);
                }
            }

            candidates.add(new SpacedReviewCandidate(
                    entity.getId(),
                    entity.getCanonicalSkillCode(),
                    resolveSkillDisplayName(entity.getCanonicalSkillCode()),
                    entity.getTriggerType(),
                    entity.getTargetMistakeCode(),
                    currentRetention,
                    entity.getHalfLifeDays(),
                    entity.getDueAt(),
                    entity.getAssignedTwinId(),
                    entity.getPromptPreviewLatex(),
                    entity.getStatus(),
                    entity.getPedagogicalBadgeKey()
            ));
        }

        // 4. Select and interleave daily queue (INV-SR-003, INV-SR-004: capped at 3)
        List<SpacedReviewCandidate> selectedCandidates = scheduler.selectAndInterleaveDailyQueue(
                candidates,
                SpacedRepetitionScheduler.DAILY_QUEUE_CAP,
                evaluationTime
        );

        // 5. Mark selected items as SERVED if currently PENDING (only when mutating/allowed)
        List<SpacedQueueItemDto> items = new ArrayList<>();
        Set<String> distinctSkills = new HashSet<>();

        for (SpacedReviewCandidate candidate : selectedCandidates) {
            distinctSkills.add(candidate.canonicalSkillCode());
            if (mutateState) {
                repository.findById(candidate.itemId()).ifPresent(e -> {
                    if (e.getStatus() == SpacedQueueStatus.PENDING) {
                        e.setStatus(SpacedQueueStatus.SERVED);
                        e.setUpdatedAt(evaluationTime);
                        repository.save(e);
                    }
                });
            }

            items.add(new SpacedQueueItemDto(
                    candidate.itemId(),
                    candidate.canonicalSkillCode(),
                    candidate.skillDisplayName(),
                    candidate.triggerType(),
                    candidate.targetMistakeCode(),
                    candidate.estimatedRetention(),
                    candidate.halfLifeDays(),
                    candidate.dueAt(),
                    candidate.assignedTwinId(),
                    candidate.promptPreviewLatex(),
                    mutateState && candidate.status() == SpacedQueueStatus.PENDING ? SpacedQueueStatus.SERVED : candidate.status(),
                    candidate.pedagogicalBadgeKey()
            ));
        }

        boolean interleavingSatisfied = checkInterleavingSatisfied(candidates, items);

        return new SpacedQueueResponse(
                SpacedQueueResponse.CURRENT_SCHEMA_VERSION,
                userId,
                evaluationTime,
                SpacedRepetitionScheduler.DAILY_QUEUE_CAP,
                items.size(),
                distinctSkills.size(),
                interleavingSatisfied,
                items
        );
    }

    @Transactional
    public SpacedItemCompletionResponse completeQueueItem(
            UUID userId,
            UUID itemId,
            SpacedItemCompleteRequest request,
            Instant now
    ) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(itemId, "itemId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        Instant completionTime = now != null ? now : Instant.now();

        // INV-SR-006 BOLA: Query scoped by (itemId, userId). Throws 404 on tenant mismatch.
        SpacedReviewQueueEntity item = repository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new SpacedQueueItemNotFoundException(itemId));

        if (tierGuardsEnabled()) requireAccess(userId, LearningOsCapability.DAILY_COACH_MISSION);

        // Validation: Must be active item
        if (item.getStatus() != SpacedQueueStatus.PENDING && item.getStatus() != SpacedQueueStatus.SERVED) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.REQUEST_VALIDATION_FAILED,
                    "Spaced queue item is not in an active state: " + item.getStatus(),
                    false,
                    "CHECK_ITEM_STATUS"
            );
        }

        // Rapid guess guard: Minimum 3000 ms deliberate response time
        if (request.responseTimeMs() < 3000L) {
            throw new ApiProblemException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    ApiErrorCode.REQUEST_VALIDATION_FAILED,
                    "Rapid guess guard triggered. Deliberate mathematical reasoning required before submission.",
                    true,
                    "REASON_BEFORE_SUBMITTING"
            );
        }

        // Independent verification credit requires responseTimeMs >= 8000
        boolean passedIndependently = request.passed()
                && request.independentVerification()
                && request.responseTimeMs() >= 8000L;

        boolean trapTriggered = item.getTargetMistakeCode() != null
                && item.getTargetMistakeCode().equals(request.triggeredMistakeCode());

        boolean overallSuccess = passedIndependently && !trapTriggered;

        // Half-life update (INV-SR-001)
        BigDecimal previousHalfLife = item.getHalfLifeDays();
        double elapsedDays = Math.max(0.0, (double) Duration.between(item.getCreatedAt(), completionTime).toSeconds() / 86400.0);
        BigDecimal updatedHalfLife = scheduler.computeUpdatedHalfLife(previousHalfLife, elapsedDays, overallSuccess);
        item.setHalfLifeDays(updatedHalfLife);

        // Next due timestamp calculation
        Instant nextDueAt = scheduler.computeDueTimestamp(new BigDecimal("0.8500"), updatedHalfLife, completionTime);

        // Resolution State Machine (INV-SR-002)
        MistakeResolutionStatus resolutionStatus = MistakeResolutionStatus.NONE;
        if (item.getTriggerType() == SpacedTriggerType.TRAP_IMMUNITY_7D) {
            if (overallSuccess) {
                resolutionStatus = MistakeResolutionStatus.STABLE_RESOLVED;
                updateMistakeStatus(userId, item.getCanonicalSkillCode(), item.getTargetMistakeCode(), "STABLE_RESOLVED", completionTime);
            } else {
                resolutionStatus = MistakeResolutionStatus.RELAPSED;
                updateMistakeStatus(userId, item.getCanonicalSkillCode(), item.getTargetMistakeCode(), "RELAPSED", completionTime);
            }
        }

        item.setStatus(SpacedQueueStatus.COMPLETED);
        item.setCompletedAt(completionTime);
        item.setUpdatedAt(completionTime);
        BigDecimal updatedRetention = scheduler.computeCurrentRetention(new BigDecimal("0.8500"), updatedHalfLife, completionTime, completionTime);
        item.setEstimatedRetention(updatedRetention);

        repository.save(item);
        if (lifecycleMessagingService != null) {
            try {
                lifecycleMessagingService.cancelSpacedReviewReminderIfQueueCleared(userId, completionTime, itemId);
            } catch (RuntimeException ignored) {
            }
        }

        return new SpacedItemCompletionResponse(
                itemId,
                previousHalfLife,
                updatedHalfLife,
                updatedRetention,
                nextDueAt,
                resolutionStatus
        );
    }

    @Transactional
    public SpacedQueueItemDto dismissQueueItem(
            UUID userId,
            UUID itemId,
            SpacedItemDismissRequest request,
            Instant now
    ) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(itemId, "itemId cannot be null");
        Instant dismissalTime = now != null ? now : Instant.now();

        // INV-SR-006 BOLA: Returns 404 on tenant mismatch
        SpacedReviewQueueEntity item = repository.findByIdAndUserId(itemId, userId)
                .orElseThrow(() -> new SpacedQueueItemNotFoundException(itemId));

        if (tierGuardsEnabled()) requireAccess(userId, LearningOsCapability.DAILY_COACH_MISSION);
        item.setStatus(SpacedQueueStatus.DISMISSED);
        item.setUpdatedAt(dismissalTime);
        SpacedReviewQueueEntity saved = repository.save(item);
        if (lifecycleMessagingService != null) {
            try {
                lifecycleMessagingService.cancelSpacedReviewReminderIfQueueCleared(userId, dismissalTime, itemId);
            } catch (RuntimeException ignored) {
            }
        }

        return new SpacedQueueItemDto(
                saved.getId(),
                saved.getCanonicalSkillCode(),
                resolveSkillDisplayName(saved.getCanonicalSkillCode()),
                saved.getTriggerType(),
                saved.getTargetMistakeCode(),
                saved.getEstimatedRetention(),
                saved.getHalfLifeDays(),
                saved.getDueAt(),
                saved.getAssignedTwinId(),
                saved.getPromptPreviewLatex(),
                saved.getStatus(),
                saved.getPedagogicalBadgeKey()
        );
    }

    private void syncEligibleQueueItems(UUID userId, Instant now) {
        // Look up pending mistake DNA retention items eligible for 7D check
        try {
            List<Map<String, Object>> pendingTraps = jdbcClient.sql("""
                    SELECT mistake_code, skill_id, last_occurred_at
                    FROM learner_mistake_dna_profiles
                    WHERE user_id = :userId
                      AND recurrence_state = 'RESOLVED_PENDING_RETENTION'
                    """)
                    .param("userId", userId)
                    .query().listOfRows();

            for (Map<String, Object> row : pendingTraps) {
                String mistakeCode = (String) row.get("mistake_code");
                String skillId = (String) row.get("skill_id");
                Timestamp ts = (Timestamp) row.get("last_occurred_at");
                if (ts != null) {
                    Instant remediatedAt = ts.toInstant();
                    Instant dueAt = scheduler.computeTrapImmunityDueTimestamp(remediatedAt);
                    if (!dueAt.isAfter(now)) {
                        Optional<SpacedReviewQueueEntity> existing = repository.findActiveByUserAndSkillAndTrigger(
                                userId, skillId, SpacedTriggerType.TRAP_IMMUNITY_7D, mistakeCode
                        );
                        if (existing.isEmpty()) {
                            SpacedReviewQueueEntity entity = new SpacedReviewQueueEntity(
                                    UUID.randomUUID(),
                                    userId,
                                    skillId,
                                    mistakeCode,
                                    SpacedTriggerType.TRAP_IMMUNITY_7D,
                                    new BigDecimal("0.7100"),
                                    new BigDecimal("7.00"),
                                    dueAt,
                                    UUID.randomUUID(),
                                    "\\Delta = b^2 - 4ac \\text{ for 7-day immunity check}",
                                    SpacedQueueStatus.PENDING
                            );
                            repository.save(entity);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void updateMistakeStatus(
            UUID userId,
            String skillCode,
            String mistakeCode,
            String status,
            Instant now
    ) {
        if (mistakeCode == null || mistakeCode.isBlank()) return;

        try {
            jdbcClient.sql("""
                    UPDATE learner_mistake_dna_profiles
                    SET recurrence_state = :status, as_of = :now
                    WHERE user_id = :userId AND mistake_code = :mistakeCode AND skill_id = :skillId
                    """)
                    .param("status", status)
                    .param("now", Timestamp.from(now))
                    .param("userId", userId)
                    .param("mistakeCode", mistakeCode)
                    .param("skillId", skillCode)
                    .update();
        } catch (Exception ignored) {
        }

        try {
            jdbcClient.sql("""
                    UPDATE mistake_dna_profiles
                    SET lifecycle_status = :status
                    WHERE learner_id = :userId AND mistake_code = :mistakeCode AND skill_node_id = :skillId
                    """)
                    .param("status", status)
                    .param("userId", userId)
                    .param("mistakeCode", mistakeCode)
                    .param("skillId", skillCode)
                    .update();
        } catch (Exception ignored) {
        }
    }

    private boolean checkInterleavingSatisfied(
            List<SpacedReviewCandidate> duePool,
            List<SpacedQueueItemDto> items
    ) {
        if (items.size() < 2) {
            return true;
        }

        Set<String> poolSkills = new HashSet<>();
        for (SpacedReviewCandidate c : duePool) {
            poolSkills.add(c.canonicalSkillCode());
        }

        Set<String> surfacedSkills = new HashSet<>();
        for (SpacedQueueItemDto item : items) {
            surfacedSkills.add(item.canonicalSkillCode());
        }

        if (poolSkills.size() >= 2 && surfacedSkills.size() < 2) {
            return false;
        }

        for (int i = 0; i < items.size() - 1; i++) {
            if (items.get(i).canonicalSkillCode().equals(items.get(i + 1).canonicalSkillCode())) {
                if (surfacedSkills.size() >= 2) {
                    return false;
                }
            }
        }

        return true;
    }

    private String resolveSkillDisplayName(String skillCode) {
        if (skillCode == null) return "Mathematical Reasoning";
        return switch (skillCode) {
            case "MATH.ALG.QUADRATIC_DISCRIMINANT" -> "Quadratic Discriminant Analysis";
            case "MATH.CALC.CHAIN_RULE_COMPOSITE", "MATH.CALC.CHAIN_RULE" -> "Composite Function Chain Rule";
            case "MATH.TRIG.DOUBLE_ANGLE_IDENTITY" -> "Double Angle Trigonometric Identity";
            case "MATH.ALG.RATIONAL_EQ" -> "Rational Equation Invariance";
            case "MATH.ALG.LINEAR_INEQ" -> "Linear Inequality Sign Properties";
            case "MATH.ALG.RADICAL_EQ" -> "Radical Equation Extraneous Root Detection";
            default -> skillCode.replace("MATH.", "").replace(".", " - ").replace("_", " ");
        };
    }
}
