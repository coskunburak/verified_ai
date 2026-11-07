package com.verifiedai.attempt.application;

import com.verifiedai.attempt.domain.*;
import com.verifiedai.curriculum.application.CurriculumTaxonomyCatalog;
import com.verifiedai.mistake.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import static com.verifiedai.mistake.MistakeClassificationDecision.*;

@Component
public class AttemptMistakeClassifier {
    private final CurriculumTaxonomyCatalog skills;
    public AttemptMistakeClassifier(CurriculumTaxonomyCatalog skills) { this.skills = skills; }
    public AttemptMistakeClassification classify(AttemptEvaluation e, Instant now) {
        return classify(e, now, false);
    }
    public AttemptMistakeClassification classify(AttemptEvaluation e, AttemptStructuredWork work, Instant now) {
        if(!e.attemptId().equals(work.attemptId()) || !e.ownerId().equals(work.ownerId())
            || !e.problemSessionId().equals(work.problemSessionId()) || !e.source().equals(work.source()) || e.workRevision()!=work.revision()
            || !e.stepIds().equals(work.steps().stream().map(AttemptStructuredWork.Step::id).toList()))
            throw new IllegalArgumentException("Mismatched captured evidence");
        boolean review=false;
        for(int i=0;i<work.steps().size();i++) {
            var verdict=e.analysis().steps().get(i).verdict();
            if(verdict==StepVerdict.NOT_ASSESSED) break;
            if(work.steps().get(i).reviewStatus()==AttemptStructuredWork.ReviewStatus.REQUIRED) {
                if(verdict!=StepVerdict.UNCERTAIN) throw new IllegalArgumentException("Unreviewed work cannot be proof");
                review=true;
            }
            if(verdict==StepVerdict.UNCERTAIN || verdict==StepVerdict.FIRST_WRONG_STEP) break;
        }
        return classify(e,now,review);
    }
    private AttemptMistakeClassification classify(AttemptEvaluation e, Instant now, boolean sourceNeedsReview) {
        e.analysis().validateFor(e.executionId(), e.stepIds().size());
        var analysis = e.analysis();
        Status status; String code = null, skill = null, evidence = null; UUID stepId = null;
        Integer index = analysis.firstWrongStepIndex();
        if(index != null) {
            for(int i = 0; i < index - 1; i++)
                if(analysis.steps().get(i).verdict() != StepVerdict.VALID_EQUIVALENT) throw new IllegalArgumentException("Unproven causal prefix");
            var step = analysis.steps().get(index - 1);
            code = step.mistakeCode().name(); evidence = step.diagnosis().evidenceId(); stepId = e.stepIds().get(index - 1);
            boolean currentSignature="structural-signatures-v1".equals(step.diagnosis().classifierPolicyVersion());
            if(MistakeTaxonomy.CATEGORIES.containsKey(code) && !currentSignature) code="INVALID_TRANSFORMATION";
            status = MistakeTaxonomy.CATEGORIES.containsKey(code) ? Status.SIGNATURE_SUPPORTED : Status.INVALID_TRANSFORMATION;
            String candidate = MistakeTaxonomy.SKILLS.get(code);
            if(status == Status.SIGNATURE_SUPPORTED && candidate != null && skills.isActiveSkill(candidate)) skill = candidate;
        } else if(sourceNeedsReview) {
            status = Status.SOURCE_REVIEW_REQUIRED;
        } else if(analysis.steps().stream().anyMatch(s -> s.verdict() == StepVerdict.UNCERTAIN)) {
            var reason = analysis.steps().stream().filter(s -> s.verdict() == StepVerdict.UNCERTAIN).findFirst().orElseThrow().reasonCode();
            status = reason == AttemptAnalysisResult.Reason.UNKNOWN_DOMAIN ? Status.UNCERTAIN
                : reason == AttemptAnalysisResult.Reason.UNDEFINED_SOURCE ? Status.SOURCE_REVIEW_REQUIRED : Status.UNSUPPORTED;
        } else {
            status = e.finalAnswerStatus() == AttemptEvaluation.FinalStatus.NOT_PROVIDED ? Status.INCOMPLETE_WORK
                : e.finalAnswerStatus() == AttemptEvaluation.FinalStatus.REVIEW_REQUIRED ? Status.SOURCE_REVIEW_REQUIRED
                : e.finalAnswerStatus() == AttemptEvaluation.FinalStatus.DISPROVEN ? Status.UNCERTAIN : Status.NO_ERROR_ESTABLISHED;
        }
        var decision = new MistakeClassificationDecision(status, code, status == Status.SIGNATURE_SUPPORTED ? MistakeTaxonomy.CATEGORIES.get(code) : null,
            Authority.DETERMINISTIC_EVIDENCE, status == Status.SIGNATURE_SUPPORTED ? Strength.STRUCTURAL_AND_DISPROOF
                : status == Status.INVALID_TRANSFORMATION ? Strength.EXACT_DISPROOF : Strength.NONE,
            status == Status.SIGNATURE_SUPPORTED ? Review.AUTO_DETECTED : Review.REQUIRED, skill,
            status == Status.SIGNATURE_SUPPORTED ? "attempt.mistake." + code : "attempt.classification." + status);
        var result = new AttemptMistakeClassification("attempt-mistake-classification-v1", UUID.randomUUID(), e.attemptId(), e.ownerId(),
            e.problemSessionId(), e.id(), e.executionId(), e.source(), e.workRevision(), analysis.taxonomyVersion(), MistakeTaxonomy.VERSION,
            MistakeTaxonomy.POLICY, analysis.verifierVersion(), stepId, index, evidence, decision, now.toString(), false);
        result.validateFor(e);
        return result;
    }
}
