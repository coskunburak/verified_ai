package com.verifiedai.exam.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.verifiedai.exam.application.CandidateProfileService;
import com.verifiedai.exam.application.CandidateProfileService.UpsertResult;
import com.verifiedai.exam.application.SyllabusIngestionService;
import com.verifiedai.exam.application.SyllabusIngestionService.IngestResult;
import com.verifiedai.exam.domain.CandidateProfileModels.AccessibilityAccommodations;
import com.verifiedai.exam.domain.CandidateProfileModels.AdjustedExamSection;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateHorizonHistoryEntry;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileHistoryResponse;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileResponse;
import com.verifiedai.exam.domain.CandidateProfileModels.FeasibilityStatus;
import com.verifiedai.exam.domain.CandidateProfileModels.ParsedSyllabusTopicItem;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusIngestionResponse;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusUploadStatus;
import com.verifiedai.exam.domain.ExamModels.Jurisdiction;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class CandidateProfileControllerWebMvcTest {

    private static final UUID USER_ID = UUID.fromString("92000000-0000-4000-8000-000000000101");
    private static final UUID OTHER_USER_ID = UUID.fromString("92000000-0000-4000-8000-000000000202");
    private static final UUID PROFILE_ID = UUID.fromString("92000000-0000-4000-8000-000000000301");
    private static final UUID UPLOAD_ID = UUID.fromString("92000000-0000-4000-8000-000000000401");
    private static final Instant FIXED_NOW = Instant.parse("2026-11-15T10:00:00Z");

    private CandidateProfileService profileService;
    private SyllabusIngestionService syllabusService;
    private MockMvc mockMvc;
    private MockMvc unauthenticatedMockMvc;

    @BeforeEach
    void setUp() {
        profileService = mock(CandidateProfileService.class);
        syllabusService = mock(SyllabusIngestionService.class);
        CandidateProfileController controller = new CandidateProfileController(profileService, syllabusService);

        Jwt jwt = Jwt.withTokenValue("test-jwt-token")
                .header("alg", "none")
                .subject(USER_ID.toString())
                .claim("tenant_id", USER_ID.toString())
                .build();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().equals(Jwt.class);
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory
                    ) {
                        return jwt;
                    }
                })
                .build();

        unauthenticatedMockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType().equals(Jwt.class);
                    }

                    @Override
                    public Object resolveArgument(
                            MethodParameter parameter,
                            ModelAndViewContainer mavContainer,
                            NativeWebRequest webRequest,
                            WebDataBinderFactory binderFactory
                    ) {
                        return null;
                    }
                })
                .build();
    }

    @Test
    void supportsDualMountedProfileEndpointsAndEnforcesAuthAndBola() throws Exception {
        CandidateProfileResponse response = sampleProfileResponse();
        when(profileService.upsertProfile(eq(USER_ID), any(), eq("key-put-001")))
                .thenReturn(new UpsertResult(response, true));
        when(profileService.upsertProfile(eq(USER_ID), any(), eq("key-post-001")))
                .thenReturn(new UpsertResult(response, false));
        when(profileService.getProfile(USER_ID)).thenReturn(response);
        when(profileService.getProfileHistory(USER_ID)).thenReturn(new CandidateProfileHistoryResponse(
                "candidate-profile-history-v1",
                USER_ID,
                List.of(new CandidateHorizonHistoryEntry(
                        UUID.randomUUID(), PROFILE_ID, "TR_YKS_TYT_MATH", "2026.1",
                        null, LocalDate.of(2027, 6, 20), null, 60, null, 420,
                        null, new BigDecimal("35.50"), new BigDecimal("1.25"), 15,
                        FeasibilityStatus.OPTIMAL, "INITIAL_SETUP", FIXED_NOW
                )),
                0,
                false
        ));

        String payload = """
                {
                  "schemaVersion": "candidate-profile-request-v1",
                  "examCode": "TR_YKS_TYT_MATH",
                  "pinnedExamVersion": "2026.1",
                  "targetScore": 35.50,
                  "targetDate": "2027-06-20",
                  "dailyStudyMinutes": 60,
                  "weeklyStudyMinutes": 420,
                  "accommodations": {
                    "extraTimeMultiplier": 1.25,
                    "highContrastEnabled": false,
                    "screenReaderOptimized": true,
                    "reducedMotionEnabled": false
                  }
                }
                """;

        // Primary /v1 mount
        mockMvc.perform(put("/v1/candidate/profile")
                        .header("Idempotency-Key", "key-put-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schemaVersion").value("candidate-profile-v1"))
                .andExpect(jsonPath("$.feasibilityStatus").value("OPTIMAL"))
                .andExpect(jsonPath("$.trainingEligible").value(false));

        // Alias /api/v1 mount
        mockMvc.perform(post("/api/v1/candidate/profile")
                        .header("Idempotency-Key", "key-post-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeExamCode").value("TR_YKS_TYT_MATH"));

        mockMvc.perform(get("/v1/candidate/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adjustedSections[0].adjustedDurationMinutes").value(82));

        mockMvc.perform(get("/api/v1/candidate/profile/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].changeReason").value("INITIAL_SETUP"));

        // BOLA mismatch on X-User-Id -> 403 CANDIDATE_PROFILE_ACCESS_DENIED
        mockMvc.perform(get("/v1/candidate/profile")
                        .header("X-User-Id", OTHER_USER_ID.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CANDIDATE_PROFILE_ACCESS_DENIED"));

        // Unauthenticated -> 401
        unauthenticatedMockMvc.perform(get("/v1/candidate/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CANDIDATE_PROFILE_ACCESS_DENIED"));
    }

    @Test
    void supportsMultipartAndJsonSyllabusIngestionConfirmationAndProblemResponses() throws Exception {
        SyllabusIngestionResponse syllabusResponse = sampleSyllabusResponse(SyllabusUploadStatus.NEEDS_DISAMBIGUATION);
        SyllabusIngestionResponse confirmedResponse = sampleSyllabusResponse(SyllabusUploadStatus.USER_CONFIRMED);

        MockMultipartFile filePart = new MockMultipartFile(
                "file",
                "tyt-schedule.jpg",
                "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0}
        );

        when(syllabusService.ingestSyllabus(eq(USER_ID), eq("TR_YKS_TYT_MATH"), eq("2026.1"), any(), eq("image/jpeg"), eq("tyt-schedule.jpg"), eq("ing-key-1")))
                .thenReturn(new IngestResult(syllabusResponse, true));
        when(syllabusService.ingestSyllabus(eq(USER_ID), eq("TR_YKS_TYT_MATH"), eq("2026.1"), any(), eq("application/pdf"), eq("json-upload.pdf"), eq("ing-json-1")))
                .thenReturn(new IngestResult(syllabusResponse, true));
        when(syllabusService.getUpload(USER_ID, UPLOAD_ID)).thenReturn(syllabusResponse);
        when(syllabusService.confirmDisambiguation(eq(USER_ID), eq(UPLOAD_ID), any())).thenReturn(confirmedResponse);

        mockMvc.perform(multipart("/v1/candidate/syllabus-ingest")
                        .file(filePart)
                        .param("examCode", "TR_YKS_TYT_MATH")
                        .param("examVersion", "2026.1")
                        .header("Idempotency-Key", "ing-key-1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schemaVersion").value("syllabus-ingestion-v1"))
                .andExpect(jsonPath("$.status").value("NEEDS_DISAMBIGUATION"));

        String base64Pdf = Base64.getEncoder().encodeToString("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
        mockMvc.perform(post("/api/v1/candidate/syllabus-ingest")
                        .header("Idempotency-Key", "ing-json-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schemaVersion": "syllabus-ingest-request-v1",
                                  "examCode": "TR_YKS_TYT_MATH",
                                  "examVersion": "2026.1",
                                  "mimeType": "application/pdf",
                                  "base64Payload": "%s",
                                  "filenameHint": "json-upload.pdf"
                                }
                                """.formatted(base64Pdf)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadId").value(UPLOAD_ID.toString()));

        mockMvc.perform(get("/v1/candidate/syllabus-uploads/{uploadId}", UPLOAD_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remainingMonthlyQuota").value(2));

        mockMvc.perform(post("/api/v1/candidate/syllabus-uploads/{uploadId}/confirm", UPLOAD_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schemaVersion": "syllabus-confirm-request-v1",
                                  "mappings": [
                                    {"itemIndex": 0, "confirmedSkillId": "MATH.ARITHMETIC.INTEGER_OPERATIONS"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("USER_CONFIRMED"));

        // 429 quota exceeded mapping
        when(syllabusService.ingestSyllabus(eq(USER_ID), eq("TR_YKS_TYT_MATH"), eq(null), any(), any(), any(), eq("quota-hit")))
                .thenThrow(new ApiProblemException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        ApiErrorCode.SYLLABUS_QUOTA_EXCEEDED,
                        "Quota exceeded",
                        true,
                        "Wait until next cycle."
                ));
        mockMvc.perform(multipart("/v1/candidate/syllabus-ingest")
                        .file(filePart)
                        .param("examCode", "TR_YKS_TYT_MATH")
                        .header("Idempotency-Key", "quota-hit"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("SYLLABUS_QUOTA_EXCEEDED"));
    }

    private static CandidateProfileResponse sampleProfileResponse() {
        return new CandidateProfileResponse(
                "candidate-profile-v1",
                PROFILE_ID,
                USER_ID,
                UUID.fromString("91000000-0000-4000-8000-000000000101"),
                "TR_YKS_TYT_MATH",
                "2026.1",
                "c1cb8c26245fdee1b53c164a94e35a6cecbde8bae6ece948e60f577df53e1391",
                UUID.fromString("91000000-0000-4000-8000-000000000901"),
                Jurisdiction.TR,
                LocalDate.of(2027, 6, 20),
                new BigDecimal("35.50"),
                60,
                420,
                new AccessibilityAccommodations(new BigDecimal("1.25"), false, true, false),
                new BigDecimal("0.2500"),
                217,
                60,
                15,
                FeasibilityStatus.OPTIMAL,
                List.of(new AdjustedExamSection("TYT_MATH", "TYT Mathematics", "TYT Matematik", 40, 65, 82, 1)),
                false,
                0,
                false,
                FIXED_NOW,
                FIXED_NOW
        );
    }

    private static SyllabusIngestionResponse sampleSyllabusResponse(SyllabusUploadStatus status) {
        return new SyllabusIngestionResponse(
                "syllabus-ingestion-v1",
                UPLOAD_ID,
                USER_ID,
                UUID.fromString("91000000-0000-4000-8000-000000000101"),
                "TR_YKS_TYT_MATH",
                "2026.1",
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                "image/jpeg",
                4,
                status,
                false,
                false,
                2,
                List.of(new ParsedSyllabusTopicItem(
                        0,
                        "Tam Sayılarla İşlemler",
                        1,
                        "October",
                        new BigDecimal("0.9400"),
                        "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                        "MATH.ARITHMETIC",
                        new BigDecimal("0.8800"),
                        true,
                        status == SyllabusUploadStatus.USER_CONFIRMED,
                        List.of()
                )),
                0,
                false,
                FIXED_NOW.plusSeconds(30 * 86400L),
                status == SyllabusUploadStatus.USER_CONFIRMED ? FIXED_NOW : null,
                FIXED_NOW,
                FIXED_NOW
        );
    }
}
