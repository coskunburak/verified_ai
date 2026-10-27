package com.verifiedai.solving.infrastructure.persistence;

import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import com.verifiedai.solving.domain.repository.CanonicalSolutionRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation=Propagation.MANDATORY,noRollbackFor=CanonicalSolutionRepository.UnsupportedRepresentation.class)
public class JdbcCanonicalSolutionRepository implements CanonicalSolutionRepository {
    private final JdbcTemplate jdbc;
    private final CanonicalMathCodec codec;
    public JdbcCanonicalSolutionRepository(JdbcTemplate jdbc,CanonicalMathCodec codec) { this.jdbc=jdbc;this.codec=codec; }
    // Candidate rows are immutable and only exist after acceptance. Source authority is resolved by the problem API.
    private static final String ACCEPTED="""
        select 'PRIMARY' as source_role,j.id as source_job_id,j.user_id,j.problem_session_id,j.canonical_problem_id,
            j.problem_parse_id,j.problem_parse_revision,null::integer as canonical_revision,null::uuid as parent_primary_job_id,
            j.operation_id,j.ledger_record_id,c.schema_version,c.artifact_jsonb::text as artifact,
            c.provenance_jsonb::text as provenance,c.created_at
        from solution_candidates c join solve_jobs j on j.id=c.job_id
        union all
        select 'SECONDARY',j.id,j.user_id,j.problem_session_id,j.canonical_problem_id,j.problem_parse_id,
            j.problem_parse_revision,j.canonical_revision,j.primary_job_id,j.operation_id,j.ledger_record_id,
            c.schema_version,c.artifact_jsonb::text,c.provenance_jsonb::text,c.created_at
        from secondary_solution_evidence c join secondary_solve_jobs j on j.id=c.job_id
        """;
    public Optional<AcceptedArtifact> accepted(UUID user,UUID session,Origin origin) {
        return jdbc.query("select * from ("+ACCEPTED+") a where user_id=? and problem_session_id=? and source_role=? and source_job_id=?",
            this::artifact,user,session,origin.role().name(),origin.jobId()).stream().findFirst();
    }
    public List<AcceptedArtifact> pending(Version version,int limit) {
        if(limit<1 || limit>100) throw new IllegalArgumentException("Recovery batch must be bounded");
        return jdbc.query("select a.* from ("+ACCEPTED+"""
            ) a join users u on u.id=a.user_id and u.status='ACTIVE'
            where not exists(select 1 from canonical_solutions s where s.source_role=a.source_role and s.source_job_id=a.source_job_id
                and s.representation_version=? and s.normalizer_version=?)
              and not exists(select 1 from canonical_materialization_failures f where f.source_role=a.source_role and f.source_job_id=a.source_job_id
                and f.representation_version=? and f.normalizer_version=?)
            order by a.created_at,a.source_role,a.source_job_id limit ?
            """,this::artifact,version.representation(),version.normalizer(),version.representation(),version.normalizer(),limit);
    }
    public Optional<CanonicalSolution> find(UUID user,UUID session,Origin origin,Version version) {
        return one("select * from canonical_solutions where user_id=? and problem_session_id=? and source_role=? and source_job_id=? and representation_version=? and normalizer_version=?",
            user,session,origin.role().name(),origin.jobId(),version.representation(),version.normalizer());
    }
    public Optional<CanonicalSolution> find(UUID user,UUID session,UUID id) {
        return one("select * from canonical_solutions where user_id=? and problem_session_id=? and id=?",user,session,id);
    }
    public List<CanonicalSolution> forSource(UUID user,UUID session,UUID canonical,int revision,Version version) {
        return jdbc.query("""
            select * from canonical_solutions where user_id=? and problem_session_id=? and canonical_problem_id=?
                and canonical_revision=? and representation_version=? and normalizer_version=? order by created_at,source_role,source_job_id
            """,this::solution,user,session,canonical,revision,version.representation(),version.normalizer());
    }
    public CanonicalSolution insert(CanonicalSolution s) {
        var l=s.lineage(); var p=s.provenance(); var v=s.version();
        UUID primary=l.origin().role()==Role.PRIMARY?l.origin().jobId():l.parentPrimaryJobId();
        UUID secondary=l.origin().role()==Role.SECONDARY?l.origin().jobId():null;
        var content=codec.object();
        content.put("generationOrigin",s.generationOrigin()).put("problemType",s.problemType()).put("taskType",s.taskType()).put("method",s.method());
        content.set("finalAnswer",codec.json(s.finalStatement()));
        content.set("sourceAssumptionIds",codec.parse(codec.encode(s.sourceAssumptionIds())));
        content.set("sourceConstraintIds",codec.parse(codec.encode(s.sourceConstraintIds())));
        content.set("derivedRestrictionIds",codec.parse(codec.encode(s.derivedRestrictionIds())));
        content.set("ambiguities",codec.parse(codec.encode(s.ambiguities())));
        content.set("unresolvedIssues",codec.parse(codec.encode(s.unresolvedIssues())));
        var provenance=content.putObject("provenance");
        provenance.put("provider",p.provider()).put("model",p.model()).put("promptId",p.promptId())
            .put("promptVersion",p.promptVersion()).put("routePolicyVersion",p.routePolicyVersion())
            .put("candidateSchema",p.candidateSchema()).put("candidateVersion",p.candidateVersion());
        int inserted=jdbc.update("""
            insert into canonical_solutions(id,user_id,problem_session_id,canonical_problem_id,problem_parse_id,problem_parse_revision,
                canonical_revision,source_role,source_job_id,primary_job_id,secondary_job_id,representation_version,normalizer_version,
                operation_id,ledger_record_id,content_jsonb,expected_step_count,created_at)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,cast(? as jsonb),?,?)
            on conflict(source_role,source_job_id,representation_version,normalizer_version) do nothing
            """,s.id(),l.userId(),l.sessionId(),l.canonicalProblemId(),l.parseId(),l.parseRevision(),l.canonicalRevision(),l.origin().role().name(),
            l.origin().jobId(),primary,secondary,v.representation(),v.normalizer(),p.operationId(),p.ledgerRecordId(),content.toString(),s.steps().size(),Timestamp.from(s.createdAt()));
        if(inserted==0) return find(l.userId(),l.sessionId(),l.origin(),v).orElseThrow();
        for(var step:s.steps()) {
            jdbc.update("""
                insert into canonical_solution_steps(solution_id,id,candidate_step_id,ordinal,category,claim_jsonb,explanation,expected_dependency_count)
                values (?,?,?,?,?,cast(? as jsonb),?,?)
                """,s.id(),step.id(),step.candidateStepId(),step.order(),step.category().name(),codec.json(step.claim()).toString(),step.explanation(),step.dependsOn().size());
        }
        Map<String,Step> steps=new HashMap<>(); s.steps().forEach(step->steps.put(step.candidateStepId(),step));
        for(var step:s.steps()) {
            int referenceOrder=0;
            for(String reference:step.dependsOn()) {
                var target=steps.get(reference);
                jdbc.update("insert into canonical_solution_dependencies values (?,?,?,?,?,?)",s.id(),step.id(),step.order(),target.id(),target.order(),++referenceOrder);
            }
        }
        return s;
    }
    public Optional<Failure> failure(UUID user,UUID session,Origin origin,Version version) {
        return jdbc.queryForList("""
            select failure_code from canonical_materialization_failures where user_id=? and problem_session_id=?
                and source_role=? and source_job_id=? and representation_version=? and normalizer_version=?
            """,String.class,user,session,origin.role().name(),origin.jobId(),version.representation(),version.normalizer())
            .stream().findFirst().map(Failure::valueOf);
    }
    public void fail(AcceptedArtifact a,Version v,Failure failure,Instant now) {
        jdbc.update("""
            insert into canonical_materialization_failures(source_role,source_job_id,user_id,problem_session_id,primary_job_id,
                secondary_job_id,representation_version,normalizer_version,failure_code,created_at)
            values (?,?,?,?,?,?,?,?,?,?) on conflict do nothing
            """,a.origin().role().name(),a.origin().jobId(),a.userId(),a.sessionId(),a.origin().role()==Role.PRIMARY?a.origin().jobId():a.parentPrimaryJobId(),
            a.origin().role()==Role.SECONDARY?a.origin().jobId():null,v.representation(),v.normalizer(),failure.name(),Timestamp.from(now));
    }
    public List<Map<String,Object>> export(UUID user) {
        var result=new ArrayList<Map<String,Object>>();
        var headers=jdbc.queryForList("select * from canonical_solutions where user_id=? order by created_at,id",user);
        for(var header:headers) {
            var row=new LinkedHashMap<String,Object>(header);
            // Export all owned content including older/unknown representations without interpreting it.
            row.put("content_jsonb",header.get("content_jsonb").toString());
            row.put("steps",jdbc.queryForList("select id,candidate_step_id,ordinal,category,claim_jsonb::text as claim,explanation from canonical_solution_steps where solution_id=? order by ordinal",header.get("id")));
            row.put("dependencies",jdbc.queryForList("select step_id,dependency_id,reference_order from canonical_solution_dependencies where solution_id=? order by step_ordinal,reference_order",header.get("id")));
            result.add(row);
        }
        return List.copyOf(result);
    }
    public List<Map<String,Object>> exportFailures(UUID user) {
        return jdbc.queryForList("select * from canonical_materialization_failures where user_id=? order by created_at,source_job_id",user);
    }
    private Optional<CanonicalSolution> one(String sql,Object... args) { return jdbc.query(sql,this::solution,args).stream().findFirst(); }
    private AcceptedArtifact artifact(ResultSet r,int n) throws SQLException {
        return new AcceptedArtifact(r.getObject("user_id",UUID.class),r.getObject("problem_session_id",UUID.class),
            r.getObject("canonical_problem_id",UUID.class),r.getObject("problem_parse_id",UUID.class),r.getInt("problem_parse_revision"),
            r.getObject("canonical_revision",Integer.class),new Origin(Role.valueOf(r.getString("source_role")),r.getObject("source_job_id",UUID.class)),
            r.getObject("parent_primary_job_id",UUID.class),r.getObject("operation_id",UUID.class),r.getObject("ledger_record_id",UUID.class),
            r.getString("schema_version"),r.getString("artifact"),r.getString("provenance"),r.getTimestamp("created_at").toInstant());
    }
    private CanonicalSolution solution(ResultSet r,int n) throws SQLException {
        String representation=r.getString("representation_version");
        if(!Version.CURRENT.representation().equals(representation)) throw new UnsupportedRepresentation();
        UUID id=r.getObject("id",UUID.class);
        var role=Role.valueOf(r.getString("source_role"));
        var lineage=new Lineage(r.getObject("user_id",UUID.class),r.getObject("problem_session_id",UUID.class),
            r.getObject("canonical_problem_id",UUID.class),r.getObject("problem_parse_id",UUID.class),r.getInt("problem_parse_revision"),
            r.getInt("canonical_revision"),new Origin(role,r.getObject("source_job_id",UUID.class)),role==Role.SECONDARY?r.getObject("primary_job_id",UUID.class):null);
        var c=codec.parse(r.getString("content_jsonb")); var p=c.path("provenance");
        var steps=jdbc.query("select * from canonical_solution_steps where solution_id=? order by ordinal",(rs,k)->{
            UUID step=rs.getObject("id",UUID.class);
            var deps=jdbc.queryForList("""
                select s.candidate_step_id from canonical_solution_dependencies d join canonical_solution_steps s
                    on s.solution_id=d.solution_id and s.id=d.dependency_id
                where d.solution_id=? and d.step_id=? order by d.reference_order
                """,String.class,id,step);
            return new Step(step,rs.getString("candidate_step_id"),rs.getInt("ordinal"),Category.valueOf(rs.getString("category")),
                codec.statement(codec.parse(rs.getString("claim_jsonb"))),rs.getString("explanation"),deps);
        },id);
        return new CanonicalSolution(id,lineage,new Version(representation,r.getString("normalizer_version")),
            new Provenance(r.getObject("operation_id",UUID.class),r.getObject("ledger_record_id",UUID.class),p.path("provider").asText(),nullable(p,"model"),
                nullable(p,"promptId"),nullable(p,"promptVersion"),nullable(p,"routePolicyVersion"),p.path("candidateSchema").asText(),p.path("candidateVersion").asInt()),
            c.path("generationOrigin").asText(),c.path("problemType").asText(),c.path("taskType").asText(),c.path("method").asText(),codec.statement(c.path("finalAnswer")),steps,
            codec.strings(c.path("sourceAssumptionIds")),codec.strings(c.path("sourceConstraintIds")),codec.strings(c.path("derivedRestrictionIds")),
            codec.strings(c.path("ambiguities")),codec.strings(c.path("unresolvedIssues")),r.getTimestamp("created_at").toInstant());
    }
    private static String nullable(com.fasterxml.jackson.databind.JsonNode p,String name) { return p.path(name).isTextual()?p.path(name).textValue():null; }
}
