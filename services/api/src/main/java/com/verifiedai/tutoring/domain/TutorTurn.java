package com.verifiedai.tutoring.domain;

import java.time.Instant;
import java.util.UUID;

/** Durable local response. No model output, retry key, reference solution or private reasoning. */
public record TutorTurn(String schemaVersion, UUID id, UUID tutorSessionId, UUID learnerMessageId,
    long learnerSequenceNumber, UUID replyId, String role, String content, String language,
    String policyVersion, SocraticPolicy.Phase previousPhase, SocraticPolicy.Progress progress,
    SocraticPolicy.Intent intent, SocraticPolicy.Action action, SocraticPolicy.Outcome outcome,
    String correctness, String referenceStatus, boolean uncertain, String provenance,
    Instant createdAt, boolean trainingEligible) {
    public static TutorTurn create(TutorMessage learner, SocraticPolicy.Progress previous,
            SocraticPolicy.Intent intent, SocraticPolicy.Decision decision, String language, Instant now) {
        if (!java.util.Set.of("en", "tr").contains(language)
                || !SocraticPolicy.decide(previous,intent,SocraticPolicy.Evidence.UNKNOWN).equals(decision))
            throw new IllegalArgumentException("Unsupported deterministic tutor decision");
        String text = SocraticPolicy.question(decision.action(), language);
        TutorPolicy.content(text);
        return new TutorTurn("tutor-turn-v1", UUID.randomUUID(), learner.tutorSessionId(), learner.id(),
            learner.sequenceNumber(), UUID.randomUUID(), "TUTOR", text, language, SocraticPolicy.VERSION,
            previous.phase(), decision.progress(), intent, decision.action(), decision.outcome(), "UNKNOWN",
            "UNAVAILABLE", true, "DETERMINISTIC_POLICY", now, false);
    }
}
