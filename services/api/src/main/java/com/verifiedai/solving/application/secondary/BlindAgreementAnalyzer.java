package com.verifiedai.solving.application.secondary;

import com.fasterxml.jackson.databind.JsonNode;
import com.verifiedai.solving.domain.model.SolutionCandidate;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashSet;
import org.springframework.stereotype.Component;

/** Corroboration of visible output only, never a correctness or proof-step verdict. */
@Component
public final class BlindAgreementAnalyzer {
    public enum Outcome { AGREEMENT, DISAGREEMENT, INDETERMINATE, INCOMPATIBLE_ASSUMPTIONS, STALE_LINEAGE }
    public record Evidence(String schemaVersion,String comparisonPolicyVersion,Outcome outcome,String reason,
        boolean sameMethod,int primaryStepCount,int secondaryStepCount,String independence,
        boolean mathematicalVerificationPerformed) { }
    public Evidence compare(SolutionCandidate a,SolutionCandidate b,String independence) {
        if(!a.canonicalProblemId().equals(b.canonicalProblemId()) || !a.problemParseId().equals(b.problemParseId())
            || a.problemParseRevision()!=b.problemParseRevision())
            return evidence(a,b,Outcome.STALE_LINEAGE,"LINEAGE_MISMATCH",independence);
        if(!new HashSet<>(a.sourceAssumptionIds()).equals(new HashSet<>(b.sourceAssumptionIds()))
            || !new HashSet<>(a.sourceConstraintIds()).equals(new HashSet<>(b.sourceConstraintIds()))
            || !new HashSet<>(a.derivedRestrictionIds()).equals(new HashSet<>(b.derivedRestrictionIds())))
            return evidence(a,b,Outcome.INCOMPATIBLE_ASSUMPTIONS,"RESTRICTIONS_DIFFER",independence);
        if(!a.problemType().equals(b.problemType()) || !a.taskType().equals(b.taskType())
            || !a.ambiguities().isEmpty() || !b.ambiguities().isEmpty()
            || !a.unresolvedIssues().isEmpty() || !b.unresolvedIssues().isEmpty())
            return evidence(a,b,Outcome.INDETERMINATE,"TASK_OR_UNRESOLVED_EVIDENCE",independence);
        JsonNode x=a.finalAnswer(),y=b.finalAnswer();
        // Exact identical output is structural agreement, not mathematical proof.
        if(x.equals(y)) return evidence(a,b,Outcome.AGREEMENT,"IDENTICAL_STRUCTURED_ANSWER",independence);
        JsonNode nx=null,ny=null;
        if("EXPRESSION".equals(x.path("kind").asText()) && "EXPRESSION".equals(y.path("kind").asText())) {
            nx=x.path("expression");ny=y.path("expression");
        } else if("RELATION".equals(x.path("kind").asText()) && "RELATION".equals(y.path("kind").asText())
            && x.path("left").equals(y.path("left")) && "VARIABLE".equals(x.path("left").path("kind").asText())
            && x.path("relation").equals(y.path("relation"))) {
            nx=x.path("right");ny=y.path("right");
            // Different inequality thresholds do not prove incompatible solution sets.
            if(!"EQUALS".equals(x.path("relation").asText()))
                return evidence(a,b,Outcome.INDETERMINATE,"INEQUALITY_REQUIRES_VERIFICATION",independence);
        }
        Rational rx=exact(nx,0),ry=exact(ny,0);
        if(rx!=null && ry!=null) return evidence(a,b,rx.equals(ry)?Outcome.AGREEMENT:Outcome.DISAGREEMENT,
            rx.equals(ry)?"EXACT_RATIONAL_MATCH":"INCOMPATIBLE_EXACT_RESULTS",independence);
        return evidence(a,b,Outcome.INDETERMINATE,"SYMBOLIC_EQUIVALENCE_UNPROVEN",independence);
    }
    private Evidence evidence(SolutionCandidate a,SolutionCandidate b,Outcome o,String reason,String independence) {
        return new Evidence("blind-agreement-v1","exact-visible-answer-v1",o,reason,a.method().equals(b.method()),
            a.steps().size(),b.steps().size(),independence,false);
    }
    private record Rational(BigInteger n,BigInteger d) {
        Rational {
            if(d.signum()==0) throw new ArithmeticException();
            if(d.signum()<0) { n=n.negate();d=d.negate(); }
            BigInteger gcd=n.gcd(d);n=n.divide(gcd);d=d.divide(gcd);
        }
    }
    private Rational exact(JsonNode n,int depth) {
        if(n==null || depth>16) return null;
        try {
            if("NUMBER".equals(n.path("kind").asText())) {
                String value=n.path("value").asText();
                if(value.length()>64 || !value.matches("-?[0-9]+(\\.[0-9]+)?")) return null;
                BigDecimal v=new BigDecimal(value);return new Rational(v.unscaledValue(),BigInteger.TEN.pow(Math.max(0,v.scale())));
            }
            // Bounded exact literals/divisions only; this is not a general expression evaluator.
            if("BINARY".equals(n.path("kind").asText()) && "DIVIDE".equals(n.path("operator").asText())) {
                Rational left=exact(n.path("left"),depth+1),right=exact(n.path("right"),depth+1);
                if(left==null || right==null || left.n.bitLength()>2048 || right.n.bitLength()>2048
                    || left.d.bitLength()>2048 || right.d.bitLength()>2048) return null;
                return new Rational(left.n.multiply(right.d),left.d.multiply(right.n));
            }
        } catch(ArithmeticException | NumberFormatException e) { return null; }
        return null;
    }
}
