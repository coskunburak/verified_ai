package com.verifiedai.integration.verification;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.problem.application.canonicalization.CanonicalProblemApplicationService;
import com.verifiedai.problem.application.solving.*;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.solving.*;
import com.verifiedai.solving.application.canonical.*;
import com.verifiedai.solving.domain.model.SolveState;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import com.verifiedai.solving.infrastructure.persistence.*;
import com.verifiedai.verification.application.VerificationPlanning;
import com.verifiedai.verification.domain.model.VerificationPlan.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class VerificationPlanningIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired VerificationPlanning planning;
    @Autowired CanonicalSolutions solutions;
    @Autowired CanonicalProblemApplicationService canonical;
    @Autowired SolveSourceAccess sources;
    @Autowired SolveStore accepted;
    @Autowired JdbcCanonicalSolutionRepository repository;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    UUID user, session, solutionId, job;
    SolverSourceFixture fixture;
    @BeforeEach void setup() {
        fixture = new SolverSourceFixture(jdbc); user = fixture.insertUser();
        session = fixture.insertSupportedParse(user, SolverSourceFixture.equationParseJson("x + 1 = 2"), 1);
        canonical.canonicalize(user, session);
        job = tx.execute(status -> {
            var source = sources.current(user, session); var now = Instant.now();
            var row = accepted.insert(source, "planning-" + UUID.randomUUID(), null, 1, now, now.plusSeconds(60));
            accepted.claim(row.id(), now); accepted.finish(row.id(), SolveState.GENERATED_UNVERIFIED, null, null, now);
            accepted.candidate(row.id(), SolverTestData.candidate(source).toString(), "{\"provider\":\"LOCAL_FIXTURE\",\"model\":\"synthetic\"}", now);
            return row.id();
        });
        solutionId = solutions.materialize(user, session, new Origin(Role.PRIMARY, job)).solutionId();
    }
    @Test void realOwnedCanonicalSourceProducesRepeatablePlanWithoutExecutionOrWrites() {
        var before = jdbc.queryForObject("select count(*) from canonical_solutions where user_id=?", Integer.class, user);
        var first = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> planning.plan(user, session, solutionId));
        assertThat(first.state()).isEqualTo(State.PLANNED);
        assertThat(first.plan().executionAvailability()).isEqualTo(ExecutionAvailability.NOT_EXECUTABLE);
        assertThat(planning.plan(user, session, solutionId)).isEqualTo(first);
        assertThat(first.plan().lineage().userId()).isEqualTo(user);
        assertThat(jdbc.queryForObject("select count(*) from canonical_solutions where user_id=?", Integer.class, user)).isEqualTo(before);
        assertThat(accepted.get(job).orElseThrow().ledgerRecordId()).isNull();
        assertThat(solutions.historical(user, session, solutionId).state()).isEqualTo(CanonicalSolutions.ReadState.GENERATED_UNVERIFIED);
    }
    @Test void crossUserWrongSessionAndDeletedAccountAreRejected() {
        var other = fixture.insertUser();
        assertThatThrownBy(() -> planning.plan(other, session, solutionId)).isInstanceOf(ApiProblemException.class);
        var otherSession = fixture.insertSupportedParse(user, SolverSourceFixture.equationParseJson("x = 2"), 1);
        assertThatThrownBy(() -> planning.plan(user, otherSession, solutionId)).isInstanceOf(ApiProblemException.class);
        jdbc.update("update users set status='DELETION_REQUESTED' where id=?", user);
        assertThatThrownBy(() -> planning.plan(user, session, solutionId)).isInstanceOf(ApiProblemException.class);
    }
    @Test void newSelectedParseNeverReusesHistoricalPlanAsCurrent() {
        var prior = planning.plan(user, session, solutionId);
        fixture.insertSupportedParse(user, session, SolverSourceFixture.equationParseJson("x + 2 = 4"), 2);
        canonical.canonicalize(user, session);
        var result = planning.plan(user, session, solutionId);
        assertThat(prior.plan()).isNotNull(); assertThat(result.plan()).isNull();
        assertThat(result.state()).isEqualTo(State.STALE_SOURCE);
    }
    @Test void sourceReviewAndDeletionFailClosed() {
        jdbc.update("update problem_parses set review_required=true where id=(select current_parse_id from problem_sessions where id=?)", session);
        assertThat(planning.plan(user, session, solutionId).state()).isEqualTo(State.SOURCE_UNAVAILABLE);
        jdbc.update("delete from problem_sessions where id=?", session);
        assertThatThrownBy(() -> planning.plan(user, session, solutionId)).isInstanceOf(ApiProblemException.class);
    }
    @Test void historicalNormalizerAndUnknownRepresentationCannotPlan() {
        var current = solutions.historical(user, session, solutionId).solution();
        var historical = CanonicalTestData.copy(current, new Version("canonical-solution-v1", "historical-normalizer-v0"), current.steps());
        var unsupported = CanonicalTestData.copy(current, new Version("canonical-solution-v99", "historical-normalizer-v0"), current.steps());
        tx.executeWithoutResult(status -> { repository.insert(historical); repository.insert(unsupported); });
        assertThat(planning.plan(user, session, historical.id()).reason()).isEqualTo(Reason.HISTORICAL_VERSION);
        assertThat(planning.plan(user, session, unsupported.id()).reason()).isEqualTo(Reason.UNSUPPORTED_REPRESENTATION);
    }
    @Test void callerTransactionIsRejectedBeforeNestedOwnerLocksCanDeadlock() {
        assertThatThrownBy(() -> tx.execute(status -> planning.plan(user, session, solutionId))).isInstanceOf(IllegalTransactionStateException.class);
    }
}
