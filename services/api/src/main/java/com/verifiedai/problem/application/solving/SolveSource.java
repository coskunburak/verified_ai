package com.verifiedai.problem.application.solving;

import java.util.UUID;

/** Immutable canonical snapshot; the problem module remains its authority. */
public record SolveSource(UUID canonicalProblemId, UUID userId, UUID problemSessionId,
    UUID problemParseId, int problemParseRevision, int canonicalRevision,
    String problemType, String taskType, String canonicalJson) { }
