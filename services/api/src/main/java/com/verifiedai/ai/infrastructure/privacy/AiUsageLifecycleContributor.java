package com.verifiedai.ai.infrastructure.privacy;

import com.verifiedai.ai.infrastructure.persistence.AiUsageRecordJpaRepository;
import com.verifiedai.sharedkernel.privacy.AccountDataLifecycleContributor;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
    name = "app.ai.usage-ledger.enabled",
    havingValue = "true",
    matchIfMissing = true
)
class AiUsageLifecycleContributor
    implements AccountDataLifecycleContributor {

    private final AiUsageRecordJpaRepository
        repository;

    AiUsageLifecycleContributor(
        AiUsageRecordJpaRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public String category() {
        return "aiUsage";
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(
        UUID userId
    ) {
        long count =
            repository.countByUserId(
                userId
            );

        if (count == 0) {
            return Map.of();
        }

        return Map.of(
            "operationCount",
            count,
            "containsRawStudentContent",
            false
        );
    }

    @Override
    @Transactional
    public void deleteUserData(
        UUID userId,
        Instant now
    ) {
        repository.anonymizeUser(
            userId
        );
    }
}
