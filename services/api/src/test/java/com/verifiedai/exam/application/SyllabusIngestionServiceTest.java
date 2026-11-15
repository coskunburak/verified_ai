package com.verifiedai.exam.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.verifiedai.curriculum.application.CurriculumTaxonomyCatalog;
import com.verifiedai.curriculum.application.CurriculumTaxonomySnapshot;
import com.verifiedai.exam.application.CandidateProfileServiceTest.InMemoryCandidateProfileRepository;
import com.verifiedai.exam.application.SyllabusIngestionService.IngestResult;
import com.verifiedai.exam.domain.CandidateProfileModels.AccessibilityAccommodations;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileRecord;
import com.verifiedai.exam.domain.CandidateProfileModels.FeasibilityStatus;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusConfirmRequest;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusIngestionResponse;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusMappingConfirmation;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusUploadStatus;
import com.verifiedai.exam.domain.ExamModels.ExamDefinition;
import com.verifiedai.exam.domain.ExamModels.Jurisdiction;
import com.verifiedai.exam.events.CandidateGoalUpdatedEvent;
import com.verifiedai.exam.infrastructure.vision.DeterministicSyllabusVisionGateway;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class SyllabusIngestionServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-11-15T10:00:00Z");
    private static final UUID USER_ID = UUID.fromString("92000000-0000-4000-8000-000000000101");
    private static final UUID OTHER_USER_ID = UUID.fromString("92000000-0000-4000-8000-000000000202");

    private InMemoryCandidateProfileRepository repository;
    private ExamRepository examRepository;
    private SimpleMeterRegistry meterRegistry;
    private List<CandidateGoalUpdatedEvent> publishedEvents;
    private SyllabusIngestionService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCandidateProfileRepository();
        examRepository = mock(ExamRepository.class);
        ExamDefinition tytExam = CandidateProfileServiceTest.buildTytMathDefinition();
        when(examRepository.findLatestByCode("TR_YKS_TYT_MATH", false)).thenReturn(Optional.of(tytExam));
        when(examRepository.findByCodeAndVersion("TR_YKS_TYT_MATH", "2026.1")).thenReturn(Optional.of(tytExam));

        repository.saveProfileAndRecordHistory(
                new CandidateProfileRecord(
                        UUID.randomUUID(), USER_ID, tytExam.id(), "TR_YKS_TYT_MATH", "2026.1",
                        null, Jurisdiction.TR, LocalDate.of(2027, 6, 20), new BigDecimal("35.0"),
                        60, 420, AccessibilityAccommodations.standardDefaults(), new BigDecimal("0.25"),
                        20, FeasibilityStatus.OPTIMAL, "init-key", "candidate-profile-v1", false, FIXED_NOW, FIXED_NOW
                ),
                new com.verifiedai.exam.domain.CandidateProfileModels.CandidateHorizonHistoryEntry(
                        UUID.randomUUID(), UUID.randomUUID(), "TR_YKS_TYT_MATH", "2026.1",
                        null, LocalDate.of(2027, 6, 20), null, 60, null, 420,
                        null, new BigDecimal("35.0"), new BigDecimal("1.00"), 20,
                        FeasibilityStatus.OPTIMAL, "INITIAL_SETUP", FIXED_NOW
                ),
                true
        );

        meterRegistry = new SimpleMeterRegistry();
        publishedEvents = new ArrayList<>();
        CurriculumTaxonomyCatalog catalog = mock(CurriculumTaxonomyCatalog.class);
        when(catalog.snapshot()).thenReturn(new CurriculumTaxonomySnapshot(
                "curriculum-v1-seed",
                java.util.Set.of("MATH"),
                java.util.Set.of("MATH.ARITHMETIC", "MATH.EQUATIONS", "MATH.FUNCTIONS", "MATH.EXPONENTS_AND_RADICALS", "MATH.CALCULUS"),
                java.util.Set.of(
                        "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                        "MATH.EQUATIONS.LINEAR_SOLVING",
                        "MATH.EQUATIONS.QUADRATIC_SOLVING",
                        "MATH.FUNCTIONS.LINEAR",
                        "MATH.EXPONENTS_AND_RADICALS.EXPONENTS",
                        "MATH.CALCULUS.DERIVATIVES",
                        "MATH.CALCULUS.INTEGRALS"
                ),
                java.util.Map.of(
                        "MATH.ARITHMETIC.INTEGER_OPERATIONS", "MATH.ARITHMETIC",
                        "MATH.EQUATIONS.LINEAR_SOLVING", "MATH.EQUATIONS",
                        "MATH.EQUATIONS.QUADRATIC_SOLVING", "MATH.EQUATIONS",
                        "MATH.FUNCTIONS.LINEAR", "MATH.FUNCTIONS",
                        "MATH.EXPONENTS_AND_RADICALS.EXPONENTS", "MATH.EXPONENTS_AND_RADICALS",
                        "MATH.CALCULUS.DERIVATIVES", "MATH.CALCULUS",
                        "MATH.CALCULUS.INTEGRALS", "MATH.CALCULUS"
                ),
                java.util.Map.of(
                        "MATH.ARITHMETIC", "MATH",
                        "MATH.EQUATIONS", "MATH",
                        "MATH.FUNCTIONS", "MATH",
                        "MATH.EXPONENTS_AND_RADICALS", "MATH",
                        "MATH.CALCULUS", "MATH"
                )
        ));
        service = new SyllabusIngestionService(
                repository,
                examRepository,
                catalog,
                new DeterministicSyllabusVisionGateway(),
                event -> {
                    if (event instanceof CandidateGoalUpdatedEvent goalEvent) {
                        publishedEvents.add(goalEvent);
                    }
                },
                meterRegistry,
                Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
                true,
                3,
                30,
                0.70d
        );
    }

    @Test
    void validatesMagicBytesAndRejectsSpoofedOrOversizedUploadsBeforeQuotaOrAi() {
        byte[] fakeTextBytes = "not-a-real-jpeg-image".getBytes(StandardCharsets.UTF_8);
        ApiProblemException fakeMedia = assertThrows(ApiProblemException.class, () ->
                service.ingestSyllabus(USER_ID, "TR_YKS_TYT_MATH", null, fakeTextBytes, "image/jpeg", "fake.jpg", null)
        );
        assertEquals(ApiErrorCode.SYLLABUS_MEDIA_UNSUPPORTED, fakeMedia.code());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, fakeMedia.status());

        byte[] htmlBytes = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        ApiProblemException htmlEx = assertThrows(ApiProblemException.class, () ->
                service.ingestSyllabus(USER_ID, "TR_YKS_TYT_MATH", null, htmlBytes, "application/pdf", "xss.pdf", null)
        );
        assertEquals(ApiErrorCode.SYLLABUS_MEDIA_UNSUPPORTED, htmlEx.code());

        byte[] oversizedPdf = new byte[(10 * 1024 * 1024) + 1];
        oversizedPdf[0] = '%';
        oversizedPdf[1] = 'P';
        oversizedPdf[2] = 'D';
        oversizedPdf[3] = 'F';
        oversizedPdf[4] = '-';
        ApiProblemException tooLarge = assertThrows(ApiProblemException.class, () ->
                service.ingestSyllabus(USER_ID, "TR_YKS_TYT_MATH", null, oversizedPdf, "application/pdf", "huge.pdf", null)
        );
        assertEquals(ApiErrorCode.SYLLABUS_MEDIA_UNSUPPORTED, tooLarge.code());
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, tooLarge.status());
        assertEquals(0, repository.countNonCachedSyllabusUploadsSince(USER_ID, FIXED_NOW.minusSeconds(3600)));
    }

    @Test
    void supportsAllAcceptedMagicBytesDeduplicatesViaSha256AndEnforcesRolling30DayQuota() {
        byte[] jpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x01, 0x02};
        byte[] png = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        byte[] webp = "RIFF1234WEBP".getBytes(StandardCharsets.US_ASCII);
        byte[] heic = "\u0000\u0000\u0000\u0018ftypheic\u0000\u0000\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1);

        IngestResult first = service.ingestSyllabus(USER_ID, "TR_YKS_TYT_MATH", null, jpeg, "image/jpeg", "plan1.jpg", "key-1");
        assertTrue(first.created());
        assertFalse(first.response().deduplicatedFromCache());
        assertEquals(2, first.response().remainingMonthlyQuota());
        assertEquals(SyllabusUploadStatus.PARSED_AUTO_CONFIRMED, first.response().status());
        assertFalse(first.response().trainingEligible());

        // Same image hash deduplicates at $0.00 spend without consuming quota
        IngestResult cached = service.ingestSyllabus(USER_ID, "TR_YKS_TYT_MATH", null, jpeg, "image/jpeg", "plan1-copy.jpg", "key-copy");
        assertFalse(cached.created());
        assertTrue(cached.response().deduplicatedFromCache());
        assertEquals(0, cached.response().llmTokensUsed());
        assertEquals(2, cached.response().remainingMonthlyQuota());
        assertEquals(1.0d, meterRegistry.counter("verifiedai_exam_syllabus_cache_hit_total").count());

        // Second & third unique uploads succeed (PNG & WEBP)
        IngestResult second = service.ingestSyllabus(USER_ID, "TR_YKS_TYT_MATH", null, png, "image/png", "plan2.png", null);
        assertEquals(1, second.response().remainingMonthlyQuota());
        assertEquals("image/png", second.response().mimeType());

        IngestResult third = service.ingestSyllabus(USER_ID, "TR_YKS_TYT_MATH", null, webp, "image/webp", "plan3.webp", null);
        assertEquals(0, third.response().remainingMonthlyQuota());
        assertEquals("image/webp", third.response().mimeType());

        // Fourth unique upload (HEIC) hits rolling 30-day quota
        ApiProblemException quotaEx = assertThrows(ApiProblemException.class, () ->
                service.ingestSyllabus(USER_ID, "TR_YKS_TYT_MATH", null, heic, "image/heic", "plan4.heic", null)
        );
        assertEquals(ApiErrorCode.SYLLABUS_QUOTA_EXCEEDED, quotaEx.code());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, quotaEx.status());
        assertEquals(1.0d, meterRegistry.counter("verifiedai_exam_syllabus_quota_exceeded_total").count());
        assertTrue(meterRegistry.timer("syllabus_ingestion_latency_seconds").count() >= 3);
    }

    @Test
    void triggersDisambiguationBelowThresholdAndEnforcesBolaAndLearnerConfirmation() {
        byte[] ambiguousPdf = "%PDF-1.4\nAMBIGUOUS_SYLLABUS\n".getBytes(StandardCharsets.UTF_8);
        IngestResult ingested = service.ingestSyllabus(
                USER_ID, "TR_YKS_TYT_MATH", "2026.1", ambiguousPdf, "application/pdf", "ambiguous-plan.pdf", null
        );
        SyllabusIngestionResponse upload = ingested.response();
        assertEquals(SyllabusUploadStatus.NEEDS_DISAMBIGUATION, upload.status());
        assertEquals(2, upload.parsedTopics().size());
        assertTrue(upload.parsedTopics().get(0).autoConfirmed());
        assertNotNull(upload.parsedTopics().get(0).matchedSkillId());
        assertFalse(upload.parsedTopics().get(1).autoConfirmed());
        assertNull(upload.parsedTopics().get(1).matchedSkillId());
        assertTrue(upload.parsedTopics().get(1).similarityScore().doubleValue() < 0.70d);

        // BOLA protection on GET and POST confirm
        ApiProblemException readDenied = assertThrows(ApiProblemException.class, () ->
                service.getUpload(OTHER_USER_ID, upload.uploadId())
        );
        assertEquals(ApiErrorCode.CANDIDATE_PROFILE_ACCESS_DENIED, readDenied.code());
        assertEquals(HttpStatus.FORBIDDEN, readDenied.status());

        // Unknown skill rejected on confirm
        ApiProblemException unknownSkill = assertThrows(ApiProblemException.class, () ->
                service.confirmDisambiguation(USER_ID, upload.uploadId(), new SyllabusConfirmRequest(
                        "syllabus-confirm-request-v1",
                        List.of(new SyllabusMappingConfirmation(1, "MATH.NONEXISTENT.SKILL"))
                ))
        );
        assertEquals(ApiErrorCode.EXAM_CURRICULUM_SKILL_UNKNOWN, unknownSkill.code());

        // Valid learner override confirmation succeeds and notifies studyplan event bus
        SyllabusIngestionResponse confirmed = service.confirmDisambiguation(USER_ID, upload.uploadId(), new SyllabusConfirmRequest(
                "syllabus-confirm-request-v1",
                List.of(new SyllabusMappingConfirmation(1, "MATH.EQUATIONS.LINEAR_SOLVING"))
        ));
        assertEquals(SyllabusUploadStatus.USER_CONFIRMED, confirmed.status());
        assertEquals("MATH.EQUATIONS.LINEAR_SOLVING", confirmed.parsedTopics().get(1).matchedSkillId());
        assertTrue(confirmed.parsedTopics().get(1).userConfirmed());
        assertNotNull(confirmed.confirmedAt());
        assertEquals(1, publishedEvents.size());
        assertEquals("SYLLABUS_CONFIRMED", publishedEvents.getFirst().triggerType());
        assertEquals(1.0d, meterRegistry.counter("verifiedai_exam_syllabus_user_confirmed_total").count());
    }
}
