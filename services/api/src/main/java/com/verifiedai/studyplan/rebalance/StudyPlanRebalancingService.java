package com.verifiedai.studyplan.rebalance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.studyplan.coach.*;
import com.verifiedai.studyplan.rebalance.dto.StudyPlanRebalanceRequest;
import com.verifiedai.studyplan.rebalance.dto.StudyPlanRebalanceResponse;
import com.verifiedai.studyplan.rebalance.dto.StudyPlanRecoveryStatusResponse;
import com.verifiedai.studyplan.spaced.SpacedQueueStatus;
import com.verifiedai.studyplan.spaced.SpacedReviewQueueEntity;
import com.verifiedai.studyplan.spaced.SpacedReviewQueueRepository;
import com.verifiedai.studyplan.spaced.SpacedTriggerType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class StudyPlanRebalancingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StudyPlanRebalancingService.class);
    private static final String SCHEMA_VERSION = "1.0.0";
    private static final int DEFAULT_DAYS_UNTIL_EXAM = 30;
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final RebalanceEventRepository rebalanceEventRepository;
    private final DailyCoachSessionRepository coachSessionRepository;
    private final DailyCoachTaskRepository coachTaskRepository;
    private final SpacedReviewQueueRepository spacedReviewRepository;
    private final MissedDayRecoveryPolicy policy;
    private final ApplicationEventPublisher eventPublisher;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final boolean rebalancingEnabled;

    private final Counter planRebalancedCounter;
    private final Counter missedDaysRecoveredCounter;
    private final Counter overloadPruningCounter;

    @Autowired
    StudyPlanRebalancingService(
            RebalanceEventRepository rebalanceEventRepository,
            DailyCoachSessionRepository coachSessionRepository,
            DailyCoachTaskRepository coachTaskRepository,
            SpacedReviewQueueRepository spacedReviewRepository,
            MissedDayRecoveryPolicy policy,
            ApplicationEventPublisher eventPublisher,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            Clock clock,
            @Value("${verifiedai.studyplan.rebalancing-enabled:true}") boolean rebalancingEnabled
    ) {
        this(
                rebalanceEventRepository,
                coachSessionRepository,
                coachTaskRepository,
                spacedReviewRepository,
                policy,
                eventPublisher,
                meterRegistryProvider.getIfAvailable(SimpleMeterRegistry::new),
                objectMapperProvider.getIfAvailable(() -> new ObjectMapper().findAndRegisterModules()),
                clock,
                rebalancingEnabled
        );
    }

    public StudyPlanRebalancingService(
            RebalanceEventRepository rebalanceEventRepository,
            DailyCoachSessionRepository coachSessionRepository,
            DailyCoachTaskRepository coachTaskRepository,
            SpacedReviewQueueRepository spacedReviewRepository,
            MissedDayRecoveryPolicy policy,
            ApplicationEventPublisher eventPublisher,
            MeterRegistry meterRegistry,
            ObjectMapper objectMapper,
            Clock clock,
            boolean rebalancingEnabled
    ) {
        this.rebalanceEventRepository = Objects.requireNonNull(rebalanceEventRepository, "rebalanceEventRepository cannot be null");
        this.coachSessionRepository = Objects.requireNonNull(coachSessionRepository, "coachSessionRepository cannot be null");
        this.coachTaskRepository = Objects.requireNonNull(coachTaskRepository, "coachTaskRepository cannot be null");
        this.spacedReviewRepository = Objects.requireNonNull(spacedReviewRepository, "spacedReviewRepository cannot be null");
        this.policy = Objects.requireNonNull(policy, "policy cannot be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher cannot be null");
        this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.rebalancingEnabled = rebalancingEnabled;

        this.planRebalancedCounter = Counter.builder("verifiedai.studyplan.plan_rebalanced")
                .description("Total deterministic study plan rebalances executed")
                .register(this.meterRegistry);
        this.missedDaysRecoveredCounter = Counter.builder("verifiedai.studyplan.missed_days_recovered")
                .description("Total missed study days recovered without backlog wall")
                .register(this.meterRegistry);
        this.overloadPruningCounter = Counter.builder("verifiedai.studyplan.overload_pruning_executed")
                .description("Total rebalance executions that pruned or shifted overflow tasks")
                .register(this.meterRegistry);
    }

    public StudyPlanRebalancingService(
            RebalanceEventRepository rebalanceEventRepository,
            DailyCoachSessionRepository coachSessionRepository,
            DailyCoachTaskRepository coachTaskRepository,
            SpacedReviewQueueRepository spacedReviewRepository,
            MissedDayRecoveryPolicy policy,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            boolean rebalancingEnabled
    ) {
        this(
                rebalanceEventRepository,
                coachSessionRepository,
                coachTaskRepository,
                spacedReviewRepository,
                policy,
                eventPublisher,
                new SimpleMeterRegistry(),
                new ObjectMapper().findAndRegisterModules(),
                clock,
                rebalancingEnabled
        );
    }

    @Transactional(readOnly = true)
    public StudyPlanRecoveryStatusResponse getRecoveryStatus(UUID userId, LocalDate asOfDate) {
        Objects.requireNonNull(userId, "userId cannot be null");
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now(clock);

        if (!rebalancingEnabled) {
            return new StudyPlanRecoveryStatusResponse(
                    SCHEMA_VERSION,
                    userId,
                    effectiveDate,
                    false,
                    0,
                    DEFAULT_DAYS_UNTIL_EXAM,
                    MissedDayRecoveryPolicy.DEFAULT_BASELINE_BUDGET_MINUTES,
                    MissedDayRecoveryPolicy.DEFAULT_BASELINE_BUDGET_MINUTES,
                    false,
                    MissedDayRecoveryPolicy.MAX_ACTIVE_TODAY_TASKS,
                    0,
                    0,
                    PruneReasonCode.NONE,
                    "studyplan.rebalance.welcome_steady",
                    null
            );
        }

        List<DailyCoachSessionEntity> sessions = coachSessionRepository.findAllByUserIdOrderByLocalDateDesc(userId);
        int baselineBudget = resolveBaselineBudget(sessions);
        Integer inferredMissedDays = inferMissedDays(sessions, effectiveDate);

        List<MissedDayRecoveryPolicy.CandidateTask> candidates = collectCandidates(userId, sessions, effectiveDate);
        MissedDayRecoveryPolicy.RecoveryComputation computation = policy.evaluate(
                new MissedDayRecoveryPolicy.RecoveryInput(
                        effectiveDate,
                        null,
                        inferredMissedDays,
                        baselineBudget,
                        DEFAULT_DAYS_UNTIL_EXAM,
                        false,
                        candidates
                )
        );

        UUID lastEventId = rebalanceEventRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(RebalanceEventEntity::getId)
                .orElse(null);

        return new StudyPlanRecoveryStatusResponse(
                SCHEMA_VERSION,
                userId,
                effectiveDate,
                computation.recoveryNeeded(),
                computation.missedDaysCount(),
                computation.daysUntilExam(),
                computation.previousDailyBudgetMinutes(),
                computation.rebalancedDailyBudgetMinutes(),
                computation.miniModeRecommended(),
                computation.activeTodayTasksCount(),
                computation.prunedTasksCount(),
                computation.rescheduledReviewsCount(),
                computation.pruneReasonCode(),
                computation.welcomeMessageKey(),
                lastEventId
        );
    }

    @Transactional
    public StudyPlanRebalanceResponse executeRebalance(UUID userId, StudyPlanRebalanceRequest request) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        if (!rebalancingEnabled) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.STUDY_PLAN_REBALANCING_DISABLED,
                    "Study plan rebalancing is currently disabled",
                    true,
                    "TRY_LATER"
            );
        }

        if (request.triggerType() == null) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.STUDY_PLAN_REBALANCE_INVALID_REQUEST,
                    "trigger_type is required",
                    true,
                    "PROVIDE_TRIGGER_TYPE"
            );
        }

        LocalDate effectiveDate = request.asOfDate() != null ? request.asOfDate() : LocalDate.now(clock);
        boolean preferMiniMode = Boolean.TRUE.equals(request.preferMiniMode())
                || request.triggerType() == RebalanceTriggerType.MINI_MODE_DOWNSHIFT;

        String idempotencyKey = resolveIdempotencyKey(userId, effectiveDate, request.triggerType(), preferMiniMode, request.idempotencyKey());
        Optional<RebalanceEventEntity> existingOpt = rebalanceEventRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
        if (existingOpt.isPresent()) {
            return toResponse(existingOpt.get(), true);
        }

        Instant now = clock.instant();
        List<DailyCoachSessionEntity> allSessions = coachSessionRepository.findAllByUserIdOrderByLocalDateDesc(userId);
        int baselineBudget = resolveBaselineBudget(allSessions);
        int daysUntilExam = request.daysUntilExamOverride() != null
                ? request.daysUntilExamOverride()
                : DEFAULT_DAYS_UNTIL_EXAM;
        int missedDays = request.missedDaysOverride() != null
                ? request.missedDaysOverride()
                : inferMissedDays(allSessions, effectiveDate);

        // 1. Prune obsolete prior-day open coach sessions without stacking tasks onto today (INV-REBAL-001)
        List<MissedDayRecoveryPolicy.CandidateTask> candidateTasks = new ArrayList<>();
        List<DailyCoachSessionEntity> priorOpenSessions = coachSessionRepository.findByUserIdAndLocalDateBeforeAndStatusIn(
                userId,
                effectiveDate,
                List.of(DailyCoachSessionStatus.IN_PROGRESS)
        );
        for (DailyCoachSessionEntity prior : priorOpenSessions) {
            List<DailyCoachTaskEntity> priorTasks = coachTaskRepository.findAllBySessionIdOrderByTaskOrderAsc(prior.getId());
            for (DailyCoachTaskEntity task : priorTasks) {
                if (task.getStatus() == DailyCoachTaskStatus.PENDING) {
                    candidateTasks.add(new MissedDayRecoveryPolicy.CandidateTask(
                            task.getId(),
                            task.getTargetSkillCode(),
                            task.getTaskType() == DailyCoachTaskType.TRAP_REPAIR ? 0.50 : 0.35,
                            0.50,
                            prior.getLocalDate(),
                            task.getTaskType() == DailyCoachTaskType.TRAP_REPAIR
                    ));
                    task.setStatus(DailyCoachTaskStatus.SKIPPED);
                }
            }
            coachTaskRepository.skipAllPendingTasksBySessionId(prior.getId());
            prior.setStatus(DailyCoachSessionStatus.EXPIRED);
            prior.setUpdatedAt(now);
            coachSessionRepository.save(prior);
        }

        // 2. Gather today's coach tasks (if session already exists) and active due spaced reviews
        Optional<DailyCoachSessionEntity> todaySessionOpt = coachSessionRepository.findByUserIdAndLocalDate(userId, effectiveDate);
        if (todaySessionOpt.isPresent()) {
            List<DailyCoachTaskEntity> todayTasks = coachTaskRepository.findAllBySessionIdOrderByTaskOrderAsc(todaySessionOpt.get().getId());
            for (DailyCoachTaskEntity task : todayTasks) {
                if (task.getStatus() == DailyCoachTaskStatus.PENDING) {
                    candidateTasks.add(new MissedDayRecoveryPolicy.CandidateTask(
                            task.getId(),
                            task.getTargetSkillCode(),
                            task.getTaskType() == DailyCoachTaskType.TRAP_REPAIR ? 0.55 : 0.38,
                            0.55,
                            effectiveDate,
                            task.getTaskType() == DailyCoachTaskType.TRAP_REPAIR
                    ));
                }
            }
        }

        Instant endOfToday = effectiveDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<SpacedReviewQueueEntity> dueSpacedItems = spacedReviewRepository.findActiveDueByUserId(userId, endOfToday);
        for (SpacedReviewQueueEntity item : dueSpacedItems) {
            double roi = item.getTriggerType() == SpacedTriggerType.TRAP_IMMUNITY_7D ? 0.48 : 0.32;
            if (item.getEstimatedRetention().doubleValue() > 0.85) {
                roi = 0.15;
            }
            candidateTasks.add(new MissedDayRecoveryPolicy.CandidateTask(
                    item.getId(),
                    item.getCanonicalSkillCode(),
                    roi,
                    item.getEstimatedRetention().doubleValue(),
                    effectiveDate,
                    false
            ));
        }

        MissedDayRecoveryPolicy.RecoveryComputation computation = policy.evaluate(
                new MissedDayRecoveryPolicy.RecoveryInput(
                        effectiveDate,
                        null,
                        missedDays,
                        baselineBudget,
                        daysUntilExam,
                        preferMiniMode,
                        candidateTasks
                )
        );

        // 3. Re-anchor overflow spaced review items beyond today's allowed cap onto future dates (INV-REBAL-001)
        Set<UUID> survivingIds = new HashSet<>(computation.activeTaskIds());
        int overflowIndex = 0;
        int actualRescheduledReviews = 0;
        for (SpacedReviewQueueEntity item : dueSpacedItems) {
            if (!survivingIds.contains(item.getId())) {
                int shiftDays = 1 + (overflowIndex % 3);
                overflowIndex++;
                Instant futureDueAt = effectiveDate.plusDays(shiftDays).atTime(9, 0).toInstant(ZoneOffset.UTC);
                item.setDueAt(futureDueAt);
                item.setUpdatedAt(now);
                spacedReviewRepository.save(item);
                actualRescheduledReviews++;
            }
        }

        int finalRescheduledCount = Math.max(computation.rescheduledReviewsCount(), actualRescheduledReviews);

        // 4. Synchronize or create today's DailyCoachSessionEntity within [5, 25] min and <= 3 tasks
        synchronizeTodayCoachSession(
                userId,
                effectiveDate,
                todaySessionOpt.orElse(null),
                computation,
                now
        );

        UUID eventId = UUID.randomUUID();
        String prunedConceptJson = serializeConceptIds(computation.prunedConceptIds());
        RebalanceEventEntity entity = new RebalanceEventEntity(
                eventId,
                userId,
                null,
                effectiveDate,
                request.triggerType(),
                computation.missedDaysCount(),
                computation.daysUntilExam(),
                computation.previousDailyBudgetMinutes(),
                computation.rebalancedDailyBudgetMinutes(),
                computation.prunedTasksCount(),
                finalRescheduledCount,
                computation.activeTodayTasksCount(),
                computation.miniModeActivated(),
                computation.pruneReasonCode(),
                prunedConceptJson,
                idempotencyKey,
                now
        );

        RebalanceEventEntity saved;
        try {
            saved = rebalanceEventRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent idempotency race safety (INV-REBAL-004)
            RebalanceEventEntity concurrent = rebalanceEventRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                    .orElseThrow(() -> ex);
            return toResponse(concurrent, true);
        }

        eventPublisher.publishEvent(new RebalanceEvent(
                saved.getId(),
                saved.getUserId(),
                saved.getRecoveryDate(),
                saved.getTriggerType(),
                saved.getMissedDaysCount(),
                saved.getDaysUntilExam(),
                saved.getPreviousDailyBudget(),
                saved.getRebalancedDailyBudget(),
                saved.getPrunedTasksCount(),
                saved.getRescheduledReviewsCount(),
                saved.getActiveTodayTasksCount(),
                saved.isMiniModeActivated(),
                saved.getPruneReasonCode(),
                saved.getCreatedAt()
        ));

        planRebalancedCounter.increment();
        if (saved.getMissedDaysCount() > 0) {
            missedDaysRecoveredCounter.increment(saved.getMissedDaysCount());
        }
        if (saved.getPrunedTasksCount() > 0 || saved.getRescheduledReviewsCount() > 0) {
            overloadPruningCounter.increment();
        }

        LOGGER.info(
                "studyplan.rebalance.executed eventId={} trigger={} missedDays={} budget={} activeTasks={} pruned={} rescheduled={} reason={}",
                saved.getId(),
                saved.getTriggerType(),
                saved.getMissedDaysCount(),
                saved.getRebalancedDailyBudget(),
                saved.getActiveTodayTasksCount(),
                saved.getPrunedTasksCount(),
                saved.getRescheduledReviewsCount(),
                saved.getPruneReasonCode()
        );

        return toResponse(saved, false);
    }

    @Transactional(readOnly = true)
    public StudyPlanRebalanceResponse getRebalanceEvent(UUID userId, UUID eventId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(eventId, "eventId cannot be null");
        RebalanceEventEntity entity = rebalanceEventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> new StudyPlanRecoveryNotFoundException(eventId));
        return toResponse(entity, false);
    }

    private void synchronizeTodayCoachSession(
            UUID userId,
            LocalDate effectiveDate,
            DailyCoachSessionEntity existingToday,
            MissedDayRecoveryPolicy.RecoveryComputation computation,
            Instant now
    ) {
        if (existingToday != null) {
            existingToday.setBudgetMinutes(computation.rebalancedDailyBudgetMinutes());
            existingToday.setDownshifted(computation.miniModeActivated());
            existingToday.setTotalTasksCount(computation.activeTodayTasksCount());
            if (computation.miniModeActivated()) {
                coachTaskRepository.skipTasksAfterOrder(existingToday.getId(), 1);
                existingToday.setBriefingText(
                        "Hoş geldin! 5 dakikalık Mini Mod ile tek odaklı görevin hazır."
                );
            } else {
                existingToday.setBriefingText(
                        "Hoş geldin! Birikmiş ödev yok, bugünün " + computation.rebalancedDailyBudgetMinutes()
                                + " dakikalık dengelenmiş görevine odaklanalım."
                );
            }
            existingToday.setUpdatedAt(now);
            coachSessionRepository.save(existingToday);
            return;
        }

        UUID sessionId = UUID.randomUUID();
        int taskCount = computation.activeTodayTasksCount();
        String briefing = computation.miniModeActivated()
                ? "Hoş geldin! 5 dakikalık Mini Mod ile tek odaklı görevin hazır."
                : "Hoş geldin! Birikmiş ödev yok, bugünün " + computation.rebalancedDailyBudgetMinutes()
                + " dakikalık dengelenmiş görevine odaklanalım.";

        DailyCoachSessionEntity newSession = new DailyCoachSessionEntity(
                sessionId,
                userId,
                effectiveDate,
                computation.rebalancedDailyBudgetMinutes(),
                DailyCoachSessionStatus.IN_PROGRESS,
                computation.miniModeActivated(),
                false,
                0,
                taskCount,
                briefing,
                DailyCoachMode.SOCRATIC_COACH_MODE,
                now
        );
        coachSessionRepository.save(newSession);

        List<DailyCoachTaskEntity> tasks = new ArrayList<>(taskCount);
        for (int order = 1; order <= taskCount; order++) {
            DailyCoachTaskType type = switch (order) {
                case 1 -> DailyCoachTaskType.TRAP_REPAIR;
                case 2 -> DailyCoachTaskType.SPACED_RETENTION;
                default -> DailyCoachTaskType.EXAM_SYNTHESIS;
            };
            String skill = switch (order) {
                case 1 -> "MATH.ALG.RATIONAL_EQ";
                case 2 -> "MATH.ALG.QUADRATIC_DISCRIMINANT";
                default -> "MATH.CALC.CHAIN_RULE_COMPOSITE";
            };
            String mistake = switch (order) {
                case 1 -> "ALG_DOMAIN_EXCLUSION_MISSED";
                case 2 -> "TRAP_SIGN_DISTRIB_NEG";
                default -> "CALC_CHAIN_RULE_OMITTED";
            };
            String latex = switch (order) {
                case 1 -> "\\frac{x^2 - 4}{x - 2} = x + 2, \\quad x \\neq 2";
                case 2 -> "2x^2 - 5x - 3 = 0 \\implies \\Delta = (-5)^2 - 4(2)(-3)";
                default -> "\\frac{d}{dx}\\left[(3x^2 + 1)^4\\right] = 24x(3x^2 + 1)^3";
            };
            tasks.add(new DailyCoachTaskEntity(
                    UUID.randomUUID(),
                    sessionId,
                    order,
                    type,
                    skill,
                    mistake,
                    "twin_85_" + effectiveDate + "_" + order,
                    5,
                    "growth_opportunity: Dengelenmiş günlük odak görevi.",
                    latex,
                    DailyCoachTaskStatus.PENDING
            ));
        }
        coachTaskRepository.saveAll(tasks);
    }

    private List<MissedDayRecoveryPolicy.CandidateTask> collectCandidates(
            UUID userId,
            List<DailyCoachSessionEntity> sessions,
            LocalDate effectiveDate
    ) {
        List<MissedDayRecoveryPolicy.CandidateTask> candidates = new ArrayList<>();
        for (DailyCoachSessionEntity session : sessions) {
            if (session.getLocalDate().isBefore(effectiveDate)
                    && session.getStatus() == DailyCoachSessionStatus.IN_PROGRESS) {
                List<DailyCoachTaskEntity> tasks = coachTaskRepository.findAllBySessionIdOrderByTaskOrderAsc(session.getId());
                for (DailyCoachTaskEntity task : tasks) {
                    if (task.getStatus() == DailyCoachTaskStatus.PENDING) {
                        candidates.add(new MissedDayRecoveryPolicy.CandidateTask(
                                task.getId(),
                                task.getTargetSkillCode(),
                                task.getTaskType() == DailyCoachTaskType.TRAP_REPAIR ? 0.50 : 0.35,
                                0.50,
                                session.getLocalDate(),
                                task.getTaskType() == DailyCoachTaskType.TRAP_REPAIR
                        ));
                    }
                }
            }
        }
        Instant endOfToday = effectiveDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<SpacedReviewQueueEntity> dueSpaced = spacedReviewRepository.findActiveDueByUserId(userId, endOfToday);
        for (SpacedReviewQueueEntity item : dueSpaced) {
            double roi = item.getTriggerType() == SpacedTriggerType.TRAP_IMMUNITY_7D ? 0.48 : 0.32;
            if (item.getEstimatedRetention().doubleValue() > 0.85) {
                roi = 0.15;
            }
            candidates.add(new MissedDayRecoveryPolicy.CandidateTask(
                    item.getId(),
                    item.getCanonicalSkillCode(),
                    roi,
                    item.getEstimatedRetention().doubleValue(),
                    effectiveDate,
                    false
            ));
        }
        return candidates;
    }

    private int resolveBaselineBudget(List<DailyCoachSessionEntity> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return MissedDayRecoveryPolicy.DEFAULT_BASELINE_BUDGET_MINUTES;
        }
        return sessions.get(0).getBudgetMinutes();
    }

    private int inferMissedDays(List<DailyCoachSessionEntity> sessions, LocalDate effectiveDate) {
        if (sessions == null || sessions.isEmpty()) {
            return 0;
        }
        Optional<LocalDate> latestCompleted = sessions.stream()
                .filter(s -> s.getStatus() == DailyCoachSessionStatus.COMPLETED)
                .map(DailyCoachSessionEntity::getLocalDate)
                .filter(d -> d.isBefore(effectiveDate))
                .max(LocalDate::compareTo);
        if (latestCompleted.isPresent()) {
            long days = ChronoUnit.DAYS.between(latestCompleted.get(), effectiveDate);
            return (int) Math.max(0, Math.min(365, days - 1));
        }

        boolean completedToday = sessions.stream()
                .anyMatch(s -> s.getLocalDate().equals(effectiveDate) && s.getStatus() == DailyCoachSessionStatus.COMPLETED);
        if (completedToday) {
            return 0;
        }

        Optional<LocalDate> latestPriorOpen = sessions.stream()
                .filter(s -> s.getLocalDate().isBefore(effectiveDate) && s.getStatus() == DailyCoachSessionStatus.IN_PROGRESS)
                .map(DailyCoachSessionEntity::getLocalDate)
                .max(LocalDate::compareTo);
        if (latestPriorOpen.isPresent()) {
            long days = ChronoUnit.DAYS.between(latestPriorOpen.get(), effectiveDate);
            return (int) Math.max(1, Math.min(365, days));
        }
        return 0;
    }

    private String resolveIdempotencyKey(
            UUID userId,
            LocalDate effectiveDate,
            RebalanceTriggerType triggerType,
            boolean preferMiniMode,
            String explicitKey
    ) {
        if (explicitKey != null && !explicitKey.isBlank()) {
            return explicitKey.trim();
        }
        return "rebalance:" + userId + ":" + effectiveDate + ":" + triggerType.name() + ":" + preferMiniMode;
    }

    private String serializeConceptIds(List<String> conceptIds) {
        try {
            return objectMapper.writeValueAsString(conceptIds != null ? conceptIds : List.of());
        } catch (JsonProcessingException ex) {
            return "[]";
        }
    }

    private List<String> deserializeConceptIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private StudyPlanRebalanceResponse toResponse(RebalanceEventEntity entity, boolean idempotentReplay) {
        String welcomeKey = entity.isMiniModeActivated()
                ? "studyplan.rebalance.welcome_mini_mode"
                : (entity.getMissedDaysCount() >= 1 || entity.getPrunedTasksCount() > 0 || entity.getRescheduledReviewsCount() > 0
                ? "studyplan.rebalance.welcome_recovered"
                : "studyplan.rebalance.welcome_steady");

        return new StudyPlanRebalanceResponse(
                SCHEMA_VERSION,
                entity.getId(),
                entity.getUserId(),
                entity.getRecoveryDate(),
                entity.getTriggerType(),
                entity.getMissedDaysCount(),
                entity.getDaysUntilExam(),
                entity.getPreviousDailyBudget(),
                entity.getRebalancedDailyBudget(),
                entity.getPrunedTasksCount(),
                entity.getRescheduledReviewsCount(),
                entity.getActiveTodayTasksCount(),
                entity.isMiniModeActivated(),
                entity.getPruneReasonCode(),
                deserializeConceptIds(entity.getPrunedConceptIdsJson()),
                welcomeKey,
                idempotentReplay,
                entity.getCreatedAt()
        );
    }
}
