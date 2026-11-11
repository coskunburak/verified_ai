package com.verifiedai.mastery.application;

import java.util.Map;

public interface MathVerifierTwinPort {

    GeneratedTwinSpec synthesizeIsomorphicTwin(
            String curriculumCode,
            String targetSkillCode,
            String rootCauseSkillCode,
            Map<String, Object> canonicalProblemAst,
            String attemptId
    );

    boolean verifySymbolicEquivalence(String submittedLatex, String expectedCanonicalExpr);
}
