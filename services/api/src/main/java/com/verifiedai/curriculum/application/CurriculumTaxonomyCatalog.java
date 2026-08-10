package com.verifiedai.curriculum.application;

import java.util.Set;

public interface CurriculumTaxonomyCatalog {
    boolean isActiveSubject(String subjectId);

    boolean isActiveTopic(String topicId);

    boolean isActiveSkill(String skillId);

    String topicForSkill(String skillId);

    String subjectForTopic(String topicId);

    Set<String> activeSkillIdsForTopic(String topicId);

    CurriculumTaxonomySnapshot snapshot();
}
