package com.verifiedai.calculus;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.solving.application.*;
import com.verifiedai.solving.application.canonical.*;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution.*;
import com.verifiedai.solving.domain.repository.CanonicalSolutionRepository.AcceptedArtifact;
import com.verifiedai.verification.application.VerificationSourceReader;
import com.verifiedai.verification.domain.model.*;
import com.verifiedai.verification.domain.policy.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CalculusCandidatePlanningTest {
    @Test void bothOriginsPreserveStableIdentityStepsAndVersionedProvenanceAndPlanExplicitMethods() {
        for (var example : CalculusIntegrationData.cases()) {
            var source=CalculusIntegrationData.source(example.path("parse"));
            var candidate=CalculusIntegrationData.candidate(source,example.path("candidateExpression"));
            var validator=new SolutionCandidateValidator(); var codec=new CanonicalMathCodec();
            var mapper=new CanonicalCandidateMapper(validator,codec);
            for (var role : Role.values()) {
                var origin=new Origin(role,UUID.randomUUID()); var operation=UUID.randomUUID();
                var artifact=new AcceptedArtifact(source.userId(),source.problemSessionId(),source.canonicalProblemId(),source.problemParseId(),
                    source.problemParseRevision(),source.canonicalRevision(),origin,role==Role.SECONDARY?UUID.randomUUID():null,
                    operation,null,"solution-candidate-v2",candidate.toString(),"{\"provider\":\"LOCAL_FIXTURE\",\"model\":\"owner-calculus-fixture-v1\"}",Instant.EPOCH);
                var solution=mapper.map(artifact,source,Version.CALCULUS,Instant.EPOCH);
                assertThat(solution.id()).isEqualTo(CanonicalSolution.identity(origin,Version.CALCULUS));
                assertThat(solution.provenance().operationId()).isEqualTo(operation);
                assertThat(solution.steps().getFirst().id()).isEqualTo(CanonicalSolution.stepIdentity(solution.id(),"step-1"));
                var capabilities=new VerificationCapabilities(); capabilities.configureCalculus(true,true,"bounded-calculus-v1","test");
                var planner=new VerificationPlanner(capabilities); var typed=new VerificationSourceReader(codec).read(source);
                assertThat(planner.plan(typed,solution).plan().executionAvailability()).isEqualTo(VerificationPlan.ExecutionAvailability.NOT_EXECUTABLE);
                capabilities.calculusReadiness(true);
                var plan=planner.plan(typed,solution).plan();
                var wrongVariable=new PlanningSource(typed.identity(),typed.problemType(),typed.taskType(),typed.statements(),
                    Map.of("y",PlanningSource.Domain.REAL),typed.restrictions(),typed.assumptionIds(),typed.constraintIds(),
                    typed.derivedRestrictionIds(),typed.reviewRequired(),typed.calculus());
                assertThat(planner.plan(wrongVariable,solution).reason()).isEqualTo(VerificationPlan.Reason.UNDECLARED_VARIABLE);
                assertThat(plan.policyVersion()).isEqualTo(VerificationPlanner.CALCULUS_POLICY);
                assertThat(plan.methods().getFirst().method().name()).isEqualTo(candidate.path("method").asText());
                assertThat(plan.executionAvailability()).isEqualTo(VerificationPlan.ExecutionAvailability.EXECUTABLE);
                capabilities.configureCalculus(false,true,"bounded-calculus-v1","test");
                assertThat(planner.plan(typed,solution).plan().executionAvailability()).isEqualTo(VerificationPlan.ExecutionAvailability.NOT_EXECUTABLE);
            }
        }
    }
    @Test void candidatesCannotRedefineSourceDowngradeForgeRevisionsOrAddVariables() {
        var example=CalculusIntegrationData.cases().getFirst(); var source=CalculusIntegrationData.source(example.path("parse"));
        var valid=CalculusIntegrationData.candidate(source,example.path("candidateExpression")); var validator=new SolutionCandidateValidator();
        for (String field : List.of("original","interval","approach","variable","calculus")) {
            var bad=valid.deepCopy(); bad.putObject(field);
            assertThatThrownBy(()->validator.validate(bad.toString(),source,"LOCAL_FIXTURE")).isInstanceOf(CandidateRejected.class);
        }
        var wrong=valid.deepCopy(); wrong.put("canonicalRevision",99);
        assertThatThrownBy(()->validator.validate(wrong.toString(),source,"LOCAL_FIXTURE")).isInstanceOf(CandidateRejected.class);
        var downgrade=valid.deepCopy(); downgrade.put("schemaVersion","solution-candidate-v1");
        assertThatThrownBy(()->validator.validate(downgrade.toString(),source,"LOCAL_FIXTURE")).isInstanceOf(CandidateRejected.class);
        var mixed=downgrade.deepCopy(); mixed.remove("canonicalRevision"); mixed.put("candidateVersion",1).put("method","ARITHMETIC");
        assertThatThrownBy(()->validator.validate(mixed.toString(),source,"LOCAL_FIXTURE")).isInstanceOf(CandidateRejected.class);
        var symbol=valid.deepCopy(); ((ObjectNode)symbol.path("finalAnswer").path("expression").path("right").path("left")).put("symbol","y");
        assertThatThrownBy(()->validator.validate(symbol.toString(),source,"LOCAL_FIXTURE")).isInstanceOf(CandidateRejected.class);
    }
    @Test void productionUnapprovedAlgorithmAndGenericV1ActivationFailClosed() {
        var capabilities=new VerificationCapabilities();
        assertThatIllegalArgumentException().isThrownBy(()->capabilities.configureCalculus(true,true,"bounded-calculus-v1","production"));
        assertThatIllegalArgumentException().isThrownBy(()->capabilities.configureCalculus(true,true,"unknown","test"));
        assertThatIllegalArgumentException().isThrownBy(()->new VerificationCapabilities(true,true,"FINITE_LIMIT"));
        assertThatIllegalArgumentException().isThrownBy(()->new VerificationCapabilities(true,true,"DERIVATIVE_BACKCHECK"));
    }
}
