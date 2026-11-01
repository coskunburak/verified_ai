package com.verifiedai.tutoring.domain;
import java.time.Instant;
import java.util.UUID;
public record TutorMessage(String schemaVersion, UUID id, UUID tutorSessionId, TutorPolicy.Role role,
    long sequenceNumber, String content, Instant createdAt) {}
