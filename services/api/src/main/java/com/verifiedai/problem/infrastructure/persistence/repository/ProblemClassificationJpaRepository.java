package com.verifiedai.problem.infrastructure.persistence.repository;

import com.verifiedai.problem.infrastructure.persistence.entity.ProblemClassificationJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemClassificationJpaRepository
    extends JpaRepository<ProblemClassificationJpaEntity, UUID> {

    Optional<ProblemClassificationJpaEntity>
    findByClassificationJobId(
        UUID classificationJobId
    );

    Optional<ProblemClassificationJpaEntity>
    findByRequestFingerprint(
        String requestFingerprint
    );

    Optional<ProblemClassificationJpaEntity>
    findFirstByCanonicalProblemIdAndUserIdOrderByRevisionDesc(
        UUID canonicalProblemId,
        UUID userId
    );

    List<ProblemClassificationJpaEntity>
    findByCanonicalProblemIdInAndUserIdOrderByRevisionDesc(
        List<UUID> canonicalProblemIds,
        UUID userId
    );

    Optional<ProblemClassificationJpaEntity>
    findFirstByProblemSessionIdAndUserIdOrderByCreatedAtDesc(
        UUID problemSessionId,
        UUID userId
    );

    @Query("""
        select coalesce(max(classification.revision), 0)
        from ProblemClassificationJpaEntity classification
        where classification.canonicalProblemId = :canonicalProblemId
        """)
    int maxRevision(
        @Param("canonicalProblemId")
        UUID canonicalProblemId
    );
}
