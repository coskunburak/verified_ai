package com.verifiedai.attempt.domain;

import java.time.Instant;
import java.util.*;

/** Immutable derived syntax, without correctness, mistake or mastery authority. */
public record AttemptStructuredWork(String schemaVersion, UUID attemptId, UUID ownerId, UUID problemSessionId,
    AttemptSourceIdentity source, int revision, UUID supersedesAttemptId, String normalizationVersion,
    String createdAt, AttemptWorkInput.Engine engine, String provenance, UUID captureId, UUID assetSessionId,
    UUID recognitionJobId, String provider, String model, String promptVersion,
    List<AttemptWorkInput.Observation> observations, List<Step> steps, boolean trainingEligible) {
    public enum NormalizationStatus { NORMALIZED, UNSUPPORTED, REVIEW_REQUIRED }
    public enum ReviewStatus { CONFIRMED, REQUIRED }
    public record Step(UUID id,int index,AttemptWorkInput.Role role,String originalText,String reviewedText,
        List<UUID> observationIds,String normalizedStatement,NormalizationStatus normalizationStatus,ReviewStatus reviewStatus) {
        public Step {
            observationIds=List.copyOf(observationIds);
            if(id==null || index<1 || index>12 || role==null || normalizationStatus==null || reviewStatus==null
                || !AttemptAnalysisPolicy.validText(reviewedText)
                || (normalizationStatus==NormalizationStatus.NORMALIZED)!=(normalizedStatement!=null)
                || (reviewStatus==ReviewStatus.REQUIRED)!=(normalizationStatus==NormalizationStatus.REVIEW_REQUIRED)
                || (role==AttemptWorkInput.Role.AMBIGUOUS || reviewedText.contains("\n") || reviewedText.contains("\r")) && reviewStatus!=ReviewStatus.REQUIRED
                || normalizedStatement!=null && normalizedStatement.length()>1800)
                throw new IllegalArgumentException("Invalid structured step");
        }
    }
    public AttemptStructuredWork {
        observations=List.copyOf(observations); steps=List.copyOf(steps);
        if(!"attempt-structured-work-v1".equals(schemaVersion) || attemptId==null || ownerId==null || problemSessionId==null
            || source==null || createdAt==null || engine==null || revision<1 || revision>30
            || (revision==1)!=(supersedesAttemptId==null) || trainingEligible
            || !"structured-canonical-v1".equals(normalizationVersion) || steps.isEmpty() || steps.size()>12)
            throw new IllegalArgumentException("Invalid structured work");
        Instant.parse(createdAt);
        boolean gateway=engine==AttemptWorkInput.Engine.GATEWAY_RECOGNITION;
        if(!Objects.equals(provenance,gateway?"SERVER_RECORDED":"CLIENT_ATTESTED")
            || (gateway ? provider==null || model==null || promptVersion==null : provider!=null || model!=null || promptVersion!=null))
            throw new IllegalArgumentException("Invalid provenance");
        new AttemptWorkInput("attempt-structured-work-request-v1",source,engine,captureId,assetSessionId,recognitionJobId,supersedesAttemptId,
            observations,steps.stream().map(s->new AttemptWorkInput.Step(s.id(),s.reviewedText(),s.role(),s.observationIds(),s.reviewStatus()==ReviewStatus.CONFIRMED)).toList());
        for(var step:steps) if(engine==AttemptWorkInput.Engine.MANUAL ? !Objects.equals(step.originalText(),step.reviewedText()) : step.originalText()!=null)
            throw new IllegalArgumentException("Original and reviewed evidence differ");
        for(int i=0;i<steps.size();i++) if(steps.get(i).index()!=i+1) throw new IllegalArgumentException("Invalid step order");
    }
}
