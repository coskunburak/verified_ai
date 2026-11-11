package com.verifiedai.mastery.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class LearnerSkillMasteryId implements Serializable {
    private UUID userId;
    private String skillCode;

    public LearnerSkillMasteryId() {
    }

    public LearnerSkillMasteryId(UUID userId, String skillCode) {
        this.userId = userId;
        this.skillCode = skillCode;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getSkillCode() {
        return skillCode;
    }

    public void setSkillCode(String skillCode) {
        this.skillCode = skillCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LearnerSkillMasteryId that = (LearnerSkillMasteryId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(skillCode, that.skillCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, skillCode);
    }
}
