package com.verifiedai.solving.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.verifiedai.solving.domain.model.SolveState;
import java.time.Instant;
import java.util.UUID;

public record SolveView(UUID jobId, UUID canonicalProblemId, UUID problemParseId, int problemParseRevision,
    int attempt, SolveState state, String failureCode, Instant createdAt, JsonNode candidate,
    String provider, String model, String promptVersion, String routePolicyVersion, String verificationNotice) { }
