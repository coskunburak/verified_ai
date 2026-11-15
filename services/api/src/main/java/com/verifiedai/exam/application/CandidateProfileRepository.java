package com.verifiedai.exam.application;

import com.verifiedai.exam.domain.CandidateProfileModels.CandidateHorizonHistoryEntry;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileRecord;
import com.verifiedai.exam.domain.CandidateProfileModels.ParsedSyllabusTopicItem;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusUploadRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CandidateProfileRepository {

    Optional<CandidateProfileRecord> findProfileByUserId(UUID userId);

    Optional<CandidateProfileRecord> findProfileByUserAndIdempotencyKey(UUID userId, String idempotencyKey);

    CandidateProfileRecord saveProfileAndRecordHistory(
            CandidateProfileRecord profile,
            CandidateHorizonHistoryEntry historyEntry,
            boolean insertNewProfile
    );

    List<CandidateHorizonHistoryEntry> findHorizonHistoryByUserId(UUID userId, int limit);

    Optional<SyllabusUploadRecord> findSyllabusUploadById(UUID uploadId);

    Optional<SyllabusUploadRecord> findSyllabusUploadByUserAndHash(UUID userId, String imageHash);

    Optional<SyllabusUploadRecord> findSyllabusUploadByUserAndIdempotencyKey(UUID userId, String idempotencyKey);

    int countNonCachedSyllabusUploadsSince(UUID userId, Instant cutoff);

    SyllabusUploadRecord insertSyllabusUpload(SyllabusUploadRecord record);

    SyllabusUploadRecord confirmSyllabusUpload(
            UUID uploadId,
            UUID userId,
            List<ParsedSyllabusTopicItem> updatedTopics,
            Instant confirmedAt,
            Instant updatedAt
    );

    List<SyllabusUploadRecord> findSyllabusUploadsByUserId(UUID userId, int limit);

    int deleteAllCandidateDataForUser(UUID userId);

    int purgeExpiredSyllabusUploads(Instant cutoff);
}
