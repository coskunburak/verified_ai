package com.verifiedai.attempt.domain;

public record AttemptEvaluationInput(String schemaVersion, AttemptSourceIdentity source, int workRevision, boolean realDomainConfirmed) {
    public AttemptEvaluationInput(String schemaVersion, AttemptSourceIdentity source, int workRevision) { this(schemaVersion,source,workRevision,false); }
    public AttemptEvaluationInput {
        if(!"attempt-evaluation-request-v1".equals(schemaVersion) || source==null || workRevision<1 || workRevision>30)
            throw new IllegalArgumentException("Invalid evaluation request");
    }
}
