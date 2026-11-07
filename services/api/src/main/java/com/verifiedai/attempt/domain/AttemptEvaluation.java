package com.verifiedai.attempt.domain;

import java.time.Instant;
import java.util.*;

/** Derived evidence only: neither the captured attempt nor its syntax is upgraded. */
public record AttemptEvaluation(String schemaVersion, UUID id, UUID attemptId, UUID ownerId,
    UUID problemSessionId, AttemptSourceIdentity source, int workRevision, String policyVersion,
    UUID executionId, String createdAt, List<UUID> stepIds, AttemptAnalysisResult analysis,
    boolean realDomainConfirmed, UUID finalStepId, FinalStatus finalAnswerStatus, AttemptAnalysisResult finalAnswerEvidence,
    boolean trainingEligible) {
    public enum FinalStatus { VERIFIED, DISPROVEN, INCONCLUSIVE, NOT_PROVIDED, REVIEW_REQUIRED }
    public AttemptEvaluation {
        stepIds=List.copyOf(stepIds);
        if(!"attempt-evaluation-v1".equals(schemaVersion) || id==null || attemptId==null || ownerId==null
            || problemSessionId==null || source==null || workRevision<1 || workRevision>30
            || !"structured-evaluation-v1".equals(policyVersion) || executionId==null || trainingEligible
            || analysis==null || !"attempt-analysis-v2".equals(analysis.schemaVersion()) || finalAnswerStatus==null || stepIds.isEmpty() || stepIds.size()>12
            || new HashSet<>(stepIds).size()!=stepIds.size() || stepIds.stream().anyMatch(Objects::isNull)) invalid();
        Instant.parse(createdAt);
        analysis.validateFor(executionId,stepIds.size());
        if(!realDomainConfirmed && "REVIEWED_REAL_TASK".equals(analysis.domainBasis())) invalid();
        if((finalStepId==null)!=(finalAnswerStatus==FinalStatus.NOT_PROVIDED)
            || finalStepId!=null && !stepIds.contains(finalStepId)) invalid();
        if(finalAnswerEvidence!=null) {
            finalAnswerEvidence.validateFor(executionId,1);
            if(!"attempt-analysis-v2".equals(finalAnswerEvidence.schemaVersion()) || !realDomainConfirmed && "REVIEWED_REAL_TASK".equals(finalAnswerEvidence.domainBasis())) invalid();
        }
        if(finalAnswerStatus==FinalStatus.VERIFIED && (finalAnswerEvidence==null
            || !"attempt-analysis-v2".equals(finalAnswerEvidence.schemaVersion())
            || finalAnswerEvidence.steps().getFirst().verdict()!=StepVerdict.VALID_EQUIVALENT)) invalid();
        if(finalAnswerStatus==FinalStatus.DISPROVEN && (finalAnswerEvidence==null
            || finalAnswerEvidence.steps().getFirst().verdict()!=StepVerdict.FIRST_WRONG_STEP)) invalid();
        if(Set.of(FinalStatus.NOT_PROVIDED,FinalStatus.REVIEW_REQUIRED).contains(finalAnswerStatus) && finalAnswerEvidence!=null) invalid();
    }
    private static void invalid() { throw new IllegalArgumentException("Invalid evaluation evidence"); }
}
