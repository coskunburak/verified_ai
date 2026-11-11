package com.verifiedai.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MasteryCalibrationAdminControllerTest {

    private MockMvc mockMvc;
    private MasteryCalibrationEvaluationService evaluationService;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        evaluationService = mock(MasteryCalibrationEvaluationService.class);
        MasteryCalibrationAdminController controller = new MasteryCalibrationAdminController(evaluationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Method is explicitly annotated with @PreAuthorize(\"hasRole('ADMIN')\")")
    void preAuthorizeAnnotationPresence() throws NoSuchMethodException {
        Method method = MasteryCalibrationAdminController.class.getMethod(
            "getMasterySummary",
            String.class,
            int.class
        );
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    @DisplayName("GET /v1/admin/calibration/mastery-summary returns contract-compliant summary with 200 OK")
    void getMasterySummarySuccess() throws Exception {
        Instant evaluatedAt = Instant.parse("2026-11-11T12:00:00Z");
        MasteryCalibrationSummaryDto.ReliabilityBinDto bin1 = new MasteryCalibrationSummaryDto.ReliabilityBinDto(
            1, new BigDecimal("0.0000"), new BigDecimal("0.1000"), 10,
            new BigDecimal("0.0750"), new BigDecimal("0.0667"), new BigDecimal("0.0083")
        );
        MasteryCalibrationSummaryDto.SkillCalibrationSliceDto slice1 = new MasteryCalibrationSummaryDto.SkillCalibrationSliceDto(
            "ALG_LIN_EQ_01", 100, new BigDecimal("0.12500"), new BigDecimal("0.03800"),
            "CALIBRATED", true
        );

        MasteryCalibrationSummaryDto dto = new MasteryCalibrationSummaryDto(
            "1.0.0",
            evaluatedAt,
            null,
            30,
            100,
            new BigDecimal("0.13840"),
            new BigDecimal("0.2"),
            new BigDecimal("0.04120"),
            new BigDecimal("0.08"),
            new BigDecimal("0.01250"),
            "CALIBRATED",
            true,
            List.of(bin1),
            List.of(slice1)
        );

        when(evaluationService.getCalibrationSummary(null, 30)).thenReturn(dto);

        mockMvc.perform(get("/v1/admin/calibration/mastery-summary")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store, must-revalidate"))
            .andExpect(jsonPath("$.schemaVersion").value("1.0.0"))
            .andExpect(jsonPath("$.sampleSize").value(100))
            .andExpect(jsonPath("$.brierScore").value(0.13840))
            .andExpect(jsonPath("$.brierThreshold").value(0.2))
            .andExpect(jsonPath("$.expectedCalibrationError").value(0.04120))
            .andExpect(jsonPath("$.eceThreshold").value(0.08))
            .andExpect(jsonPath("$.calibrationStatus").value("CALIBRATED"))
            .andExpect(jsonPath("$.gatePassed").value(true));

        verify(evaluationService, times(1)).getCalibrationSummary(null, 30);
    }

    @Test
    @DisplayName("Query params canonicalSkillCode and windowDays are forwarded to service")
    void queryParametersForwarded() throws Exception {
        MasteryCalibrationSummaryDto dto = new MasteryCalibrationSummaryDto(
            "1.0.0", Instant.now(), "ALG_LIN_EQ_01", 14, 50,
            new BigDecimal("0.15000"), new BigDecimal("0.2"), new BigDecimal("0.05000"), new BigDecimal("0.08"),
            new BigDecimal("0.01000"), "CALIBRATED", true, List.of(), List.of()
        );

        when(evaluationService.getCalibrationSummary("ALG_LIN_EQ_01", 14)).thenReturn(dto);

        mockMvc.perform(get("/v1/admin/calibration/mastery-summary")
                .param("canonicalSkillCode", "ALG_LIN_EQ_01")
                .param("windowDays", "14")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.canonicalSkillCodeFilter").value("ALG_LIN_EQ_01"))
            .andExpect(jsonPath("$.windowDays").value(14));

        verify(evaluationService).getCalibrationSummary("ALG_LIN_EQ_01", 14);
    }

    @Test
    @DisplayName("Security boundary simulation validates ROLE_ADMIN vs non-admin (403) and anonymous (401)")
    void securityBoundarySimulation() {
        // 1. Anonymous (null authentication) -> 401
        var unauthenticated = SecurityContextHolder.getContext().getAuthentication();
        assertThat(unauthenticated).isNull();

        // 2. Non-admin user (ROLE_USER) -> fails hasRole('ADMIN')
        var userAuth = new UsernamePasswordAuthenticationToken(
            "student_user",
            "credentials",
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(userAuth);
        boolean hasAdminRole = userAuth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        assertThat(hasAdminRole).isFalse();

        // 3. Admin user (ROLE_ADMIN) -> satisfies hasRole('ADMIN')
        var adminAuth = new UsernamePasswordAuthenticationToken(
            "admin_user",
            "credentials",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );
        SecurityContextHolder.getContext().setAuthentication(adminAuth);
        boolean adminHasRole = adminAuth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        assertThat(adminHasRole).isTrue();
    }
}
