package com.verifiedai.studyplan.practice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
public class AdaptivePracticeQuestionSelector {

    private static final Logger log = LoggerFactory.getLogger(AdaptivePracticeQuestionSelector.class);

    private static final BigDecimal MIN_DIFFICULTY = new BigDecimal("0.0500");
    private static final BigDecimal MAX_DIFFICULTY = new BigDecimal("0.9500");
    private static final BigDecimal ZPD_WINDOW_DELTA = new BigDecimal("0.1000");
    private static final BigDecimal ZERO = new BigDecimal("0.0000");
    private static final BigDecimal ONE = new BigDecimal("1.0000");

    private static final BigDecimal TRAP_CLOSURE_DELTA = new BigDecimal("-0.0500");
    private static final BigDecimal SPACED_DRILL_DELTA = new BigDecimal("0.0200");
    private static final BigDecimal EXAM_SYNTHESIS_DELTA = new BigDecimal("0.0800");
    private static final BigDecimal STREAK_STEP = new BigDecimal("0.0150");

    private static final BigDecimal WEIGHT_DIFFICULTY = new BigDecimal("0.5000");
    private static final BigDecimal WEIGHT_TRAP = new BigDecimal("0.3000");
    private static final BigDecimal WEIGHT_EXAM_ROI = new BigDecimal("0.2000");

    private final CuratedPracticeQuestionBank questionBank;
    private final SymPyTwinVerifierClient twinVerifierClient;
    private final PracticeSessionItemRepository itemRepository;

    public AdaptivePracticeQuestionSelector(
            CuratedPracticeQuestionBank questionBank,
            SymPyTwinVerifierClient twinVerifierClient,
            PracticeSessionItemRepository itemRepository
    ) {
        this.questionBank = Objects.requireNonNull(questionBank, "questionBank cannot be null");
        this.twinVerifierClient = Objects.requireNonNull(twinVerifierClient, "twinVerifierClient cannot be null");
        this.itemRepository = Objects.requireNonNull(itemRepository, "itemRepository cannot be null");
    }

    public record ZpdCalculationResult(
            BigDecimal targetDifficulty,
            BigDecimal lowerBound,
            BigDecimal upperBound
    ) {
    }

    public ZpdCalculationResult calculateZpd(
            BigDecimal learnerMastery,
            PracticeSlotRole slotRole,
            int streakCounter
    ) {
        BigDecimal slotDelta = switch (slotRole) {
            case TRAP_CLOSURE -> TRAP_CLOSURE_DELTA;
            case SPACED_DRILL -> SPACED_DRILL_DELTA;
            case EXAM_SYNTHESIS -> EXAM_SYNTHESIS_DELTA;
        };

        int clampedStreak = Math.max(-3, Math.min(3, streakCounter));
        BigDecimal streakAdj = STREAK_STEP.multiply(new BigDecimal(clampedStreak)).setScale(4, RoundingMode.HALF_EVEN);

        BigDecimal baseDifficulty = learnerMastery
                .add(slotDelta)
                .add(streakAdj)
                .setScale(4, RoundingMode.HALF_EVEN);

        BigDecimal targetDifficulty = baseDifficulty.max(MIN_DIFFICULTY).min(MAX_DIFFICULTY);
        BigDecimal lowerBound = targetDifficulty.subtract(ZPD_WINDOW_DELTA).max(ZERO).setScale(4, RoundingMode.HALF_EVEN);
        BigDecimal upperBound = targetDifficulty.add(ZPD_WINDOW_DELTA).min(ONE).setScale(4, RoundingMode.HALF_EVEN);

        return new ZpdCalculationResult(targetDifficulty, lowerBound, upperBound);
    }

    public List<PracticeSessionItemEntity> allocateSessionItems(
            PracticeSessionEntity session,
            UUID userId,
            String targetSkillId,
            String activeTrapFamily,
            BigDecimal learnerMastery,
            int streakCounter,
            BigDecimal examNetRoiWeight
    ) {
        List<PracticeSessionItemEntity> items = new ArrayList<>();
        Set<String> excludedQuestionIds = getRecentQuestionIdsSeen(userId, 14);

        List<PracticeSlotRole> slotRoles = determineSlotRoles(session.getSessionMode(), activeTrapFamily);

        for (int i = 0; i < slotRoles.size(); i++) {
            PracticeSlotRole role = slotRoles.get(i);
            ZpdCalculationResult zpd = calculateZpd(learnerMastery, role, streakCounter);

            PracticeSessionItemEntity item = null;

            // Attempt Tier-1 Twin for TRAP_CLOSURE
            if (role == PracticeSlotRole.TRAP_CLOSURE && activeTrapFamily != null && !activeTrapFamily.isBlank()) {
                try {
                    SymPyTwinVerifierClient.GeneratedTwinPayload twin = twinVerifierClient.generateTwin(
                            userId, targetSkillId, activeTrapFamily, i
                    );
                    item = new PracticeSessionItemEntity(
                            UUID.randomUUID(),
                            session,
                            userId,
                            i,
                            role,
                            PracticeQuestionSource.DETERMINISTIC_TWIN_TIER1,
                            "TWIN-" + targetSkillId + "-" + activeTrapFamily + "-" + i,
                            targetSkillId,
                            activeTrapFamily,
                            zpd.targetDifficulty(),
                            twin.targetDifficulty().setScale(4, RoundingMode.HALF_EVEN),
                            zpd.lowerBound(),
                            zpd.upperBound(),
                            twin.promptLatex(),
                            twin.promptInstructionTr(),
                            twin.promptInstructionEn(),
                            twin.canonicalSolutionExpr(),
                            twin.trapDistractorExpr(),
                            twin.sympyVerificationHash(),
                            true, // isTwinVerification
                            60,   // independentLockSeconds
                            Instant.now(),
                            Instant.now()
                    );
                } catch (Exception exc) {
                    log.warn("INV-PRAC-FAILCLOSED: Tier-1 twin generation failed for trap family {}, falling back to curated bank: {}", activeTrapFamily, exc.getMessage());
                }
            }

            // Fallback or standard curated bank selection
            if (item == null) {
                CuratedPracticeQuestion selected = selectCuratedCandidate(
                        targetSkillId,
                        role == PracticeSlotRole.TRAP_CLOSURE ? activeTrapFamily : null,
                        zpd.targetDifficulty(),
                        examNetRoiWeight,
                        excludedQuestionIds
                );
                excludedQuestionIds.add(selected.questionId());

                boolean isTwin = (role == PracticeSlotRole.TRAP_CLOSURE);
                item = new PracticeSessionItemEntity(
                        UUID.randomUUID(),
                        session,
                        userId,
                        i,
                        role,
                        PracticeQuestionSource.CURATED_BANK,
                        selected.questionId(),
                        selected.skillId(),
                        selected.trapFamily(),
                        zpd.targetDifficulty(),
                        selected.difficulty(),
                        zpd.lowerBound(),
                        zpd.upperBound(),
                        selected.promptLatex(),
                        selected.instructionTr(),
                        selected.instructionEn(),
                        selected.canonicalSolutionExpr(),
                        selected.trapDistractorExpr(),
                        selected.sympyVerificationHash(),
                        isTwin,
                        isTwin ? 60 : 0,
                        Instant.now(),
                        Instant.now()
                );
            }

            session.addItem(item);
            items.add(item);
        }

        return items;
    }

    private List<PracticeSlotRole> determineSlotRoles(PracticeSessionMode mode, String activeTrapFamily) {
        return switch (mode) {
            case DAILY_1_2_1 -> {
                List<PracticeSlotRole> slots = new ArrayList<>();
                // Slot 1: Trap closure if active trap debt exists, else Exam Synthesis fallback (INV-PRAC-001)
                if (activeTrapFamily != null && !activeTrapFamily.isBlank()) {
                    slots.add(PracticeSlotRole.TRAP_CLOSURE);
                } else {
                    slots.add(PracticeSlotRole.EXAM_SYNTHESIS);
                }
                // Slots 2 & 3: Spaced drills
                slots.add(PracticeSlotRole.SPACED_DRILL);
                slots.add(PracticeSlotRole.SPACED_DRILL);
                // Slot 4: Exam synthesis
                slots.add(PracticeSlotRole.EXAM_SYNTHESIS);
                yield slots;
            }
            case TRAP_VERIFICATION_DRILL -> List.of(
                    PracticeSlotRole.TRAP_CLOSURE,
                    PracticeSlotRole.TRAP_CLOSURE,
                    PracticeSlotRole.TRAP_CLOSURE,
                    PracticeSlotRole.TRAP_CLOSURE
            );
            case SPACED_REVIEW -> List.of(
                    PracticeSlotRole.SPACED_DRILL,
                    PracticeSlotRole.SPACED_DRILL,
                    PracticeSlotRole.SPACED_DRILL,
                    PracticeSlotRole.SPACED_DRILL
            );
            case EXAM_ROI_SYNTHESIS -> List.of(
                    PracticeSlotRole.EXAM_SYNTHESIS,
                    PracticeSlotRole.EXAM_SYNTHESIS,
                    PracticeSlotRole.EXAM_SYNTHESIS,
                    PracticeSlotRole.EXAM_SYNTHESIS
            );
        };
    }

    private CuratedPracticeQuestion selectCuratedCandidate(
            String skillId,
            String activeTrapFamily,
            BigDecimal targetDifficulty,
            BigDecimal examNetRoiWeight,
            Set<String> excludedQuestionIds
    ) {
        List<CuratedPracticeQuestion> pool = questionBank.getCandidatesForSkill(skillId);
        List<CuratedPracticeQuestion> candidates = pool.stream()
                .filter(q -> !excludedQuestionIds.contains(q.questionId()))
                .toList();

        if (candidates.isEmpty()) {
            candidates = pool; // If all candidates seen in 14 days, allow repetition
        }

        BigDecimal roiWeight = examNetRoiWeight != null ? examNetRoiWeight.setScale(4, RoundingMode.HALF_EVEN) : new BigDecimal("0.5000");

        // Utility scoring: U(q) = 0.5000 * (1 - |d_q - d_target|) + 0.3000 * I(trap) + 0.2000 * R_exam(s)
        return candidates.stream()
                .max(Comparator
                        .comparing((CuratedPracticeQuestion q) -> scoreCandidate(q, activeTrapFamily, targetDifficulty, roiWeight))
                        .thenComparing((CuratedPracticeQuestion q) -> q.difficulty().subtract(targetDifficulty).abs(), Comparator.reverseOrder())
                        .thenComparing(CuratedPracticeQuestion::questionId, Comparator.reverseOrder()) // Lexicographical UUID tie-breaking
                )
                .orElseGet(() -> questionBank.getAll().get(0));
    }

    public BigDecimal scoreCandidate(
            CuratedPracticeQuestion q,
            String activeTrapFamily,
            BigDecimal targetDifficulty,
            BigDecimal examNetRoiWeight
    ) {
        BigDecimal diffDistance = q.difficulty().subtract(targetDifficulty).abs();
        BigDecimal diffTerm = ONE.subtract(diffDistance).max(ZERO);

        BigDecimal trapIndicator = (activeTrapFamily != null && activeTrapFamily.equalsIgnoreCase(q.trapFamily()))
                ? ONE
                : ZERO;

        BigDecimal roiTerm = examNetRoiWeight.min(ONE).max(ZERO);

        return WEIGHT_DIFFICULTY.multiply(diffTerm)
                .add(WEIGHT_TRAP.multiply(trapIndicator))
                .add(WEIGHT_EXAM_ROI.multiply(roiTerm))
                .setScale(4, RoundingMode.HALF_EVEN);
    }

    private Set<String> getRecentQuestionIdsSeen(UUID userId, int days) {
        Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
        List<PracticeSessionItemEntity> recentItems = itemRepository.findAll();
        Set<String> ids = new HashSet<>();
        for (PracticeSessionItemEntity item : recentItems) {
            if (item.getUserId().equals(userId) && item.getCreatedAt().isAfter(cutoff)) {
                ids.add(item.getSourceQuestionId());
            }
        }
        return ids;
    }
}
