package com.verifiedai.solving.domain.repository;

import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Solving-owned storage boundary. Implementations never resolve problem source authority. */
public interface CanonicalSolutionRepository {
    record AcceptedArtifact(UUID userId,UUID sessionId,UUID canonicalProblemId,UUID parseId,int parseRevision,
        Integer canonicalRevision,Origin origin,UUID parentPrimaryJobId,UUID operationId,UUID ledgerRecordId,String schema,
        String artifactJson,String provenanceJson,Instant acceptedAt) { }
    final class UnsupportedRepresentation extends RuntimeException {
        public UnsupportedRepresentation() { super("Unsupported canonical representation"); }
    }
    Optional<AcceptedArtifact> accepted(UUID user,UUID session,Origin origin);
    List<AcceptedArtifact> pending(Version version,int limit);
    Optional<CanonicalSolution> find(UUID user,UUID session,Origin origin,Version version);
    Optional<CanonicalSolution> find(UUID user,UUID session,UUID id);
    List<CanonicalSolution> forSource(UUID user,UUID session,UUID canonical,int revision,Version version);
    CanonicalSolution insert(CanonicalSolution solution);
    Optional<Failure> failure(UUID user,UUID session,Origin origin,Version version);
    void fail(AcceptedArtifact artifact,Version version,Failure failure,Instant now);
}
