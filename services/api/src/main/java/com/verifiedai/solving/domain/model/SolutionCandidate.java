package com.verifiedai.solving.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.UUID;

/** Structurally accepted public artifact, never a mathematical verification verdict. */
public record SolutionCandidate(String schemaVersion, UUID canonicalProblemId, UUID problemParseId,
    int problemParseRevision, int candidateVersion, String generationOrigin, String problemType,
    String taskType, String method, List<Step> steps, JsonNode finalAnswer,
    List<String> sourceAssumptionIds, List<String> sourceConstraintIds, List<String> derivedRestrictionIds,
    List<String> ambiguities, List<String> unresolvedIssues,
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT) int canonicalRevision) {
    public SolutionCandidate(String schemaVersion, UUID canonicalProblemId, UUID problemParseId,
        int problemParseRevision, int candidateVersion, String generationOrigin, String problemType,
        String taskType, String method, List<Step> steps, JsonNode finalAnswer,
        List<String> sourceAssumptionIds, List<String> sourceConstraintIds, List<String> derivedRestrictionIds,
        List<String> ambiguities, List<String> unresolvedIssues) {
        this(schemaVersion, canonicalProblemId, problemParseId, problemParseRevision, candidateVersion,
            generationOrigin, problemType, taskType, method, steps, finalAnswer, sourceAssumptionIds,
            sourceConstraintIds, derivedRestrictionIds, ambiguities, unresolvedIssues, 0);
    }
    public record Step(String id, int order, String category, JsonNode claim,
        String explanation, List<String> dependsOn) { }
}
