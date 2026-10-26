package com.verifiedai.solving.application;

import com.verifiedai.problem.application.solving.SolveSourceAccess;
import com.verifiedai.solving.infrastructure.persistence.SolveStore;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Read-only recovery: no dispatch, entitlement debit, retry or candidate mutation. */
@Service
public class CurrentSolveQuery {
    private final SolveSourceAccess sources;
    private final SolveStore store;
    private final PrimarySolveService solves;
    private final TransactionTemplate tx;
    public CurrentSolveQuery(SolveSourceAccess sources, SolveStore store, PrimarySolveService solves, TransactionTemplate tx) {
        this.sources = sources; this.store = store; this.solves = solves; this.tx = tx;
    }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    public record CurrentSolveResponse(SolveView job) { }
    public CurrentSolveResponse current(UUID user, UUID session) {
        UUID job = tx.execute(status -> {
            sources.lockOwner(user, session);
            return store.latestForSession(user, session).map(value -> value.id()).orElse(null);
        });
        // Existing get rechecks owner and selected source, projecting historical results STALE.
        return new CurrentSolveResponse(job == null ? null : solves.get(user, session, job));
    }
}
