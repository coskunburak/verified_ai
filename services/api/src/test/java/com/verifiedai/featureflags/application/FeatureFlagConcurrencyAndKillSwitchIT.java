package com.verifiedai.featureflags.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.featureflags.domain.FeatureFlagDefinitionSnapshot;
import com.verifiedai.featureflags.infrastructure.FeatureFlagJdbcRepository;
import com.verifiedai.featureflags.infrastructure.FeatureFlagJdbcRepository.StoredExperimentDefinition;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "verifiedai.featureflags.enabled=true",
    "verifiedai.featureflags.client-config-enabled=true",
    "verifiedai.featureflags.admin-mutations-enabled=true"
})
final class FeatureFlagConcurrencyAndKillSwitchIT extends PostgresIntegrationTestSupport {

    @Autowired
    FeatureFlagEvaluationPort evaluationPort;

    @Autowired
    FeatureFlagGovernanceService governanceService;

    @Autowired
    FeatureFlagPrivacyLifecycleContributor privacyContributor;

    @Autowired
    FeatureFlagJdbcRepository repository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("""
            truncate table
                feature_flag_audit_logs,
                experiment_assignments,
                refresh_tokens,
                sessions,
                user_identities,
                users
            cascade
            """);
        jdbcTemplate.execute("""
            UPDATE feature_flag_definitions
            SET enabled = CASE WHEN flag_key IN ('pdf_upload_enabled', 'strong_model_pct') THEN TRUE ELSE FALSE END,
                kill_switch_active = FALSE,
                rollout_basis_points = CASE WHEN flag_key IN ('pdf_upload_enabled', 'strong_model_pct') THEN 10000 ELSE 0 END,
                current_value_json = default_value_json,
                version = 0,
                updated_at = NOW()
            """);
        jdbcTemplate.execute("""
            UPDATE experiment_definitions
            SET status = 'DRAFT',
                rollout_basis_points = 0,
                experiment_version = 1,
                version = 0,
                updated_at = NOW()
            """);
    }

    @Test
    void concurrentExperimentAssignmentWritesSingleZeroFlickerRowAndHandlesKillSwitches() throws Exception {
        UUID adminId = UUID.fromString("00000000-0000-0000-0000-00000000AA01");
        UUID learnerId = UUID.fromString("00000000-0000-0000-0000-00000000BB01");
        UUID secondLearnerId = UUID.fromString("00000000-0000-0000-0000-00000000BB02");
        seedActiveUser(learnerId);
        seedActiveUser(secondLearnerId);

        StoredExperimentDefinition exp01 = repository.findExperimentByKey("EXP-LOS-01").orElseThrow();
        governanceService.updateExperiment(
            "EXP-LOS-01",
            new UpdateExperimentCommand("RUNNING", 10_000, "Launch EXP-LOS-01 at 100% traffic", exp01.version(), "idem-exp01-run-1"),
            adminId
        );

        int threadCount = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<ExperimentAssignmentView>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                Callable<ExperimentAssignmentView> task = () -> {
                    readyLatch.countDown();
                    startLatch.await();
                    return evaluationPort.assignExperiment("EXP-LOS-01", learnerId);
                };
                futures.add(pool.submit(task));
            }
            readyLatch.await();
            startLatch.countDown();

            Set<String> observedVariants = new HashSet<>();
            for (Future<ExperimentAssignmentView> future : futures) {
                ExperimentAssignmentView view = future.get();
                observedVariants.add(view.variantKey());
                assertThat(view.status()).isEqualTo("RUNNING");
            }
            assertThat(observedVariants).hasSize(1);
        } finally {
            pool.shutdownNow();
        }

        Integer persistedRows = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM experiment_assignments WHERE user_id = ? AND experiment_key = 'EXP-LOS-01'",
            Integer.class,
            learnerId
        );
        assertThat(persistedRows).isEqualTo(1);

        // Emergency kill switch activation immediately disables pdf_upload_enabled & appends audit log
        FeatureFlagDefinitionSnapshot pdfBefore = repository.findFlagByKey("pdf_upload_enabled").orElseThrow();
        assertThat(evaluationPort.evaluateBoolean("pdf_upload_enabled", learnerId, false)).isTrue();

        governanceService.toggleKillSwitch(
            "pdf_upload_enabled",
            new ToggleKillSwitchCommand(true, "Mitigate PDF parser parser vulnerability", pdfBefore.version(), "idem-ks-pdf-1"),
            adminId
        );

        assertThat(evaluationPort.evaluateBoolean("pdf_upload_enabled", learnerId, true)).isFalse();
        ClientRemoteConfigView bundle = evaluationPort.evaluateClientBundle(learnerId);
        assertThat(bundle.killSwitches()).containsExactly("pdf_upload_enabled");
        assertThat(bundle.llmTokensUsed()).isZero();
        assertThat(bundle.trainingEligible()).isFalse();

        // Linked experiment (EXP-LOS-08 -> client_mistake_moment_paywall_ui) falls back immediately on kill switch
        StoredExperimentDefinition exp08 = repository.findExperimentByKey("EXP-LOS-08").orElseThrow();
        governanceService.updateExperiment(
            "EXP-LOS-08",
            new UpdateExperimentCommand("RUNNING", 10_000, "Run EXP-LOS-08 paywall test", exp08.version(), "idem-exp08-run-1"),
            adminId
        );
        FeatureFlagDefinitionSnapshot linkedBefore = repository.findFlagByKey("client_mistake_moment_paywall_ui").orElseThrow();
        governanceService.toggleKillSwitch(
            "client_mistake_moment_paywall_ui",
            new ToggleKillSwitchCommand(true, "Pause linked paywall experiment immediately", linkedBefore.version(), "idem-ks-paywall-1"),
            adminId
        );
        ExperimentAssignmentView forcedControl = evaluationPort.assignExperiment("EXP-LOS-08", learnerId);
        assertThat(forcedControl.variantKey()).isEqualTo("control_quota_wall");
        assertThat(forcedControl.reason()).isEqualTo("KILL_SWITCH_ACTIVE");

        // Idempotency replay succeeds with no duplicate audit entry
        FeatureFlagDefinitionSnapshot healthBefore = repository.findFlagByKey("client_platform_health_screen").orElseThrow();
        UpdateFeatureFlagCommand enableHealthCmd = new UpdateFeatureFlagCommand(
            true,
            10_000,
            "true",
            null,
            "Enable health screen for internal QA",
            healthBefore.version(),
            "idem-flag-health-1"
        );
        governanceService.updateFlag("client_platform_health_screen", enableHealthCmd, adminId);
        governanceService.updateFlag("client_platform_health_screen", enableHealthCmd, adminId);

        Integer replayAuditCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM feature_flag_audit_logs WHERE idempotency_key = 'idem-flag-health-1'",
            Integer.class
        );
        assertThat(replayAuditCount).isEqualTo(1);

        // Idempotency key reuse with changed payload -> 409 FEATURE_FLAG_IDEMPOTENCY_CONFLICT
        UpdateFeatureFlagCommand conflictingPayload = new UpdateFeatureFlagCommand(
            false,
            0,
            "false",
            null,
            "Conflicting payload attempt",
            healthBefore.version() + 1,
            "idem-flag-health-1"
        );
        assertThatThrownBy(() -> governanceService.updateFlag("client_platform_health_screen", conflictingPayload, adminId))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                ApiProblemException problem = (ApiProblemException) ex;
                assertThat(problem.status()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(problem.code()).isEqualTo(ApiErrorCode.FEATURE_FLAG_IDEMPOTENCY_CONFLICT);
            });

        // Stale expectedVersion with fresh idempotency key -> 409 OPTIMISTIC_CONFLICT
        UpdateFeatureFlagCommand staleVersionCmd = new UpdateFeatureFlagCommand(
            false,
            0,
            "false",
            null,
            "Stale version check",
            0L,
            "idem-flag-health-stale"
        );
        assertThatThrownBy(() -> governanceService.updateFlag("client_platform_health_screen", staleVersionCmd, adminId))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                ApiProblemException problem = (ApiProblemException) ex;
                assertThat(problem.status()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(problem.code()).isEqualTo(ApiErrorCode.OPTIMISTIC_CONFLICT);
            });

        // Privacy lifecycle contributor: export + isolated delete for userA without affecting userB
        evaluationPort.assignExperiment("EXP-LOS-01", secondLearnerId);

        assertThat(privacyContributor.category()).isEqualTo("featureflags");
        Map<String, Object> exported = privacyContributor.exportUserData(learnerId);
        assertThat(exported.get("category")).isEqualTo("featureflags");
        assertThat(exported.get("trainingEligible")).isEqualTo(false);
        assertThat((Integer) exported.get("assignmentCount")).isEqualTo(1);

        privacyContributor.deleteUserData(learnerId, Instant.now());

        Integer learnerRemaining = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM experiment_assignments WHERE user_id = ?",
            Integer.class,
            learnerId
        );
        Integer secondLearnerRemaining = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM experiment_assignments WHERE user_id = ?",
            Integer.class,
            secondLearnerId
        );
        assertThat(learnerRemaining).isZero();
        assertThat(secondLearnerRemaining).isEqualTo(1);
    }

    private void seedActiveUser(UUID userId) {
        jdbcTemplate.update(
            "INSERT INTO users (id, status, created_at, updated_at) VALUES (?, 'ACTIVE', NOW(), NOW()) ON CONFLICT (id) DO NOTHING",
            userId
        );
    }
}
