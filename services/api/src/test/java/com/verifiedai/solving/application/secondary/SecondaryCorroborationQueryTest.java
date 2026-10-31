package com.verifiedai.solving.application.secondary;

import com.verifiedai.problem.application.solving.SolveSourceAccess;
import com.verifiedai.solving.application.SecondaryCorroborationQuery;
import com.verifiedai.solving.domain.model.secondary.SecondaryState;
import com.verifiedai.solving.infrastructure.persistence.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static com.verifiedai.verification.PlanningTestData.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecondaryCorroborationQueryTest {
    final SolveSourceAccess sources = mock(SolveSourceAccess.class);
    final SolveStore primary = mock(SolveStore.class);
    final SecondarySolveStore store = mock(SecondarySolveStore.class);
    final SecondaryCorroborationQuery query = new SecondaryCorroborationQuery(sources, primary, store);
    final com.verifiedai.solving.domain.model.canonical.CanonicalSolution solution = answer(equation());
    String agreement(String outcome) {
        return "{\"schemaVersion\":\"blind-agreement-v1\",\"comparisonPolicyVersion\":\"exact-visible-answer-v1\","
            + "\"outcome\":\"" + outcome + "\",\"reason\":\"fixture\",\"sameMethod\":false,\"primaryStepCount\":1,\"secondaryStepCount\":1,"
            + "\"independence\":\"APPROVED_DISTINCT_MODEL_SHARED_FAILURE_RISK\",\"mathematicalVerificationPerformed\":false}";
    }
    SecondarySolveStore.Corroboration row(String json, int revision, SecondaryState state) {
        var l = solution.lineage();
        return new SecondarySolveStore.Corroboration(UUID.randomUUID(), l.canonicalProblemId(), l.parseId(), revision, l.canonicalRevision(), state, json);
    }
    @Test void ownedTypedQueryValidatesEveryComparisonVersionIndependenceAndProofFlag() {
        for (String outcome : List.of("AGREEMENT", "DISAGREEMENT", "INCOMPATIBLE_ASSUMPTIONS", "INDETERMINATE", "STALE_LINEAGE")) {
            when(store.corroboration(any(), any(), any())).thenReturn(List.of(row(agreement(outcome), 1, SecondaryState.GENERATED_UNVERIFIED)));
            assertThat(query.snapshot(solution).signals()).containsExactly(SecondaryCorroborationQuery.Signal.valueOf(outcome));
        }
        for (var invalid : List.of(agreement("AGREEMENT").replace("blind-agreement-v1", "unknown"),
            agreement("AGREEMENT").replace("exact-visible-answer-v1", "unknown"),
            agreement("AGREEMENT").replace("APPROVED_DISTINCT_MODEL_SHARED_FAILURE_RISK", "SYNTHETIC_SOURCE_BLIND_ONLY"),
            agreement("AGREEMENT").replace("Performed\":false", "Performed\":true"), "{}", agreement("UNKNOWN"))) {
            when(store.corroboration(any(), any(), any())).thenReturn(List.of(row(invalid, 1, SecondaryState.GENERATED_UNVERIFIED)));
            assertThat(query.snapshot(solution).signals()).containsExactly(SecondaryCorroborationQuery.Signal.INVALID_INDEPENDENCE);
        }
        var l = solution.lineage();
        verify(sources, atLeastOnce()).lockOwner(l.userId(), l.sessionId());
        verify(store, atLeastOnce()).corroboration(l.userId(), l.sessionId(), l.origin().jobId());
    }
    @Test void stalePendingAndOverflowCannotHideAsAbsentCorroboration() {
        when(store.corroboration(any(), any(), any())).thenReturn(List.of(row(agreement("AGREEMENT"), 2, SecondaryState.GENERATED_UNVERIFIED)));
        assertThat(query.snapshot(solution).signals()).containsExactly(SecondaryCorroborationQuery.Signal.STALE_LINEAGE);
        when(store.corroboration(any(), any(), any())).thenReturn(List.of(row(null, 1, SecondaryState.RUNNING)));
        assertThat(query.snapshot(solution).signals()).containsExactly(SecondaryCorroborationQuery.Signal.PENDING);
        when(store.corroboration(any(), any(), any())).thenReturn(java.util.stream.IntStream.range(0, 17)
            .mapToObj(i -> row(agreement("AGREEMENT"), 1, SecondaryState.GENERATED_UNVERIFIED)).toList());
        assertThat(query.snapshot(solution).signals()).containsExactly(SecondaryCorroborationQuery.Signal.INVALID_INDEPENDENCE);
    }
    @Test void missingFalseFlagsAndMissingPublishedEvidenceCannotBecomeAgreementOrAbsence() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var valid = mapper.readTree(agreement("AGREEMENT"));
        for (var fields = valid.fieldNames(); fields.hasNext();) {
            String field = fields.next();
            var missing = ((com.fasterxml.jackson.databind.node.ObjectNode) valid).deepCopy();
            missing.remove(field);
            when(store.corroboration(any(), any(), any())).thenReturn(List.of(row(missing.toString(), 1, SecondaryState.GENERATED_UNVERIFIED)));
            assertThat(query.snapshot(solution).signals()).as(field).containsExactly(SecondaryCorroborationQuery.Signal.INVALID_INDEPENDENCE);
        }
        when(store.corroboration(any(), any(), any())).thenReturn(List.of(row(null, 1, SecondaryState.GENERATED_UNVERIFIED)));
        assertThat(query.snapshot(solution).signals()).containsExactly(SecondaryCorroborationQuery.Signal.INVALID_INDEPENDENCE);
        when(store.corroboration(any(), any(), any())).thenReturn(List.of(row(null, 1, SecondaryState.STALE)));
        assertThat(query.snapshot(solution).signals()).containsExactly(SecondaryCorroborationQuery.Signal.STALE_LINEAGE);
    }

}
