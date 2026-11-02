package com.verifiedai.tutoring.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import org.springframework.http.HttpStatus;

/** Reviewed, bounded disclosure. Never consumes arbitrary generated or learner text. */
public final class HintPolicy {
    public static final String VERSION = "progressive-hints-v1";
    public static final String COST_VERSION = "hint-assistance-v1";
    public enum Level { H1, H2, H3 }
    public enum Assistance { NONE, CONCEPTUAL, METHOD, FIRST_STEP, EXPLICIT_REVEAL }
    public enum Availability { AVAILABLE, EXHAUSTED, CLOSED, STALE, STOPPED, UNAVAILABLE }
    private HintPolicy() {}
    public static Level next(Level current) {
        return current == null ? Level.H1 : switch(current) { case H1 -> Level.H2; case H2 -> Level.H3; case H3 -> null; };
    }
    public static Assistance assistance(Level level) {
        return switch(level) { case H1 -> Assistance.CONCEPTUAL; case H2 -> Assistance.METHOD; case H3 -> Assistance.FIRST_STEP; };
    }
    public static SocraticPolicy.Progress advance(SocraticPolicy.Progress previous, Level current, Level requested) {
        if (requested == null || requested != next(current))
            throw TutorPolicy.failure(ApiErrorCode.TUTOR_HINT_LEVEL_BLOCKED, HttpStatus.CONFLICT);
        return SocraticPolicy.offerHint(previous);
    }
    public static Availability availability(TutorSession session, SocraticPolicy.Progress progress, Level current, boolean enabled, boolean supported) {
        if (session.status() == TutorPolicy.Status.CLOSED) return Availability.CLOSED;
        if (!session.sourceCurrent()) return Availability.STALE;
        if (progress.phase().terminal() || progress.turnCount() >= SocraticPolicy.MAX_TURNS - 1) return Availability.STOPPED;
        if (current == Level.H3) return Availability.EXHAUSTED;
        return enabled && supported ? Availability.AVAILABLE : Availability.UNAVAILABLE;
    }
    public static String content(Level level, String language) {
        if (!java.util.Set.of("en", "tr").contains(language)) throw new IllegalArgumentException("Unsupported hint language");
        boolean tr = "tr".equals(language);
        return switch(level) {
            case H1 -> tr ? "Bilinmeyeni yalnız bırakmayı düşün. Eşitliğin iki tarafının dengesi korunmalı."
                : "Think about isolating the unknown while keeping both sides of the equation balanced.";
            case H2 -> tr ? "Ters işlemleri kullan: önce eklenen sabit terimi kaldır, ardından çarpma işlemini geri al."
                : "Use inverse operations: remove the added constant before undoing multiplication.";
            case H3 -> tr ? "İlk adım olarak iki taraftan da eklenen sabit terimi çıkar. Henüz bölme yapmadan oluşan denklemi sen yaz."
                : "For the first step, subtract the added constant from both sides. Write the resulting equation yourself, without dividing yet.";
        };
    }
    public static void validatePublication(Level level, String language, String text) {
        if (!content(level, language).equals(text))
            throw TutorPolicy.failure(ApiErrorCode.TUTOR_HINT_INVALID, HttpStatus.UNPROCESSABLE_ENTITY);
    }
    /** No authorized verified-answer port exists in this release. Intent is necessary, never sufficient. */
    public static void requireReveal(boolean confirmed) {
        if (!confirmed) throw TutorPolicy.failure(ApiErrorCode.REQUEST_VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY);
        throw TutorPolicy.failure(ApiErrorCode.TUTOR_REVEAL_UNAVAILABLE, HttpStatus.CONFLICT);
    }
}
