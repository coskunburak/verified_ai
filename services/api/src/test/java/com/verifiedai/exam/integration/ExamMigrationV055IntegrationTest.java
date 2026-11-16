package com.verifiedai.exam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.verifiedai.exam.application.CandidateProfileRepository;
import com.verifiedai.exam.application.CandidateProfileService;
import com.verifiedai.exam.application.CandidateProfileService.UpsertResult;
import com.verifiedai.exam.application.SyllabusIngestionService;
import com.verifiedai.exam.application.SyllabusIngestionService.IngestResult;
import com.verifiedai.exam.domain.CandidateProfileModels.AccessibilityAccommodations;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileUpsertRequest;
import com.verifiedai.exam.domain.CandidateProfileModels.FeasibilityStatus;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusConfirmRequest;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusIngestionResponse;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusMappingConfirmation;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusUploadStatus;
import com.verifiedai.exam.infrastructure.privacy.ExamPrivacyLifecycleContributor;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ExamMigrationV055IntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private CandidateProfileService candidateProfileService;

    @Autowired
    private SyllabusIngestionService syllabusIngestionService;

    @Autowired
    private CandidateProfileRepository candidateProfileRepository;

    @Autowired
    private ExamPrivacyLifecycleContributor privacyContributor;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void v055PersistenceEndToEndAndPrivacyLifecycleAndConstraints() {
        UUID userId = UUID.randomUUID();
        jdbcClient.sql("""
                INSERT INTO users (id, status, created_at, updated_at)
                VALUES (:id, 'ACTIVE', NOW(), NOW())
                ON CONFLICT (id) DO NOTHING
                """)
                .param("id", userId)
                .update();

        UpsertResult upserted = candidateProfileService.upsertProfile(
                userId,
                new CandidateProfileUpsertRequest(
                        "candidate-profile-request-v1",
                        "TR_YKS_TYT_MATH",
                        "2026.2",
                        new BigDecimal("35.00"),
                        LocalDate.now().plusDays(120),
                        60,
                        420,
                        new AccessibilityAccommodations(new BigDecimal("1.50"), true, true, false),
                        new BigDecimal("0.2500"),
                        "PG_INT_SETUP",
                        "pg-profile-key-1"
                ),
                null
        );
        assertTrue(upserted.created());
        assertEquals(FeasibilityStatus.OPTIMAL, upserted.response().feasibilityStatus());
        assertEquals(98, upserted.response().adjustedSections().getFirst().adjustedDurationMinutes()); // ceil(65 * 1.5) = 98
        assertNotNull(upserted.response().activeTargetId());

        byte[] ambiguousPdf = "%PDF-1.4\nAMBIGUOUS_SYLLABUS\n".getBytes(StandardCharsets.UTF_8);
        IngestResult ingested = syllabusIngestionService.ingestSyllabus(
                userId, "TR_YKS_TYT_MATH", "2026.2", ambiguousPdf, "application/pdf", "ambiguous.pdf", "pg-ing-key-1"
        );
        assertTrue(ingested.created());
        assertEquals(SyllabusUploadStatus.NEEDS_DISAMBIGUATION, ingested.response().status());

        SyllabusIngestionResponse confirmed = syllabusIngestionService.confirmDisambiguation(
                userId,
                ingested.response().uploadId(),
                new SyllabusConfirmRequest(
                        "syllabus-confirm-request-v1",
                        List.of(new SyllabusMappingConfirmation(1, "MATH.EQUATIONS.LINEAR_ONE_VARIABLE"))
                )
        );
        assertEquals(SyllabusUploadStatus.USER_CONFIRMED, confirmed.status());

        // DB constraint rejects training_eligible = TRUE
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbcClient.sql("UPDATE candidate_profiles SET training_eligible = TRUE WHERE user_id = :userId")
                        .param("userId", userId)
                        .update()
        );

        // GDPR export includes candidateProfile, candidateHorizonHistory, syllabusUploads with trainingEligible=false
        Map<String, Object> exported = privacyContributor.exportUserData(userId);
        assertNotNull(exported.get("candidateProfile"));
        assertEquals(1, exported.get("candidateHorizonHistoryCount"));
        assertEquals(1, exported.get("syllabusUploadCount"));
        assertFalse((Boolean) exported.get("trainingEligible"));

        // 30-day purge deletes expired rows cleanly
        int purgedNone = privacyContributor.purgeExpiredSyllabusArtifacts(Instant.now());
        assertEquals(0, purgedNone);
        int purgedExpired = privacyContributor.purgeExpiredSyllabusArtifacts(Instant.now().plusSeconds(31L * 86400L));
        assertTrue(purgedExpired >= 1);
        assertTrue(candidateProfileRepository.findSyllabusUploadsByUserId(userId, 10).isEmpty());

        // GDPR delete removes remaining candidate profile & horizon history & targets
        privacyContributor.deleteUserData(userId, Instant.now());
        assertTrue(candidateProfileRepository.findProfileByUserId(userId).isEmpty());
        assertTrue(candidateProfileRepository.findHorizonHistoryByUserId(userId, 10).isEmpty());
    }
}
