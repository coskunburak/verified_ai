package com.verifiedai.studyplan.coldstart;

import com.verifiedai.studyplan.coldstart.ColdStartModels.StoredColdStartRecord;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence and read-projection port for Sprint 8.10 cold-start diagnostics.
 */
public interface ColdStartDiagnosticRepository {

    Optional<StoredColdStartRecord> findByLearnerIdAndIdempotencyKey(UUID learnerId, String idempotencyKey);

    Optional<StoredColdStartRecord> findLatestByLearnerId(UUID learnerId);

    Map<String, Integer> findSkillEvidenceCounts(UUID learnerId, Collection<String> skillCodes);

    Optional<Instant> findLastVerifiedActivityAt(UUID learnerId);

    void insertDiagnostic(StoredColdStartRecord record);
}
