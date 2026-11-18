package com.verifiedai.exam.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.verifiedai.exam.application.ExamAnalyticsService;
import com.verifiedai.exam.domain.ExamProgressReport;
import com.verifiedai.exam.domain.ExamProgressReport.ConqueredTrapsEvidenceStatus;
import com.verifiedai.exam.domain.ExamProgressReport.EvidenceStatus;
import com.verifiedai.exam.domain.ExamProgressReport.TrapDataStatus;
import com.verifiedai.exam.domain.ExamProgressReport.TrapRemediationDto;
import com.verifiedai.exam.domain.ExamProgressReport.TrapSourceKind;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class ExamAnalyticsControllerTest {

    @Test
    void resolvesAuthenticatedPrincipalAndSetsNoStoreHeader() {
        var service = mock(ExamAnalyticsService.class);
        var controller = new ExamAnalyticsController(service);
        UUID owner = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken(owner, "synthetic", List.of());
        var request = mock(HttpServletRequest.class);

        ExamProgressReport stub = emptyReport(owner);
        when(service.getProgressReport(owner, "TR_YKS_TYT_MATH")).thenReturn(stub);

        var response = controller.progress("TR_YKS_TYT_MATH", request, auth);
        assertEquals(200, response.getStatusCode().value());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals(owner, response.getBody().userId());
        verify(service).getProgressReport(owner, "TR_YKS_TYT_MATH");
    }

    @Test
    void rejectsMissingAuthAndForeignUserIdQueryParameter() {
        var service = mock(ExamAnalyticsService.class);
        var controller = new ExamAnalyticsController(service);
        var request = mock(HttpServletRequest.class);

        assertThrows(ApiProblemException.class, () -> controller.progress("TR_YKS_TYT_MATH", request, null));

        UUID owner = UUID.randomUUID();
        UUID foreign = UUID.randomUUID();
        var auth = new UsernamePasswordAuthenticationToken(owner, "synthetic", List.of());
        when(request.getParameter("userId")).thenReturn(foreign.toString());

        var failure = assertThrows(ExamAnalyticsService.Failure.class,
                () -> controller.progress("TR_YKS_TYT_MATH", request, auth));
        assertEquals(403, failure.status);
        assertEquals("EXAM_ACCESS_DENIED", failure.code);

        var problemResponse = controller.handleServiceFailure(failure);
        assertEquals(403, problemResponse.getStatusCode().value());
        assertEquals("no-store", problemResponse.getHeaders().getCacheControl());
        assertEquals("EXAM_ACCESS_DENIED", problemResponse.getBody().getProperties().get("code"));
        verifyNoInteractions(service);
    }

    private static ExamProgressReport emptyReport(UUID owner) {
        Instant now = Instant.parse("2026-11-18T10:00:00Z");
        return new ExamProgressReport(
                ExamProgressReport.SCHEMA_VERSION,
                UUID.randomUUID(),
                owner,
                "TR_YKS_TYT_MATH",
                0,
                EvidenceStatus.EMPTY,
                null,
                List.of(),
                List.of(),
                new TrapRemediationDto(
                        TrapSourceKind.SESSION_MATCHED_TRAP_INCIDENCE,
                        TrapDataStatus.INSUFFICIENT_DATA,
                        List.of(),
                        0,
                        null,
                        0,
                        ConqueredTrapsEvidenceStatus.UNAVAILABLE,
                        false
                ),
                0,
                LocalDate.of(2026, 11, 18),
                ExamProgressReport.ALGORITHM_VERSION,
                false,
                0,
                BigDecimal.ZERO,
                now,
                now.plusSeconds(86400)
        );
    }
}
