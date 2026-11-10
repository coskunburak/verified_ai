package com.verifiedai.attempt.application;

import com.verifiedai.attempt.domain.*;
import com.verifiedai.attempt.infrastructure.persistence.*;
import com.verifiedai.curriculum.application.CurriculumTaxonomyCatalog;
import com.verifiedai.mistake.*;
import com.verifiedai.problem.application.solving.SolveSourceAccess;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Component;
import static com.verifiedai.mistake.MistakeClassificationDecision.*;

/** Adapts accepted immutable lineage, never model guesses or legacy V031 mastery events. */
@Component
public class AttemptMistakeDnaEvidenceSource implements MistakeDnaEvidenceSource {
    private final AttemptMistakeRepository classifications;
    private final AttemptEvaluationRepository evaluations;
    private final AttemptWorkRepository works;
    private final SolveSourceAccess sources;
    private final CurriculumTaxonomyCatalog skills;
    public AttemptMistakeDnaEvidenceSource(AttemptMistakeRepository classifications, AttemptEvaluationRepository evaluations,
        AttemptWorkRepository works, SolveSourceAccess sources, CurriculumTaxonomyCatalog skills) {
        this.classifications=classifications; this.evaluations=evaluations; this.works=works; this.sources=sources; this.skills=skills;
    }
    public List<Evidence> evidence(UUID owner) {
        var result=new ArrayList<Evidence>();
        for(var c:classifications.export(owner)) {
            var work=works.get(owner,c.attemptId()).orElseThrow();
            var e=evaluations.get(owner,c.attemptId()).orElseThrow().artifact();
            c.validateFor(e);
            if(!work.source().equals(e.source()) || work.revision()!=e.workRevision()
                || !e.stepIds().equals(work.steps().stream().map(AttemptStructuredWork.Step::id).toList()))
                throw new IllegalArgumentException("Invalid DNA work lineage");
            var skill=sources.skillAt(owner,work.source().canonicalId(),Instant.parse(work.createdAt())).orElse(null);
            String cluster=skill!=null && skills.snapshot().ontologyVersion().equals(skill.ontologyVersion()) && skills.isActiveSkill(skill.skillId()) ? skill.skillId() : null;
            boolean causal=c.decision().status()==Status.SIGNATURE_SUPPORTED
                && c.decision().authority()==Authority.DETERMINISTIC_EVIDENCE
                && c.decision().evidenceStrength()==Strength.STRUCTURAL_AND_DISPROOF
                && c.decision().reviewStatus()==Review.AUTO_DETECTED
                && c.decision().skillId()!=null && c.decision().skillId().equals(cluster)
                && c.decision().skillId().equals(MistakeTaxonomy.SKILLS.get(c.decision().code()));
            boolean success=c.decision().status()==Status.NO_ERROR_ESTABLISHED
                && e.finalAnswerStatus()==AttemptEvaluation.FinalStatus.VERIFIED
                && e.analysis().steps().stream().allMatch(s->s.verdict()==StepVerdict.VALID_EQUIVALENT);
            // Only the proven causal prefix is relevant for failures; later uncertain steps add nothing.
            int reviewedThrough=causal ? c.firstWrongStepIndex() : work.steps().size();
            boolean reviewed=work.steps().subList(0,reviewedThrough).stream()
                .allMatch(s->s.reviewStatus()==AttemptStructuredWork.ReviewStatus.CONFIRMED);
            boolean superseded=works.superseded(owner,work.attemptId());
            // Superseded canonical source cannot retroactively contribute to current learner state.
            try {
                var current=sources.current(owner,work.problemSessionId());
                superseded |= !work.source().equals(new AttemptSourceIdentity(current.canonicalProblemId(),current.canonicalRevision(),current.problemParseId(),current.problemParseRevision()));
            } catch(com.verifiedai.sharedkernel.error.ApiProblemException ex) {
                if(ex.code()!=com.verifiedai.sharedkernel.error.ApiErrorCode.SOLVE_SOURCE_NOT_READY) throw ex;
                superseded=true;
            }
            result.add(new Evidence(c.id(),c.attemptId(),e.id(),e.executionId(),e.workRevision(),Instant.parse(work.createdAt()),
                c.decision(),skill==null?null:skill.id(),cluster,cluster!=null && reviewed && (causal || success),superseded));
        }
        return List.copyOf(result);
    }
}
