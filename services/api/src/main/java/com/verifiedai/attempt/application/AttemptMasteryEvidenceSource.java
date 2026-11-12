package com.verifiedai.attempt.application;

import com.verifiedai.attempt.domain.AttemptLearningEvidence;
import com.verifiedai.attempt.domain.AttemptAnalysisResult;
import com.verifiedai.attempt.domain.AttemptStructuredWork;
import com.verifiedai.attempt.infrastructure.persistence.AttemptWorkRepository;
import com.verifiedai.curriculum.application.CurriculumTaxonomyCatalog;
import com.verifiedai.mastery.*;
import com.verifiedai.mistake.MistakeClassificationDecision;
import com.verifiedai.mistake.MistakeTaxonomy;
import com.verifiedai.mistake.MistakeRetentionResolutionListener;
import com.verifiedai.problem.application.solving.SolveSourceAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import static com.verifiedai.mastery.LearningEvidenceType.*;

/** Reuses the accepted V035/V036 lineage adapter; never consumes mastery output as evidence. */
@Component
public class AttemptMasteryEvidenceSource implements MasteryEvidenceSource, MistakeRetentionResolutionListener.TwinSource {
    private final AttemptMistakeDnaEvidenceSource accepted;
    private final JdbcTemplate jdbc;
    private final SolveSourceAccess sources;
    private final CurriculumTaxonomyCatalog catalog;
    private final AttemptWorkRepository works;
    public AttemptMasteryEvidenceSource(AttemptMistakeDnaEvidenceSource accepted,JdbcTemplate jdbc,
        SolveSourceAccess sources,CurriculumTaxonomyCatalog catalog,AttemptWorkRepository works) {
        this.accepted=accepted; this.jdbc=jdbc; this.sources=sources; this.catalog=catalog; this.works=works;
    }
    public List<Evidence> evidence(UUID owner) {
        var result=new ArrayList<Evidence>();
        var excluded = excluded(owner);
        for(var e:accepted.evidence(owner)) {
            var type=type(owner,e.attemptId(),e.observedAt());
            var work=works.get(owner,e.attemptId()).orElse(null);
            if(work==null || work.steps().stream().anyMatch(step->step.reviewStatus()!=AttemptStructuredWork.ReviewStatus.CONFIRMED)) type=UNSUPPORTED;
            if(!e.verifiedExposure() || e.superseded() || excluded.contains(e.attemptId()) || e.skillEvidenceId()==null) type=UNSUPPORTED;
            result.add(new Evidence(e.evaluationId(),e.attemptId(),e.evaluationId(),e.executionId(),e.skillEvidenceId(),
                e.exposureSkill(),e.observedAt(),type,e.decision().status()==MistakeClassificationDecision.Status.NO_ERROR_ESTABLISHED,
                false,"accepted-evaluation-v1"));
        }
        verifiedDrills(owner, excluded).forEach(drill -> result.add(drill.evidence()));
        return List.copyOf(result);
    }

    private Set<UUID> excluded(UUID owner) {
        return Set.copyOf(jdbc.queryForList("select distinct attempt_id from learner_mistake_dispute_events where user_id=?", UUID.class, owner));
    }

    private record Drill(Evidence evidence, String verifiedHazard) { }

    @Override
    public List<MistakeRetentionResolutionListener.Twin> twins(UUID owner) {
        return verifiedDrills(owner, excluded(owner)).stream()
            .filter(d -> d.verifiedHazard() != null && d.evidence().type() != UNSUPPORTED)
            .map(d -> new MistakeRetentionResolutionListener.Twin(d.evidence().id(), d.verifiedHazard(),
                d.evidence().observedAt(), d.evidence().type() == INDEPENDENT, d.evidence().correct()))
            .toList();
    }

    private List<Drill> verifiedDrills(UUID owner, Set<UUID> excluded) {
        var result = new ArrayList<Drill>();
        // One completion per immutable drill, not its learner_mistake_events replay/delivery copy.
        jdbc.query("""
            select d.*,a.analysis_jsonb,a.problem_session_id,a.canonical_id,a.canonical_revision,a.parse_id,a.parse_revision,
                (select count(*) from student_attempt_steps st where st.attempt_id=a.id) as step_count
            from legacy_counterfactual_twin_drills d join student_attempts a on a.id=d.attempt_id and a.user_id=d.user_id
            where d.user_id=? and d.completed_at is not null order by d.completed_at,d.id
            """,r->{
                UUID id=r.getObject("id",UUID.class),attempt=r.getObject("attempt_id",UUID.class);
                Instant at=r.getTimestamp("completed_at").toInstant();
                var type=type(owner,id,at); String skill=null; UUID skillEvidence=null,execution=null; boolean correct=false;
                String hazard = null;
                var assistance=r.getString("assistance");
                if("HINT_ASSISTED".equals(assistance) && type==INDEPENDENT) type=HINT_ASSISTED;
                else if(!Set.of("INDEPENDENT","HINT_ASSISTED").contains(String.valueOf(assistance))) type=UNSUPPORTED;
                try {
                    var artifact=new ObjectMapper().readValue(r.getString("artifact"),AttemptLearningEvidence.Twin.class);
                    var parent=new ObjectMapper().readValue(r.getString("analysis_jsonb"),AttemptAnalysisResult.class);
                    parent.validateFor(attempt,r.getInt("step_count")); execution=parent.executionId();
                    if(parent.firstWrongStepIndex()==null || parent.steps().get(parent.firstWrongStepIndex()-1).diagnosis()==null
                        || parent.steps().get(parent.firstWrongStepIndex()-1).mistakeCode()!=artifact.mistakeCode()) type=UNSUPPORTED;
                    if (type != UNSUPPORTED && !at.isBefore(r.getTimestamp("created_at").toInstant())) {
                        var wrong = parent.steps().get(parent.firstWrongStepIndex()-1);
                        if (wrong.counterexampleStatus() == AttemptAnalysisResult.WitnessStatus.AVAILABLE
                            && "structural-signatures-v1".equals(wrong.diagnosis().classifierPolicyVersion())
                            && !works.superseded(owner, attempt)) hazard = artifact.mistakeCode().name();
                    }
                    var mapping=sources.skillAt(owner,r.getObject("canonical_id",UUID.class),r.getTimestamp("created_at").toInstant()).orElse(null);
                    var current=sources.current(owner,r.getObject("problem_session_id",UUID.class));
                    skill=mapping==null?null:mapping.skillId(); skillEvidence=mapping==null?null:mapping.id();
                    if(mapping==null || !catalog.snapshot().ontologyVersion().equals(mapping.ontologyVersion()) || !catalog.isActiveSkill(skill)
                        || !Objects.equals(skill,MistakeTaxonomy.SKILLS.get(artifact.mistakeCode().name()))
                        || !current.canonicalProblemId().equals(r.getObject("canonical_id",UUID.class))
                        || current.canonicalRevision()!=r.getInt("canonical_revision")
                        || !current.problemParseId().equals(r.getObject("parse_id",UUID.class))
                        || current.problemParseRevision()!=r.getInt("parse_revision") || excluded.contains(attempt)) type=UNSUPPORTED;
                    if(r.getInt("correct_option")!=artifact.correctOption()) type=UNSUPPORTED;
                    correct=r.getInt("selected_option")==artifact.correctOption() && r.getInt("correct_option")==artifact.correctOption();
                } catch(java.io.IOException | IllegalArgumentException ex) { type=UNSUPPORTED; }
                catch(com.verifiedai.sharedkernel.error.ApiProblemException ex) {
                    if(ex.code()!=com.verifiedai.sharedkernel.error.ApiErrorCode.SOLVE_SOURCE_NOT_READY) throw ex;
                    type=UNSUPPORTED;
                }
                result.add(new Drill(new Evidence(id,attempt,null,execution,skillEvidence,skill,at,type,correct,true,"verified-twin-v1"), hazard));
            },owner);
        return List.copyOf(result);
    }
    private LearningEvidenceType type(UUID owner,UUID evidence,Instant at) {
        return jdbc.query("select * from attempt_mastery_context where user_id=? and evidence_id=?",(r,n)->
            !r.getBoolean("provenance_complete") || !r.getTimestamp("captured_at").toInstant().equals(at)?UNSUPPORTED:r.getBoolean("solution_revealed")?SOLUTION_REVEALED_ASSISTED:
                r.getInt("hints_used_count")>0?HINT_ASSISTED:INDEPENDENT,owner,evidence).stream().findFirst().orElse(UNSUPPORTED);
    }
}
