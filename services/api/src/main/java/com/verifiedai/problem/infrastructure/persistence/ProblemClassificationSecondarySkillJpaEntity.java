package com.verifiedai.problem.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "problem_classification_secondary_skills")
public class ProblemClassificationSecondarySkillJpaEntity {

    @EmbeddedId
    private ProblemClassificationSecondarySkillId id;

    @Column(nullable = false)
    private short ordinal;

    protected ProblemClassificationSecondarySkillJpaEntity() {
    }

    public ProblemClassificationSecondarySkillJpaEntity(
        UUID classificationId,
        String skillId,
        int ordinal
    ) {
        if (ordinal < 0
            || ordinal
            >= ProblemClassificationSecondarySkillLimit.MAX) {
            throw new IllegalArgumentException(
                "Secondary skill ordinal is outside supported range"
            );
        }

        this.id =
            new ProblemClassificationSecondarySkillId(
                classificationId,
                skillId
            );

        this.ordinal = (short) ordinal;
    }

    public UUID classificationId() {
        return id.classificationId();
    }

    public String skillId() {
        return id.skillId();
    }

    public short ordinal() {
        return ordinal;
    }
}
