package com.verifiedai.solving.application.secondary;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.solving.SolverTestData;
import com.verifiedai.solving.application.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static com.verifiedai.solving.application.secondary.BlindAgreementAnalyzer.Outcome.*;

class BlindAgreementAnalyzerTest {
    final SolutionCandidateValidator validator=new SolutionCandidateValidator();
    final BlindAgreementAnalyzer analyzer=new BlindAgreementAnalyzer();
    final com.verifiedai.problem.application.solving.SolveSource source=SolverTestData.source();
    ObjectNode candidate(String number) {
        var c=SolverTestData.candidate(source);c.set("finalAnswer",SolverTestData.relation(number));return c;
    }
    BlindAgreementAnalyzer.Evidence compare(ObjectNode a,ObjectNode b) {
        return analyzer.compare(validator.validate(a.toString(),source,"LOCAL_FIXTURE"),
            validator.validate(b.toString(),source,"LOCAL_FIXTURE"),"SYNTHETIC_SOURCE_BLIND_ONLY");
    }
    @Test void identicalWrongAnswersAreOnlyAgreementEvidence() {
        var e=compare(candidate("99"),candidate("99"));assertThat(e.outcome()).isEqualTo(AGREEMENT);
        assertThat(e.mathematicalVerificationPerformed()).isFalse();
        assertThat(validator.serialize(e)).doesNotContain("VERIFIED").contains("blind-agreement-v1");
    }
    @Test void exactDecimalAndRationalEquivalence() {
        var a=candidate("0.5");((ObjectNode)a.path("finalAnswer").path("right")).put("numericType","DECIMAL");
        var b=candidate("1");var fraction=((ObjectNode)b.path("finalAnswer")).putObject("right");
        fraction.put("kind","BINARY").put("operator","DIVIDE");
        fraction.set("left",SolverTestData.relation("1").path("right"));fraction.set("right",SolverTestData.relation("2").path("right"));
        assertThat(compare(a,b).outcome()).isEqualTo(AGREEMENT);
    }
    @Test void noFloatingToleranceOrApproximateEqualityIsInvented() {
        var a=candidate("0.5");var b=candidate("0.5000000000000001");
        ((ObjectNode)a.path("finalAnswer").path("right")).put("numericType","DECIMAL");
        ((ObjectNode)b.path("finalAnswer").path("right")).put("numericType","DECIMAL");
        assertThat(compare(a,b).outcome()).isEqualTo(DISAGREEMENT);
    }
    @Test void explicitIncompatibleExactResultsDisagree() { assertThat(compare(candidate("1"),candidate("2")).outcome()).isEqualTo(DISAGREEMENT); }
    @Test void structuralDifferencesAreIndeterminateRatherThanFalseDisagreement() {
        var a=candidate("1");var b=candidate("1");var n=((ObjectNode)b.path("finalAnswer")).putObject("right");
        n.put("kind","BINARY").put("operator","ADD");n.set("left",SolverTestData.relation("1").path("left"));
        n.set("right",SolverTestData.relation("1").path("left"));
        assertThat(compare(a,b).outcome()).isEqualTo(INDETERMINATE);
    }
    @Test void ambiguitiesPreservedAndStepCountsAreNotProofValidation() {
        var a=candidate("1");var b=candidate("1");b.withArray("ambiguities").add("Missing premise");
        assertThat(compare(a,b).outcome()).isEqualTo(INDETERMINATE);
        var e=compare(a,candidate("1"));assertThat(e.sameMethod()).isTrue();assertThat(e.primaryStepCount()).isEqualTo(1);
    }
    @Test void lineageAndAssumptionMismatchHaveExplicitOutcomes() throws Exception {
        var a=validator.validate(candidate("1").toString(),source,"LOCAL_FIXTURE");
        var otherSource=SolverTestData.source();
        var other=validator.validate(SolverTestData.candidate(otherSource).toString(),otherSource,"LOCAL_FIXTURE");
        assertThat(analyzer.compare(a,other,"SYNTHETIC").outcome()).isEqualTo(STALE_LINEAGE);
        var raw=candidate("1");raw.withArray("sourceAssumptionIds").add("different");
        var changed=new com.fasterxml.jackson.databind.ObjectMapper().treeToValue(raw,com.verifiedai.solving.domain.model.SolutionCandidate.class);
        assertThat(analyzer.compare(a,changed,"SYNTHETIC").outcome()).isEqualTo(INCOMPATIBLE_ASSUMPTIONS);
    }
    @Test void differentInequalityThresholdsAreIndeterminate() {
        var a=candidate("1");var b=candidate("2");
        a.put("problemType","INEQUALITY").put("taskType","SOLVE_INEQUALITY");
        b.put("problemType","INEQUALITY").put("taskType","SOLVE_INEQUALITY");
        ((ObjectNode)a.path("finalAnswer")).put("relation","GREATER_THAN");
        ((ObjectNode)b.path("finalAnswer")).put("relation","GREATER_THAN");
        try {
            var m=new com.fasterxml.jackson.databind.ObjectMapper();
            var first=m.treeToValue(a,com.verifiedai.solving.domain.model.SolutionCandidate.class);
            var second=m.treeToValue(b,com.verifiedai.solving.domain.model.SolutionCandidate.class);
            assertThat(analyzer.compare(first,second,"SYNTHETIC").outcome()).isEqualTo(INDETERMINATE);
        } catch(com.fasterxml.jackson.core.JsonProcessingException e) { throw new AssertionError(e); }
    }
    @Test void malformedCandidateIsRejectedBeforeComparison() {
        assertThatThrownBy(()->validator.validate("{}",source,"LOCAL_FIXTURE")).isInstanceOf(CandidateRejected.class);
    }
}
