package com.verifiedai.problem.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class ProblemClassificationSecondarySkillId
    implements Serializable {

    @Column(name = "classification_id")
    private UUID classificationId;

    @Column(name = "skill_id")
    private String skillId;

    protected ProblemClassificationSecondarySkillId() {
    }

    public ProblemClassificationSecondarySkillId(
        UUID classificationId,
        String skillId
    ) {
        this.classificationId =
            Objects.requireNonNull(classificationId);

        this.skillId =
            Objects.requireNonNull(skillId);
    }

    public UUID classificationId() {
        return classificationId;
    }

    public String skillId() {
        return skillId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (!(other
            instanceof ProblemClassificationSecondarySkillId that)) {
            return false;
        }

        return classificationId.equals(that.classificationId)
            && skillId.equals(that.skillId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            classificationId,
            skillId
        );
    }
}
