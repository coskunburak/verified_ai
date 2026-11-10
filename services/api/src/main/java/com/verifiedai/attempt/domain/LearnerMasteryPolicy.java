package com.verifiedai.attempt.domain;
import java.time.Instant;
/** Compatibility facade; mastery owns the unchanged legacy mathematics. */
public final class LearnerMasteryPolicy {
    private LearnerMasteryPolicy() { }
    public record State(double score,double halfLife,int count,double confidence,Instant assessedAt) { }
    public static State observe(State prior,boolean correct,boolean assisted,Instant now) {
        var value=com.verifiedai.mastery.LegacyMasteryPolicy.observe(prior==null?null:
            new com.verifiedai.mastery.LegacyMasteryPolicy.State(prior.score(),prior.halfLife(),prior.count(),prior.confidence(),prior.assessedAt()),correct,assisted,now);
        return new State(value.score(),value.halfLife(),value.count(),value.confidence(),value.assessedAt());
    }
}
