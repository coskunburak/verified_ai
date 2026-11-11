package com.verifiedai.mastery;

import java.math.BigDecimal;
import java.math.RoundingMode;
import static com.verifiedai.mastery.HalfLifeRetentionPolicy.elapsedDays;
import java.time.Instant;
import java.util.*;
import static com.verifiedai.mastery.LearningEvidenceType.*;

/** bkt-lite-v2 / DOC-86. Stable UTC ordering, StrictMath, scale 8 HALF_EVEN at each transition. */
public final class EvidenceStratifiedMasteryPolicy {
    public static final String VERSION="bkt-lite-v2";
    public static final double PRIOR=.25, FLOOR=.05, CEILING=.99, SLIP=.10, GUESS=.15;
    public static final double INDEPENDENT_TRANSITION=.18, TWIN_TRANSITION=.24, HINT_TRANSITION=.07,
        INCORRECT_TRANSITION=.03, ASSISTED_CEILING=.78, MASTERED=.85, INITIAL_HALF_LIFE=3,
        COMPETENCE_PRIOR_STRENGTH=3, COMPETENCE_HALF_LIFE=28;
    public record State(double score,double halfLife,int independent,int assisted,int hints,int independentPasses,
        double confidence,boolean cappedByAssistance,Instant observedAt,Instant confidenceObservedAt) {
        public int count() { return independent+assisted; }
        public String status() { return score>=MASTERED && independentPasses>0?"MASTERED":"PRACTICING"; }
    }
    public record Step(MasteryEvidenceSource.Evidence evidence,State prior,State next) { }
    public record Projection(State state,double independentCompetence,double retention,double confidence,List<Step> steps) { }
    public State initial() { return new State(PRIOR,INITIAL_HALF_LIFE,0,0,0,0,0,false,null,null); }
    public State observe(State prior,MasteryEvidenceSource.Evidence e) {
        if(e.type()==UNSUPPORTED) return prior;
        if(prior.observedAt()!=null && e.observedAt().isBefore(prior.observedAt())) throw new IllegalArgumentException("Evidence order");
        double m=retention(prior,e.observedAt());
        boolean correct=e.correct() && e.type()!=SOLUTION_REVEALED_ASSISTED;
        double slip=SLIP,guess=GUESS,transition=INCORRECT_TRANSITION;
        if(correct && e.type()==HINT_ASSISTED) { slip*=1.5; guess*=2; transition=HINT_TRANSITION; }
        else if(correct) transition=e.verifiedTwin()?TWIN_TRANSITION:INDEPENDENT_TRANSITION;
        else if(e.type()==SOLUTION_REVEALED_ASSISTED) transition=0;
        double numerator=m*(correct?1-slip:slip);
        double posterior=numerator/(numerator+(1-m)*(correct?guess:1-guess));
        double next=posterior+(1-posterior)*transition;
        boolean capped=false;
        if(correct && e.type()==HINT_ASSISTED) {
            // Assistance can neither inflate above the cap nor erase an independently earned score.
            double cap=StrictMath.max(ASSISTED_CEILING,prior.score());
            capped=next>ASSISTED_CEILING;
            next=prior.score()>ASSISTED_CEILING?prior.score():StrictMath.min(cap,next);
        }
        if(e.type()==SOLUTION_REVEALED_ASSISTED) next=StrictMath.min(prior.score(),next);
        double half=HalfLifeRetentionPolicy.next(prior,e);
        int independent=prior.independent()+(e.type()==INDEPENDENT?1:0);
        int assisted=prior.assisted()+(e.type()!=INDEPENDENT?1:0);
        int hints=prior.hints()+(e.type()==HINT_ASSISTED?1:0);
        double confidence=1-StrictMath.exp(-(independent+.4*hints)/5);
        return new State(round(StrictMath.max(FLOOR,StrictMath.min(CEILING,next))),round(half),independent,assisted,hints,
            prior.independentPasses()+(correct && e.type()==INDEPENDENT?1:0),round(confidence),
            capped,e.observedAt(),e.type()==SOLUTION_REVEALED_ASSISTED?prior.confidenceObservedAt():e.observedAt());
    }
    public Projection project(List<MasteryEvidenceSource.Evidence> input,Instant asOf) {
        var unique=new HashMap<UUID,MasteryEvidenceSource.Evidence>();
        for(var e:input) {
            var old=unique.putIfAbsent(e.id(),e);
            if(old!=null && !old.equals(e)) throw new IllegalArgumentException("Conflicting evidence identity");
        }
        var ordered=unique.values().stream().filter(e->e.type()!=UNSUPPORTED && !e.observedAt().isAfter(asOf))
            .sorted(Comparator.comparing(MasteryEvidenceSource.Evidence::observedAt).thenComparing(e->e.id().toString())).toList();
        State state=initial(); var steps=new ArrayList<Step>(); double successes=0,total=0;
        for(var e:ordered) {
            var next=observe(state,e); steps.add(new Step(e,state,next)); state=next;
            double weight=StrictMath.pow(2,-elapsedDays(e.observedAt(),asOf)/COMPETENCE_HALF_LIFE);
            total+=weight;
            if(e.type()==INDEPENDENT && e.correct()) successes+=weight;
        }
        return new Projection(state,round((successes+COMPETENCE_PRIOR_STRENGTH*PRIOR)/(total+COMPETENCE_PRIOR_STRENGTH)),
            retention(state,asOf),round(state.confidence()*StrictMath.pow(2,-elapsedDays(state.confidenceObservedAt(),asOf)/(2*state.halfLife()))),List.copyOf(steps));
    }
    public double retention(State state,Instant at) {
        return round(HalfLifeRetentionPolicy.retention(state,at));
    }
    private static double round(double value) { return BigDecimal.valueOf(value).setScale(8,RoundingMode.HALF_EVEN).doubleValue(); }
}
