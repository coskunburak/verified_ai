package com.verifiedai.tutoring.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Durable public allowlist. Retry keys and source mathematics never leave storage. */
public record TutorHint(String schemaVersion, UUID id, UUID tutorSessionId, int sequenceNumber,
    HintPolicy.Level level, String content, String language, String policyVersion, String costPolicyVersion,
    HintPolicy.Assistance assistance, String masteryEvidence, String inference, String commercial,
    SocraticPolicy.Phase previousPhase, SocraticPolicy.Progress progress, String referenceStatus,
    boolean uncertain, Instant createdAt, boolean trainingEligible) {
    public static TutorHint create(UUID session, HintPolicy.Level level, String language,
        SocraticPolicy.Progress previous, SocraticPolicy.Progress progress, Instant now) {
        if (!SocraticPolicy.offerHint(previous).equals(progress)) throw new IllegalArgumentException("Invalid hint progress");
        String content = HintPolicy.content(level, language);
        HintPolicy.validatePublication(level, language, content);
        return new TutorHint("tutor-hint-v1", UUID.randomUUID(), session, level.ordinal()+1, level, content, language,
            HintPolicy.VERSION, HintPolicy.COST_VERSION, HintPolicy.assistance(level), "ASSISTANCE_ONLY",
            "DETERMINISTIC_NO_INFERENCE", "NO_DEBIT", previous.phase(), progress, "UNAVAILABLE", true,
            now.truncatedTo(java.time.temporal.ChronoUnit.MICROS), false);
    }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public record History(String schemaVersion, UUID tutorSessionId, List<TutorHint> hints,
        HintPolicy.Level currentLevel, HintPolicy.Level nextLevel, HintPolicy.Availability availability,
        SocraticPolicy.Progress progress, String revealAvailability) {}
}
