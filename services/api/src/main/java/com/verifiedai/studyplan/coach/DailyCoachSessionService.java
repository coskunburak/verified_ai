package com.verifiedai.studyplan.coach;

import com.verifiedai.billing.application.CapabilityAccessPolicy;
import com.verifiedai.billing.application.LearningOsCapability;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.studyplan.coach.dto.*;
import com.verifiedai.studyplan.lifecycle.StudyPlanLifecycleMessagingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@Service
public class DailyCoachSessionService {
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


    private static final Set<Integer> ALLOWED_BUDGET_MINUTES = Set.of(5, 10, 15, 20, 25);

    private final DailyCoachSessionRepository sessionRepository;
    private final DailyCoachTaskRepository taskRepository;
    private final DeterministicCoachBriefingBuilder briefingBuilder;
    private final JdbcClient jdbcClient;
    private final Clock clock;

    @Autowired
    public DailyCoachSessionService(
            DailyCoachSessionRepository sessionRepository,
            DailyCoachTaskRepository taskRepository,
            DeterministicCoachBriefingBuilder briefingBuilder,
            JdbcClient jdbcClient,
            Clock clock
    ) {
        this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository cannot be null");
        this.taskRepository = Objects.requireNonNull(taskRepository, "taskRepository cannot be null");
        this.briefingBuilder = Objects.requireNonNull(briefingBuilder, "briefingBuilder cannot be null");
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public DailyCoachSessionService(
            DailyCoachSessionRepository sessionRepository,
            DailyCoachTaskRepository taskRepository,
            DeterministicCoachBriefingBuilder briefingBuilder,
            JdbcClient jdbcClient
    ) {
        this(sessionRepository, taskRepository, briefingBuilder, jdbcClient, Clock.systemUTC());
    }

    private record MistakeSignal(String skillCode, String mistakeCode, String recurrenceState) {}
    private record SpacedSignal(String skillCode, String mistakeCode, String triggerType, String promptPreviewLatex) {}
    private record RecommendationSignal(String skillCode, String mistakeCode, String rationaleCode) {}

    @Transactional
    public DailyCoachSummaryResponse getOrCreateTodaySummary(UUID userId, LocalDate localDate) {
        Objects.requireNonNull(userId, "userId cannot be null");
        LocalDate effectiveDate = localDate != null ? localDate : LocalDate.now(clock);

        Optional<DailyCoachSessionEntity> existing = sessionRepository.findByUserIdAndLocalDate(userId, effectiveDate);
        if (existing.isPresent()) {
            DailyCoachSessionEntity session = existing.get();
            List<DailyCoachTaskEntity> tasks = taskRepository.findAllBySessionIdOrderByTaskOrderAsc(session.getId());
            List<MistakeSignal> traps = queryActiveTraps(userId);
            return toSummaryResponse(session, tasks, traps);
        }

        if (tierGuardsEnabled()) requireAccess(userId, LearningOsCapability.DAILY_COACH_MISSION);
        return createDeterministicMission(userId, effectiveDate);
    }

    @Transactional
    public DailyCoachSummaryResponse updatePreferences(UUID userId, DailyCoachPreferencesRequest request) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");
        if (tierGuardsEnabled()) requireAccess(userId, LearningOsCapability.DAILY_COACH_MISSION);
        Objects.requireNonNull(request.preferredMode(), "preferredMode cannot be null");
        Objects.requireNonNull(request.budgetMinutes(), "budgetMinutes cannot be null");

        if (!ALLOWED_BUDGET_MINUTES.contains(request.budgetMinutes())) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.REQUEST_VALIDATION_FAILED,
                    "budgetMinutes must be one of 5, 10, 15, 20, or 25",
                    true,
                    "SELECT_VALID_BUDGET_MINUTES"
            );
        }

        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();

        DailyCoachSessionEntity session = sessionRepository.findByUserIdAndLocalDate(userId, today)
                .orElseGet(() -> createSessionAndTasksEntity(userId, today));

        session.setPreferredMode(request.preferredMode());
        session.setBudgetMinutes(request.budgetMinutes());
        session.setUpdatedAt(now);

        List<DailyCoachTaskEntity> tasks = taskRepository.findAllBySessionIdOrderByTaskOrderAsc(session.getId());
        session.setBriefingText(briefingBuilder.buildBriefing(
                session.getStatus(),
                session.getPreferredMode(),
                session.getBudgetMinutes(),
                session.isColdStart(),
                session.isDownshifted(),
                tasks,
                "tr"
        ));

        DailyCoachSessionEntity saved = sessionRepository.save(session);
        List<MistakeSignal> traps = queryActiveTraps(userId);
        return toSummaryResponse(saved, tasks, traps);
    }

    @Transactional
    public DailyCoachSummaryResponse completeTask(
            UUID userId,
            UUID sessionId,
            DailyCoachTaskCompleteRequest request
    ) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        DailyCoachSessionEntity session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new DailyCoachSessionNotFoundException(sessionId));

        List<DailyCoachTaskEntity> tasks = taskRepository.findAllBySessionIdOrderByTaskOrderAsc(sessionId);
        if (tierGuardsEnabled()) requireAccess(userId, LearningOsCapability.DAILY_COACH_MISSION);
        DailyCoachTaskEntity target = tasks.stream()
                .filter(t -> t.getId().equals(request.taskId()))
                .findFirst()
                .orElseThrow(() -> new DailyCoachSessionNotFoundException(sessionId, request.taskId()));

        Instant now = clock.instant();
        if (target.getStatus() == DailyCoachTaskStatus.PENDING) {
            target.setStatus(DailyCoachTaskStatus.COMPLETED);
            target.setCompletedAt(now);
            taskRepository.save(target);
        }

        int completedCount = (int) tasks.stream()
                .filter(t -> t.getStatus() == DailyCoachTaskStatus.COMPLETED)
                .count();
        boolean allActiveDone = tasks.stream()
                .noneMatch(t -> t.getStatus() == DailyCoachTaskStatus.PENDING);

        session.setCompletedTasksCount(Math.min(completedCount, session.getTotalTasksCount()));
        if (allActiveDone) {
            session.setStatus(DailyCoachSessionStatus.COMPLETED);
        }
        session.setUpdatedAt(now);
        session.setBriefingText(briefingBuilder.buildBriefing(
                session.getStatus(),
                session.getPreferredMode(),
                session.getBudgetMinutes(),
                session.isColdStart(),
                session.isDownshifted(),
                tasks,
                "tr"
        ));

        DailyCoachSessionEntity saved = sessionRepository.save(session);
        cancelDailyReminderIfCompleted(userId, saved);
        List<MistakeSignal> traps = queryActiveTraps(userId);
        return toSummaryResponse(saved, tasks, traps);
    }

    @Transactional
    public DailyCoachSummaryResponse downshiftMission(UUID userId, UUID sessionId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(sessionId, "sessionId cannot be null");

        DailyCoachSessionEntity session = sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new DailyCoachSessionNotFoundException(sessionId));

        if (tierGuardsEnabled()) requireAccess(userId, LearningOsCapability.DAILY_COACH_MISSION);

        List<DailyCoachTaskEntity> tasks = taskRepository.findAllBySessionIdOrderByTaskOrderAsc(sessionId);
        Instant now = clock.instant();

        List<DailyCoachTaskEntity> modifiedTasks = new ArrayList<>();
        boolean taskOneCompleted = false;

        for (DailyCoachTaskEntity task : tasks) {
            if (task.getTaskOrder() == 1) {
                taskOneCompleted = task.getStatus() == DailyCoachTaskStatus.COMPLETED;
            } else if (task.getStatus() == DailyCoachTaskStatus.PENDING) {
                task.setStatus(DailyCoachTaskStatus.SKIPPED);
                modifiedTasks.add(task);
            }
        }
        if (!modifiedTasks.isEmpty()) {
            taskRepository.saveAll(modifiedTasks);
        }

        session.setBudgetMinutes(5);
        session.setDownshifted(true);
        session.setTotalTasksCount(1);
        session.setCompletedTasksCount(taskOneCompleted ? 1 : 0);
        if (taskOneCompleted) {
            session.setStatus(DailyCoachSessionStatus.COMPLETED);
        } else {
            session.setStatus(DailyCoachSessionStatus.IN_PROGRESS);
        }
        session.setUpdatedAt(now);
        session.setBriefingText(briefingBuilder.buildBriefing(
                session.getStatus(),
                session.getPreferredMode(),
                5,
                session.isColdStart(),
                true,
                tasks,
                "tr"
        ));

        DailyCoachSessionEntity saved = sessionRepository.save(session);
        cancelDailyReminderIfCompleted(userId, saved);
        List<MistakeSignal> traps = queryActiveTraps(userId);
        return toSummaryResponse(saved, tasks, traps);
    }

    private void cancelDailyReminderIfCompleted(UUID userId, DailyCoachSessionEntity saved) {
        if (saved.getStatus() == DailyCoachSessionStatus.COMPLETED && lifecycleMessagingService != null) {
            try {
                lifecycleMessagingService.cancelDailyMissionReminderIfCompleted(userId, saved.getLocalDate());
            } catch (RuntimeException ignored) {
            }
        }
    }

    private DailyCoachSummaryResponse createDeterministicMission(UUID userId, LocalDate effectiveDate) {
        List<MistakeSignal> traps = queryActiveTraps(userId);
        List<SpacedSignal> spaced = querySpacedQueue(userId);
        List<RecommendationSignal> recs = queryRecommendations(userId);

        boolean isColdStart = traps.isEmpty() && spaced.isEmpty() && recs.isEmpty();

        DailyCoachMode preferredMode = sessionRepository.findFirstByUserIdOrderByLocalDateDesc(userId)
                .map(DailyCoachSessionEntity::getPreferredMode)
                .orElse(DailyCoachMode.SOCRATIC_COACH_MODE);

        UUID sessionId = UUID.randomUUID();
        Instant now = clock.instant();

        List<DailyCoachTaskEntity> tasks = buildDeterministicTasks(
                sessionId, userId, effectiveDate, traps, spaced, recs, isColdStart
        );

        String briefing = briefingBuilder.buildBriefing(
                DailyCoachSessionStatus.IN_PROGRESS,
                preferredMode,
                15,
                isColdStart,
                false,
                tasks,
                "tr"
        );

        DailyCoachSessionEntity session = new DailyCoachSessionEntity(
                sessionId,
                userId,
                effectiveDate,
                15,
                DailyCoachSessionStatus.IN_PROGRESS,
                false,
                isColdStart,
                0,
                tasks.size(),
                briefing,
                preferredMode,
                now
        );

        try {
            DailyCoachSessionEntity savedSession = sessionRepository.save(session);
            List<DailyCoachTaskEntity> savedTasks = taskRepository.saveAll(tasks);
            return toSummaryResponse(savedSession, savedTasks, traps);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent daily creation guard (INV-COACH-004)
            DailyCoachSessionEntity concurrent = sessionRepository.findByUserIdAndLocalDate(userId, effectiveDate)
                    .orElseThrow(() -> ex);
            List<DailyCoachTaskEntity> concurrentTasks = taskRepository.findAllBySessionIdOrderByTaskOrderAsc(concurrent.getId());
            return toSummaryResponse(concurrent, concurrentTasks, traps);
        }
    }

    private DailyCoachSessionEntity createSessionAndTasksEntity(UUID userId, LocalDate effectiveDate) {
        DailyCoachSummaryResponse created = createDeterministicMission(userId, effectiveDate);
        return sessionRepository.findByIdAndUserId(created.sessionId(), userId)
                .orElseThrow(() -> new DailyCoachSessionNotFoundException(created.sessionId()));
    }

    private List<DailyCoachTaskEntity> buildDeterministicTasks(
            UUID sessionId,
            UUID userId,
            LocalDate localDate,
            List<MistakeSignal> traps,
            List<SpacedSignal> spaced,
            List<RecommendationSignal> recs,
            boolean isColdStart
    ) {
        List<DailyCoachTaskEntity> tasks = new ArrayList<>(3);

        // Block 1: TRAP_REPAIR (5 min)
        String skill1 = !traps.isEmpty() ? traps.get(0).skillCode() : "MATH.ALG.RATIONAL_EQ";
        String mistake1 = !traps.isEmpty() ? traps.get(0).mistakeCode() : "ALG_DOMAIN_EXCLUSION_MISSED";
        String rationale1 = isColdStart
                ? "growth_opportunity: Başlangıç tanı ikizi ile payda tanım kümesi kontrolünü doğruluyoruz."
                : "growth_opportunity: Aktif tuzak örüntüsünü bağımsız ikiz doğrulamasıyla güçlendiriyoruz.";
        tasks.add(new DailyCoachTaskEntity(
                UUID.randomUUID(),
                sessionId,
                1,
                DailyCoachTaskType.TRAP_REPAIR,
                skill1,
                mistake1,
                deterministicTwinId(userId, localDate, 1, skill1, mistake1),
                5,
                rationale1,
                deterministicPreviewLatex(skill1, mistake1, 1),
                DailyCoachTaskStatus.PENDING
        ));

        // Block 2: SPACED_RETENTION (5 min)
        String skill2 = !spaced.isEmpty() ? spaced.get(0).skillCode() : "MATH.ALG.QUADRATIC_DISCRIMINANT";
        String mistake2 = !spaced.isEmpty() ? spaced.get(0).mistakeCode() : "TRAP_SIGN_DISTRIB_NEG";
        String preview2 = !spaced.isEmpty() && spaced.get(0).promptPreviewLatex() != null && !spaced.get(0).promptPreviewLatex().isBlank()
                ? spaced.get(0).promptPreviewLatex()
                : deterministicPreviewLatex(skill2, mistake2, 2);
        String rationale2 = (!spaced.isEmpty() && "TRAP_IMMUNITY_7D".equals(spaced.get(0).triggerType()))
                ? "immunity_check: 7 günlük kalıcılık kontrolü ile çözüm bağışıklığını doğruluyoruz."
                : "retention_refresh: Yarı-ömür hatırlama eşiği yaklaşan beceriyi tazeliyoruz.";
        tasks.add(new DailyCoachTaskEntity(
                UUID.randomUUID(),
                sessionId,
                2,
                DailyCoachTaskType.SPACED_RETENTION,
                skill2,
                mistake2,
                deterministicTwinId(userId, localDate, 2, skill2, mistake2),
                5,
                rationale2,
                preview2,
                DailyCoachTaskStatus.PENDING
        ));

        // Block 3: EXAM_SYNTHESIS (5 min)
        RecommendationSignal selectedRec = recs.stream()
                .filter(r -> !r.skillCode().equals(skill1) && !r.skillCode().equals(skill2))
                .findFirst()
                .orElse(recs.isEmpty() ? null : recs.get(0));
        String skill3 = selectedRec != null ? selectedRec.skillCode() : "MATH.CALC.CHAIN_RULE_COMPOSITE";
        String mistake3 = selectedRec != null ? selectedRec.mistakeCode() : "CALC_CHAIN_RULE_OMITTED";
        String rationale3 = isColdStart
                ? "growth_opportunity: Yüksek AYT getirili bileşke fonksiyon senteziyle temel kurulum yapıyoruz."
                : "retention_refresh: Yüksek sınav getirili konuda adım doğrulama hızını artırıyoruz.";
        tasks.add(new DailyCoachTaskEntity(
                UUID.randomUUID(),
                sessionId,
                3,
                DailyCoachTaskType.EXAM_SYNTHESIS,
                skill3,
                mistake3,
                deterministicTwinId(userId, localDate, 3, skill3, mistake3),
                5,
                rationale3,
                deterministicPreviewLatex(skill3, mistake3, 3),
                DailyCoachTaskStatus.PENDING
        ));

        return tasks;
    }

    private DailyCoachSummaryResponse toSummaryResponse(
            DailyCoachSessionEntity session,
            List<DailyCoachTaskEntity> tasks,
            List<MistakeSignal> traps
    ) {
        List<DailyCoachTaskDto> taskDtos = tasks.stream()
                .map(t -> new DailyCoachTaskDto(
                        t.getId(),
                        t.getTaskOrder(),
                        t.getTaskType(),
                        t.getTargetSkillCode(),
                        t.getTargetMistakeCode(),
                        t.getAssignedTwinId(),
                        t.getEstimatedMinutes(),
                        t.getWhyThisTaskRationale(),
                        t.getPromptPreviewLatex(),
                        t.getStatus(),
                        t.getCompletedAt()
                ))
                .toList();

        List<TrapRadarPreviewDto> radar = buildTrapRadar(traps, tasks);
        int streakDays = computeStreakDays(session.getUserId());

        ColdStartDemoCardDto coldStartDemo = session.isColdStart()
                ? new ColdStartDemoCardDto(
                        "Örnek Adım Kontrolü: Eşitsizlik Yön Analizi",
                        "-2(x - 3) > 8",
                        "x - 3 < -4",
                        "x - 3 > -4",
                        "Her iki tarafı negatif bir sayıya bölerken eşitsizlik yönü tersine döner."
                )
                : null;

        return new DailyCoachSummaryResponse(
                session.getId(),
                session.getUserId(),
                session.getLocalDate(),
                session.getBudgetMinutes(),
                session.getStatus(),
                session.isDownshifted(),
                session.isColdStart(),
                session.getCompletedTasksCount(),
                session.getTotalTasksCount(),
                session.getBriefingText(),
                session.getPreferredMode(),
                streakDays,
                taskDtos,
                radar,
                coldStartDemo
        );
    }

    private List<TrapRadarPreviewDto> buildTrapRadar(List<MistakeSignal> traps, List<DailyCoachTaskEntity> tasks) {
        List<TrapRadarPreviewDto> radar = new ArrayList<>(3);
        for (MistakeSignal trap : traps) {
            if (radar.size() >= 3) break;
            String badge = "RESOLVED_PENDING_RETENTION".equals(trap.recurrenceState())
                    ? "immunity_check"
                    : "growth_opportunity";
            String statusLabel = "RESOLVED_PENDING_RETENTION".equals(trap.recurrenceState())
                    ? "strengthening"
                    : "active_trap_pattern";
            radar.add(new TrapRadarPreviewDto(
                    trap.skillCode(),
                    trap.mistakeCode(),
                    readableTrapTitle(trap.mistakeCode()),
                    badge,
                    statusLabel
            ));
        }
        if (radar.isEmpty()) {
            for (DailyCoachTaskEntity task : tasks) {
                if (radar.size() >= 2) break;
                if (task.getTargetMistakeCode() != null) {
                    String badge = task.getTaskType() == DailyCoachTaskType.SPACED_RETENTION
                            ? "retention_refresh"
                            : "growth_opportunity";
                    radar.add(new TrapRadarPreviewDto(
                            task.getTargetSkillCode(),
                            task.getTargetMistakeCode(),
                            readableTrapTitle(task.getTargetMistakeCode()),
                            badge,
                            "emerging"
                    ));
                }
            }
        }
        return radar;
    }

    private int computeStreakDays(UUID userId) {
        try {
            List<DailyCoachSessionEntity> history = sessionRepository.findAllByUserIdOrderByLocalDateDesc(userId);
            int streak = 0;
            LocalDate expectedDate = null;
            for (DailyCoachSessionEntity entry : history) {
                if (entry.getStatus() != DailyCoachSessionStatus.COMPLETED) {
                    if (streak == 0 && entry.getLocalDate().equals(LocalDate.now(clock))) {
                        continue;
                    }
                    break;
                }
                if (expectedDate == null) {
                    streak++;
                    expectedDate = entry.getLocalDate().minusDays(1);
                } else if (entry.getLocalDate().equals(expectedDate)) {
                    streak++;
                    expectedDate = expectedDate.minusDays(1);
                } else {
                    break;
                }
            }
            return streak;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private List<MistakeSignal> queryActiveTraps(UUID userId) {
        try {
            return jdbcClient.sql("""
                    SELECT skill_id, mistake_code, recurrence_state
                    FROM learner_mistake_dna_profiles
                    WHERE user_id = :userId
                      AND recurrence_state IN ('RECURRING_PATTERN', 'IN_REMEDIATION', 'RELAPSED', 'OBSERVED_ONCE')
                    ORDER BY smoothed_propensity DESC
                    LIMIT 3
                    """)
                    .param("userId", userId)
                    .query((rs, rowNum) -> new MistakeSignal(
                            rs.getString("skill_id"),
                            rs.getString("mistake_code"),
                            rs.getString("recurrence_state")
                    ))
                    .list();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<SpacedSignal> querySpacedQueue(UUID userId) {
        try {
            return jdbcClient.sql("""
                    SELECT canonical_skill_code, target_mistake_code, trigger_type, prompt_preview_latex
                    FROM spaced_review_queue
                    WHERE user_id = :userId
                      AND status IN ('PENDING', 'SERVED')
                    ORDER BY due_at ASC
                    LIMIT 2
                    """)
                    .param("userId", userId)
                    .query((rs, rowNum) -> new SpacedSignal(
                            rs.getString("canonical_skill_code"),
                            rs.getString("target_mistake_code"),
                            rs.getString("trigger_type"),
                            rs.getString("prompt_preview_latex")
                    ))
                    .list();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<RecommendationSignal> queryRecommendations(UUID userId) {
        try {
            return jdbcClient.sql("""
                    SELECT target_skill_code, target_mistake_code, rationale_code
                    FROM learner_recommendations
                    WHERE user_id = :userId
                      AND status = 'ACTIVE'
                    ORDER BY priority_score DESC
                    LIMIT 3
                    """)
                    .param("userId", userId)
                    .query((rs, rowNum) -> new RecommendationSignal(
                            rs.getString("target_skill_code"),
                            rs.getString("target_mistake_code"),
                            rs.getString("rationale_code")
                    ))
                    .list();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static String deterministicTwinId(
            UUID userId,
            LocalDate localDate,
            int taskOrder,
            String skillCode,
            String mistakeCode
    ) {
        String raw = userId + ":" + localDate + ":" + taskOrder + ":" + skillCode + ":"
                + (mistakeCode != null ? mistakeCode : "NONE");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(12);
            for (int i = 0; i < 6; i++) {
                hex.append(String.format("%02x", hash[i]));
            }
            return "twin_84_" + hex;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String deterministicPreviewLatex(String skillCode, String mistakeCode, int taskOrder) {
        String combined = ((skillCode != null ? skillCode : "") + " " + (mistakeCode != null ? mistakeCode : "")).toUpperCase();
        if (combined.contains("RATIONAL") || combined.contains("DOMAIN") || combined.contains("DENOMINATOR")) {
            return "\\frac{x^2 - 4}{x - 2} = x + 2, \\quad x \\neq 2";
        }
        if (combined.contains("INEQ")) {
            return "-3(x - 2) > 9 \\implies x - 2 < -3";
        }
        if (combined.contains("DISCRIMINANT") || combined.contains("QUADRATIC") || combined.contains("SIGN")) {
            return "2x^2 - 5x - 3 = 0 \\implies \\Delta = (-5)^2 - 4(2)(-3)";
        }
        if (combined.contains("CHAIN") || combined.contains("CALC") || taskOrder == 3) {
            return "\\frac{d}{dx}\\left[(3x^2 + 1)^4\\right] = 24x(3x^2 + 1)^3";
        }
        return "\\frac{x^2 - 9}{x - 3} = x + 3, \\quad x \\neq 3";
    }

    private static String readableTrapTitle(String mistakeCode) {
        if (mistakeCode == null) {
            return "Odak Beceri İkizi";
        }
        return switch (mistakeCode) {
            case "ALG_DOMAIN_EXCLUSION_MISSED", "TRAP_DOMAIN_EXCLUSION" -> "Payda Tanım Kümesi Kısıtı";
            case "INEQ_NEGATIVE_MULT_NO_FLIP", "TRAP_INEQUALITY_SIGN_FLIP" -> "Negatif Bölen Eşitsizlik Yönü";
            case "TRAP_SIGN_DISTRIB_NEG" -> "Diskriminant İşaret Dağılımı";
            case "CALC_CHAIN_RULE_OMITTED", "TRAP_CHAIN_RULE_INNER" -> "Bileşke Fonksiyon İç Türevi";
            case "ALG_SQUARE_BOTH_SIDES_EXTRANEOUS", "TRAP_EXTRANEOUS_ROOT" -> "Köklü Denklem Tanım Kontrolü";
            default -> mistakeCode.replace('_', ' ');
        };
    }
}
