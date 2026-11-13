package com.verifiedai.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.verifiedai.analytics.WeeklyLearnerDigestRepository.LearnerContextProjection;
import com.verifiedai.analytics.WeeklyLearnerDigestRepository.WeeklyEvidenceProjection;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiExceptionHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WeeklyReportControllerSecurityTest {

    private static final UUID USER_A = UUID.fromString("00000000-0000-4000-8000-000000000890");
    private static final UUID USER_B = UUID.fromString("00000000-0000-4000-8000-000000000899");
    private static final Instant FIXED_NOW = Instant.parse("2026-11-13T10:30:00Z");

    private MockMvc mockMvc;
    private MockMvc disabledMockMvc;

    @BeforeEach
    void setUp() {
        InMemoryDigestRepo repo = new InMemoryDigestRepo();
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        WeeklyLearnerDigestService enabledService = new WeeklyLearnerDigestService(
            repo,
            null,
            metrics,
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
            true
        );
        WeeklyLearnerDigestService disabledService = new WeeklyLearnerDigestService(
            repo,
            null,
            metrics,
            Clock.fixed(FIXED_NOW, ZoneOffset.UTC),
            false
        );

        mockMvc = MockMvcBuilders.standaloneSetup(new WeeklyReportController(enabledService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
        disabledMockMvc = MockMvcBuilders.standaloneSetup(new WeeklyReportController(disabledService))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unauthenticatedRequestsReturn401() throws Exception {
        mockMvc.perform(get("/v1/reports/weekly/2026-W46"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code", is("AUTH_CREDENTIALS_INVALID")));

        mockMvc.perform(post("/v1/reports/weekly/2026-W46/share-packet")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientRole\":\"TUTOR\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void crossLearnerTargetUserIdInSharePacketReturns403ScopeViolation() throws Exception {
        authenticateAs(USER_A);
        mockMvc.perform(get("/v1/reports/weekly/2026-W46")).andExpect(status().isOk());

        String crossLearnerBody = String.format(
            "{\"recipientRole\":\"TUTOR\",\"targetUserId\":\"%s\"}", USER_B
        );
        mockMvc.perform(post("/v1/reports/weekly/2026-W46/share-packet")
                .contentType(MediaType.APPLICATION_JSON)
                .content(crossLearnerBody))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code", is("WEEKLY_SHARE_PACKET_SCOPE_VIOLATION")));
    }

    @Test
    void rawTranscriptOrMediaScopeFlagsReturn403ScopeViolation() throws Exception {
        authenticateAs(USER_A);
        mockMvc.perform(get("/v1/reports/weekly/2026-W46")).andExpect(status().isOk());

        mockMvc.perform(post("/v1/reports/weekly/2026-W46/share-packet")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientRole\":\"TUTOR\",\"includeRawTranscripts\":true}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code", is("WEEKLY_SHARE_PACKET_SCOPE_VIOLATION")));

        mockMvc.perform(post("/v1/reports/weekly/2026-W46/share-packet")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientRole\":\"PARENT\",\"rawTutorTranscript\":\"leak\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code", is("WEEKLY_SHARE_PACKET_SCOPE_VIOLATION")));
    }

    @Test
    void invalidAndFutureWeekIsoReturn400() throws Exception {
        authenticateAs(USER_A);
        mockMvc.perform(get("/v1/reports/weekly/2026-13"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("WEEKLY_REPORT_INVALID_WEEK_ISO")));

        mockMvc.perform(get("/v1/reports/weekly/2026-W50"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code", is("WEEKLY_REPORT_FUTURE_WEEK_NOT_ALLOWED")));
    }

    @Test
    void missingDigestForSharePacketReturns404() throws Exception {
        authenticateAs(USER_A);
        mockMvc.perform(post("/v1/reports/weekly/2026-W44/share-packet")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientRole\":\"TUTOR\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code", is("WEEKLY_REPORT_NOT_FOUND")));
    }

    @Test
    void disabledFeatureKillSwitchReturns503RecoverableTrue() throws Exception {
        authenticateAs(USER_A);
        disabledMockMvc.perform(get("/v1/reports/weekly/2026-W46"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code", is("WEEKLY_REPORT_FEATURE_DISABLED")))
            .andExpect(jsonPath("$.details.recoverable", is(true)));
    }

    @Test
    void responsesExcludeRawPiiAndTranscriptsAndSetTrainingEligibleFalse() throws Exception {
        authenticateAs(USER_A);

        MvcResult digestRes = mockMvc.perform(get("/api/v1/reports/weekly/2026-W46?locale=en"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.training_eligible", is(false)))
            .andReturn();
        String digestJson = digestRes.getResponse().getContentAsString();
        assertThat(digestJson).doesNotContain("rawTutorTranscript", "studentEmail", "presignedUrl", "started_at", "ended_at");

        MvcResult shareRes = mockMvc.perform(post("/v1/reports/weekly/2026-W46/share-packet")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientRole\":\"TUTOR\",\"displayAlias\":\"Deniz A.\",\"locale\":\"en\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.training_eligible", is(false)))
            .andExpect(jsonPath("$.privacyAttestation.policyId", is("G-PRIV-03")))
            .andExpect(jsonPath("$.privacyAttestation.piiExcluded", is(true)))
            .andExpect(jsonPath("$.privacyAttestation.rawTutorTranscriptsIncluded", is(false)))
            .andReturn();
        String shareJson = shareRes.getResponse().getContentAsString();
        assertThat(shareJson).doesNotContain(
            "\"rawTutorTranscript\"",
            "\"studentEmail\"",
            "\"presignedUrl\"",
            "\"started_at\"",
            "\"ended_at\"",
            "\"tutorTurns\"",
            "\"rawPhotos\""
        );
    }

    private static void authenticateAs(UUID userId) {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(
            new VerifiedJwtPrincipal(UUID.fromString("00000000-0000-4000-8000-000000000001"), userId),
            null,
            "ROLE_USER"
        );
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private static final class InMemoryDigestRepo extends WeeklyLearnerDigestRepository {

        private final Map<String, WeeklyLearnerDigest> store = new ConcurrentHashMap<>();

        @Override
        public Optional<WeeklyLearnerDigest> findByUserAndWeek(UUID userId, String weekIso) {
            return Optional.ofNullable(store.get(userId + "|" + weekIso));
        }

        @Override
        public void upsertDigest(WeeklyLearnerDigest digest) {
            store.put(digest.userId() + "|" + digest.weekIso(), digest);
        }

        @Override
        public LearnerContextProjection readLearnerContext(UUID userId) {
            return new LearnerContextProjection("UTC", "en", 2);
        }

        @Override
        public WeeklyEvidenceProjection readWeeklyEvidence(
            UUID userId,
            LocalDate weekStartDate,
            LocalDate weekEndDate,
            Instant weekStartTs,
            Instant weekEndTs,
            Instant prevWeekStartTs
        ) {
            return WeeklyEvidenceProjection.empty();
        }
    }
}
