package com.verifiedai.exam.infrastructure.persistence;

import com.verifiedai.exam.domain.ExamScratchpadAsset;
import com.verifiedai.exam.domain.ExamSession;
import com.verifiedai.exam.domain.ExamSessionItem;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamSessionRepository {

    record IdempotencyRecord(
            UUID id,
            UUID sessionId,
            UUID userId,
            String operationType,
            String idempotencyKey,
            String requestPayloadHash,
            int responseStatusCode,
            String responseSnapshotJson,
            boolean trainingEligible,
            Instant createdAt
    ) {
    }

    Optional<ExamSession> findById(UUID sessionId);

    Optional<ExamSession> findActiveByUserIdAndAssemblyId(UUID userId, UUID assemblyId);

    List<ExamSession> findByUserId(UUID userId);

    ExamSession saveSession(ExamSession session);

    ExamSession updateSessionHeader(ExamSession session);

    ExamSession updateSessionAndItem(ExamSession session, ExamSessionItem updatedItem, int expectedAutosaveVersion);

    ExamSession saveScratchpadAsset(ExamSession session, ExamSessionItem updatedItem, ExamScratchpadAsset asset);

    Optional<IdempotencyRecord> findIdempotencyRecord(UUID sessionId, String operationType, String idempotencyKey);

    void saveIdempotencyRecord(IdempotencyRecord record);

    int deleteByUserId(UUID userId);
}
