package com.verifiedai.exam.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.verifiedai.exam.application.ActiveExamNoHintGuard.HintGuardResult;
import com.verifiedai.exam.application.ExamSessionRuntimeService;
import com.verifiedai.exam.application.ExamSessionRuntimeService.AutosaveResult;
import com.verifiedai.exam.application.ExamSessionRuntimeService.ScratchpadAttachResult;
import com.verifiedai.exam.application.ExamSessionRuntimeService.SessionStateResult;
import com.verifiedai.exam.application.ExamSessionRuntimeService.StartOrResumeResult;
import com.verifiedai.exam.application.ExamSessionRuntimeService.SubmitSessionResult;
import com.verifiedai.exam.domain.AppliedAccommodationSnapshot;
import com.verifiedai.exam.domain.ExamItemResponseState;
import com.verifiedai.exam.domain.ExamScratchpadAsset;
import com.verifiedai.exam.domain.ExamSession;
import com.verifiedai.exam.domain.ExamSession.F28VerificationBatchEntry;
import com.verifiedai.exam.domain.ExamSession.F28VerificationBatchManifest;
import com.verifiedai.exam.domain.ExamSession.ServerClockSync;
import com.verifiedai.exam.domain.ExamSessionItem;
import com.verifiedai.exam.domain.ExamSessionItem.SessionItemOptionSnapshot;
import com.verifiedai.exam.domain.ExamSessionStatus;
import com.verifiedai.exam.domain.QuestionDifficultyBand;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class ExamSessionRuntimeControllerTest {

    private static final UUID USER_ID = UUID.fromString("91000000-0000-4000-8000-000000000a01");
    private static final UUID SESSION_ID = UUID.fromString("95000000-0000-4000-8000-000000000001");
    private static final UUID ASSEMBLY_ID = UUID.fromString("94100000-0000-4000-8000-000000000001");
    private static final Instant T0 = Instant.parse("2026-11-15T10:00:00Z");

    private ExamSessionRuntimeService runtimeService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        runtimeService = mock(ExamSessionRuntimeService.class);
        ExamSessionRuntimeController controller = new ExamSessionRuntimeController(runtimeService);

        HandlerMethodArgumentResolver jwtResolver = new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                        && Jwt.class.isAssignableFrom(parameter.getParameterType());
            }

            @Override
            public Object resolveArgument(
                    MethodParameter parameter,
                    ModelAndViewContainer mavContainer,
                    NativeWebRequest webRequest,
                    WebDataBinderFactory binderFactory
            ) {
                return new Jwt(
                        "synthetic-token",
                        T0,
                        T0.plusSeconds(3600),
                        Map.of("alg", "none"),
                        Map.of("sub", USER_ID.toString())
                );
            }
        };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(jwtResolver)
                .build();
    }

    @Test
    void dualMappedEndpointsHandleStartGetAutosaveScratchpadSubmitAndNoHintGuard() throws Exception {
        ExamSession session = sampleSession(ExamSessionStatus.IN_PROGRESS);
        ServerClockSync sync = new ServerClockSync(T0, 900, 1020, 0, false, false, false);

        when(runtimeService.startOrResumeSession(eq(USER_ID), any()))
                .thenReturn(new StartOrResumeResult(session, sync, true));
        mockMvc.perform(post("/v1/exams/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assemblyId": "94100000-0000-4000-8000-000000000001",
                                  "offlineGraceBufferSeconds": 120,
                                  "trainingEligible": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schemaVersion").value("exam-session-response-v1"))
                .andExpect(jsonPath("$.noHintPolicyEnforced").value(true))
                .andExpect(jsonPath("$.llmTokensUsed").value(0))
                .andExpect(jsonPath("$.trainingEligible").value(false));

        when(runtimeService.getSessionState(eq(USER_ID), eq(SESSION_ID), any()))
                .thenReturn(new SessionStateResult(session, sync));
        mockMvc.perform(get("/api/v1/exams/sessions/" + SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.clockSync.remainingSeconds").value(900));

        ExamSessionItem item = session.items().getFirst();
        when(runtimeService.autosaveItem(eq(USER_ID), eq(SESSION_ID), eq(1), anyString(), any()))
                .thenReturn(new AutosaveResult(false, null, session, item, sync));
        mockMvc.perform(patch("/v1/exams/sessions/" + SESSION_ID + "/items/1")
                        .header("Idempotency-Key", "k-auto-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "SELECT_OPTION",
                                  "selectedOptionKey": "B",
                                  "deltaTimeSpentSeconds": 10,
                                  "expectedAutosaveVersion": 0,
                                  "clientAutosaveSeq": 1,
                                  "trainingEligible": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("exam-session-item-autosave-response-v1"))
                .andExpect(jsonPath("$.llmTokensUsed").value(0));

        String storageKey = "users/" + USER_ID + "/exam-sessions/" + SESSION_ID + "/scratchpad/page1.jpg";
        ExamScratchpadAsset asset = new ExamScratchpadAsset(
                UUID.randomUUID(), SESSION_ID, item.id(), USER_ID, 1, 1,
                storageKey, "image/jpeg", 48_000L, "a".repeat(64),
                1080, 1440, "PAPER_DENEME_BOOKLET", "345 Yayinlari",
                "QUEUED_FOR_POST_SUBMIT_BATCH", false, T0, T0
        );
        when(runtimeService.attachScratchpad(eq(USER_ID), eq(SESSION_ID), eq(1), anyString(), any()))
                .thenReturn(new ScratchpadAttachResult(false, true, null, session, item, asset, sync));
        mockMvc.perform(post("/v1/exams/sessions/" + SESSION_ID + "/items/1/scratchpad")
                        .header("Idempotency-Key", "k-scratch-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pageNumber": 1,
                                  "storageKey": "%s",
                                  "mimeType": "image/jpeg",
                                  "byteSize": 48000,
                                  "sha256Checksum": "%s",
                                  "captureSource": "PAPER_DENEME_BOOKLET",
                                  "bookletPublisher": "345 Yayinlari",
                                  "trainingEligible": false
                                }
                                """.formatted(storageKey, "a".repeat(64))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.schemaVersion").value("exam-scratchpad-upload-response-v1"))
                .andExpect(jsonPath("$.asset.f28VerificationStatus").value("QUEUED_FOR_POST_SUBMIT_BATCH"));

        when(runtimeService.checkNoHintGuard(eq(USER_ID), eq(SESSION_ID), anyString()))
                .thenReturn(new HintGuardResult(
                        "exam-no-hint-guard-response-v1",
                        SESSION_ID,
                        ExamSessionStatus.IN_PROGRESS,
                        false,
                        "EXAM_NO_HINT_MODE_ACTIVE",
                        "SOCRATIC_HINT_DRAWER",
                        "exam.session.no_hint.explanation",
                        T0,
                        0,
                        false
                ));
        mockMvc.perform(post("/v1/exams/sessions/" + SESSION_ID + "/hint-guard-check")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requestedSurface": "SOCRATIC_HINT_DRAWER"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.hintAllowed").value(false))
                .andExpect(jsonPath("$.policyCode").value("EXAM_NO_HINT_MODE_ACTIVE"))
                .andExpect(jsonPath("$.llmTokensUsed").value(0));

        ExamSession submitted = session.submit(ExamSessionStatus.SUBMITTED, "USER_INITIATED", "sub-1", T0, 0L);
        F28VerificationBatchManifest manifest = new F28VerificationBatchManifest(
                "f28-batch-manifest-v1",
                "QUEUED_FOR_F28_BATCH",
                SESSION_ID,
                USER_ID,
                "TR_YKS_TYT_MATH",
                1,
                0,
                false,
                T0,
                List.of(new F28VerificationBatchEntry(
                        asset.id(), item.id(), 1, 1, "Q1", "MATH.ARITH", storageKey, "a".repeat(64),
                        "PAPER_DENEME_BOOKLET", "345 Yayinlari", "QUEUED_FOR_POST_SUBMIT_BATCH"
                ))
        );
        when(runtimeService.submitSession(eq(USER_ID), eq(SESSION_ID), anyString(), any()))
                .thenReturn(new SubmitSessionResult(false, null, submitted, manifest, sync));
        mockMvc.perform(post("/api/v1/exams/sessions/" + SESSION_ID + "/submit")
                        .header("Idempotency-Key", "sub-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"submissionReason": "USER_INITIATED", "trainingEligible": false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("exam-session-submit-response-v1"))
                .andExpect(jsonPath("$.f28VerificationBatchManifest.totalAssets").value(1))
                .andExpect(jsonPath("$.llmTokensUsed").value(0));

        // BOLA mismatch on X-User-Id -> 403 Forbidden
        mockMvc.perform(get("/v1/exams/sessions/" + SESSION_ID)
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EXAM_CANDIDATE_ACCESS_DENIED"));

        // Propagates 409 problem details cleanly
        when(runtimeService.autosaveItem(eq(USER_ID), eq(SESSION_ID), anyInt(), anyString(), any()))
                .thenThrow(new ApiProblemException(
                        HttpStatus.CONFLICT,
                        ApiErrorCode.EXAM_ITEM_STALE_VERSION,
                        "Stale autosave version",
                        true,
                        "Refresh session"
                ));
        mockMvc.perform(patch("/v1/exams/sessions/" + SESSION_ID + "/items/1")
                        .header("Idempotency-Key", "k-stale")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "SELECT_OPTION",
                                  "selectedOptionKey": "A",
                                  "deltaTimeSpentSeconds": 5,
                                  "expectedAutosaveVersion": 0,
                                  "clientAutosaveSeq": 2,
                                  "trainingEligible": false
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EXAM_ITEM_STALE_VERSION"));
    }

    private static ExamSession sampleSession(ExamSessionStatus status) {
        ExamSessionItem item = new ExamSessionItem(
                UUID.randomUUID(),
                SESSION_ID,
                1,
                UUID.randomUUID(),
                "Q1",
                "MAIN",
                1,
                "MATH.ARITH",
                "SINGLE_CHOICE_MCQ",
                QuestionDifficultyBand.EASY,
                "Solve 2 + 2.",
                List.of(
                        new SessionItemOptionSnapshot("A", 1, "3", null, false),
                        new SessionItemOptionSnapshot("B", 2, "4", null, false)
                ),
                null,
                BigDecimal.ONE,
                new BigDecimal("0.2500"),
                ExamItemResponseState.UNVISITED,
                null,
                null,
                false,
                List.of(),
                0,
                0,
                0L,
                null,
                true,
                false,
                T0,
                T0,
                List.of()
        );
        return new ExamSession(
                SESSION_ID,
                USER_ID,
                ASSEMBLY_ID,
                UUID.randomUUID(),
                "TR_YKS_TYT_MATH",
                1,
                status,
                "FREE_NAVIGATION",
                true,
                new AppliedAccommodationSnapshot(new BigDecimal("1.00"), false, false, false),
                900,
                new BigDecimal("1.00"),
                900,
                120,
                T0,
                T0.plusSeconds(900),
                T0.plusSeconds(1020),
                T0,
                null,
                null,
                null,
                null,
                1,
                0,
                0,
                1,
                0,
                0L,
                null,
                false,
                T0,
                T0,
                List.of(item)
        );
    }
}
