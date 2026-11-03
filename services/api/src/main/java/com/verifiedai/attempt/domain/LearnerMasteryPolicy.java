package com.verifiedai.attempt.domain;
import java.time.*;

/** BKT-lite-v1: server observations only; deterministic decay and bounded update. */
public final class LearnerMasteryPolicy {
    private LearnerMasteryPolicy() { }
    public record State(double score,double halfLife,int count,double confidence,Instant assessedAt) { }
    public static State observe(State prior,boolean correct,boolean assisted,Instant now) {
        double score=prior==null?.15:prior.score(), half=prior==null?1:prior.halfLife();
        int count=prior==null?0:prior.count();
        if(prior!=null) {
            double days=Math.max(0,Duration.between(prior.assessedAt(),now).toMillis()/86400000.0);
            score=.15+(score-.15)*Math.pow(2,-days/half);
        }
        double slip=assisted?.18:.08, guess=assisted?.30:.15;
        double numerator=correct?score*(1-slip):score*slip;
        double posterior=numerator/(numerator+(1-score)*(correct?guess:1-guess));
        half=Math.max(1,Math.min(90,half*(correct?(assisted?1.35:2.2):.5)));
        return new State(Math.max(.05,Math.min(.99,posterior)),half,count+1,Math.min(.95,(count+1.0)/(count+6.0)),now);
    }
}
