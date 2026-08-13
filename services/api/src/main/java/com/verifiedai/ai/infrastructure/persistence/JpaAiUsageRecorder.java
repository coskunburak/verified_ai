package com.verifiedai.ai.infrastructure.persistence;

import com.verifiedai.ai.application.AiUsageRecord;
import com.verifiedai.ai.application.AiUsageRecorder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
    name = "app.ai.usage-ledger.enabled",
    havingValue = "true",
    matchIfMissing = true
)
class JpaAiUsageRecorder
    implements AiUsageRecorder {

    private final AiUsageRecordJpaRepository
        repository;

    JpaAiUsageRecorder(
        AiUsageRecordJpaRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void reserve(
        AiUsageRecord record
    ) {
        if (
            repository
                .findByOperationId(
                    record.operationId()
                )
                .isPresent()
        ) {
            throw new IllegalStateException(
                "AI operation already has a usage ledger record"
            );
        }

        repository.saveAndFlush(
            AiUsageRecordJpaEntity.from(
                record
            )
        );
    }

    @Override
    @Transactional
    public void complete(
        AiUsageRecord record
    ) {
        AiUsageRecordJpaEntity entity =
            repository
                .findById(record.id())
                .orElseThrow(() ->
                    new IllegalStateException(
                        "AI usage ledger reservation is missing"
                    )
                );

        entity.apply(record);

        repository.saveAndFlush(entity);
    }
}
