package com.verifiedai.attempt.domain;
import java.time.Instant;
import java.util.*;
public record StudentAttempt(String schemaVersion,UUID id,UUID problemSessionId,AttemptSourceIdentity source,
    AttemptSourceType sourceType,String normalizationVersion,AttemptStatus status,List<StudentAttemptStep> steps,AttemptAnalysisResult analysis,
    Instant createdAt,Instant updatedAt,boolean trainingEligible,
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) AttemptCaptureReview captureReview) {
    public StudentAttempt(String schemaVersion,UUID id,UUID problemSessionId,AttemptSourceIdentity source,AttemptSourceType sourceType,
        String normalizationVersion,AttemptStatus status,List<StudentAttemptStep> steps,AttemptAnalysisResult analysis,Instant createdAt,Instant updatedAt,boolean trainingEligible) {
        this(schemaVersion,id,problemSessionId,source,sourceType,normalizationVersion,status,steps,analysis,createdAt,updatedAt,trainingEligible,null);
    }
    public StudentAttempt {
        steps=List.copyOf(steps);
        if(!Set.of("manual-canonical-v1","manual-canonical-v2").contains(normalizationVersion) || trainingEligible || !"student-attempt-v1".equals(schemaVersion)) throw new IllegalArgumentException("Invalid attempt");
    }
}
