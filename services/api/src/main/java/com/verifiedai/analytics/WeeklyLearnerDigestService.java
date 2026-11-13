package com.verifiedai.analytics;

import com.verifiedai.analytics.WeeklyLearnerDigestRepository.AttemptEventRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.LearnerContextProjection;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.LedgerDayRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.MasteryTransitionRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.MistakeDnaProfileRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.MistakeWindowCountRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.SkillMasteryRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.SpacedReviewRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.StudySessionRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.TwinCompletionRow;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.WeeklyEvidenceProjection;
import com.verifiedai.analytics.WeeklyReportDtos.DailyActivityBucketDto;
import com.verifiedai.analytics.WeeklyReportDtos.ExecutiveSummaryDto;
import com.verifiedai.analytics.WeeklyReportDtos.ExplainableRecommendationItemDto;
import com.verifiedai.analytics.WeeklyReportDtos.MasteryMomentumDto;
import com.verifiedai.analytics.WeeklyReportDtos.PrivacyAttestationDto;
import com.verifiedai.analytics.WeeklyReportDtos.RecommendationExplanationDto;
import com.verifiedai.analytics.WeeklyReportDtos.RecommendationFactorBreakdownDto;
import com.verifiedai.analytics.WeeklyReportDtos.ShareExecutiveMetricsDto;
import com.verifiedai.analytics.WeeklyReportDtos.ShareFocusThemeItemDto;
import com.verifiedai.analytics.WeeklyReportDtos.ShareMasterySnapshotItemDto;
import com.verifiedai.analytics.WeeklyReportDtos.ShareRecommendedActionItemDto;
import com.verifiedai.analytics.WeeklyReportDtos.SkillMomentumItemDto;
import com.verifiedai.analytics.WeeklyReportDtos.WeeklyDigestDto;
import com.verifiedai.analytics.WeeklyReportDtos.WeeklyRecurringMistakeItemDto;
import com.verifiedai.analytics.WeeklyReportDtos.WeeklySharePacketDto;
import com.verifiedai.analytics.WeeklyReportDtos.WeeklySharePacketRequest;
import com.verifiedai.analytics.WeeklyReportDtos.WeeklyWinItemDto;
import com.verifiedai.curriculum.api.CurriculumDagSnapshot;
import com.verifiedai.curriculum.api.CurriculumGraphQueryPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes and caches deterministic, privacy-safe weekly learner digests, explainable
 * next-step recommendations, and G-PRIV-03 share packets with zero LLM invocations ($0.00 spend).
 */
@Service
public class WeeklyLearnerDigestService {

    private static final Logger LOG = LoggerFactory.getLogger(WeeklyLearnerDigestService.class);
    private static final Pattern ISO_WEEK_PATTERN = Pattern.compile("^([0-9]{4})-W(0[1-9]|[1-4][0-9]|5[0-3])$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[^\\s@]+@[^\\s@]+\\.[^\\s@]+");
    private static final Pattern DIGITS_SEQ_PATTERN = Pattern.compile("\\d{4,}");
    private static final String CURRICULUM_CODE = "AYT_MAT_2026";
    private static final List<String> FALLBACK_STARTER_SKILLS = List.of(
        "MATH.ARITHMETIC.ORDER_OF_OPERATIONS",
        "ALG_LIN_ISO_01"
    );

    private static final double WEIGHT_MASTERY_GAP = 0.40;
    private static final double WEIGHT_MISTAKE = 0.25;
    private static final double WEIGHT_RETENTION = 0.20;
    private static final double WEIGHT_PREREQ = 0.15;

    private final WeeklyLearnerDigestRepository repository;
    private final CurriculumGraphQueryPort curriculumGraphQueryPort;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final boolean weeklyDigestEnabled;

    @Autowired
    public WeeklyLearnerDigestService(
        WeeklyLearnerDigestRepository repository,
        CurriculumGraphQueryPort curriculumGraphQueryPort,
        MeterRegistry meterRegistry,
        Clock clock,
        @Value("${verifiedai.analytics.weekly-digest-enabled:true}") boolean weeklyDigestEnabled
    ) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.curriculumGraphQueryPort = curriculumGraphQueryPort;
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
        this.weeklyDigestEnabled = weeklyDigestEnabled;
    }

    @Transactional
    public WeeklyDigestDto getWeeklyDigest(
        UUID authenticatedUserId,
        String rawWeekIso,
        String requestedLocale,
        boolean forceRefresh
    ) {
        ensureFeatureEnabled();
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId cannot be null");

        LearnerContextProjection context = repository.readLearnerContext(authenticatedUserId);
        ZoneId learnerZone = resolveZone(context.timezone());
        IsoWeekWindow weekWindow = parseAndValidateWeek(rawWeekIso, learnerZone);
        String locale = normalizeLocale(requestedLocale != null ? requestedLocale : context.preferredLocale());

        Optional<WeeklyLearnerDigest> cached = repository.findByUserAndWeek(authenticatedUserId, weekWindow.weekIso());
        if (!forceRefresh && cached.isPresent() && locale.equals(cached.get().locale())) {
            WeeklyDigestDto cachedDto = enforceNonTrainingFlag(cached.get().payload());
            recordViewedMetric(cachedDto.status(), locale, true);
            LOG.info("Weekly digest served from cache weekIso={} status={} locale={}",
                weekWindow.weekIso(), cachedDto.status(), locale);
            return cachedDto;
        }

        Instant weekStartTs = weekWindow.startDate().atStartOfDay(learnerZone).toInstant();
        Instant weekEndTs = weekWindow.endDate().plusDays(1).atStartOfDay(learnerZone).toInstant();
        Instant prevWeekStartTs = weekWindow.startDate().minusWeeks(1).atStartOfDay(learnerZone).toInstant();

        WeeklyEvidenceProjection evidence = repository.readWeeklyEvidence(
            authenticatedUserId,
            weekWindow.startDate(),
            weekWindow.endDate(),
            weekStartTs,
            weekEndTs,
            prevWeekStartTs
        );

        UUID digestId = cached.map(WeeklyLearnerDigest::digestId)
            .orElseGet(() -> deterministicUuid("weekly-digest", authenticatedUserId, weekWindow.weekIso()));
        Instant generatedAt = clock.instant();

        WeeklyDigestDto computed = computeDigestPayload(
            digestId,
            authenticatedUserId,
            weekWindow,
            locale,
            learnerZone,
            context,
            evidence,
            generatedAt
        );

        WeeklyLearnerDigest aggregate = new WeeklyLearnerDigest(
            computed.digestId(),
            authenticatedUserId,
            computed.weekIso(),
            computed.weekStartDate(),
            computed.weekEndDate(),
            computed.locale(),
            computed.status(),
            computed.executiveSummary().solvedAttemptsCount(),
            computed.executiveSummary().independentTwinsSolvedCount(),
            computed.executiveSummary().studyMinutes(),
            computed.executiveSummary().activeDaysCount(),
            computed,
            computed.recommendations(),
            false,
            generatedAt,
            generatedAt
        );
        repository.upsertDigest(aggregate);

        recordViewedMetric(computed.status(), locale, false);
        LOG.info("Weekly digest computed weekIso={} status={} locale={}",
            weekWindow.weekIso(), computed.status(), locale);
        return computed;
    }

    @Transactional(readOnly = true)
    public WeeklySharePacketDto createSharePacket(
        UUID authenticatedUserId,
        String rawWeekIso,
        WeeklySharePacketRequest request
    ) {
        ensureFeatureEnabled();
        Objects.requireNonNull(authenticatedUserId, "authenticatedUserId cannot be null");

        LearnerContextProjection context = repository.readLearnerContext(authenticatedUserId);
        ZoneId learnerZone = resolveZone(context.timezone());
        IsoWeekWindow weekWindow = parseAndValidateWeek(rawWeekIso, learnerZone);

        ShareRecipientRole recipientRole = validateSharePacketRequest(authenticatedUserId, request);

        WeeklyLearnerDigest digestRecord = repository.findByUserAndWeek(authenticatedUserId, weekWindow.weekIso())
            .orElseThrow(() -> WeeklyReportExceptions.reportNotFound(weekWindow.weekIso()));
        if (!digestRecord.userId().equals(authenticatedUserId)) {
            throw WeeklyReportExceptions.sharePacketScopeViolation("Cross-learner digest access is forbidden.");
        }

        WeeklyDigestDto digest = digestRecord.payload();
        String locale = normalizeLocale(
            request != null && request.getLocale() != null ? request.getLocale() : digest.locale()
        );
        String safeAlias = sanitizeDisplayAlias(request != null ? request.getDisplayAlias() : null, locale);

        List<ShareMasterySnapshotItemDto> masterySnapshot = digest.masteryMomentum().topSkillDeltas().stream()
            .limit(6)
            .map(s -> new ShareMasterySnapshotItemDto(
                s.skillId(),
                s.skillTitle(),
                s.masteryScore(),
                s.masteryDelta()
            ))
            .toList();

        List<ShareFocusThemeItemDto> focusThemes = digest.recurringMistakes().stream()
            .limit(5)
            .map(m -> new ShareFocusThemeItemDto(
                m.mistakeCode(),
                m.label(),
                m.skillTitle(),
                m.trendDirection(),
                m.supportiveGuidance()
            ))
            .toList();

        List<ShareRecommendedActionItemDto> recommendedNextActions = digest.recommendations().stream()
            .limit(3)
            .map(r -> new ShareRecommendedActionItemDto(
                r.recommendationId(),
                r.targetSkillTitle(),
                r.reasonCode(),
                r.priorityScore(),
                r.estimatedMinutes(),
                r.headline(),
                r.explanation().summaryText(),
                r.evidenceRefs()
            ))
            .toList();

        ExecutiveSummaryDto summary = digest.executiveSummary();
        String scopeSummary = "tr".equals(locale)
            ? "Yalnızca haftalık beceri ustalığı yüzdeleri, özet hata temaları ve açıklanabilir önerileri içerir. Ham eğitmen sohbeti, öğrenci fotoğrafları ve doğrudan kimlik bilgileri hariçtir."
            : "Contains weekly skill mastery percentages, aggregate mistake themes, and explainable next-step targets only. Raw tutor turns, student photos, and direct identifiers are excluded.";

        PrivacyAttestationDto attestation = new PrivacyAttestationDto(
            "G-PRIV-03",
            true,
            false,
            false,
            false,
            false,
            scopeSummary
        );

        UUID packetId = deterministicUuid("weekly-share-packet", authenticatedUserId,
            weekWindow.weekIso() + ":" + recipientRole.name());

        Counter.builder("weekly_tutor_packet_exported")
            .description("Count of privacy-safe weekly tutor/parent share packets exported")
            .tag("recipientRole", recipientRole.name())
            .tag("locale", locale)
            .register(meterRegistry)
            .increment();

        LOG.info("Weekly share packet created weekIso={} role={} locale={}",
            weekWindow.weekIso(), recipientRole.name(), locale);

        return new WeeklySharePacketDto(
            "weekly-share-packet-v1",
            packetId,
            digest.digestId(),
            digest.weekIso(),
            digest.weekStartDate(),
            digest.weekEndDate(),
            locale,
            recipientRole,
            safeAlias,
            false,
            clock.instant(),
            new ShareExecutiveMetricsDto(
                summary.solvedAttemptsCount(),
                summary.independentTwinsSolvedCount(),
                summary.studyMinutes(),
                summary.activeDaysCount(),
                summary.independentShareRate()
            ),
            masterySnapshot,
            focusThemes,
            recommendedNextActions,
            attestation
        );
    }

    /**
     * Pure deterministic priority formula:
     * PriorityScore(c) = clamp[0,1](0.40*(1 - M_c) + 0.25*E_mistake + 0.20*D_retention + 0.15*P_prereq).
     */
    public static double computePriorityScore(
        double masteryScore,
        double mistakeExposure,
        double retentionDecayRisk,
        double prerequisitePressure
    ) {
        double raw = WEIGHT_MASTERY_GAP * clamp01(1.0 - clamp01(masteryScore))
            + WEIGHT_MISTAKE * clamp01(mistakeExposure)
            + WEIGHT_RETENTION * clamp01(retentionDecayRisk)
            + WEIGHT_PREREQ * clamp01(prerequisitePressure);
        return round4(clamp01(raw));
    }

    public IsoWeekWindow parseAndValidateWeek(String rawWeekIso, ZoneId learnerZone) {
        if (rawWeekIso == null || rawWeekIso.isBlank()) {
            throw WeeklyReportExceptions.invalidWeekIso(rawWeekIso);
        }
        Matcher matcher = ISO_WEEK_PATTERN.matcher(rawWeekIso.trim());
        if (!matcher.matches()) {
            throw WeeklyReportExceptions.invalidWeekIso(rawWeekIso);
        }
        int weekYear = Integer.parseInt(matcher.group(1));
        int weekNumber = Integer.parseInt(matcher.group(2));

        LocalDate weekStart;
        try {
            weekStart = LocalDate.of(weekYear, 1, 4)
                .with(WeekFields.ISO.weekOfWeekBasedYear(), weekNumber)
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        } catch (DateTimeException ex) {
            throw WeeklyReportExceptions.invalidWeekIso(rawWeekIso);
        }

        if (weekStart.get(IsoFields.WEEK_BASED_YEAR) != weekYear
            || weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) != weekNumber) {
            throw WeeklyReportExceptions.invalidWeekIso(rawWeekIso);
        }

        LocalDate weekEnd = weekStart.plusDays(6);
        LocalDate currentWeekMonday = LocalDate.now(clock.withZone(learnerZone))
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (weekStart.isAfter(currentWeekMonday)) {
            throw WeeklyReportExceptions.futureWeekNotAllowed(rawWeekIso.trim());
        }
        return new IsoWeekWindow(rawWeekIso.trim(), weekStart, weekEnd);
    }

    private WeeklyDigestDto computeDigestPayload(
        UUID digestId,
        UUID userId,
        IsoWeekWindow weekWindow,
        String locale,
        ZoneId learnerZone,
        LearnerContextProjection context,
        WeeklyEvidenceProjection evidence,
        Instant generatedAt
    ) {
        CurriculumDagSnapshot dag = resolveCurriculumDag();
        Map<String, String> skillTitles = dag != null ? dag.skillTitles() : Map.of();

        List<DailyActivityBucketDto> dailyBuckets = buildDailyBuckets(
            weekWindow.startDate(),
            locale,
            learnerZone,
            evidence
        );

        int activeDaysCount = (int) dailyBuckets.stream().filter(DailyActivityBucketDto::active).count();
        int solvedAttemptsCount = evidence.attempts().size();
        int independentTwinsSolvedCount = (int) evidence.twinCompletions().stream()
            .filter(t -> "SOLVED_UNASSISTED".equalsIgnoreCase(t.status()))
            .count();
        int totalSessionSeconds = evidence.studySessions().stream()
            .mapToInt(s -> Math.max(0, s.activeDurationSeconds()))
            .sum();
        int studyMinutes = Math.max(
            totalSessionSeconds > 0 ? Math.max(1, (int) Math.round(totalSessionSeconds / 60.0)) : 0,
            dailyBuckets.stream().mapToInt(DailyActivityBucketDto::studyMinutes).sum()
        );

        int totalEvidenceUnits = Math.max(solvedAttemptsCount, evidence.twinCompletions().size())
            + (int) evidence.twinCompletions().stream().filter(t -> !"SOLVED_UNASSISTED".equalsIgnoreCase(t.status())).count();
        double independentShareRate = computeIndependentShareRate(evidence, solvedAttemptsCount, independentTwinsSolvedCount, totalEvidenceUnits);

        boolean isEmptyWeek = solvedAttemptsCount == 0
            && evidence.twinCompletions().isEmpty()
            && studyMinutes == 0
            && activeDaysCount == 0
            && evidence.completedPlanItemsCount() == 0;
        WeeklyDigestStatus status = isEmptyWeek ? WeeklyDigestStatus.EMPTY_WEEK : WeeklyDigestStatus.GENERATED;

        List<WeeklyRecurringMistakeItemDto> recurringMistakes = buildRecurringMistakes(locale, evidence, skillTitles);
        MasteryMomentumDto masteryMomentum = buildMasteryMomentum(
            locale,
            dailyBuckets,
            evidence,
            skillTitles,
            generatedAt
        );
        List<ExplainableRecommendationItemDto> recommendations = buildRecommendations(
            isEmptyWeek,
            locale,
            evidence,
            dag,
            skillTitles
        );
        List<WeeklyWinItemDto> wins = buildWins(
            isEmptyWeek,
            locale,
            solvedAttemptsCount,
            independentTwinsSolvedCount,
            activeDaysCount,
            masteryMomentum,
            recurringMistakes,
            evidence
        );

        ExecutiveSummaryDto summary = buildExecutiveSummary(
            isEmptyWeek,
            locale,
            solvedAttemptsCount,
            independentTwinsSolvedCount,
            studyMinutes,
            activeDaysCount,
            independentShareRate,
            evidence.completedPlanItemsCount(),
            context.currentStreakDays()
        );

        return new WeeklyDigestDto(
            "weekly-digest-v1",
            digestId,
            userId,
            weekWindow.weekIso(),
            weekWindow.startDate(),
            weekWindow.endDate(),
            locale,
            status,
            false,
            generatedAt,
            summary,
            wins,
            recurringMistakes,
            masteryMomentum,
            recommendations
        );
    }

    private List<DailyActivityBucketDto> buildDailyBuckets(
        LocalDate weekStartDate,
        String locale,
        ZoneId learnerZone,
        WeeklyEvidenceProjection evidence
    ) {
        Map<LocalDate, LedgerDayRow> ledgerByDate = new HashMap<>();
        for (LedgerDayRow row : evidence.ledgerDays()) {
            ledgerByDate.put(row.activityLocalDate(), row);
        }
        Map<LocalDate, Integer> attemptsByDate = new HashMap<>();
        for (AttemptEventRow a : evidence.attempts()) {
            LocalDate d = a.createdAt().atZone(learnerZone).toLocalDate();
            attemptsByDate.merge(d, 1, Integer::sum);
        }
        Map<LocalDate, Integer> independentTwinsByDate = new HashMap<>();
        for (TwinCompletionRow t : evidence.twinCompletions()) {
            if ("SOLVED_UNASSISTED".equalsIgnoreCase(t.status())) {
                LocalDate d = t.completedAt().atZone(learnerZone).toLocalDate();
                independentTwinsByDate.merge(d, 1, Integer::sum);
            }
        }
        Map<LocalDate, Integer> minutesByDate = new HashMap<>();
        for (StudySessionRow s : evidence.studySessions()) {
            LocalDate d = s.activityTs().atZone(learnerZone).toLocalDate();
            int mins = Math.max(0, (int) Math.round(s.activeDurationSeconds() / 60.0));
            minutesByDate.merge(d, mins, Integer::sum);
        }

        List<DailyActivityBucketDto> buckets = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate date = weekStartDate.plusDays(i);
            LedgerDayRow ledger = ledgerByDate.get(date);
            int solved = attemptsByDate.getOrDefault(date, 0);
            int indep = Math.max(
                independentTwinsByDate.getOrDefault(date, 0),
                ledger != null ? ledger.independentActivityCount() : 0
            );
            int mins = minutesByDate.getOrDefault(date, 0);
            if (mins == 0 && (solved > 0 || indep > 0)) {
                mins = Math.max(solved * 5, indep * 6);
            }
            boolean active = mins > 0 || solved > 0 || indep > 0
                || (ledger != null && ("VERIFIED_INDEPENDENT".equals(ledger.dailyStatus())
                    || "CONTINUITY_PRESERVED".equals(ledger.dailyStatus())));
            buckets.add(new DailyActivityBucketDto(
                date,
                localizedDayLabel(date.getDayOfWeek(), locale),
                mins,
                solved,
                indep,
                active
            ));
        }
        return List.copyOf(buckets);
    }

    private double computeIndependentShareRate(
        WeeklyEvidenceProjection evidence,
        int solvedAttemptsCount,
        int independentTwinsSolvedCount,
        int totalEvidenceUnits
    ) {
        if (!evidence.skillMasteries().isEmpty()) {
            int indep = evidence.skillMasteries().stream().mapToInt(SkillMasteryRow::independentEvidenceCount).sum();
            int total = evidence.skillMasteries().stream()
                .mapToInt(m -> m.independentEvidenceCount() + m.assistedEvidenceCount())
                .sum();
            if (total > 0) {
                return round4(clamp01((double) indep / total));
            }
        }
        int denom = Math.max(1, Math.max(solvedAttemptsCount, totalEvidenceUnits));
        return solvedAttemptsCount == 0 && independentTwinsSolvedCount == 0
            ? 0.0
            : round4(clamp01((double) independentTwinsSolvedCount / denom));
    }

    private List<WeeklyRecurringMistakeItemDto> buildRecurringMistakes(
        String locale,
        WeeklyEvidenceProjection evidence,
        Map<String, String> skillTitles
    ) {
        Map<String, MistakeWindowCountRow> countsByKey = new HashMap<>();
        for (MistakeWindowCountRow c : evidence.mistakeWindowCounts()) {
            countsByKey.put(c.mistakeCode() + "|" + c.skillId(), c);
        }

        List<WeeklyRecurringMistakeItemDto> items = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (MistakeDnaProfileRow p : evidence.mistakeProfiles()) {
            String key = p.mistakeCode() + "|" + p.skillId();
            seen.add(key);
            MistakeWindowCountRow counts = countsByKey.get(key);
            int thisWeek = counts != null ? counts.thisWeekErrors() : Math.max(1, p.recentErrorCount());
            int prevWeek = counts != null ? counts.prevWeekErrors() : 0;
            if (thisWeek <= 0 && p.recentErrorCount() <= 0) {
                continue;
            }
            items.add(toMistakeItem(
                locale,
                p.mistakeCode(),
                p.skillId(),
                skillTitles.getOrDefault(p.skillId(), humanizeCode(p.skillId())),
                thisWeek,
                prevWeek,
                evidence.completedMicroLessonMistakeCodes().contains(p.mistakeCode())
            ));
        }

        for (MistakeWindowCountRow c : evidence.mistakeWindowCounts()) {
            String key = c.mistakeCode() + "|" + c.skillId();
            if (seen.contains(key) || c.thisWeekErrors() <= 0) {
                continue;
            }
            items.add(toMistakeItem(
                locale,
                c.mistakeCode(),
                c.skillId(),
                skillTitles.getOrDefault(c.skillId(), humanizeCode(c.skillId())),
                c.thisWeekErrors(),
                c.prevWeekErrors(),
                evidence.completedMicroLessonMistakeCodes().contains(c.mistakeCode())
            ));
        }

        return items.stream()
            .sorted(Comparator.comparingInt(WeeklyRecurringMistakeItemDto::occurrenceCountThisWeek).reversed()
                .thenComparing(WeeklyRecurringMistakeItemDto::mistakeCode))
            .limit(8)
            .toList();
    }

    private WeeklyRecurringMistakeItemDto toMistakeItem(
        String locale,
        String mistakeCode,
        String skillId,
        String skillTitle,
        int thisWeekCount,
        int prevWeekCount,
        boolean microLessonCompleted
    ) {
        String trend;
        if (prevWeekCount > 0 && thisWeekCount < prevWeekCount) {
            trend = "IMPROVING";
        } else if (thisWeekCount <= 1 || thisWeekCount == prevWeekCount) {
            trend = "STABLE";
        } else {
            trend = "NEEDS_GENTLE_FOCUS";
        }
        String label = localizedMistakeLabel(mistakeCode, locale);
        String guidance;
        if ("tr".equals(locale)) {
            guidance = microLessonCompleted
                ? "Mikro ders tamamlandı; bir sonraki adımda bu kontrolü bağımsız bir ikiz soruda uygulayın."
                : "Bir sonraki çözümde adımı kısa bir doğrulama kontrolü ile yavaşça gözden geçirmek kalıcılığı artırır.";
        } else {
            guidance = microLessonCompleted
                ? "Micro-lesson completed; try applying this check calmly on one independent twin problem."
                : "Pausing for one quick verification check on the transition step makes this pattern click.";
        }
        List<String> refs = new ArrayList<>();
        refs.add("mistake_dna:" + mistakeCode);
        if (microLessonCompleted) {
            refs.add("micro_lesson:" + mistakeCode);
        }
        return new WeeklyRecurringMistakeItemDto(
            mistakeCode,
            label,
            skillId,
            skillTitle,
            thisWeekCount,
            prevWeekCount,
            trend,
            microLessonCompleted,
            guidance,
            List.copyOf(refs)
        );
    }

    private MasteryMomentumDto buildMasteryMomentum(
        String locale,
        List<DailyActivityBucketDto> dailyBuckets,
        WeeklyEvidenceProjection evidence,
        Map<String, String> skillTitles,
        Instant asOf
    ) {
        Map<String, Double> earliestPriorBySkill = new HashMap<>();
        Map<String, Double> latestPosteriorBySkill = new HashMap<>();
        for (MasteryTransitionRow tr : evidence.masteryTransitions()) {
            earliestPriorBySkill.putIfAbsent(tr.skillId(), tr.priorScore());
            latestPosteriorBySkill.put(tr.skillId(), tr.posteriorScore());
        }

        Map<String, Double> retentionBySkill = new HashMap<>();
        int dueReviewsCount = 0;
        for (SpacedReviewRow sr : evidence.spacedReviews()) {
            retentionBySkill.merge(sr.skillId(), sr.estimatedRetention(), Math::min);
            if ("PENDING".equalsIgnoreCase(sr.status()) && !sr.dueAt().isAfter(asOf)) {
                dueReviewsCount++;
            }
        }

        List<SkillMomentumItemDto> skillDeltas = new ArrayList<>();
        double masterySum = 0.0;
        double retentionSum = 0.0;
        int improvedCount = 0;

        for (SkillMasteryRow row : evidence.skillMasteries()) {
            double mastery = clamp01(row.score());
            double retention = retentionBySkill.getOrDefault(row.skillId(), clamp01(Math.min(0.98, 0.55 + mastery * 0.40)));
            double delta = 0.0;
            if (earliestPriorBySkill.containsKey(row.skillId())) {
                delta = round4(latestPosteriorBySkill.get(row.skillId()) - earliestPriorBySkill.get(row.skillId()));
            }
            if (delta > 0.0) {
                improvedCount++;
            }
            masterySum += mastery;
            retentionSum += retention;
            skillDeltas.add(new SkillMomentumItemDto(
                row.skillId(),
                skillTitles.getOrDefault(row.skillId(), humanizeCode(row.skillId())),
                round4(mastery),
                round4(retention),
                delta,
                row.independentEvidenceCount()
            ));
        }

        int n = skillDeltas.size();
        double avgMastery = n > 0 ? round4(masterySum / n) : 0.0;
        double avgRetention = n > 0 ? round4(retentionSum / n) : 0.0;
        List<SkillMomentumItemDto> topDeltas = skillDeltas.stream()
            .sorted(Comparator.comparingDouble(SkillMomentumItemDto::masteryDelta).reversed()
                .thenComparing(SkillMomentumItemDto::skillId))
            .limit(6)
            .toList();

        return new MasteryMomentumDto(
            avgMastery,
            avgRetention,
            improvedCount,
            Math.max(n, latestPosteriorBySkill.size()),
            dueReviewsCount,
            dailyBuckets,
            topDeltas
        );
    }

    private List<ExplainableRecommendationItemDto> buildRecommendations(
        boolean isEmptyWeek,
        String locale,
        WeeklyEvidenceProjection evidence,
        CurriculumDagSnapshot dag,
        Map<String, String> skillTitles
    ) {
        Map<String, SkillMasteryRow> masteryBySkill = new HashMap<>();
        for (SkillMasteryRow m : evidence.skillMasteries()) {
            masteryBySkill.put(m.skillId(), m);
        }

        Map<String, Integer> mistakeCountBySkill = new HashMap<>();
        Map<String, Double> propensityBySkill = new HashMap<>();
        Map<String, List<String>> mistakeRefsBySkill = new HashMap<>();
        for (MistakeDnaProfileRow p : evidence.mistakeProfiles()) {
            mistakeCountBySkill.merge(p.skillId(), Math.max(1, p.recentErrorCount()), Integer::sum);
            propensityBySkill.merge(p.skillId(), p.smoothedPropensity(), Math::max);
            mistakeRefsBySkill.computeIfAbsent(p.skillId(), k -> new ArrayList<>()).add("mistake_dna:" + p.mistakeCode());
        }
        for (MistakeWindowCountRow c : evidence.mistakeWindowCounts()) {
            if (c.thisWeekErrors() > 0) {
                mistakeCountBySkill.merge(c.skillId(), c.thisWeekErrors(), Math::max);
                mistakeRefsBySkill.computeIfAbsent(c.skillId(), k -> new ArrayList<>()).add("mistake_dna:" + c.mistakeCode());
            }
        }

        Map<String, Double> retentionBySkill = new HashMap<>();
        Set<String> spacedReviewSkills = new HashSet<>();
        for (SpacedReviewRow s : evidence.spacedReviews()) {
            retentionBySkill.merge(s.skillId(), s.estimatedRetention(), Math::min);
            spacedReviewSkills.add(s.skillId());
        }

        Set<String> candidateSkillIds = new LinkedHashSet<>();
        candidateSkillIds.addAll(masteryBySkill.keySet());
        candidateSkillIds.addAll(mistakeCountBySkill.keySet());
        candidateSkillIds.addAll(spacedReviewSkills);

        if (candidateSkillIds.isEmpty()) {
            List<String> starters = dag != null && !dag.topologicallySortedSkillCodes().isEmpty()
                ? dag.topologicallySortedSkillCodes().stream().limit(2).toList()
                : FALLBACK_STARTER_SKILLS;
            List<ExplainableRecommendationItemDto> coldStart = new ArrayList<>();
            int idx = 1;
            for (String skillId : starters) {
                String title = skillTitles.getOrDefault(skillId, humanizeCode(skillId));
                double score = computePriorityScore(0.35, 0.0, 0.30, countPrereqDependents(skillId, dag, evidence) / 3.0);
                RecommendationFactorBreakdownDto breakdown = new RecommendationFactorBreakdownDto(
                    WEIGHT_MASTERY_GAP,
                    WEIGHT_MISTAKE,
                    WEIGHT_RETENTION,
                    WEIGHT_PREREQ,
                    0.35,
                    0,
                    0.70,
                    countPrereqDependents(skillId, dag, evidence)
                );
                String recId = "rec-0" + idx + "-starter";
                coldStart.add(new ExplainableRecommendationItemDto(
                    recId,
                    skillId,
                    title,
                    RecommendationReasonCode.COLD_START_ONBOARDING,
                    score,
                    10,
                    "CONCEPT_WARMUP",
                    localizedRecommendationHeadline(RecommendationReasonCode.COLD_START_ONBOARDING, title, locale),
                    new RecommendationExplanationDto(
                        localizedRecommendationSummary(RecommendationReasonCode.COLD_START_ONBOARDING, title, 0, 0, locale),
                        breakdown
                    ),
                    List.of("curriculum:" + skillId, "profile:cold_start")
                ));
                idx++;
            }
            return List.copyOf(coldStart);
        }

        record ScoredCandidate(
            String skillId,
            String skillTitle,
            RecommendationReasonCode reasonCode,
            double priorityScore,
            int estimatedMinutes,
            String actionType,
            RecommendationFactorBreakdownDto breakdown,
            int mistakeCount,
            int blockedDeps,
            List<String> evidenceRefs
        ) {}

        List<ScoredCandidate> scored = new ArrayList<>();
        for (String skillId : candidateSkillIds) {
            SkillMasteryRow masteryRow = masteryBySkill.get(skillId);
            double masteryScore = masteryRow != null ? clamp01(masteryRow.score()) : 0.35;
            double retentionScore = retentionBySkill.getOrDefault(
                skillId,
                masteryRow != null ? clamp01(0.55 + masteryScore * 0.40) : 0.70
            );
            int mistakeCount = mistakeCountBySkill.getOrDefault(skillId, 0);
            double propensity = propensityBySkill.getOrDefault(skillId, 0.0);
            double eMistake = clamp01(Math.max(propensity, Math.min(1.0, mistakeCount / 4.0)));
            double dRetention = clamp01(1.0 - retentionScore);
            int blockedDeps = countPrereqDependents(skillId, dag, evidence);
            double pPrereq = clamp01(blockedDeps / 3.0);

            double priority = computePriorityScore(masteryScore, eMistake, dRetention, pPrereq);

            RecommendationReasonCode reason = isEmptyWeek && masteryRow == null
                ? RecommendationReasonCode.COLD_START_ONBOARDING
                : dominantReasonCode(masteryScore, eMistake, dRetention, pPrereq, mistakeCount);

            String actionType = switch (reason) {
                case RECURRING_MISTAKE_PATTERN -> "TWIN_DRILL";
                case RETENTION_DECAY_RISK -> "SPACED_REVIEW";
                case PREREQUISITE_BLOCKER -> "MICRO_LESSON";
                case COLD_START_ONBOARDING -> "CONCEPT_WARMUP";
                case LOW_MASTERY_FOUNDATION -> "DIAGNOSTIC_CHECK";
            };
            int estimatedMinutes = switch (reason) {
                case RETENTION_DECAY_RISK -> 8;
                case COLD_START_ONBOARDING -> 10;
                case RECURRING_MISTAKE_PATTERN, PREREQUISITE_BLOCKER -> 12;
                case LOW_MASTERY_FOUNDATION -> 15;
            };

            List<String> refs = new ArrayList<>();
            refs.add(masteryRow != null ? "mastery:" + skillId : "curriculum:" + skillId);
            mistakeRefsBySkill.getOrDefault(skillId, List.of()).stream().distinct().limit(2).forEach(refs::add);
            if (spacedReviewSkills.contains(skillId)) {
                refs.add("spaced_review:" + skillId);
            }
            if (blockedDeps > 0 && refs.size() < 5) {
                refs.add("prereq:" + skillId);
            }
            List<String> cappedRefs = refs.stream().distinct().limit(5).toList();

            RecommendationFactorBreakdownDto breakdown = new RecommendationFactorBreakdownDto(
                WEIGHT_MASTERY_GAP,
                WEIGHT_MISTAKE,
                WEIGHT_RETENTION,
                WEIGHT_PREREQ,
                round4(masteryScore),
                mistakeCount,
                round4(retentionScore),
                blockedDeps
            );
            String title = skillTitles.getOrDefault(skillId, humanizeCode(skillId));
            scored.add(new ScoredCandidate(
                skillId,
                title,
                reason,
                priority,
                estimatedMinutes,
                actionType,
                breakdown,
                mistakeCount,
                blockedDeps,
                cappedRefs
            ));
        }

        List<ScoredCandidate> top = scored.stream()
            .sorted(Comparator.comparingDouble(ScoredCandidate::priorityScore).reversed()
                .thenComparing(ScoredCandidate::skillId))
            .limit(5)
            .toList();

        List<ExplainableRecommendationItemDto> results = new ArrayList<>(top.size());
        for (int i = 0; i < top.size(); i++) {
            ScoredCandidate c = top.get(i);
            results.add(new ExplainableRecommendationItemDto(
                String.format("rec-%02d-%s", i + 1, c.skillId().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")),
                c.skillId(),
                c.skillTitle(),
                c.reasonCode(),
                c.priorityScore(),
                c.estimatedMinutes(),
                c.actionType(),
                localizedRecommendationHeadline(c.reasonCode(), c.skillTitle(), locale),
                new RecommendationExplanationDto(
                    localizedRecommendationSummary(c.reasonCode(), c.skillTitle(), c.mistakeCount(), c.blockedDeps(), locale),
                    c.breakdown()
                ),
                c.evidenceRefs()
            ));
        }
        return List.copyOf(results);
    }

    private RecommendationReasonCode dominantReasonCode(
        double masteryScore,
        double eMistake,
        double dRetention,
        double pPrereq,
        int mistakeCount
    ) {
        double cMastery = WEIGHT_MASTERY_GAP * (1.0 - masteryScore);
        double cMistake = WEIGHT_MISTAKE * eMistake;
        double cRetention = WEIGHT_RETENTION * dRetention;
        double cPrereq = WEIGHT_PREREQ * pPrereq;

        if (mistakeCount > 0 && cMistake >= cMastery && cMistake >= cRetention) {
            return RecommendationReasonCode.RECURRING_MISTAKE_PATTERN;
        }
        if (cPrereq >= cMastery && cPrereq >= cRetention && cPrereq > 0.05) {
            return RecommendationReasonCode.PREREQUISITE_BLOCKER;
        }
        if (cRetention >= cMastery && dRetention >= 0.30) {
            return RecommendationReasonCode.RETENTION_DECAY_RISK;
        }
        return RecommendationReasonCode.LOW_MASTERY_FOUNDATION;
    }

    private int countPrereqDependents(
        String skillId,
        CurriculumDagSnapshot dag,
        WeeklyEvidenceProjection evidence
    ) {
        int dbCount = evidence.prerequisiteDependentCounts().getOrDefault(skillId, 0);
        int dagCount = 0;
        if (dag != null) {
            for (CurriculumDagSnapshot.Edge e : dag.edges()) {
                if (skillId.equals(e.fromSkillCode())) {
                    dagCount++;
                }
            }
        }
        return Math.max(dbCount, dagCount);
    }

    private List<WeeklyWinItemDto> buildWins(
        boolean isEmptyWeek,
        String locale,
        int solvedAttemptsCount,
        int independentTwinsSolvedCount,
        int activeDaysCount,
        MasteryMomentumDto momentum,
        List<WeeklyRecurringMistakeItemDto> mistakes,
        WeeklyEvidenceProjection evidence
    ) {
        List<WeeklyWinItemDto> wins = new ArrayList<>();
        if (isEmptyWeek) {
            wins.add(new WeeklyWinItemDto(
                "win-gentle-start",
                "GENTLE_START",
                "tr".equals(locale) ? "Yeni Hafta İçin Hazır Başlangıç" : "Fresh Canvas Ready for a Calm Start",
                "tr".equals(locale)
                    ? "Kısa bir 10 dakikalık ısınma adımı ile haftalık ivmenizi kolayca başlatabilirsiniz."
                    : "One calm 10-minute warmup problem is all it takes to open your week cleanly.",
                null,
                null,
                "weekly:empty_week"
            ));
            return wins;
        }

        if (independentTwinsSolvedCount > 0) {
            UUID twinRef = evidence.twinCompletions().stream()
                .filter(t -> "SOLVED_UNASSISTED".equalsIgnoreCase(t.status()))
                .map(TwinCompletionRow::twinId)
                .findFirst()
                .orElseGet(() -> UUID.nameUUIDFromBytes("twin-summary".getBytes(StandardCharsets.UTF_8)));
            wins.add(new WeeklyWinItemDto(
                "win-independent-twins",
                "INDEPENDENT_TRANSFER",
                "tr".equals(locale) ? "Bağımsız İkiz Transferi" : "Independent Twin Transfer",
                "tr".equals(locale)
                    ? independentTwinsSolvedCount + " ikiz problem ipucu olmadan doğrulandı."
                    : "Verified " + independentTwinsSolvedCount + " twin check(s) completely independently.",
                momentum.topSkillDeltas().isEmpty() ? null : momentum.topSkillDeltas().getFirst().skillId(),
                "+" + independentTwinsSolvedCount + ("tr".equals(locale) ? " doğrulanmış" : " verified"),
                "twin_attempt:" + twinRef
            ));
        }

        if (activeDaysCount >= 2) {
            wins.add(new WeeklyWinItemDto(
                "win-habit-continuity",
                "HABIT_CONTINUITY",
                "tr".equals(locale) ? "Düzenli Çalışma Ritmi" : "Steady Study Rhythm",
                "tr".equals(locale)
                    ? "Bu hafta " + activeDaysCount + " farklı günde öğrenme pratiği yaptınız."
                    : "Practiced across " + activeDaysCount + " days this week to keep concepts fresh.",
                null,
                activeDaysCount + "/7",
                "ledger:active_days_" + activeDaysCount
            ));
        }

        for (SkillMomentumItemDto delta : momentum.topSkillDeltas()) {
            if (delta.masteryDelta() > 0.01 && wins.size() < 4) {
                int pctGain = (int) Math.round(delta.masteryDelta() * 100.0);
                wins.add(new WeeklyWinItemDto(
                    "win-mastery-" + delta.skillId().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"),
                    "MASTERY_GAIN",
                    delta.skillTitle(),
                    "tr".equals(locale)
                        ? "Doğrulanan bağımsız adımlarla ustalık puanı düzenli yükseldi."
                        : "Mastery progressed smoothly through verified step checks.",
                    delta.skillId(),
                    "+" + pctGain + "%",
                    "mastery:" + delta.skillId()
                ));
            }
        }

        for (WeeklyRecurringMistakeItemDto m : mistakes) {
            if ("IMPROVING".equals(m.trendDirection()) && wins.size() < 5) {
                wins.add(new WeeklyWinItemDto(
                    "win-mistake-" + m.mistakeCode().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"),
                    "MISTAKE_RESOLVED",
                    m.label(),
                    "tr".equals(locale)
                        ? "Bu temadaki tekrar sayısı geçen haftaya göre azaldı."
                        : "Occurrences eased compared with last week as verification checks took hold.",
                    m.skillId(),
                    null,
                    "mistake_dna:" + m.mistakeCode()
                ));
            }
        }

        if (wins.isEmpty() && solvedAttemptsCount > 0) {
            wins.add(new WeeklyWinItemDto(
                "win-retention-effort",
                "RETENTION_CONFIRMED",
                "tr".equals(locale) ? "Çözüm Adımları Kaydedildi" : "Verified Solution Checkpoints",
                "tr".equals(locale)
                    ? solvedAttemptsCount + " deneme adım adım doğrulandı."
                    : "Reviewed and verified " + solvedAttemptsCount + " structured solution attempt(s).",
                null,
                String.valueOf(solvedAttemptsCount),
                "attempts:count_" + solvedAttemptsCount
            ));
        }

        return List.copyOf(wins);
    }

    private ExecutiveSummaryDto buildExecutiveSummary(
        boolean isEmptyWeek,
        String locale,
        int solvedAttemptsCount,
        int independentTwinsSolvedCount,
        int studyMinutes,
        int activeDaysCount,
        double independentShareRate,
        int completedPlanItemsCount,
        int currentStreakDays
    ) {
        String headline;
        String encouragement;
        if (isEmptyWeek) {
            headline = "tr".equals(locale)
                ? "Bu hafta henüz pratik kaydı yok — sakin bir ilk adımla başlayın"
                : "Quiet week so far — a calm 10-minute warmup opens your momentum";
            encouragement = "tr".equals(locale)
                ? "Hazır olduğunuzda temel beceri kartlarından biriyle kısa bir deneme çözebilirsiniz."
                : "Whenever you feel ready, pick one foundational warmup below to build confidence.";
        } else {
            headline = "tr".equals(locale)
                ? activeDaysCount + " günde istikrarlı ilerleme ve " + independentTwinsSolvedCount + " bağımsız doğrulama"
                : "Steady progress across " + activeDaysCount + " active days and " + independentTwinsSolvedCount + " independent checks";
            encouragement = "tr".equals(locale)
                ? "Toplam " + studyMinutes + " dakikalık odaklanmış pratik ve adım adım geri bildirimlerle becerileriniz güçleniyor."
                : "Focused practice across " + studyMinutes + " minutes is turning careful verification steps into durable mastery.";
        }
        return new ExecutiveSummaryDto(
            headline,
            encouragement,
            solvedAttemptsCount,
            independentTwinsSolvedCount,
            studyMinutes,
            activeDaysCount,
            independentShareRate,
            completedPlanItemsCount,
            currentStreakDays
        );
    }

    private CurriculumDagSnapshot resolveCurriculumDag() {
        if (curriculumGraphQueryPort == null) {
            return null;
        }
        return curriculumGraphQueryPort.findDagByCurriculumCode(CURRICULUM_CODE).orElse(null);
    }

    private ShareRecipientRole validateSharePacketRequest(
        UUID authenticatedUserId,
        WeeklySharePacketRequest request
    ) {
        if (request == null) {
            throw WeeklyReportExceptions.sharePacketScopeViolation("Share packet request body is required.");
        }
        if (request.getTargetUserId() != null && !request.getTargetUserId().equals(authenticatedUserId)) {
            throw WeeklyReportExceptions.sharePacketScopeViolation("Cross-learner share packet access is forbidden.");
        }
        if (Boolean.TRUE.equals(request.getIncludeRawTranscripts())
            || Boolean.TRUE.equals(request.getIncludeRawMedia())
            || Boolean.TRUE.equals(request.getIncludeExactTimestamps())) {
            throw WeeklyReportExceptions.sharePacketScopeViolation(
                "Raw tutor transcripts, raw student media, and exact session timestamps are excluded under G-PRIV-03."
            );
        }
        if (!request.getExtraFields().isEmpty()) {
            throw WeeklyReportExceptions.sharePacketScopeViolation(
                "Restricted or unknown share scope fields are not permitted: "
                    + String.join(",", request.getExtraFields().keySet())
            );
        }
        String rawRole = request.getRecipientRole();
        if (rawRole == null || rawRole.isBlank()) {
            throw WeeklyReportExceptions.sharePacketScopeViolation("Recipient role is required (TUTOR or PARENT).");
        }
        try {
            return ShareRecipientRole.valueOf(rawRole.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw WeeklyReportExceptions.sharePacketScopeViolation("Unsupported recipient role: " + rawRole);
        }
    }

    private String sanitizeDisplayAlias(String rawAlias, String locale) {
        String fallback = "tr".equals(locale) ? "Öğrenci" : "Learner";
        if (rawAlias == null || rawAlias.isBlank()) {
            return fallback;
        }
        String cleaned = EMAIL_PATTERN.matcher(rawAlias.trim()).replaceAll("");
        cleaned = DIGITS_SEQ_PATTERN.matcher(cleaned).replaceAll("").trim();
        if (cleaned.length() < 2) {
            return fallback;
        }
        return cleaned.length() > 40 ? cleaned.substring(0, 40).trim() : cleaned;
    }

    private void ensureFeatureEnabled() {
        if (!weeklyDigestEnabled) {
            throw WeeklyReportExceptions.featureDisabled();
        }
    }

    private void recordViewedMetric(WeeklyDigestStatus status, String locale, boolean cacheHit) {
        Counter.builder("weekly_report_viewed")
            .description("Count of weekly learning digest views")
            .tag("status", status.name())
            .tag("locale", locale)
            .tag("cacheHit", Boolean.toString(cacheHit))
            .register(meterRegistry)
            .increment();
    }

    private static WeeklyDigestDto enforceNonTrainingFlag(WeeklyDigestDto dto) {
        if (!dto.trainingEligible()) {
            return dto;
        }
        return new WeeklyDigestDto(
            dto.schemaVersion(),
            dto.digestId(),
            dto.userId(),
            dto.weekIso(),
            dto.weekStartDate(),
            dto.weekEndDate(),
            dto.locale(),
            dto.status(),
            false,
            dto.generatedAt(),
            dto.executiveSummary(),
            dto.wins(),
            dto.recurringMistakes(),
            dto.masteryMomentum(),
            dto.recommendations()
        );
    }

    private static String normalizeLocale(String rawLocale) {
        if (rawLocale != null && rawLocale.trim().toLowerCase(Locale.ROOT).startsWith("tr")) {
            return "tr";
        }
        return "en";
    }

    private static ZoneId resolveZone(String rawZone) {
        if (rawZone != null && !rawZone.isBlank()) {
            try {
                return ZoneId.of(rawZone.trim());
            } catch (DateTimeException ignored) {
                return ZoneOffset.UTC;
            }
        }
        return ZoneOffset.UTC;
    }

    private static UUID deterministicUuid(String namespace, UUID userId, String suffix) {
        return UUID.nameUUIDFromBytes((namespace + ":" + userId + ":" + suffix).getBytes(StandardCharsets.UTF_8));
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || value < 0.0) {
            return 0.0;
        }
        return Math.min(1.0, value);
    }

    private static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private static String localizedDayLabel(DayOfWeek day, String locale) {
        boolean tr = "tr".equals(locale);
        return switch (day) {
            case MONDAY -> tr ? "Pzt" : "Mon";
            case TUESDAY -> tr ? "Sal" : "Tue";
            case WEDNESDAY -> tr ? "Çar" : "Wed";
            case THURSDAY -> tr ? "Per" : "Thu";
            case FRIDAY -> tr ? "Cum" : "Fri";
            case SATURDAY -> tr ? "Cmt" : "Sat";
            case SUNDAY -> tr ? "Paz" : "Sun";
        };
    }

    private static String localizedMistakeLabel(String mistakeCode, String locale) {
        boolean tr = "tr".equals(locale);
        return switch (mistakeCode) {
            case "SIGN_DISTRIBUTION_FLIP" -> tr
                ? "Parantez içine eksi işareti dağıtma adımı"
                : "Distributing a negative sign across parentheses";
            case "ARITHMETIC_ORDER_SWAP" -> tr
                ? "İşlem önceliği sıralaması"
                : "Order of operations sequencing";
            case "FRACTION_DENOMINATOR_MISMATCH" -> tr
                ? "Kesirlerde payda eşitleme kontrolü"
                : "Matching denominators before combining fractions";
            default -> humanizeCode(mistakeCode);
        };
    }

    private static String localizedRecommendationHeadline(
        RecommendationReasonCode reason,
        String skillTitle,
        String locale
    ) {
        boolean tr = "tr".equals(locale);
        return switch (reason) {
            case RECURRING_MISTAKE_PATTERN -> tr
                ? skillTitle + " için kısa bir ikiz pekiştirme seti"
                : "Short verification twin practice for " + skillTitle;
            case RETENTION_DECAY_RISK -> tr
                ? skillTitle + " konusunda 8 dakikalık hafıza tazeleme"
                : "Quick 8-minute retention refresh in " + skillTitle;
            case PREREQUISITE_BLOCKER -> tr
                ? skillTitle + " temelini güçlendirerek sonraki adımları açın"
                : "Unlock connected topics by solidifying " + skillTitle;
            case COLD_START_ONBOARDING -> tr
                ? skillTitle + " ile 10 dakikalık sakin ısınma"
                : "Gentle 10-minute warmup in " + skillTitle;
            case LOW_MASTERY_FOUNDATION -> tr
                ? skillTitle + " için rehberli temel kontrolü"
                : "Guided foundation check in " + skillTitle;
        };
    }

    private static String localizedRecommendationSummary(
        RecommendationReasonCode reason,
        String skillTitle,
        int mistakeCount,
        int blockedDeps,
        String locale
    ) {
        boolean tr = "tr".equals(locale);
        return switch (reason) {
            case RECURRING_MISTAKE_PATTERN -> tr
                ? "Bu hafta " + skillTitle + " içinde " + Math.max(1, mistakeCount) + " kez görülen kalıbı kalıcı olarak netleştirmek için önerildi."
                : "Suggested because this pattern showed up " + Math.max(1, mistakeCount) + " time(s) recently and a short twin check locks in clarity.";
            case RETENTION_DECAY_RISK -> tr
                ? "Aralıklı tekrar zamanı yaklaştığı için " + skillTitle + " bilgisini taze tutar."
                : "A timely spaced check keeps your recent gains in " + skillTitle + " durable.";
            case PREREQUISITE_BLOCKER -> tr
                ? skillTitle + " konusundaki gelişim " + Math.max(1, blockedDeps) + " bağlantılı sonraki konunun önünü açıyor."
                : "Strengthening " + skillTitle + " directly supports " + Math.max(1, blockedDeps) + " connected next topic(s).";
            case COLD_START_ONBOARDING -> tr
                ? "Haftaya özgüvenle başlamak için başlangıç noktası olarak önerildi."
                : "Recommended as an approachable starting point to build steady momentum this week.";
            case LOW_MASTERY_FOUNDATION -> tr
                ? skillTitle + " alanındaki temel adımları güçlendirmek için dengeli bir sonraki hedef olarak seçildi."
                : "Selected to gently strengthen core confidence in " + skillTitle + " before advancing.";
        };
    }

    private static String humanizeCode(String code) {
        if (code == null || code.isBlank()) {
            return "Core Math Foundations";
        }
        String[] parts = code.replace('.', '_').split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank() || "MATH".equalsIgnoreCase(part)) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                sb.append(part.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return sb.isEmpty() ? code : sb.toString();
    }

    public record IsoWeekWindow(String weekIso, LocalDate startDate, LocalDate endDate) {
    }
}
