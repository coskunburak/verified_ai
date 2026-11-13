package com.verifiedai.studyplan.coach;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Deterministic localized briefing builder (ADR-011 $0.00 LLM token cost invariant).
 * Supports Turkish ('tr', default aligned with DOC-85) and English ('en') with strictly
 * non-stigmatizing vocabulary (DOC-83 §7).
 */
@Component
public class DeterministicCoachBriefingBuilder {

    private static final long EXPLAINER_BUDGET_NANOS = 5_000_000L;
    private final Optional<CoachBriefingExplainer> optionalExplainer;

    public DeterministicCoachBriefingBuilder() {
        this.optionalExplainer = Optional.empty();
    }

    @Autowired
    public DeterministicCoachBriefingBuilder(Optional<CoachBriefingExplainer> optionalExplainer) {
        this.optionalExplainer = optionalExplainer != null ? optionalExplainer : Optional.empty();
    }

    public String buildBriefing(
            DailyCoachSessionStatus status,
            DailyCoachMode mode,
            int budgetMinutes,
            boolean isColdStart,
            boolean isDownshifted,
            List<DailyCoachTaskEntity> tasks,
            String localeTag
    ) {
        if (optionalExplainer.isPresent() && status == DailyCoachSessionStatus.IN_PROGRESS && !isDownshifted) {
            long startNanos = System.nanoTime();
            try {
                Optional<String> candidate = optionalExplainer.get()
                        .summarizeBriefing(mode, budgetMinutes, isColdStart, isDownshifted, localeTag);
                long elapsed = System.nanoTime() - startNanos;
                if (elapsed < EXPLAINER_BUDGET_NANOS && candidate.isPresent() && !candidate.get().isBlank()) {
                    return clampBriefing(candidate.get().trim());
                }
            } catch (RuntimeException ignored) {
                // Immediate deterministic fallback (< 5 ms)
            }
        }

        boolean isEnglish = localeTag != null && localeTag.toLowerCase().startsWith("en");
        if (isEnglish) {
            return buildEnglishBriefing(status, mode, budgetMinutes, isColdStart, isDownshifted, tasks);
        }
        return buildTurkishBriefing(status, mode, budgetMinutes, isColdStart, isDownshifted, tasks);
    }

    private String buildTurkishBriefing(
            DailyCoachSessionStatus status,
            DailyCoachMode mode,
            int budgetMinutes,
            boolean isColdStart,
            boolean isDownshifted,
            List<DailyCoachTaskEntity> tasks
    ) {
        if (status == DailyCoachSessionStatus.COMPLETED) {
            return "Günün koç görevi tamamlandı! Bağımsız çözüm serin bir gün daha güçlendi.";
        }
        if (isDownshifted) {
            return "Yoğun gün modu aktif: 5 dakikalık tek öncelik ikiziyle günlük serini güvenle koruyorsun.";
        }
        if (isColdStart) {
            return "İlk 3 adımlı tanı ve hazırlık görevinle güçlü bir başlangıç yapalım.";
        }
        String focusSkill = tasks != null && !tasks.isEmpty()
                ? readableTurkishSkill(tasks.get(0).getTargetSkillCode())
                : "odak beceri";
        if (mode == DailyCoachMode.SOLVER_MODE) {
            return String.format(
                    "Bugün %d dakikada %s odaklı hızlı adım kontrolü ve aralıklı tekrar ile ivmeni koruyoruz.",
                    budgetMinutes,
                    focusSkill
            );
        }
        return String.format(
                "Bugün %d dakikada %s için tuzak onarımı, kalıcılık tekrarı ve sınav sentezi ile ilerliyoruz.",
                budgetMinutes,
                focusSkill
        );
    }

    private String buildEnglishBriefing(
            DailyCoachSessionStatus status,
            DailyCoachMode mode,
            int budgetMinutes,
            boolean isColdStart,
            boolean isDownshifted,
            List<DailyCoachTaskEntity> tasks
    ) {
        if (status == DailyCoachSessionStatus.COMPLETED) {
            return "Daily coach mission complete! Your independent streak is one day stronger.";
        }
        if (isDownshifted) {
            return "Busy-day 5-minute mini streak active: complete your top priority twin to keep momentum.";
        }
        if (isColdStart) {
            return "Start strong with your 3-step readiness mission and interactive step-diff preview.";
        }
        String modeLabel = mode == DailyCoachMode.SOLVER_MODE ? "fast step-diff verification" : "Socratic trap repair";
        return String.format(
                "Today's %d-minute focus area pairs %s with retention refresh and exam synthesis.",
                budgetMinutes,
                modeLabel
        );
    }

    private String readableTurkishSkill(String skillCode) {
        if (skillCode == null) {
            return "odak beceri";
        }
        if (skillCode.contains("RATIONAL")) {
            return "rasyonel denklem tanım kümesi";
        }
        if (skillCode.contains("INEQ")) {
            return "eşitsizlik yön analizi";
        }
        if (skillCode.contains("QUADRATIC") || skillCode.contains("DISCRIMINANT")) {
            return "ikinci derece diskriminant";
        }
        if (skillCode.contains("CHAIN_RULE")) {
            return "bileşke fonksiyon türevi";
        }
        return "temel matematik";
    }

    private String clampBriefing(String text) {
        if (text.length() <= 320) {
            return text;
        }
        return text.substring(0, 317) + "...";
    }
}
