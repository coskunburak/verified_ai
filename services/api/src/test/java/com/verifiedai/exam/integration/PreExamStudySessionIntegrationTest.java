package com.verifiedai.exam.integration;

import static org.junit.jupiter.api.Assertions.*;
import com.verifiedai.exam.infrastructure.persistence.PreExamStudySessionRepository;
import com.verifiedai.exam.infrastructure.persistence.PreExamStudySessionRepository.Snapshot;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Owner-run PostgreSQL/Flyway V062 gate using synthetic identities only. */
class PreExamStudySessionIntegrationTest extends PostgresIntegrationTestSupport {
    @Autowired PreExamStudySessionRepository repository;
    @Autowired JsonMapper mapper;
    @Autowired JdbcTemplate jdbc;
    private final Instant now = Instant.parse("2026-11-18T09:00:00Z");

    private Snapshot fixture(UUID owner) throws IOException {
        UUID session = UUID.randomUUID();
        String fingerprint = session.toString().replace("-", "").repeat(2);
        return new Snapshot(session, owner, "TR_YKS_TYT_MATH", fingerprint, "b".repeat(64), "c".repeat(64),
                payload("pre-exam-review-session", owner, session, "b".repeat(64)),
                payload("exam-cheat-sheet", owner, session, "c".repeat(64)), now, now.plusSeconds(3600));
    }
    private String payload(String name, UUID owner, UUID session, String etag) throws IOException {
        try (var input = getClass().getResourceAsStream("/schemas/exam/v1/examples/valid/" + name + ".json")) {
            assertNotNull(input);
            ObjectNode node = (ObjectNode) mapper.readTree(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            node.put("userId", owner.toString()); node.put("sessionId", session.toString()); node.put("etag", etag);
            return mapper.writeValueAsString(node);
        }
    }
    @Test void ownershipDeduplicationAndCascadeDeletion() throws Exception {
        UUID owner = UUID.randomUUID(), stranger = UUID.randomUUID();
        Snapshot row = fixture(owner);
        try {
            repository.insert(row);
            assertTrue(repository.findFresh(stranger, row.examCode(), row.sourceFingerprint(), now).isEmpty());
            assertEquals(row.sessionId(), repository.findFresh(owner, row.examCode(), row.sourceFingerprint(), now).orElseThrow().sessionId());
            assertTrue(repository.recordExport(stranger, row.sessionId(), "synthetic-export", now).isEmpty());
            assertEquals(true, repository.recordExport(owner, row.sessionId(), "synthetic-export", now).orElseThrow());
            assertEquals(false, repository.recordExport(owner, row.sessionId(), "synthetic-export", now).orElseThrow());
            assertEquals(1, repository.exportEventsByUserId(owner).size());
            assertEquals(1, repository.exportByUserId(owner).size());
            assertTrue(repository.exportByUserId(stranger).isEmpty());
            assertEquals(1, repository.deleteByUserId(owner));
            assertTrue(repository.exportEventsByUserId(owner).isEmpty());
        } finally { repository.deleteByUserId(owner); }
    }
    @Test void databasePrivacyChecksAndExpiry() throws Exception {
        UUID owner = UUID.randomUUID(); Snapshot row = fixture(owner);
        try {
            repository.insert(row);
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "UPDATE pre_exam_study_sessions SET training_eligible=TRUE WHERE session_id=?", row.sessionId()));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "UPDATE pre_exam_study_sessions SET llm_tokens_used=1 WHERE session_id=?", row.sessionId()));
            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                    "UPDATE pre_exam_study_sessions SET hours_remaining=48 WHERE session_id=?", row.sessionId()));
            assertThrows(DataIntegrityViolationException.class, () -> repository.insert(row));
            assertTrue(repository.findFresh(owner, row.examCode(), row.sourceFingerprint(), row.expiresAt()).isEmpty());
            assertTrue(repository.recordExport(owner, row.sessionId(), "expired", row.expiresAt()).isEmpty());
            repository.purgeExpired(row.expiresAt());
            assertTrue(repository.exportByUserId(owner).isEmpty());
        } finally { repository.deleteByUserId(owner); }
    }
    @Test void mismatchedPayloadCannotBePersisted() throws Exception {
        UUID owner = UUID.randomUUID(); Snapshot row = fixture(owner);
        var forged = new Snapshot(row.sessionId(), UUID.randomUUID(), row.examCode(), row.sourceFingerprint(),
                row.reviewEtag(), row.sheetEtag(), row.reviewJson(), row.sheetJson(), row.createdAt(), row.expiresAt());
        var failure = assertThrows(InvalidDataAccessApiUsageException.class, () -> repository.insert(forged));
        assertInstanceOf(IllegalArgumentException.class, failure.getCause());
        assertEquals("Snapshot identity or privacy invariant violated", failure.getCause().getMessage());
        assertTrue(repository.exportByUserId(owner).isEmpty());
        assertTrue(repository.exportByUserId(forged.userId()).isEmpty());
    }
}
