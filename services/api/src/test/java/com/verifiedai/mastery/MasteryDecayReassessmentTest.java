package com.verifiedai.mastery;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.verifiedai.mastery.SkillReassessmentTriggerService.Reason.*;
import static org.assertj.core.api.Assertions.*;

class MasteryDecayReassessmentTest {
    @Test void oneVerifiedAnswerCanLoseRetentionWhileMasteryAndEvidenceIdentityStayFixed() {
        Instant at = Instant.parse("2026-01-01T00:00:00Z");
        var id = new UUID(0,1);
        var e = new MasteryEvidenceSource.Evidence(id,id,id,id,id,"MATH.TEST",at,LearningEvidenceType.INDEPENDENT,true,false,"synthetic");
        var policy = new EvidenceStratifiedMasteryPolicy();
        var early = policy.project(List.of(e),at);
        var late = policy.project(List.of(e,e),at.plus(Duration.ofDays(1)));
        assertThat(early.state()).isEqualTo(late.state());
        assertThat(early.confidence()).isLessThan(.2);
        assertThat(late.confidence()).isLessThan(early.confidence());
        assertThat(late.retention()).isLessThan(.72);
        assertThat(SkillReassessmentTriggerService.decide(early,false)).isEqualTo(NOT_DUE);
        assertThat(SkillReassessmentTriggerService.decide(late,false)).isEqualTo(RETENTION_THRESHOLD);
        assertThat(SkillReassessmentTriggerService.decide(late,true)).isEqualTo(DELAYED_TRAP_CHECK);
        assertThat(SkillReassessmentTriggerService.decide(policy.project(List.of(),at),false)).isEqualTo(NOT_DUE);
    }
}
