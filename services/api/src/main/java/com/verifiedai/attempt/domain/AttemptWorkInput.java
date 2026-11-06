package com.verifiedai.attempt.domain;

import java.math.BigDecimal;
import java.util.*;

/** Exact authored evidence. Native observations are client attestations, never trusted OCR facts. */
public record AttemptWorkInput(String schemaVersion, AttemptSourceIdentity source, Engine engine,
    UUID captureId, UUID assetSessionId, UUID recognitionJobId, UUID supersedesAttemptId,
    List<Observation> observations, List<Step> steps) {
    public enum Engine { MANUAL, NATIVE_VISION, GATEWAY_RECOGNITION }
    public enum Role { INTERMEDIATE, FINAL_ANSWER, AMBIGUOUS }
    public record Box(BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height) {
        public Box {
            for(var value:Arrays.asList(x,y,width,height))
                if(value==null || value.signum()<0 || value.compareTo(BigDecimal.ONE)>0) invalid();
            if(x.add(width).compareTo(BigDecimal.ONE)>0 || y.add(height).compareTo(BigDecimal.ONE)>0) invalid();
        }
    }
    public record Observation(UUID id,String rawText,BigDecimal confidence,Box box) {
        public Observation {
            if(id==null || !AttemptAnalysisPolicy.validText(rawText)
                || confidence!=null && (confidence.signum()<0 || confidence.compareTo(BigDecimal.ONE)>0)) invalid();
        }
    }
    public record Step(UUID id,String text,Role role,List<UUID> observationIds,boolean confirmed) {
        public Step {
            if(id==null || role==null || !AttemptAnalysisPolicy.validText(text) || observationIds==null) invalid();
            observationIds=List.copyOf(observationIds);
            if(observationIds.size()>12 || new HashSet<>(observationIds).size()!=observationIds.size()) invalid();
        }
    }
    public AttemptWorkInput {
        if(!"attempt-structured-work-request-v1".equals(schemaVersion) || source==null || engine==null
            || observations==null || steps==null) invalid();
        observations=List.copyOf(observations); steps=List.copyOf(steps);
        if(steps.isEmpty() || steps.size()>12 || observations.size()>12
            || steps.stream().map(Step::id).distinct().count()!=steps.size()
            || observations.stream().map(Observation::id).distinct().count()!=observations.size()
            || steps.stream().filter(s->s.role()==Role.FINAL_ANSWER).count()>1) invalid();
        var observed=new HashSet<>(observations.stream().map(Observation::id).toList());
        var linked=new HashSet<UUID>();
        for(var step:steps) {
            linked.addAll(step.observationIds());
            if(engine==Engine.MANUAL ? !step.observationIds().isEmpty() : step.observationIds().isEmpty()) invalid();
        }
        if(!observed.equals(linked)) invalid(); // Never silently drop original OCR, including segmentation edits.
        switch(engine) {
            case MANUAL -> { if(!observations.isEmpty() || captureId!=null || assetSessionId!=null || recognitionJobId!=null) invalid(); }
            case NATIVE_VISION -> { if(observations.isEmpty() || captureId==null || assetSessionId!=null || recognitionJobId!=null) invalid(); }
            case GATEWAY_RECOGNITION -> { if(observations.isEmpty() || captureId!=null || assetSessionId==null || recognitionJobId==null) invalid(); }
        }
    }
    private static void invalid() { throw new IllegalArgumentException("Invalid structured work evidence"); }
}
