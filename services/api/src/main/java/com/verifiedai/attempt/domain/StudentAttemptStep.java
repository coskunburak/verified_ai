package com.verifiedai.attempt.domain;
/** Exact learner text and normalized syntax are separate from verifier evidence. */
public record StudentAttemptStep(int index,String content,String normalizedStatement) {
    public StudentAttemptStep {
        if(index<1 || index>12 || !AttemptAnalysisPolicy.validText(content))
            throw new IllegalArgumentException("Invalid learner step");
    }
}
