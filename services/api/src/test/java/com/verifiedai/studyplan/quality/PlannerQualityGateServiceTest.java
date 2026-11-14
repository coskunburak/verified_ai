package com.verifiedai.studyplan.quality;

import com.verifiedai.studyplan.quality.QualityGateModels.GateStatus;
import com.verifiedai.studyplan.quality.QualityGateModels.QualityGateRawAggregateSnapshot;
import com.verifiedai.studyplan.quality.QualityGateModels.StudyPlanQualityGateReport;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class PlannerQualityGateServiceTest {

    private SimpleMeterRegistry meterRegistry;
    private PlannerQualityGateService service;

    @BeforeEach
    void setUp() {
        JdbcClient jdbcClient = mock(JdbcClient.class);
        meterRegistry = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC);
        service = new PlannerQualityGateService(jdbcClient, meterRegistry, clock);
    }

    @Test
    void healthyAggregatesPassAllContractGatesWithZeroLlmTokensAndZeroPii() {
        QualityGateRawAggregateSnapshot healthy = new QualityGateRawAggregateSnapshot(
                10, 0,
                25, 0,
                20, 14, 1,
                15, 12,
                18, 0,
                8, 0,
                16, 16, 12,
                10, 8, 0,
                10, 0, 0
        );

        StudyPlanQualityGateReport report = service.evaluateFromSnapshot(healthy);

        assertEquals(QualityGateModels.SCHEMA_VERSION, report.schemaVersion());
        assertEquals(GateStatus.PASS, report.overallStatus());
        assertEquals(0, report.p0RegressionCount());
        assertEquals(0, report.p1RegressionCount());
        assertEquals(0, report.llmTokensUsed());
        assertFalse(report.trainingEligible());
        assertEquals(4, report.contractChecks().size());
        assertEquals(0, report.telemetryMetrics().piiViolationCount());
        assertEquals(1.0, meterRegistry.counter("studyplan_quality_gate_evaluated_total", "status", "PASS").count());
    }

    @Test
    void detectsP0RegressionsForColdStartCostPiiOrTrainingEligibility() {
        QualityGateRawAggregateSnapshot p0Broken = new QualityGateRawAggregateSnapshot(
                5, 1,
                10, 2,
                10, 8, 1,
                10, 8,
                10, 0,
                5, 0,
                16, 16, 5,
                10, 7, 1,
                10, 0, 1
        );

        StudyPlanQualityGateReport report = service.evaluateFromSnapshot(p0Broken);

        assertEquals(GateStatus.FAIL, report.overallStatus());
        assertEquals(2, report.p0RegressionCount());
        assertEquals(1, report.telemetryMetrics().piiViolationCount());
        assertEquals(GateStatus.FAIL, report.contractChecks().get(0).status());
        assertEquals(GateStatus.FAIL, report.contractChecks().get(3).status());
    }

    @Test
    void detectsP1WarnWhenSpacedReviewOverdueThresholdBreachedWithoutP0Failure() {
        QualityGateRawAggregateSnapshot overdueSpaced = new QualityGateRawAggregateSnapshot(
                5, 0,
                10, 0,
                10, 2, 5,
                10, 8,
                10, 0,
                5, 0,
                16, 16, 5,
                10, 7, 0,
                10, 0, 0
        );

        StudyPlanQualityGateReport report = service.evaluateFromSnapshot(overdueSpaced);

        assertEquals(GateStatus.WARN, report.overallStatus());
        assertEquals(0, report.p0RegressionCount());
        assertEquals(1, report.p1RegressionCount());
        assertEquals(GateStatus.WARN, report.contractChecks().get(1).status());
    }
}
