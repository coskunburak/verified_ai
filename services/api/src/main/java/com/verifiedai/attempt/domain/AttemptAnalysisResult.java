package com.verifiedai.attempt.domain;
import java.util.*;
public record AttemptAnalysisResult(String schemaVersion,String verifierVersion,String taxonomyVersion,
    UUID executionId,String correlationId,AttemptOutcome outcome,Integer firstWrongStepIndex,String domain,List<Step> steps, @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) com.fasterxml.jackson.databind.JsonNode naturalDomain, @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String domainBasis, @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) com.fasterxml.jackson.databind.JsonNode solutionSet) {
    public AttemptAnalysisResult(String schemaVersion,String verifierVersion,String taxonomyVersion,UUID executionId,String correlationId,AttemptOutcome outcome,Integer firstWrongStepIndex,String domain,List<Step> steps) { this(schemaVersion,verifierVersion,taxonomyVersion,executionId,correlationId,outcome,firstWrongStepIndex,domain,steps,null,null,null); }
    public enum Reason { EXACT_IDENTITY, SAME_SOLUTION_SET, NON_EQUIVALENT, UNSUPPORTED_MATH, EARLIER_DIVERGENCE,
        EARLIER_UNCERTAINTY, TIMEOUT, RESOURCE_FAILURE, WORKER_FAILURE, ADMISSION_LIMIT, DISABLED, INVALID_RESPONSE, UNKNOWN_DOMAIN, UNDEFINED_SOURCE }
    public enum WitnessStatus { AVAILABLE, COUNTEREXAMPLE_UNAVAILABLE, NOT_APPLICABLE }
    public record Step(int index,StepVerdict verdict,Reason reasonCode,MistakeCode mistakeCode,
        WitnessStatus counterexampleStatus,CounterexampleEvidence counterexample, @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Diagnosis diagnosis,@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) StepDiff diff) {
        public Step(int index,StepVerdict verdict,Reason reasonCode,MistakeCode mistakeCode,WitnessStatus counterexampleStatus,CounterexampleEvidence counterexample) { this(index,verdict,reasonCode,mistakeCode,counterexampleStatus,counterexample,null,null); }
    }
    public record TokenRange(int start,int end) { public TokenRange { if(start<0 || end<=start || end>256) invalid(); } }
    public record StepDiff(String schemaVersion,String originalStepId,String submittedStepId,String changedExpressionPath,
        TokenRange originalTokenRange,TokenRange submittedTokenRange,String diffCategory,String supportingEvidenceId,String confidence,String displayFallback) {
        public StepDiff {
            if(!"step-diff-v1".equals(schemaVersion) || !"PROVEN_TRANSFORMATION".equals(diffCategory)
                || !Set.of("EXACT_STRUCTURAL","STEP_ONLY").contains(confidence) || !Set.of("SUBEXPRESSION","STEP_LEVEL").contains(displayFallback)
                || changedExpressionPath==null || !changedExpressionPath.matches("/[A-Za-z0-9_/]*")) invalid();
        }
    }
    public record Diagnosis(MistakeCode code,String evidenceId,String explanationKey,String changedExpressionPath,String displayFallback,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String classifierPolicyVersion) {
        public Diagnosis(MistakeCode code,String evidenceId,String explanationKey,String changedExpressionPath,String displayFallback) {
            this(code,evidenceId,explanationKey,changedExpressionPath,displayFallback,null);
        }
        public Diagnosis { if(classifierPolicyVersion!=null && !"structural-signatures-v1".equals(classifierPolicyVersion)) invalid(); if(code==null || evidenceId==null || evidenceId.length()>96 || explanationKey==null || !explanationKey.equals("attempt.mistake."+code.name()) || changedExpressionPath==null || !changedExpressionPath.matches("/[A-Za-z0-9_/]*") || !Set.of("STEP_LEVEL","SUBEXPRESSION").contains(displayFallback)) invalid(); }
    }
    public AttemptAnalysisResult {
        steps=List.copyOf(steps);
        boolean v2="attempt-analysis-v2".equals(schemaVersion);
        if(v2) {
            if(!"bounded-attempt-v2".equals(verifierVersion) || !"attempt-taxonomy-v2".equals(taxonomyVersion)
                || !Set.of("SOURCE_EXPLICIT","REVIEWED_REAL_TASK","UNRESOLVED").contains(domainBasis)
                || !Set.of("REAL_DOMAIN","UNSUPPORTED").contains(domain)) invalid();
        } else if(!AttemptAnalysisVersion.SCHEMA.equals(schemaVersion) || !AttemptAnalysisVersion.ALGORITHM.equals(verifierVersion)
            || !AttemptAnalysisVersion.TAXONOMY.equals(taxonomyVersion) || naturalDomain!=null || domainBasis!=null || solutionSet!=null
            || !Set.of("REAL_POLYNOMIAL","UNSUPPORTED").contains(domain)) invalid();
        if(executionId==null || correlationId==null || !correlationId.equals(executionId.toString())) invalid();
        if(v2) {
            validateSet(naturalDomain,0); validateSet(solutionSet,0);
            if("UNRESOLVED".equals(domainBasis) && "REAL_DOMAIN".equals(domain)) invalid();
        }
        if(steps.isEmpty() || steps.size()>12) invalid();
        boolean stopped=false, wrong=false, uncertain=false;
        for(int i=0;i<steps.size();i++) {
            var s=steps.get(i);
            if(s.index()!=i+1 || s.verdict()==null || s.reasonCode()==null || s.counterexampleStatus()==null
                || (stopped != (s.verdict()==StepVerdict.NOT_ASSESSED))
                || ((s.counterexampleStatus()==WitnessStatus.AVAILABLE)!=(s.counterexample()!=null))
                || ((s.verdict()==StepVerdict.FIRST_WRONG_STEP)!=(s.mistakeCode()!=null))) invalid();
            if(s.verdict()==StepVerdict.VALID_EQUIVALENT && (!(v2?"REAL_DOMAIN":"REAL_POLYNOMIAL").equals(domain)
                || !Set.of(Reason.EXACT_IDENTITY,Reason.SAME_SOLUTION_SET).contains(s.reasonCode()))) invalid();
            if(s.verdict()==StepVerdict.FIRST_WRONG_STEP && (!(v2?"REAL_DOMAIN":"REAL_POLYNOMIAL").equals(domain)
                || s.reasonCode()!=Reason.NON_EQUIVALENT || s.counterexampleStatus()==WitnessStatus.NOT_APPLICABLE)) invalid();
            if(s.verdict()!=StepVerdict.FIRST_WRONG_STEP && (s.counterexampleStatus()!=WitnessStatus.NOT_APPLICABLE || s.diagnosis()!=null)) invalid();
            if(s.verdict()==StepVerdict.NOT_ASSESSED && s.reasonCode()!=(wrong?Reason.EARLIER_DIVERGENCE:Reason.EARLIER_UNCERTAINTY)) invalid();
            if(s.verdict()==StepVerdict.UNCERTAIN && Set.of(Reason.EXACT_IDENTITY,Reason.SAME_SOLUTION_SET,Reason.NON_EQUIVALENT,
                Reason.EARLIER_DIVERGENCE,Reason.EARLIER_UNCERTAINTY).contains(s.reasonCode())) invalid();
            if(s.diff()!=null && (!v2 || s.verdict()!=StepVerdict.FIRST_WRONG_STEP || s.diagnosis()==null
                || !s.diff().supportingEvidenceId().equals(s.diagnosis().evidenceId())
                || !s.diff().submittedStepId().equals(executionId+":"+(i+1)) || !s.diff().originalStepId().equals(executionId+":"+i)
                || !s.diff().changedExpressionPath().equals(s.diagnosis().changedExpressionPath())
                || !s.diff().displayFallback().equals(s.diagnosis().displayFallback()))) invalid();
            if(!v2 && (s.diagnosis()!=null || (s.mistakeCode()!=null && s.mistakeCode()!=MistakeCode.ALG_TRANSFORMATION_INVALID))) invalid();
            if(v2 && s.verdict()==StepVerdict.FIRST_WRONG_STEP && (s.diagnosis()==null || s.diagnosis().code()!=s.mistakeCode()
                || !s.diagnosis().evidenceId().equals(executionId+":"+(i+1)))) invalid();
            if(s.counterexample()!=null && !verifierVersion.equals(s.counterexample().verifierVersion())) invalid();
            if(s.verdict()==StepVerdict.FIRST_WRONG_STEP) {
                if(!Objects.equals(firstWrongStepIndex,i+1)) invalid();
                wrong=true; stopped=true;
            }
            if(s.verdict()==StepVerdict.UNCERTAIN) { uncertain=true; stopped=true; }
            if(s.counterexample()!=null && s.verdict()!=StepVerdict.FIRST_WRONG_STEP) invalid();
        }
        if(wrong!=(firstWrongStepIndex!=null) || outcome!=(wrong?AttemptOutcome.STEP_ERROR_FOUND:
            uncertain?AttemptOutcome.NEEDS_REVIEW:AttemptOutcome.INCOMPLETE_VALID_SO_FAR)) invalid();
    }
    private static void validateSet(com.fasterxml.jackson.databind.JsonNode value,int depth) {
        if(value==null || value.isNull()) { if(depth!=0) invalid(); return; }
        if(!value.isObject() || depth>8) invalid();
        String kind=value.path("kind").asText();
        Set<String> expected=switch(kind) {
            case "EMPTY_SET","REALS" -> Set.of("kind");
            case "FINITE_SET" -> Set.of("kind","elements");
            case "INTERVAL" -> Set.of("kind","start","end","leftOpen","rightOpen");
            case "UNION","INTERSECTION" -> Set.of("kind","sets");
            default -> throw new IllegalArgumentException("Invalid set evidence");
        };
        var keys=new HashSet<String>(); value.fieldNames().forEachRemaining(keys::add);
        if(!keys.equals(expected)) invalid();
        if("INTERVAL".equals(kind)) {
            validateSetScalar(value.get("start")); validateSetScalar(value.get("end"));
            if(!value.get("leftOpen").isBoolean() || !value.get("rightOpen").isBoolean()) invalid();
        } else if("FINITE_SET".equals(kind)) {
            var elements=value.get("elements"); if(!elements.isArray() || elements.isEmpty() || elements.size()>24) invalid();
            elements.forEach(AttemptAnalysisResult::validateSetScalar);
        } else if(Set.of("UNION","INTERSECTION").contains(kind)) {
            var sets=value.get("sets"); if(!sets.isArray() || sets.size()<2 || sets.size()>24) invalid();
            sets.forEach(child -> validateSet(child,depth+1));
        }
    }
    private static void validateSetScalar(com.fasterxml.jackson.databind.JsonNode value) {
        if(!value.isTextual() || value.textValue().isEmpty() || value.textValue().length()>512) invalid();
    }
    private static void invalid() { throw new IllegalArgumentException("Invalid attempt evidence"); }
    public void validateFor(UUID id,int count) { if(!executionId.equals(id) || !correlationId.equals(id.toString()) || steps.size()!=count) invalid(); }
    public static AttemptAnalysisResult unavailable(UUID id,int count,Reason reason) {
        var steps=new ArrayList<Step>();
        for(int i=1;i<=count;i++) steps.add(new Step(i,i==1?StepVerdict.UNCERTAIN:StepVerdict.NOT_ASSESSED,
            i==1?reason:Reason.EARLIER_UNCERTAINTY,null,WitnessStatus.NOT_APPLICABLE,null));
        return new AttemptAnalysisResult(AttemptAnalysisVersion.SCHEMA,AttemptAnalysisVersion.ALGORITHM,AttemptAnalysisVersion.TAXONOMY,
            id,id.toString(),AttemptOutcome.NEEDS_REVIEW,null,"UNSUPPORTED",steps);
    }
}
