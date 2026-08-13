package com.verifiedai.problem.infrastructure.persistence.repository;

import com.verifiedai.problem.infrastructure.persistence.entity.ProblemClassificationSecondarySkillId;
import com.verifiedai.problem.infrastructure.persistence.entity.ProblemClassificationSecondarySkillJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemClassificationSecondarySkillJpaRepository
    extends JpaRepository<
    ProblemClassificationSecondarySkillJpaEntity,
    ProblemClassificationSecondarySkillId
    > {

    List<ProblemClassificationSecondarySkillJpaEntity>
    findByIdClassificationIdOrderByOrdinalAsc(
        UUID classificationId
    );

    void deleteByIdClassificationId(
        UUID classificationId
    );
}
