package com.verifiedai.solving;

import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.solving.application.SolutionCandidateValidator;
import com.verifiedai.solving.application.canonical.*;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import com.verifiedai.solving.domain.repository.CanonicalSolutionRepository.AcceptedArtifact;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CanonicalTestData {
    public static final Instant NOW=Instant.parse("2026-10-27T09:00:00Z");
    private CanonicalTestData() { }
    public static AcceptedArtifact artifact(SolveSource s,Role role,UUID job,UUID parent,String json) {
        return new AcceptedArtifact(s.userId(),s.problemSessionId(),s.canonicalProblemId(),s.problemParseId(),s.problemParseRevision(),
            role==Role.PRIMARY?null:s.canonicalRevision(),new Origin(role,job),parent,UUID.randomUUID(),null,"solution-candidate-v1",json,
            "{\"provider\":\"LOCAL_FIXTURE\",\"model\":\"synthetic\",\"promptId\":\"solver\",\"promptVersion\":\"v001\",\"routePolicyVersion\":\"fixture-v1\"}",NOW);
    }
    public static CanonicalCandidateMapper mapper() { return new CanonicalCandidateMapper(new SolutionCandidateValidator(),new CanonicalMathCodec()); }
    public static CanonicalSolution solution() {
        var s=SolverTestData.source();
        return mapper().map(artifact(s,Role.PRIMARY,UUID.randomUUID(),null,SolverTestData.candidate(s).toString()),s,Version.CURRENT,NOW);
    }
    public static CanonicalSolution copy(CanonicalSolution s,Version version,List<Step> steps) {
        var id=CanonicalSolution.identity(s.lineage().origin(),version);
        var remapped=steps.stream().map(t->new Step(CanonicalSolution.stepIdentity(id,t.candidateStepId()),t.candidateStepId(),t.order(),t.category(),t.claim(),t.explanation(),t.dependsOn())).toList();
        return new CanonicalSolution(id,s.lineage(),version,s.provenance(),s.generationOrigin(),s.problemType(),s.taskType(),s.method(),s.finalStatement(),remapped,
            s.sourceAssumptionIds(),s.sourceConstraintIds(),s.derivedRestrictionIds(),s.ambiguities(),s.unresolvedIssues(),s.createdAt());
    }
}
