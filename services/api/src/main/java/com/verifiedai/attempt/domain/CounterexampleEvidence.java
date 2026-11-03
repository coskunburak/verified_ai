package com.verifiedai.attempt.domain;
import java.math.BigInteger;
import java.util.Map;
public record CounterexampleEvidence(String schemaVersion, String witnessType, Map<String,ExactValue> variableValues,
    String previousStepEvaluation, String submittedStepEvaluation, String claimRefuted, boolean domainSatisfied,
    String verifierVersion, String explanationCode) {
    public record ExactValue(String numerator,String denominator) {
        public ExactValue {
            if(numerator==null || denominator==null || !numerator.matches("-?(0|[1-9][0-9]{0,319})")
                || !denominator.matches("[1-9][0-9]{0,319}")) throw new IllegalArgumentException("Invalid exact value");
        }
    }
    public CounterexampleEvidence {
        variableValues=Map.copyOf(variableValues);
        boolean equation="EQUATION_TRUTH".equals(witnessType);
        if(!"attempt-witness-v1".equals(schemaVersion) || !domainSatisfied || variableValues.size()>1
            || !java.util.Set.of(AttemptAnalysisVersion.ALGORITHM,"bounded-attempt-v2").contains(verifierVersion)
            || !(equation || "EXPRESSION_VALUES".equals(witnessType))
            || !(equation ? "EQUATION_EQUIVALENCE" : "EXPRESSION_IDENTITY").equals(claimRefuted)
            || !(equation ? "DIFFERENT_TRUTH" : "UNEQUAL_VALUES").equals(explanationCode)
            || previousStepEvaluation==null || submittedStepEvaluation==null
            || previousStepEvaluation.length()>650 || submittedStepEvaluation.length()>650
            || previousStepEvaluation.equals(submittedStepEvaluation)) throw new IllegalArgumentException("Invalid witness");
        if(variableValues.keySet().stream().anyMatch(k->!k.matches("[A-Za-z][A-Za-z0-9_]{0,15}")))
            throw new IllegalArgumentException("Invalid witness symbol");
        if(!equation && (!previousStepEvaluation.matches("-?(0|[1-9][0-9]*)(/[1-9][0-9]*)?")
            || !submittedStepEvaluation.matches("-?(0|[1-9][0-9]*)(/[1-9][0-9]*)?")))
            throw new IllegalArgumentException("Non-finite witness evaluation");
        if(equation && !(java.util.Set.of("true","false").contains(previousStepEvaluation)
            && java.util.Set.of("true","false").contains(submittedStepEvaluation))) throw new IllegalArgumentException("Invalid truth witness");
        if(!equation && equalRationals(previousStepEvaluation,submittedStepEvaluation))
            throw new IllegalArgumentException("Equal rational evaluations do not refute identity");
    }
    private static boolean equalRationals(String left,String right) {
        // Input syntax and size are checked above. No floating-point comparison or rounding.
        String[] a=left.split("/"), b=right.split("/");
        BigInteger an=new BigInteger(a[0]), bn=new BigInteger(b[0]);
        BigInteger ad=a.length==2?new BigInteger(a[1]):BigInteger.ONE;
        BigInteger bd=b.length==2?new BigInteger(b[1]):BigInteger.ONE;
        return an.multiply(bd).equals(bn.multiply(ad));
    }
}
