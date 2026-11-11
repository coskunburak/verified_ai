package com.verifiedai.mistake;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.verifiedai.mistake.MistakeRecurrenceState.*;
import static org.assertj.core.api.Assertions.*;

class MistakeRetentionResolutionTest {
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");
    private static final String HAZARD = "ALG_SIGN_FLIP_DISTRIBUTE";
    private static final String SKILL = MistakeTaxonomy.SKILLS.get(HAZARD);
    private static final Duration WEEK = Duration.ofDays(7);
    private MistakeDnaEvidenceSource.Evidence error(int id, Instant at) {
        var uuid = new UUID(0,id);
        var decision = new MistakeClassificationDecision(MistakeClassificationDecision.Status.SIGNATURE_SUPPORTED,HAZARD,
            MistakeTaxonomy.CATEGORIES.get(HAZARD),MistakeClassificationDecision.Authority.DETERMINISTIC_EVIDENCE,
            MistakeClassificationDecision.Strength.STRUCTURAL_AND_DISPROOF,MistakeClassificationDecision.Review.AUTO_DETECTED,
            SKILL,"attempt.mistake."+HAZARD);
        return new MistakeDnaEvidenceSource.Evidence(uuid,uuid,uuid,uuid,1,at,decision,uuid,SKILL,true,false);
    }
    private MistakeRetentionResolutionListener.Twin twin(int id, Instant at, boolean independent, boolean correct) {
        return new MistakeRetentionResolutionListener.Twin(new UUID(0,id),HAZARD,at,independent,correct);
    }

    @Test void sevenDayBoundaryRequiresIndependentSuccessOnSameHazardAndReplaysOnce() {
        var errors = List.of(error(1,START),error(2,START.plusSeconds(1)));
        var first = twin(3,START.plusSeconds(2),true,true);
        var second = twin(4,START.plusSeconds(3),true,true);
        Instant due = second.at().plus(WEEK);
        var check = twin(5,due,true,true);
        var twins = List.of(check,second,first,first,twin(6,due.minusNanos(1),true,true));
        var early = MistakeRetentionResolutionListener.replay(HAZARD,errors,twins,due.minusNanos(1),OBSERVED_ONCE);
        assertThat(early.state()).isEqualTo(RESOLVED_PENDING_RETENTION);
        assertThat(early.dueAt()).isEqualTo(due);
        var accepted = MistakeRetentionResolutionListener.replay(HAZARD,errors,twins,due,OBSERVED_ONCE);
        assertThat(accepted.state()).isEqualTo(STABLE_RESOLVED);
        assertThat(accepted.transitions()).hasSize(2);
        assertThat(accepted.transitions().getLast().checkDrill()).isEqualTo(check.id());
        var ineligible = List.of(first,second,twin(7,due,false,true),twin(8,due,true,false),
            new MistakeRetentionResolutionListener.Twin(new UUID(0,9),"ALG_FRACTION_ADD_DENOM",due,true,true));
        assertThat(MistakeRetentionResolutionListener.replay(HAZARD,errors,ineligible,due,OBSERVED_ONCE).state())
            .isEqualTo(RESOLVED_PENDING_RETENTION);
        var relapse = List.of(error(10,due.plusSeconds(1)),errors.getLast(),errors.getFirst());
        var result = MistakeRetentionResolutionListener.replay(HAZARD,relapse,twins,due.plusSeconds(1),RECURRING_PATTERN);
        assertThat(result.state()).isEqualTo(RELAPSED);
        assertThat(result.transitions().getLast().from()).isEqualTo(STABLE_RESOLVED);
        assertThat(result.dueAt()).isNull();
    }

    @Test void isolatedErrorOrInterruptedStreakCannotEstablishPending() {
        var twins = List.of(twin(3,START,true,true), twin(4,START.plusSeconds(1),false,true),
            twin(5,START.plusSeconds(2),true,true));
        var one = error(1,START);
        var end = START.plus(WEEK);
        var weak = new MistakeDnaEvidenceSource.Evidence(new UUID(0,12),one.attemptId(),one.evaluationId(),one.executionId(),
            1,START,one.decision(),one.skillEvidenceId(),SKILL,false,false);
        assertThat(MistakeRetentionResolutionListener.replay(HAZARD,List.of(one,weak),twins,end,OBSERVED_ONCE).state())
            .isEqualTo(OBSERVED_ONCE);
        var ambiguous = List.of(twin(3,START,true,true),twin(3,START,false,true));
        assertThatIllegalArgumentException().isThrownBy(() ->
            MistakeRetentionResolutionListener.replay(HAZARD,List.of(one),ambiguous,end,OBSERVED_ONCE));
        assertThat(MistakeRetentionResolutionListener.replay(HAZARD,List.of(one),twins,end,OBSERVED_ONCE).state())
            .isEqualTo(OBSERVED_ONCE);
        assertThat(MistakeRetentionResolutionListener.replay(HAZARD,List.of(one,error(2,START)),twins,end,RECURRING_PATTERN).state())
            .isEqualTo(IN_REMEDIATION);
    }
}
