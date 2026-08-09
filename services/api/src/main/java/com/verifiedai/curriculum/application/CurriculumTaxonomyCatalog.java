package com.verifiedai.curriculum.application;

public interface CurriculumTaxonomyCatalog {
    boolean isActiveSubject(String subjectId);

    boolean isActiveTopic(String topicId);

    CurriculumTaxonomySnapshot snapshot();
}
