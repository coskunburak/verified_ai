package com.verifiedai.problem.application;

import java.util.List;

record ProblemClassificationCandidates(
    String ontologyVersion,
    List<String> primarySkillIds,
    List<String> secondarySkillIds
) {
    ProblemClassificationCandidates {
        primarySkillIds = List.copyOf(primarySkillIds);
        secondarySkillIds = List.copyOf(secondarySkillIds);
    }
}
