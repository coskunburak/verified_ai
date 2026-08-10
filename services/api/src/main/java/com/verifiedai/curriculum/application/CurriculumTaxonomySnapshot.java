package com.verifiedai.curriculum.application;

import java.util.Map;
import java.util.Set;

public record CurriculumTaxonomySnapshot(
    String ontologyVersion,
    Set<String> activeSubjectIds,
    Set<String> activeTopicIds,
    Set<String> activeSkillIds,
    Map<String, String> skillToTopicMap,
    Map<String, String> topicToSubjectMap
) {
}
