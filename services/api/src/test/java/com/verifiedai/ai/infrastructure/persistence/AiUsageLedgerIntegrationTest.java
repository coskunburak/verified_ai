package com.verifiedai.ai.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.ai.application.AiCapability;
import com.verifiedai.ai.application.AiProviderFailureClass;
import com.verifiedai.ai.application.AiUsageRecord;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@TestPropertySource(
    properties = "app.ai.usage-ledger.enabled=true"
)
final class AiUsageLedgerIntegrationTest
    extends PostgresIntegrationTestSupport {

    private static final Set<String> FORBIDDEN_RAW_COLUMNS =
        Set.of(
            "raw_prompt",
            "prompt_text",
            "raw_response",
            "student_text",
            "problem_text",
            "object_key",
            "signed_url",
            "image_bytes"
        );

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    AiUsageRecordJpaRepository repository;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    Environment environment;

    @Test
    void v016MigrationAppliesAndRawContentColumnsAreAbsent() {
        assertThat(tableExists("ai_usage_records"))
            .isTrue();

        List<String> columns =
            jdbcTemplate.queryForList(
                """
                select column_name
                  from information_schema.columns
                 where table_name = 'ai_usage_records'
                """,
                String.class
            );

        assertThat(columns)
            .doesNotContainAnyElementsOf(
                FORBIDDEN_RAW_COLUMNS
            );

        assertThat(
            environment.getProperty(
                "app.ai.usage-ledger.enabled",
                Boolean.class
            )
        ).isTrue();
    }

    @Test
    void databaseConstraintsRejectDuplicateOperationsNegativeCostAndNegativeAttempt() {
        UUID operationId =
            UUID.randomUUID();

        insertRawRecord(
            UUID.randomUUID(),
            operationId,
            0,
            0
        );

        assertThatThrownBy(() ->
            insertRawRecord(
                UUID.randomUUID(),
                operationId,
                0,
                0
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );

        assertThatThrownBy(() ->
            insertRawRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                -1
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );

        assertThatThrownBy(() ->
            insertRawRecord(
                UUID.randomUUID(),
                UUID.randomUUID(),
                -1,
                0
            )
        )
            .isInstanceOf(
                DataIntegrityViolationException.class
            );
    }

    @Test
    void successAndFailureRowsPersist() {
        AiUsageRecord success =
            record(
                AiUsageRecord.Status.SUCCEEDED,
                null,
                UUID.randomUUID(),
                UUID.randomUUID(),
                42,
                1
            );

        AiUsageRecord failure =
            record(
                AiUsageRecord.Status.FAILED_TERMINAL,
                AiProviderFailureClass.TIMEOUT,
                UUID.randomUUID(),
                UUID.randomUUID(),
                0,
                1
            );

        repository.saveAndFlush(
            AiUsageRecordJpaEntity.from(success)
        );
        repository.saveAndFlush(
            AiUsageRecordJpaEntity.from(failure)
        );

        assertThat(
            repository.findByOperationId(
                success.operationId()
            )
        )
            .isPresent()
            .get()
            .extracting(
                AiUsageRecordJpaEntity::status,
                AiUsageRecordJpaEntity::estimatedCostMicros
            )
            .containsExactly("SUCCEEDED", 42L);

        assertThat(
            repository.findByOperationId(
                failure.operationId()
            )
        )
            .isPresent()
            .get()
            .extracting(
                AiUsageRecordJpaEntity::status,
                AiUsageRecordJpaEntity::estimatedCostMicros
            )
            .containsExactly("FAILED_TERMINAL", 0L);
    }

    @Test
    void anonymizationNullsUserAndProblemSessionId() {
        UUID userId =
            UUID.randomUUID();
        UUID problemSessionId =
            UUID.randomUUID();

        AiUsageRecord record =
            record(
                AiUsageRecord.Status.SUCCEEDED,
                null,
                userId,
                problemSessionId,
                10,
                1
            );

        TransactionTemplate transactionTemplate =
            new TransactionTemplate(
                transactionManager
            );

        transactionTemplate.executeWithoutResult(
            status ->
                repository.saveAndFlush(
                    AiUsageRecordJpaEntity.from(
                        record
                    )
                )
        );

        transactionTemplate.executeWithoutResult(
            status ->
                repository.anonymizeUser(
                    userId
                )
        );

        Map<String, Object> row =
            jdbcTemplate.queryForMap(
                """
                select user_id, problem_session_id
                  from ai_usage_records
                 where id = ?
                """,
                record.id()
            );

        assertThat(row.get("user_id")).isNull();
        assertThat(
            row.get("problem_session_id")
        ).isNull();
    }

    private boolean tableExists(String tableName) {
        Boolean exists =
            jdbcTemplate.queryForObject(
                """
                select exists (
                    select 1
                      from information_schema.tables
                     where table_schema = 'public'
                       and table_name = ?
                )
                """,
                Boolean.class,
                tableName
            );

        return Boolean.TRUE.equals(exists);
    }

    private void insertRawRecord(
        UUID id,
        UUID operationId,
        long estimatedCostMicros,
        int attemptCount
    ) {
        jdbcTemplate.update(
            """
            insert into ai_usage_records (
                id,
                operation_id,
                capability,
                route_policy_version,
                route_id,
                provider,
                model,
                status,
                retryable,
                attempt_count,
                fallback_used,
                request_unit_count,
                estimated_cost_micros,
                currency,
                pricing_version,
                provider_latency_ms,
                gateway_latency_ms,
                created_at
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            id,
            operationId,
            "VISION_PARSE",
            "vision-route-v1",
            "vision-parse-default-v1",
            "LOCAL_FIXTURE",
            "local-fixture-vision-v1",
            "SUCCEEDED",
            false,
            attemptCount,
            false,
            1,
            estimatedCostMicros,
            "USD",
            "test-pricing-v1",
            0,
            0,
            Timestamp.from(Instant.now())
        );
    }

    private static AiUsageRecord record(
        AiUsageRecord.Status status,
        AiProviderFailureClass failureClass,
        UUID userId,
        UUID problemSessionId,
        long estimatedCostMicros,
        int attemptCount
    ) {
        Instant now =
            Instant.now();

        return new AiUsageRecord(
            UUID.randomUUID(),
            UUID.randomUUID(),
            userId,
            problemSessionId,
            AiCapability.VISION_PARSE,
            "vision-route-v1",
            "vision-parse-default-v1",
            "LOCAL_FIXTURE",
            "local-fixture-vision-v1",
            "vision-recognition",
            "v001",
            "recognition-evidence-v1",
            "provider-request-id",
            "provider-response-id",
            status,
            failureClass,
            failureClass != null,
            attemptCount,
            false,
            List.of(),
            1,
            2,
            null,
            1,
            estimatedCostMicros,
            "USD",
            "test-pricing-v1",
            3,
            4,
            "correlation-id",
            "trace-id",
            now,
            now
        );
    }
}
