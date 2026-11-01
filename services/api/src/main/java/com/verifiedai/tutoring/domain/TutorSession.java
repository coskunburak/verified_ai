package com.verifiedai.tutoring.domain;

import java.time.Instant;
import java.util.UUID;

/** Immutable historical identity. Currentness is derived; closing never reopens a session. */
public record TutorSession(String schemaVersion, UUID id, UUID problemSessionId,
    UUID canonicalId, int canonicalRevision, UUID parseId, int parseRevision, String primarySkillId,
    String contextSchemaVersion, Long profileVersion, String explanationDepth, String language, String mastery,
    String safetyPolicyVersion, TutorPolicy.Status status, boolean sourceCurrent, long messageCount,
    long version, Instant createdAt, Instant updatedAt, boolean trainingEligible) {
    public TutorSession withCurrent(boolean current) {
        return new TutorSession(schemaVersion,id,problemSessionId,canonicalId,canonicalRevision,parseId,parseRevision,
            primarySkillId,contextSchemaVersion,profileVersion,explanationDepth,language,mastery,safetyPolicyVersion,
            status,current,messageCount,version,createdAt,updatedAt,false);
    }
}
