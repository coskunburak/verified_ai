package com.verifiedai.solving.domain.model.canonical;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable projection of exactly one accepted candidate. Canonical does not mean correct. */
public record CanonicalSolution(UUID id,Lineage lineage,Version version,Provenance provenance,
    String generationOrigin,String problemType,String taskType,String method,MathStatement finalStatement,
    List<Step> steps,List<String> sourceAssumptionIds,List<String> sourceConstraintIds,
    List<String> derivedRestrictionIds,List<String> ambiguities,List<String> unresolvedIssues,Instant createdAt) {
    public enum Failure { UNSUPPORTED_SCHEMA, INVALID_CANDIDATE, SOURCE_MISMATCH, SOURCE_UNAVAILABLE, UNSUPPORTED_REPRESENTATION }
    public enum Role { PRIMARY, SECONDARY }
    public enum Category { RESTATE, TRANSFORM, CONCLUDE }
    public record Version(String representation,String normalizer) {
        public static final Version CURRENT=new Version("canonical-solution-v1","candidate-normalizer-v1");
        public Version {
            text(representation,64); text(normalizer,64);
            if(!representation.matches("[a-z][a-z0-9-]{0,63}") || !normalizer.matches("[a-z][a-z0-9-]{0,63}"))
                throw new IllegalArgumentException("Invalid canonical version identifier");
        }
    }
    public record Origin(Role role,UUID jobId) {
        public Origin { Objects.requireNonNull(role); Objects.requireNonNull(jobId); }
    }
    public record Lineage(UUID userId,UUID sessionId,UUID canonicalProblemId,UUID parseId,
        int parseRevision,int canonicalRevision,Origin origin,UUID parentPrimaryJobId) {
        public Lineage {
            Objects.requireNonNull(userId); Objects.requireNonNull(sessionId); Objects.requireNonNull(canonicalProblemId);
            Objects.requireNonNull(parseId); Objects.requireNonNull(origin);
            if(parseRevision<1 || canonicalRevision<1 || (origin.role()==Role.SECONDARY)!=(parentPrimaryJobId!=null))
                throw new IllegalArgumentException("Invalid candidate lineage");
        }
    }
    /** IDs reference the original job/evidence/usage records; optional metadata is never fabricated. */
    public record Provenance(UUID operationId,UUID ledgerRecordId,String provider,String model,
        String promptId,String promptVersion,String routePolicyVersion,String candidateSchema,int candidateVersion) {
        public Provenance {
            Objects.requireNonNull(operationId); text(provider,128); optional(model); optional(promptId);
            optional(promptVersion); optional(routePolicyVersion); text(candidateSchema,64);
            if(candidateVersion!=1 || !"solution-candidate-v1".equals(candidateSchema)) throw new IllegalArgumentException("Unsupported candidate");
        }
        private static void optional(String s) { if(s!=null) text(s,256); }
    }
    public record Step(UUID id,String candidateStepId,int order,Category category,MathStatement claim,
        String explanation,List<String> dependsOn) {
        public Step {
            Objects.requireNonNull(id); text(candidateStepId,32); Objects.requireNonNull(category); Objects.requireNonNull(claim);
            text(explanation,1000); dependsOn=strings(dependsOn,24,32,true);
            if(order<1 || order>24) throw new IllegalArgumentException("Invalid step order");
        }
    }
    public CanonicalSolution {
        Objects.requireNonNull(id); Objects.requireNonNull(lineage); Objects.requireNonNull(version);
        Objects.requireNonNull(provenance); Objects.requireNonNull(finalStatement); Objects.requireNonNull(createdAt);
        text(generationOrigin,32); text(problemType,64); text(taskType,64); text(method,64);
        if(!java.util.Set.of("LOCAL_FIXTURE","AI_MODEL").contains(generationOrigin)
            || !java.util.Set.of("ARITHMETIC_EXPRESSION","ALGEBRAIC_EXPRESSION","EQUATION","INEQUALITY").contains(problemType)
            || !java.util.Set.of("EVALUATE","SIMPLIFY","SOLVE_EQUATION","SOLVE_INEQUALITY").contains(taskType)
            || !java.util.Set.of("ARITHMETIC","ALGEBRAIC_TRANSFORMATION","ISOLATE_VARIABLE","INEQUALITY_TRANSFORMATION").contains(method))
            throw new IllegalArgumentException("Unsupported solution vocabulary");
        if(!id.equals(identity(lineage.origin(),version))) throw new IllegalArgumentException("Unstable solution identity");
        steps=List.copyOf(steps);
        if(steps.isEmpty() || steps.size()>24) throw new IllegalArgumentException("Invalid step count");
        var seen=new HashSet<String>(); int order=1;
        for(Step step:steps) {
            if(step.order()!=order++ || seen.contains(step.candidateStepId())
                || !seen.containsAll(step.dependsOn()) || !step.id().equals(stepIdentity(id,step.candidateStepId())))
                throw new IllegalArgumentException("Invalid ordered step graph");
            seen.add(step.candidateStepId());
        }
        sourceAssumptionIds=strings(sourceAssumptionIds,16,96,true);
        sourceConstraintIds=strings(sourceConstraintIds,16,96,true);
        derivedRestrictionIds=strings(derivedRestrictionIds,32,96,true);
        ambiguities=strings(ambiguities,8,500,false); unresolvedIssues=strings(unresolvedIssues,8,500,false);
    }
    public static UUID identity(Origin origin,Version version) {
        return uuid("verifiedai/canonical/"+origin.role()+"/"+origin.jobId()+"/"+version.representation()+"/"+version.normalizer());
    }
    public static UUID stepIdentity(UUID solution,String step) { return uuid(solution+"/step/"+step); }
    private static UUID uuid(String key) { return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)); }
    private static List<String> strings(List<String> values,int max,int length,boolean unique) {
        var result=List.copyOf(values);
        if(result.size()>max || unique && new HashSet<>(result).size()!=result.size()) throw new IllegalArgumentException("Invalid collection");
        result.forEach(s->text(s,length)); return result;
    }
    private static void text(String value,int max) {
        if(value==null || value.isBlank() || value.length()>max) throw new IllegalArgumentException("Invalid bounded text");
    }
}
