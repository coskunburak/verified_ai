package com.verifiedai.attempt.domain;
/** Exact learner text and normalized syntax are separate from verifier evidence. */
public record StudentAttemptStep(int index,String content,String normalizedStatement) {}
