package com.verifiedai.solving.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.solving.SolverTestData;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SolutionCandidateValidatorTest {
    final SolutionCandidateValidator validator=new SolutionCandidateValidator();
    final SolveSource source=SolverTestData.source();
    ObjectNode valid() { return SolverTestData.candidate(source); }
    void rejects(ObjectNode n) { assertThatThrownBy(()->validator.validate(n.toString(),source,"LOCAL_FIXTURE")).isInstanceOf(CandidateRejected.class); }
    @Test void acceptsBoundedCandidateWithoutVerifyingEvenWrongAnswer() {
        var n=valid(); ((ObjectNode)n.path("finalAnswer").path("right")).put("value","999");
        var accepted=validator.validate(n.toString(),source,"LOCAL_FIXTURE");
        assertThat(accepted.finalAnswer().path("right").path("value").asText()).isEqualTo("999");
        assertThat(validator.serialize(accepted)).doesNotContain("verified", "confidence");
    }
    @Test void rejectsInvalidJsonDuplicatesAndTrailingTokens() {
        for(String s:new String[]{"{", valid()+" {}",valid().toString().replace("\"candidateVersion\":1", "\"candidateVersion\":1,\"candidateVersion\":1")})
            assertThatThrownBy(()->validator.validate(s,source,"LOCAL_FIXTURE")).isInstanceOf(CandidateRejected.class);
    }
    @Test void rejectsMissingAnswerUnknownSchemaAndInjectedAuthorityFields() {
        var n=valid();n.remove("finalAnswer");rejects(n);
        n=valid();n.put("schemaVersion","solution-candidate-v2");rejects(n);
        n=valid();n.put("verified",true);rejects(n);
        n=valid();n.put("confidence",1.0);rejects(n);
    }
    @Test void rejectsChangedSourceRevisionOrModelOrigin() {
        var n=valid();n.put("problemParseRevision",2);rejects(n);
        n=valid();n.put("canonicalProblemId",java.util.UUID.randomUUID().toString());rejects(n);
        n=valid();n.put("generationOrigin","AI_MODEL");rejects(n);
    }
    @Test void rejectsUnknownVariablesCodeAndUnsafeNumbers() {
        var n=valid(); ((ObjectNode)n.path("finalAnswer").path("left")).put("symbol","secret");rejects(n);
        for(String value:new String[]{"NaN","Infinity","__import__('os')","9".repeat(65)}) {
            n=valid();((ObjectNode)n.path("finalAnswer").path("right")).put("value",value);rejects(n);
        }
    }
    @Test void rejectsForwardReferencesAndOutOfOrderSteps() {
        var n=valid();((ObjectNode)n.path("steps").get(0)).putArray("dependsOn").add("step-1");rejects(n);
        n=valid();((ObjectNode)n.path("steps").get(0)).put("order",2);rejects(n);
    }
    @Test void retainsSourceRestrictionsAndRejectsFabricatedAssumptions() {
        var c=(ObjectNode)validator.parse(source.canonicalJson());
        c.withArray("derivedRestrictions").addObject().put("id","denominator-1");
        var restricted=new SolveSource(source.canonicalProblemId(),source.userId(),source.problemSessionId(),source.problemParseId(),1,1,
            source.problemType(),source.taskType(),c.toString());
        var n=SolverTestData.candidate(restricted);
        assertThat(validator.validate(n.toString(),restricted,"LOCAL_FIXTURE").derivedRestrictionIds()).containsExactly("denominator-1");
        n.putArray("derivedRestrictionIds");
        var missingRestriction=n.toString();
        assertThatThrownBy(()->validator.validate(missingRestriction,restricted,"LOCAL_FIXTURE")).isInstanceOf(CandidateRejected.class);
        n=valid();n.withArray("sourceAssumptionIds").add("invented");rejects(n);
    }
    @Test void rejectsNewDenominatorDomainsAndUnsupportedOperators() {
        var n=valid();var b=((ObjectNode)n.path("finalAnswer")).putObject("right");
        b.put("kind","BINARY").put("operator","DIVIDE");b.set("left",SolverTestData.relation("1").path("right"));
        b.set("right",SolverTestData.relation("1").path("left"));rejects(n);
        b.put("operator","EXECUTE");rejects(n);
    }
    @Test void rejectsOversizeOutputDepthAndVerificationClaimsInProse() {
        assertThatThrownBy(()->validator.validate(" ".repeat(65537),source,"LOCAL_FIXTURE")).isInstanceOf(CandidateRejected.class);
        var n=valid();((ObjectNode)n.path("steps").get(0)).put("explanation","VERIFIED by the model");rejects(n);
        n=valid(); var child=(ObjectNode)n.path("finalAnswer").path("right");
        for(int i=0;i<34;i++) {child.removeAll();child.put("kind","UNARY").put("operator","NEGATE");child=child.putObject("operand");}
        child.put("kind","NUMBER").put("numericType","INTEGER").put("value","1");rejects(n);
    }
    @Test void rejectsUnsupportedAndReviewRequiredCanonicalSource() {
        var c=(ObjectNode)validator.parse(source.canonicalJson());((ObjectNode)c.path("display")).put("reviewRequired",true);
        var bad=new SolveSource(source.canonicalProblemId(),source.userId(),source.problemSessionId(),source.problemParseId(),1,1,
            "EQUATION","SOLVE_EQUATION",c.toString());
        assertThatThrownBy(()->validator.sourceDocument(bad)).isInstanceOf(CandidateRejected.class);
    }
}
