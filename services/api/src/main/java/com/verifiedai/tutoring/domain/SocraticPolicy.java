package com.verifiedai.tutoring.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import java.util.*;
import org.springframework.http.HttpStatus;

/** Server-owned teaching policy. Learner intents are requests, never correctness evidence. */
public final class SocraticPolicy {
    public static final String VERSION = "socratic-policy-v1";
    public static final int MAX_TURNS = 12, MAX_UNPRODUCTIVE = 3;
    public enum Phase {
        CLARIFY_GOAL, ELICIT_CONCEPT, EVALUATE_LEARNER_STEP, OFFER_MINIMAL_SUPPORT,
        ADVANCE_ONE_STEP, SUMMARIZE_CONCEPT, COMPLETED, PAUSED, NEEDS_REVIEW, UNAVAILABLE;
        public boolean terminal() { return Set.of(COMPLETED, PAUSED, NEEDS_REVIEW, UNAVAILABLE).contains(this); }
    }
    public enum Intent { UNKNOWN, ATTEMPT_STEP, EXPLAIN_CONCEPT, SMALL_HINT, REVEAL_ANSWER, CONFUSED, CHALLENGE, OFF_TOPIC, SUMMARIZE }
    public enum Evidence { UNKNOWN, TRUSTED_CORRECT_STEP }
    public enum Action { ASK_GOAL, ASK_CONCEPT, ASK_STEP, CONCEPTUAL_NUDGE, ADVANCE, REFLECT, FINISH, PAUSE, REQUEST_REVIEW, CAPABILITY_UNAVAILABLE }
    public enum Outcome { GUIDANCE, PAUSED, NEEDS_REVIEW, COMPLETED, UNAVAILABLE }
    public record Progress(Phase phase, int unproductiveCount, int turnCount) {
        public Progress {
            Objects.requireNonNull(phase);
            if (unproductiveCount < 0 || unproductiveCount > MAX_UNPRODUCTIVE || turnCount < 0 || turnCount > MAX_TURNS)
                throw new IllegalArgumentException("Invalid pedagogical progress");
        }
        public static Progress initial() { return new Progress(Phase.CLARIFY_GOAL, 0, 0); }
    }
    public record Decision(Progress progress, Action action, Outcome outcome) {}
    private SocraticPolicy() {}

    public static Decision decide(Progress previous, Intent intent, Evidence evidence) {
        Objects.requireNonNull(intent); Objects.requireNonNull(evidence);
        if (previous.phase().terminal()) throw TutorPolicy.failure(ApiErrorCode.TUTOR_PEDAGOGY_STOPPED, HttpStatus.CONFLICT);
        int unproductive = previous.unproductiveCount();
        Phase next; Action action; Outcome outcome = Outcome.GUIDANCE;
        if (intent == Intent.CHALLENGE) {
            next = Phase.NEEDS_REVIEW; action = Action.REQUEST_REVIEW; outcome = Outcome.NEEDS_REVIEW;
        } else if (previous.turnCount() >= MAX_TURNS - 1) {
            next = Phase.PAUSED; action = Action.PAUSE; outcome = Outcome.PAUSED;
        } else if (intent == Intent.SUMMARIZE) {
            next = previous.phase() == Phase.SUMMARIZE_CONCEPT ? Phase.COMPLETED : Phase.SUMMARIZE_CONCEPT;
            action = next == Phase.COMPLETED ? Action.FINISH : Action.REFLECT;
            outcome = next == Phase.COMPLETED ? Outcome.COMPLETED : Outcome.GUIDANCE;
        } else if (Set.of(Intent.REVEAL_ANSWER, Intent.CONFUSED, Intent.SMALL_HINT, Intent.OFF_TOPIC).contains(intent)
                || (Set.of(Phase.EVALUATE_LEARNER_STEP, Phase.OFFER_MINIMAL_SUPPORT).contains(previous.phase())
                    && evidence == Evidence.UNKNOWN && intent != Intent.EXPLAIN_CONCEPT)) {
            unproductive++;
            next = unproductive >= MAX_UNPRODUCTIVE ? Phase.PAUSED : Phase.OFFER_MINIMAL_SUPPORT;
            action = next == Phase.PAUSED ? Action.PAUSE : Action.CONCEPTUAL_NUDGE;
            outcome = next == Phase.PAUSED ? Outcome.PAUSED : Outcome.GUIDANCE;
        } else if (intent == Intent.EXPLAIN_CONCEPT) {
            next = Phase.ELICIT_CONCEPT; action = Action.ASK_CONCEPT;
        } else {
            next = switch (previous.phase()) {
                case CLARIFY_GOAL -> Phase.ELICIT_CONCEPT;
                case ELICIT_CONCEPT, OFFER_MINIMAL_SUPPORT, ADVANCE_ONE_STEP -> Phase.EVALUATE_LEARNER_STEP;
                case EVALUATE_LEARNER_STEP -> Phase.ADVANCE_ONE_STEP;
                case SUMMARIZE_CONCEPT -> Phase.COMPLETED;
                default -> throw new IllegalStateException("Terminal phase");
            };
            action = switch (next) {
                case ELICIT_CONCEPT -> Action.ASK_CONCEPT;
                case EVALUATE_LEARNER_STEP -> Action.ASK_STEP;
                case ADVANCE_ONE_STEP -> Action.ADVANCE;
                case COMPLETED -> Action.FINISH;
                default -> throw new IllegalStateException("Unsupported phase");
            };
            if (next == Phase.ADVANCE_ONE_STEP) unproductive = 0;
            if (next == Phase.COMPLETED) outcome = Outcome.COMPLETED;
        }
        requireTransition(previous.phase(), next, evidence);
        return new Decision(new Progress(next, unproductive, previous.turnCount() + 1), action, outcome);
    }

    public static void requireTransition(Phase from, Phase to, Evidence evidence) {
        boolean legal = !from.terminal() && (
            Set.of(Phase.PAUSED, Phase.NEEDS_REVIEW, Phase.UNAVAILABLE, Phase.SUMMARIZE_CONCEPT,
                Phase.OFFER_MINIMAL_SUPPORT, Phase.ELICIT_CONCEPT).contains(to)
            || (from == Phase.SUMMARIZE_CONCEPT && to == Phase.COMPLETED)
            || (Set.of(Phase.ELICIT_CONCEPT, Phase.OFFER_MINIMAL_SUPPORT, Phase.ADVANCE_ONE_STEP).contains(from)
                && to == Phase.EVALUATE_LEARNER_STEP)
            || (from == Phase.EVALUATE_LEARNER_STEP && to == Phase.ADVANCE_ONE_STEP && evidence == Evidence.TRUSTED_CORRECT_STEP));
        if (!legal) throw TutorPolicy.failure(ApiErrorCode.TUTOR_ILLEGAL_TRANSITION, HttpStatus.CONFLICT);
    }

    /** Only exact, bounded request phrases are classified. Free-form mathematics stays UNKNOWN. */
    public static Intent classify(String content) {
        return switch (content.strip().toLowerCase(Locale.ROOT)) {
            case "hint", "small hint", "ipucu", "ipucu ver" -> Intent.SMALL_HINT;
            case "give me the answer", "reveal the answer", "cevabı ver", "cevabi ver" -> Intent.REVEAL_ANSWER;
            case "i am confused", "i don't understand", "anlamadım", "anlamadim" -> Intent.CONFUSED;
            case "this is wrong", "i disagree", "bu yanlış", "katılmıyorum" -> Intent.CHALLENGE;
            case "explain the concept", "kavramı açıkla" -> Intent.EXPLAIN_CONCEPT;
            case "summarize", "özetle" -> Intent.SUMMARIZE;
            default -> Intent.UNKNOWN;
        };
    }

    /** Reviewed templates contain no student/source text, final answers or hidden reasoning. */
    public static String question(Action action, String language) {
        boolean tr = "tr".equals(language);
        return switch (action) {
            case ASK_GOAL -> tr ? "Soruda neyi bulmak istiyorsun?" : "What are you trying to find in this problem?";
            case ASK_CONCEPT -> tr ? "Hedefini düşün: hangi kavram veya kuralı kullanmayı önerirsin?" : "Consider your goal: which concept or rule would you propose using?";
            case ASK_STEP -> tr ? "Bu kuralı kullanarak önereceğin tek bir adım ve gerekçesi nedir?" : "What single step would you propose using that rule, and why?";
            case CONCEPTUAL_NUDGE -> tr ? "Adımının doğruluğunu burada değerlendiremiyorum. Verilenlerle hedefi ayırıp hangi kuralın bağ kurabileceğini söyleyebilir misin?" : "I cannot assess whether your step is correct here. Can you separate the given information from the goal and name a rule that might connect them?";
            case ADVANCE -> tr ? "Kontrol edilen adımın ardından hangi tek adımı önerirsin?" : "After the checked step, what single step would you propose next?";
            case REFLECT -> tr ? "Doğruluğu henüz değerlendirilmedi. Kullandığın kavramı ve hâlâ kontrol edilmesi gereken noktayı özetler misin?" : "Correctness has not been assessed. Can you summarize the concept you used and what still needs checking?";
            case FINISH -> tr ? "Yansıtma etkinliği tamamlandı; matematiksel doğruluk değerlendirilmedi. Çözüm ekranındaki kanıtları inceleyebilir veya yeni bir oturum başlatabilirsin." : "The reflection activity is complete; mathematical correctness was not assessed. Review the evidence on the solution screen or start a new session.";
            case PAUSE -> tr ? "Burada duralım. Adımını değerlendiremiyorum; çözüm ekranını gözden geçirip yeni bir oturum başlatabilirsin." : "Let us pause here. I cannot assess your step; review the solution screen and start a new session when ready.";
            case REQUEST_REVIEW -> tr ? "İtirazını kaydettim; bir kontrol başlatılmadı. Kaynak ve doğrulama kanıtlarını çözüm ekranında incele, gerekirse kaynağı düzeltip yeni oturum aç." : "Your challenge is recorded; no recheck has been started. Review the source and verification evidence on the solution screen, correct the source if needed, and start a new session.";
            case CAPABILITY_UNAVAILABLE -> tr ? "Bu yetenek şu anda kullanılamıyor. Daha sonra tekrar deneyebilirsin." : "This capability is currently unavailable. You can retry later.";
        };
    }
}
