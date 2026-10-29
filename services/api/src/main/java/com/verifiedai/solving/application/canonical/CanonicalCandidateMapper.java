package com.verifiedai.solving.application.canonical;

import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.solving.application.CandidateRejected;
import com.verifiedai.solving.application.SolutionCandidateValidator;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import com.verifiedai.solving.domain.repository.CanonicalSolutionRepository.AcceptedArtifact;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public final class CanonicalCandidateMapper {
    private final SolutionCandidateValidator validator;
    private final CanonicalMathCodec codec;
    public CanonicalCandidateMapper(SolutionCandidateValidator validator,CanonicalMathCodec codec) { this.validator=validator;this.codec=codec; }
    public CanonicalSolution map(AcceptedArtifact a,SolveSource source,Version version,Instant now) {
        if(!Version.supported(version)) throw new ProjectionRejected(Failure.UNSUPPORTED_REPRESENTATION);
        if(!java.util.Set.of("solution-candidate-v1","solution-candidate-v2").contains(a.schema())
            || !Version.forSchema(a.schema()).equals(version)) throw new ProjectionRejected(Failure.UNSUPPORTED_SCHEMA);
        if(!a.userId().equals(source.userId()) || !a.sessionId().equals(source.problemSessionId())
            || !a.canonicalProblemId().equals(source.canonicalProblemId()) || !a.parseId().equals(source.problemParseId())
            || a.parseRevision()!=source.problemParseRevision()
            || a.canonicalRevision()!=null && a.canonicalRevision()!=source.canonicalRevision())
            throw new ProjectionRejected(Failure.SOURCE_MISMATCH);
        try {
            var p=codec.parse(a.provenanceJson());
            String provider=optional(p,"provider");
            if(provider==null || !p.isObject()) throw new IllegalArgumentException("Missing original provider");
            if(p.hasNonNull("schemaVersion") && !a.schema().equals(optional(p,"schemaVersion")))
                throw new IllegalArgumentException("Original schema provenance mismatch");
            // JSONB text can contain added whitespace; compact it before reusing the original wire-size gate.
            var c=validator.validate(codec.parse(a.artifactJson()).toString(),source,provider);
            if(!a.schema().equals(c.schemaVersion())) throw new IllegalArgumentException("Accepted candidate version mismatch");
            var lineage=new Lineage(a.userId(),a.sessionId(),a.canonicalProblemId(),a.parseId(),a.parseRevision(),
                source.canonicalRevision(),a.origin(),a.parentPrimaryJobId());
            var id=CanonicalSolution.identity(a.origin(),version);
            var steps=c.steps().stream().map(s->new Step(CanonicalSolution.stepIdentity(id,s.id()),s.id(),s.order(),
                Category.valueOf(s.category()),codec.statement(s.claim()),s.explanation(),s.dependsOn())).toList();
            return new CanonicalSolution(id,lineage,version,new Provenance(a.operationId(),a.ledgerRecordId(),provider,
                optional(p,"model"),optional(p,"promptId"),optional(p,"promptVersion"),optional(p,"routePolicyVersion"),c.schemaVersion(),c.candidateVersion()),
                c.generationOrigin(),c.problemType(),c.taskType(),c.method(),codec.statement(c.finalAnswer()),steps,
                c.sourceAssumptionIds(),c.sourceConstraintIds(),c.derivedRestrictionIds(),c.ambiguities(),c.unresolvedIssues(),now);
        } catch(CandidateRejected | IllegalArgumentException e) { throw new ProjectionRejected(Failure.INVALID_CANDIDATE); }
    }
    private static String optional(com.fasterxml.jackson.databind.JsonNode p,String name) {
        var value=p.path(name);
        if(value.isMissingNode() || value.isNull()) return null;
        if(!value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException("Invalid provenance metadata");
        return value.asText();
    }
    public static final class ProjectionRejected extends RuntimeException {
        private final Failure failure;
        public ProjectionRejected(Failure failure) { super(failure.name());this.failure=failure; }
        public Failure failure() { return failure; }
    }
}
