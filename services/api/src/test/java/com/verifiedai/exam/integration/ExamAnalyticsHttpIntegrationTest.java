package com.verifiedai.exam.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.verifiedai.exam.config.ExamProperties;
import com.verifiedai.exam.infrastructure.persistence.ExamAnalyticsSnapshotRepository;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.security.VerifiedJwtPrincipal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class ExamAnalyticsHttpIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired WebApplicationContext context;
    @Autowired ExamProperties flags;
    @Autowired ExamAnalyticsSnapshotRepository repository;
    @Autowired JdbcClient jdbc;

    private MockMvc mvc;
    private UUID owner;
    private UUID stranger;
    private boolean prevFlag;

    private static UsernamePasswordAuthenticationToken auth(UUID userId) {
        return new UsernamePasswordAuthenticationToken(
                new VerifiedJwtPrincipal(UUID.randomUUID(), userId),
                "synthetic",
                List.of(new SimpleGrantedAuthority("ROLE_LEARNER"))
        );
    }

    @BeforeEach
    void setup() {
        prevFlag = flags.isAnalyticsEnabled();
        flags.setAnalyticsEnabled(true);
        owner = UUID.randomUUID();
        stranger = UUID.randomUUID();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void cleanup() {
        flags.setAnalyticsEnabled(prevFlag);
        repository.deleteByUserId(owner);
        repository.deleteByUserId(stranger);
    }

    @Test
    void enforcesAuthFeatureFlagValidationBolaAndNoStoreOnBothPrefixes() throws Exception {
        for (String prefix : List.of("/v1", "/api/v1")) {
            // Unauthenticated -> 401
            mvc.perform(get(prefix + "/analytics/exams/progress").queryParam("examCode", "TR_YKS_TYT_MATH"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));

            // Authenticated empty learner -> 200 with strict schema defaults & no-store
            mvc.perform(get(prefix + "/analytics/exams/progress")
                            .queryParam("examCode", "YKS_TYT")
                            .with(authentication(auth(owner))))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.schemaVersion").value("exam-analytics-progress-v1"))
                    .andExpect(jsonPath("$.userId").value(owner.toString()))
                    .andExpect(jsonPath("$.examCode").value("TR_YKS_TYT_MATH"))
                    .andExpect(jsonPath("$.mockCount").value(0))
                    .andExpect(jsonPath("$.evidenceStatus").value("EMPTY"))
                    .andExpect(jsonPath("$.trainingEligible").value(false))
                    .andExpect(jsonPath("$.llmTokensUsed").value(0))
                    .andExpect(jsonPath("$.trapRemediation.associationIsCausal").value(false));

            // Foreign owner injection attempt -> 403 EXAM_ACCESS_DENIED + no-store
            mvc.perform(get(prefix + "/analytics/exams/progress")
                            .queryParam("examCode", "TR_YKS_TYT_MATH")
                            .queryParam("userId", stranger.toString())
                            .with(authentication(auth(owner))))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("EXAM_ACCESS_DENIED"));

            // Unsupported examCode -> 400 REQUEST_VALIDATION_FAILED + no-store
            mvc.perform(get(prefix + "/analytics/exams/progress")
                            .queryParam("examCode", "INTL_SAT_MATH")
                            .with(authentication(auth(owner))))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"));
        }

        // Feature off -> 403 FEATURE_DISABLED before data access
        flags.setAnalyticsEnabled(false);
        for (String prefix : List.of("/v1", "/api/v1")) {
            mvc.perform(get(prefix + "/analytics/exams/progress")
                            .queryParam("examCode", "TR_YKS_TYT_MATH")
                            .with(authentication(auth(owner))))
                    .andExpect(status().isForbidden())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
        }
    }
}
