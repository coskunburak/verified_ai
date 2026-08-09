package com.verifiedai.curriculum.application;

import java.util.Set;

public record CurriculumTaxonomySnapshot(
    String ontologyVersion,
    Set<String> activeSubjectIds,
    Set<String> activeTopicIds
) {
}
