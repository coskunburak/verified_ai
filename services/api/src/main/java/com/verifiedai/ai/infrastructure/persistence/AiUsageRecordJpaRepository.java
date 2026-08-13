package com.verifiedai.ai.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiUsageRecordJpaRepository
    extends JpaRepository<
    AiUsageRecordJpaEntity,
    UUID
    > {

    Optional<AiUsageRecordJpaEntity>
    findByOperationId(
        UUID operationId
    );

    long countByUserId(
        UUID userId
    );

    @Modifying(
        clearAutomatically = true,
        flushAutomatically = true
    )
    @Query("""
        update AiUsageRecordJpaEntity record
           set record.userId = null,
               record.problemSessionId = null
         where record.userId = :userId
        """)
    int anonymizeUser(
        @Param("userId")
        UUID userId
    );
}
