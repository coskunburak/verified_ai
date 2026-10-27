package com.verifiedai.solving.application.canonical;

import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import java.util.List;
import java.util.UUID;

/** Internal owner-scoped application contract. Secondary content is never a public API projection. */
public interface CanonicalSolutions {
    enum MaterializationState { MATERIALIZED, REUSED, REJECTED }
    enum ReadState { NOT_MATERIALIZED, GENERATED_UNVERIFIED, REVIEW_REQUIRED, HISTORICAL_SOURCE, HISTORICAL_VERSION, SOURCE_UNAVAILABLE, UNSUPPORTED_REPRESENTATION }
    record Materialization(MaterializationState state,UUID solutionId,Failure failure) { }
    record Read(UUID solutionId,ReadState state,CanonicalSolution solution) { }
    record Current(ReadState state,List<CanonicalSolution> solutions) {
        public Current { solutions=List.copyOf(solutions); }
    }
    Materialization materialize(UUID user,UUID session,Origin origin);
    Current current(UUID user,UUID session);
    Read historical(UUID user,UUID session,UUID solutionId);
}
