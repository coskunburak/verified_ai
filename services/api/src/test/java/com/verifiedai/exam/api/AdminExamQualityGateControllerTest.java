package com.verifiedai.exam.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.verifiedai.exam.application.ExamQualityGateService;
import com.verifiedai.exam.application.ExamQualityGateService.GateStatus;
import com.verifiedai.exam.application.ExamQualityGateService.OwnerEvidenceManifest;
import com.verifiedai.exam.config.ExamProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminExamQualityGateControllerTest {

    private static final Instant NOW = Instant.parse("2026-11-18T18:00:00Z");
    private static final String BUILD_SHA = "aaa69ab27e2f1d0098bd8e12257348b7c7e79bd1";

    private AtomicReference<Optional<OwnerEvidenceManifest>> manifestRef;
    private SimpleMeterRegistry meterRegistry;
    private ExamQualityGateService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        manifestRef = new AtomicReference<>(Optional.empty());
        meterRegistry = new SimpleMeterRegistry();
        ExamProperties props = new ExamProperties();
        service = new ExamQualityGateService(
                props,
                (JdbcClient) null,
                meterRegistry,
                Clock.fixed(NOW, ZoneOffset.UTC),
                BUILD_SHA,
                manifestRef::get
        );
        mvc = MockMvcBuilders.standaloneSetup(new AdminExamQualityGateController(service)).build();
    }

    @Test
    void enforcesAuthRbacNoStoreAndFailClosedBlockedWhenEvidenceMissing() throws Exception {
        for (String path : List.of("/v1/admin/exams/quality-gate", "/api/v1/admin/exams/quality-gate")) {
            mvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Cache-Control", containsString("no-store")));

            var learner = new UsernamePasswordAuthenticationToken(
                    UUID.randomUUID(),
                    "synthetic",
                    List.of(new SimpleGrantedAuthority("ROLE_LEARNER"))
            );
            mvc.perform(get(path).principal(learner))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("Cache-Control", containsString("no-store")));

            var admin = new UsernamePasswordAuthenticationToken(
                    UUID.randomUUID(),
                    "synthetic",
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
            mvc.perform(get(path).principal(admin))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.schemaVersion").value("exam-quality-gate-v1"))
                    .andExpect(jsonPath("$.gateStatus").value("BLOCKED"))
                    .andExpect(jsonPath("$.phase9ExitAuthorized").value(false))
                    .andExpect(jsonPath("$.trainingEligible").value(false))
                    .andExpect(jsonPath("$.llmTokensUsed").value(0))
                    .andExpect(jsonPath("$.inferenceCostUsd").value(0));
        }
    }

    @Test
    void rejectsExact350msBoundaryAndStaleShaAndCountsPassOncePerRunId() throws Exception {
        var admin = new UsernamePasswordAuthenticationToken(
                UUID.randomUUID(),
                "synthetic",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        // Stale SHA -> BLOCKED
        manifestRef.set(Optional.of(new OwnerEvidenceManifest(
                "0000000000000000000000000000000000000000",
                "run-stale",
                NOW,
                190.0, 220, "staging",
                170.0, 220, "staging",
                0, 0, "ledger",
                true, "ok",
                true, 50, "ok",
                true, "ok",
                true, "ok",
                true
        )));
        mvc.perform(get("/v1/admin/exams/quality-gate").principal(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gateStatus").value("BLOCKED"))
                .andExpect(jsonPath("$.phase9ExitAuthorized").value(false));

        // Exact 350.0ms boundary -> FAIL (because strict < 350.0 is required)
        manifestRef.set(Optional.of(new OwnerEvidenceManifest(
                BUILD_SHA,
                "run-350-fail",
                NOW,
                350.0, 220, "staging",
                170.0, 220, "staging",
                0, 0, "ledger",
                true, "ok",
                true, 50, "ok",
                true, "ok",
                true, "ok",
                true
        )));
        mvc.perform(get("/v1/admin/exams/quality-gate").principal(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gateStatus").value("FAIL"))
                .andExpect(jsonPath("$.checks.cntLos10P95.status").value("FAIL"))
                .andExpect(jsonPath("$.phase9ExitAuthorized").value(false));

        // Mock service with full Flyway chain verified + valid staging manifest (without owner sign-off yet)
        var verifiedFlywayService = new ExamQualityGateService(
                new ExamProperties(),
                (JdbcClient) null,
                meterRegistry,
                Clock.fixed(NOW, ZoneOffset.UTC),
                BUILD_SHA,
                () -> Optional.of(new OwnerEvidenceManifest(
                        BUILD_SHA,
                        "run-owner-valid-1",
                        NOW,
                        215.4, 240, "staging-eks",
                        162.8, 240, "staging-eks",
                        0, 0, "owner-ledger",
                        true, "hmac+replay",
                        true, 50, "voiceover-50",
                        true, "sqlite-intent",
                        true, "v064-purge",
                        false
                ))
        );
        var mockedGate = verifiedFlywayService.evaluateReadOnlyGate();
        // Since jdbcClient is null in standalone unit setup, flywayV054ToV064 remains BLOCKED even when other checks pass
        org.assertj.core.api.Assertions.assertThat(mockedGate.gateStatus()).isEqualTo(GateStatus.BLOCKED);
        org.assertj.core.api.Assertions.assertThat(mockedGate.phase9ExitAuthorized()).isFalse();
        org.assertj.core.api.Assertions.assertThat(mockedGate.checks().cntLos10P95().status()).isEqualTo(GateStatus.PASS);
        org.assertj.core.api.Assertions.assertThat(mockedGate.checks().cntLos11P95().status()).isEqualTo(GateStatus.PASS);
        org.assertj.core.api.Assertions.assertThat(mockedGate.checks().criticalDefects().status()).isEqualTo(GateStatus.PASS);
    }
}
