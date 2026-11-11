package com.verifiedai.mastery;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class SkillReassessmentTriggerService {
    public interface PendingChecks {
        Set<String> dueSkills(UUID owner, Instant asOf);
    }

    public enum Reason { RETENTION_THRESHOLD, DELAYED_TRAP_CHECK, NOT_DUE }

    private SkillReassessmentTriggerService() { }

    public static Reason decide(EvidenceStratifiedMasteryPolicy.Projection skill, boolean delayedTrapDue) {
        if (delayedTrapDue) return Reason.DELAYED_TRAP_CHECK;
        if (skill.state().score() > .72 && skill.retention() <= .72) return Reason.RETENTION_THRESHOLD;
        return Reason.NOT_DUE;
    }
}
