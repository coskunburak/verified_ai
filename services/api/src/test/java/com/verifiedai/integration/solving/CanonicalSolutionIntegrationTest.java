package com.verifiedai.integration.solving;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.problem.application.solving.*;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.solving.*;
import com.verifiedai.solving.application.*;
import com.verifiedai.solving.application.canonical.*;
import com.verifiedai.solving.domain.model.SolveState;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import com.verifiedai.solving.infrastructure.persistence.*;
import com.verifiedai.solving.infrastructure.privacy.SolvingLifecycleContributor;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class CanonicalSolutionIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired CanonicalSolutionService service;
    @Autowired JdbcCanonicalSolutionRepository repository;
    @Autowired SolveStore accepted;
    @Autowired SolveSourceAccess sources;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired SolvingLifecycleContributor privacy;
    @Autowired com.verifiedai.identity.application.AccountPrivacyApplicationService accountPrivacy;
    SolverSourceFixture fixture;
    UUID user,session;
    SolveSource source;
    @BeforeEach void setup() {
        fixture=new SolverSourceFixture(jdbc);user=fixture.insertUser();
        session=fixture.insertSupportedParse(user,SolverSourceFixture.equationParseJson("x + 1 = 2"),1);
        canonical.canonicalize(user,session);source=tx.execute(s->sources.current(user,session));
    }
    UUID persist(String artifact) {
        return tx.execute(s->{
            sources.lockOwner(user,session);var now=Instant.now();
            var last=accepted.latest(source.canonicalProblemId());
            var job=accepted.insert(source,"canonical-fixture-"+UUID.randomUUID(),last.map(j->j.id()).orElse(null),last.map(j->j.attempt()+1).orElse(1),now,now.plusSeconds(60));
            accepted.claim(job.id(),now);accepted.finish(job.id(),SolveState.GENERATED_UNVERIFIED,null,null,now);
            accepted.candidate(job.id(),artifact,"{\"provider\":\"LOCAL_FIXTURE\",\"model\":\"synthetic\"}",now);
            return job.id();
        });
    }
    UUID persist() { return persist(SolverTestData.candidate(source).toString()); }
    CanonicalSolutions.Materialization materialize(UUID job) { return service.materialize(user,session,new Origin(Role.PRIMARY,job)); }
    int count(String table,UUID id) { return jdbc.queryForObject("select count(*) from "+table+" where solution_id=?",Integer.class,id); }
    ObjectNode graph() {
        var c=SolverTestData.candidate(source);var steps=c.withArray("steps");
        var b=steps.get(0).deepCopy();((ObjectNode)b).put("id","step-2").put("order",2).put("category","TRANSFORM");((ObjectNode)b).withArray("dependsOn").add("step-1");steps.add(b);
        var d=b.deepCopy();((ObjectNode)d).put("id","step-3").put("order",3);((ObjectNode)d).withArray("dependsOn").removeAll().add("step-2").add("step-1");steps.add(d);
        return c;
    }
    @Test void orderedGraphPersistsAndRoundTripsWithExactOriginalArtifact() {
        var raw=graph();var job=persist(raw.toString());var id=materialize(job).solutionId();
        var view=service.historical(user,session,id);
        assertThat(view.state()).isEqualTo(CanonicalSolutions.ReadState.GENERATED_UNVERIFIED);
        assertThat(view.solution().steps()).extracting(Step::order).containsExactly(1,2,3);
        assertThat(view.solution().steps().getLast().dependsOn()).containsExactly("step-2","step-1");
        assertThat(count("canonical_solution_dependencies",id)).isEqualTo(3);
        assertThat(new SolutionCandidateValidator().parse((String)accepted.candidate(job).orElseThrow().get("artifact"))).isEqualTo(raw);
        assertThat(service.current(user,session).solutions()).extracting(CanonicalSolution::id).containsExactly(id);
    }
    @Test void concurrentMaterializersReuseOneAtomicProjection() throws Exception {
        var job=persist(graph().toString());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var start=new CountDownLatch(1);
            var a=pool.submit(()->{start.await();return materialize(job);});var b=pool.submit(()->{start.await();return materialize(job);});start.countDown();
            var first=a.get(15,TimeUnit.SECONDS);assertThat(b.get(15,TimeUnit.SECONDS).solutionId()).isEqualTo(first.solutionId());
            assertThat(count("canonical_solution_steps",first.solutionId())).isEqualTo(3);
            assertThat(jdbc.queryForObject("select count(*) from canonical_solutions where source_job_id=?",Integer.class,job)).isEqualTo(1);
        }
    }
    @Test void interruptedProjectionRollsBackAllStepsAndRecoveryDoesNotTouchAcceptedEvidence() {
        var job=persist(graph().toString());
        jdbc.execute("create function reject_canonical_step_test() returns trigger language plpgsql as $$ begin if NEW.ordinal=2 then raise exception 'synthetic persistence failure'; end if; return NEW; end; $$");
        jdbc.execute("create trigger reject_canonical_step_test before insert on canonical_solution_steps for each row execute function reject_canonical_step_test()");
        try { assertThatThrownBy(()->materialize(job)).isInstanceOf(DataAccessException.class); }
        finally { jdbc.execute("drop trigger reject_canonical_step_test on canonical_solution_steps");jdbc.execute("drop function reject_canonical_step_test()"); }
        assertThat(jdbc.queryForObject("select count(*) from canonical_solutions where source_job_id=?",Integer.class,job)).isZero();
        assertThat(accepted.candidate(job)).isPresent();
        assertThat(service.recover(100)).isPositive();
        var result=materialize(job);assertThat(result.state()).isEqualTo(CanonicalSolutions.MaterializationState.REUSED);
        assertThat(count("canonical_solution_steps",result.solutionId())).isEqualTo(3);
        assertThat(accepted.get(job).orElseThrow().ledgerRecordId()).isNull();
    }
    @Test void malformedHistoricalCandidateHasDurableTypedFailureWithoutFabrication() {
        var job=persist("{\"schemaVersion\":\"solution-candidate-v1\"}");
        assertThat(materialize(job).failure()).isEqualTo(Failure.INVALID_CANDIDATE);
        assertThat(materialize(job).failure()).isEqualTo(Failure.INVALID_CANDIDATE);
        assertThat(accepted.candidate(job)).isPresent();
        assertThat(jdbc.queryForObject("select count(*) from canonical_materialization_failures where source_job_id=?",Integer.class,job)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from canonical_solutions where source_job_id=?",Integer.class,job)).isZero();
        assertThat(tx.execute(s->repository.pending(Version.CURRENT,100)).stream().map(a->a.origin().jobId())).doesNotContain(job);
        privacy.deleteUserData(user,Instant.now());
        assertThat(jdbc.queryForObject("select count(*) from canonical_materialization_failures where user_id=?",Integer.class,user)).isZero();
    }
    @Test void immutableUpdatesPartialDeletesAndInvalidDependenciesAreRejected() {
        var id=materialize(persist(graph().toString())).solutionId();
        assertThatThrownBy(()->jdbc.update("update canonical_solutions set content_jsonb=content_jsonb where id=?",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("delete from canonical_solutions where id=?",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("update canonical_solution_steps set explanation='changed' where solution_id=?",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("delete from canonical_solution_steps where solution_id=? and ordinal=3",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("delete from canonical_solution_dependencies where solution_id=?",id)).isInstanceOf(DataAccessException.class);
        var steps=service.historical(user,session,id).solution().steps();
        assertThatThrownBy(()->jdbc.update("insert into canonical_solution_dependencies values (?,?,?,?,?,?)",id,steps.get(0).id(),1,steps.get(2).id(),3,1)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("insert into canonical_solution_dependencies values (?,?,?,?,?,?)",id,steps.get(0).id(),1,steps.get(0).id(),1,1)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("insert into canonical_solution_dependencies values (?,?,?,?,?,?)",id,steps.get(2).id(),3,UUID.randomUUID(),1,3)).isInstanceOf(DataAccessException.class);
        assertThat(count("canonical_solution_steps",id)).isEqualTo(3);
    }
    @Test void differentNormalizerVersionsCoexistAndUnknownRepresentationIsExplicit() {
        var job=persist();var id=materialize(job).solutionId();var original=service.historical(user,session,id).solution();
        var previous=CanonicalTestData.copy(original,new Version("canonical-solution-v1","historical-normalizer-v0"),original.steps());
        tx.executeWithoutResult(s->repository.insert(previous));
        assertThat(service.historical(user,session,previous.id()).solution().version()).isEqualTo(previous.version());
        assertThat(service.historical(user,session,previous.id()).state()).isEqualTo(CanonicalSolutions.ReadState.HISTORICAL_VERSION);
        var incompatible=CanonicalTestData.copy(original,new Version("canonical-solution-v99","historical-normalizer-v0"),original.steps());
        tx.executeWithoutResult(s->repository.insert(incompatible));
        assertThat(service.historical(user,session,incompatible.id()).state()).isEqualTo(CanonicalSolutions.ReadState.UNSUPPORTED_REPRESENTATION);
        assertThat(service.current(user,session).solutions()).extracting(CanonicalSolution::id).containsExactly(id);
        assertThat(materialize(job).solutionId()).isEqualTo(id);
    }
    @Test void sourceSelectionChangePreservesHistoryAndHistoricalRecoveryNeverPromotesOldParse() {
        var job=persist();
        fixture.insertSupportedParse(user,session,SolverSourceFixture.equationParseJson("x + 2 = 4"),2);canonical.canonicalize(user,session);
        var id=materialize(job).solutionId();
        assertThat(service.historical(user,session,id).state()).isEqualTo(CanonicalSolutions.ReadState.HISTORICAL_SOURCE);
        assertThat(service.current(user,session).solutions()).isEmpty();
        jdbc.update("update problem_parses set review_required=true where id=(select current_parse_id from problem_sessions where id=?)",session);
        assertThat(service.current(user,session).state()).isEqualTo(CanonicalSolutions.ReadState.SOURCE_UNAVAILABLE);
    }
    @Test void inactiveCrossUserForgedUuidAndRoleConfusionAreRejected() {
        var job=persist();var id=materialize(job).solutionId();var other=fixture.insertUser();
        assertThatThrownBy(()->service.historical(other,session,id)).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->service.materialize(other,session,new Origin(Role.PRIMARY,job))).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->service.materialize(user,session,new Origin(Role.SECONDARY,job))).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->service.historical(user,session,UUID.randomUUID())).isInstanceOf(ApiProblemException.class);
        jdbc.update("update users set status='DELETION_REQUESTED' where id=?",user);
        assertThatThrownBy(()->service.current(user,session)).isInstanceOf(ApiProblemException.class);
    }
    @Test void databaseRejectsForgedOwnerSourceRevisionAndPrimaryIdentityMutation() {
        var job=persist();var id=materialize(job).solutionId();var original=service.historical(user,session,id).solution();var l=original.lineage();
        var version=new Version("canonical-solution-v1","forged-normalizer");var other=fixture.insertUser();
        var forged=new Lineage(other,l.sessionId(),l.canonicalProblemId(),l.parseId(),l.parseRevision(),l.canonicalRevision(),l.origin(),null);
        var copy=new CanonicalSolution(CanonicalSolution.identity(l.origin(),version),forged,version,original.provenance(),original.generationOrigin(),original.problemType(),original.taskType(),original.method(),original.finalStatement(),
            CanonicalTestData.copy(original,version,original.steps()).steps(),original.sourceAssumptionIds(),original.sourceConstraintIds(),original.derivedRestrictionIds(),original.ambiguities(),original.unresolvedIssues(),original.createdAt());
        assertThatThrownBy(()->tx.executeWithoutResult(s->repository.insert(copy))).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("update solve_jobs set operation_id=? where id=?",UUID.randomUUID(),job)).isInstanceOf(DataAccessException.class);
        assertThat(jdbc.queryForObject("select count(*) from canonical_solutions where user_id=?",Integer.class,other)).isZero();
    }
    @Test void accountExportContainsGraphAndDeletionCascadesAllProjectionContent() {
        var job=persist(graph().toString());var id=materialize(job).solutionId();
        var export=privacy.exportUserData(user);
        assertThat(export.get("canonicalSolutions").toString()).contains("step-1","step-2","reference_order","explanation");
        var request=accountPrivacy.requestExport(user);
        assertThat(accountPrivacy.downloadExport(user,request.exportId()).get("solutionCandidates").toString())
            .contains("canonicalSolutions","step-1","reference_order");
        assertThat(privacy.exportUserData(fixture.insertUser()).get("canonicalSolutions").toString()).isEqualTo("[]");
        privacy.deleteUserData(user,Instant.now());
        assertThat(count("canonical_solution_steps",id)).isZero();assertThat(count("canonical_solution_dependencies",id)).isZero();
        assertThat(accepted.candidate(job)).isEmpty();
        assertThatThrownBy(()->service.historical(user,session,id)).isInstanceOf(ApiProblemException.class);
    }
    @Test void acceptedRetryIsSeparateHistoricalProjection() {
        var first=persist();var second=persist();
        var a=materialize(first);var b=materialize(second);
        assertThat(a.solutionId()).isNotEqualTo(b.solutionId());
        assertThat(service.current(user,session).solutions()).hasSize(2);
        assertThat(accepted.candidate(first)).isPresent();assertThat(accepted.candidate(second)).isPresent();
    }
}
