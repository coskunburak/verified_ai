package com.verifiedai.attempt.application;

import com.fasterxml.jackson.databind.*;
import com.verifiedai.attempt.domain.*;
import com.verifiedai.solving.application.canonical.CanonicalMathCodec;
import com.verifiedai.verification.application.VerificationSourceReader;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AttemptEvaluationPreparerTest {
    final ObjectMapper mapper=new ObjectMapper();
    @Test void completionRequiresTaskAndSolvedFormNotLocalEquivalence() throws Exception {
        try(var in=getClass().getResourceAsStream("/schemas/fixtures/attempt-evaluation-golden.json")) {
            for(var c:mapper.readTree(in).path("cases")) {
                var r=c.path("request"); var last=r.path("steps").get(r.path("steps").size()-1).path("statement");
                assertThat(AttemptEvaluationPreparer.completeForm(c.path("task").asText(),r,last))
                    .as(c.path("id").asText()).isEqualTo(c.path("completeForm").asBoolean());
            }
        }
    }
    @Test void strictSharedEvidenceRetainsStableIdsAndNeverClaimsMissingFinal() throws Exception {
        try(var in=getClass().getResourceAsStream("/schemas/fixtures/attempt-evaluation-v1.json")) {
            var evaluation=mapper.readValue(in,AttemptEvaluation.class);
            assertThat(evaluation.finalAnswerStatus()).isEqualTo(AttemptEvaluation.FinalStatus.NOT_PROVIDED);
            assertThat(evaluation.analysis().outcome()).isEqualTo(AttemptOutcome.INCOMPLETE_VALID_SO_FAR);
            assertThat(evaluation.stepIds()).hasSize(evaluation.analysis().steps().size());
            assertThatThrownBy(()->new AttemptEvaluation(evaluation.schemaVersion(),evaluation.id(),evaluation.attemptId(),evaluation.ownerId(),
                evaluation.problemSessionId(),evaluation.source(),1,evaluation.policyVersion(),evaluation.executionId(),evaluation.createdAt(),
                evaluation.stepIds(),evaluation.analysis(),false,null,AttemptEvaluation.FinalStatus.VERIFIED,null,false)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void unknownSourceAndMissingFinalRemainExplicit() throws Exception {
        AttemptStructuredWork work;
        try(var in=getClass().getResourceAsStream("/schemas/fixtures/attempt-evaluation-work-v1.json")) { work=mapper.readValue(in,AttemptStructuredWork.class); }
        var codec=new CanonicalMathCodec(); var preparer=new AttemptEvaluationPreparer(codec,new VerificationSourceReader(codec));
        var source=new com.verifiedai.problem.application.solving.SolveSource(work.source().canonicalId(),work.ownerId(),work.problemSessionId(),
            work.source().parseId(),1,1,"EQUATION","SOLVE_EQUATION","{}");
        var prepared=preparer.prepare(source,work,UUID.randomUUID());
        assertThat(prepared.sequence()).isNull(); assertThat(prepared.finalAnswer()).isNull();
        assertThat(prepared.completeForm()).isFalse(); assertThat(prepared.finalStepId()).isNull();
    }
    @Test void requestContractRejectsAuthorityCoercionAndMissingRevision() throws Exception {
        String name="attempt-evaluation-request-v1";
        try(var in=getClass().getResourceAsStream("/schemas/fixtures/"+name+".json")) {
            var request=(com.fasterxml.jackson.databind.node.ObjectNode)mapper.readTree(in);
            com.verifiedai.sharedkernel.contracts.VersionedJsonContract.validate(name,request.toString());
            for(String key:List.of("ownerId","provider","verdict")) {
                var bad=request.deepCopy().put(key,"forged");
                assertThatThrownBy(()->com.verifiedai.sharedkernel.contracts.VersionedJsonContract.validate(name,bad.toString())).isInstanceOf(IllegalArgumentException.class);
            }
            var bad=request.deepCopy().put("workRevision","1");
            assertThatThrownBy(()->com.verifiedai.sharedkernel.contracts.VersionedJsonContract.validate(name,bad.toString())).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
