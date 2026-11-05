package com.verifiedai.attempt.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.*;

/** Immutable learner evidence. A final answer is explicit intent, never inferred from the last step. */
public record StudentAttempt(String schemaVersion, UUID id, UUID problemSessionId, AttemptSourceIdentity source,
    AttemptSourceType sourceType, String normalizationVersion, AttemptStatus status, List<StudentAttemptStep> steps,
    AttemptAnalysisResult analysis, Instant createdAt, Instant updatedAt, boolean trainingEligible,
    @JsonInclude(JsonInclude.Include.NON_NULL) AttemptCaptureReview captureReview,
    @JsonInclude(JsonInclude.Include.NON_NULL) String finalAnswer) {
    public StudentAttempt(String schemaVersion, UUID id, UUID problemSessionId, AttemptSourceIdentity source,
        AttemptSourceType sourceType, String normalizationVersion, AttemptStatus status, List<StudentAttemptStep> steps,
        AttemptAnalysisResult analysis, Instant createdAt, Instant updatedAt, boolean trainingEligible) {
        this(schemaVersion,id,problemSessionId,source,sourceType,normalizationVersion,status,steps,analysis,createdAt,updatedAt,trainingEligible,null,null);
    }
    public StudentAttempt(String schemaVersion, UUID id, UUID problemSessionId, AttemptSourceIdentity source,
        AttemptSourceType sourceType, String normalizationVersion, AttemptStatus status, List<StudentAttemptStep> steps,
        AttemptAnalysisResult analysis, Instant createdAt, Instant updatedAt, boolean trainingEligible, AttemptCaptureReview captureReview) {
        this(schemaVersion,id,problemSessionId,source,sourceType,normalizationVersion,status,steps,analysis,createdAt,updatedAt,trainingEligible,captureReview,null);
    }
    public StudentAttempt {
        Objects.requireNonNull(id); Objects.requireNonNull(problemSessionId); Objects.requireNonNull(source);
        Objects.requireNonNull(sourceType); Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt); Objects.requireNonNull(updatedAt);
        steps=List.copyOf(steps);
        boolean answer="student-attempt-v2".equals(schemaVersion);
        if ((!answer && !"student-attempt-v1".equals(schemaVersion)) || trainingEligible
            || !Set.of("manual-canonical-v1","manual-canonical-v2").contains(normalizationVersion)
            || updatedAt.isBefore(createdAt) || (status==AttemptStatus.ANALYZED)!=(analysis!=null)
            || steps.size()>(answer?11:12) || (!answer && steps.isEmpty())
            || (answer ? !AttemptAnalysisPolicy.validText(finalAnswer) || analysis!=null || captureReview!=null
                || steps.stream().anyMatch(s->s.normalizedStatement()!=null) : finalAnswer!=null))
            throw new IllegalArgumentException("Invalid attempt evidence");
        for(int i=0;i<steps.size();i++) if(steps.get(i).index()!=i+1) throw new IllegalArgumentException("Invalid attempt order");
        if(analysis!=null) analysis.validateFor(id,steps.size());
    }
    /** Display-only compatibility, preserving unassessed state; never persisted or evaluated. */
    public StudentAttempt legacyRepresentation() {
        if(finalAnswer==null) return this;
        var display=new ArrayList<>(steps);
        display.add(new StudentAttemptStep(display.size()+1,finalAnswer,null));
        return new StudentAttempt("student-attempt-v1",id,problemSessionId,source,sourceType,normalizationVersion,
            status,display,null,createdAt,updatedAt,false);
    }
}
