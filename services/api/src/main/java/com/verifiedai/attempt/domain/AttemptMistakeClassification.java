package com.verifiedai.attempt.domain;

import com.verifiedai.mistake.*;
import java.time.Instant;
import java.util.UUID;

/** Immutable derived evidence. It never grants VERIFIED, mastery or training eligibility. */
public record AttemptMistakeClassification(String schemaVersion, UUID id, UUID attemptId, UUID ownerId,
    UUID problemSessionId, UUID evaluationId, UUID executionId, AttemptSourceIdentity source, int workRevision,
    String taxonomyVersion, String categoryVersion, String policyVersion, String verifierVersion,
    UUID firstWrongStepId, Integer firstWrongStepIndex, String supportingEvidenceId,
    MistakeClassificationDecision decision, String createdAt, boolean trainingEligible) {
    public AttemptMistakeClassification {
        if(!"attempt-mistake-classification-v1".equals(schemaVersion) || id == null || attemptId == null
            || ownerId == null || problemSessionId == null || evaluationId == null || executionId == null || source == null
            || workRevision < 1 || workRevision > 30 || !"attempt-taxonomy-v2".equals(taxonomyVersion)
            || !MistakeTaxonomy.VERSION.equals(categoryVersion) || !MistakeTaxonomy.POLICY.equals(policyVersion)
            || !"bounded-attempt-v2".equals(verifierVersion) || decision == null || trainingEligible) invalid();
        Instant.parse(createdAt);
        if(decision.hasDisproof()) {
            if(firstWrongStepId == null || firstWrongStepIndex == null || firstWrongStepIndex < 1 || firstWrongStepIndex > 12
                || !((executionId + ":" + firstWrongStepIndex).equals(supportingEvidenceId))) invalid();
        } else if(firstWrongStepId != null || firstWrongStepIndex != null || supportingEvidenceId != null) invalid();
    }
    public void validateFor(AttemptEvaluation e) {
        if(!attemptId.equals(e.attemptId()) || !ownerId.equals(e.ownerId()) || !problemSessionId.equals(e.problemSessionId())
            || !evaluationId.equals(e.id()) || !executionId.equals(e.executionId()) || !source.equals(e.source())
            || workRevision != e.workRevision() || !taxonomyVersion.equals(e.analysis().taxonomyVersion())
            || !verifierVersion.equals(e.analysis().verifierVersion())) invalid();
        if(decision.hasDisproof() != (e.analysis().firstWrongStepIndex()!=null)) invalid();
        if(decision.hasDisproof()) {
            if(!firstWrongStepIndex.equals(e.analysis().firstWrongStepIndex())
                || !firstWrongStepId.equals(e.stepIds().get(firstWrongStepIndex - 1))) invalid();
            var step = e.analysis().steps().get(firstWrongStepIndex - 1);
            if(step.verdict() != StepVerdict.FIRST_WRONG_STEP || step.diagnosis() == null
                || !supportingEvidenceId.equals(step.diagnosis().evidenceId())
                || !(decision.code().equals(step.mistakeCode().name())
                    || decision.status()==MistakeClassificationDecision.Status.INVALID_TRANSFORMATION
                    && "INVALID_TRANSFORMATION".equals(decision.code()) && step.diagnosis().classifierPolicyVersion()==null)) invalid();
            if(decision.status()==MistakeClassificationDecision.Status.SIGNATURE_SUPPORTED
                && !"structural-signatures-v1".equals(step.diagnosis().classifierPolicyVersion())) invalid();
        } else {
            boolean uncertain=e.analysis().steps().stream().anyMatch(s->s.verdict()==StepVerdict.UNCERTAIN);
            switch(decision.status()) {
                case INCOMPLETE_WORK -> { if(uncertain || e.finalAnswerStatus()!=AttemptEvaluation.FinalStatus.NOT_PROVIDED) invalid(); }
                case NO_ERROR_ESTABLISHED -> {
                    if(uncertain || !java.util.Set.of(AttemptEvaluation.FinalStatus.VERIFIED,AttemptEvaluation.FinalStatus.INCONCLUSIVE).contains(e.finalAnswerStatus())) invalid();
                }
                case UNCERTAIN -> {
                    if(!e.analysis().steps().stream().anyMatch(s->s.verdict()==StepVerdict.UNCERTAIN && s.reasonCode()==AttemptAnalysisResult.Reason.UNKNOWN_DOMAIN)
                        && e.finalAnswerStatus()!=AttemptEvaluation.FinalStatus.DISPROVEN) invalid();
                }
                case UNSUPPORTED -> {
                    if(!e.analysis().steps().stream().anyMatch(s->s.verdict()==StepVerdict.UNCERTAIN
                        && s.reasonCode()!=AttemptAnalysisResult.Reason.UNKNOWN_DOMAIN && s.reasonCode()!=AttemptAnalysisResult.Reason.UNDEFINED_SOURCE)) invalid();
                }
                case SOURCE_REVIEW_REQUIRED -> {
                    // Exact capture-review cause is additionally checked against owned work by the classifier.
                    if(!uncertain && e.finalAnswerStatus()!=AttemptEvaluation.FinalStatus.REVIEW_REQUIRED) invalid();
                }
                default -> invalid();
            }
        }
    }
    private static void invalid() { throw new IllegalArgumentException("Invalid classification evidence"); }
}
