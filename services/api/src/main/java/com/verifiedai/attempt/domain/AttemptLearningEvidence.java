package com.verifiedai.attempt.domain;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** An internal verifier artifact, not a learner achievement. Never expose correctOption before completion. */
public record AttemptLearningEvidence(String schemaVersion,String verifierVersion,UUID executionId,String correlationId,
    String action,String status,Twin twin,List<Method> methods) {
    public record Twin(String templateVersion,MistakeCode mistakeCode,JsonNode problem,List<JsonNode> options,
        int correctOption,String explanationKey,String domainLabel,String difficulty) {
        public Twin {
            options=List.copyOf(options);
            if(!"verified-twin-v1".equals(templateVersion) || mistakeCode==null || options.size()!=4 || correctOption<0 || correctOption>3
                || !Objects.equals(explanationKey,"attempt.mistake."+mistakeCode.name()) || domainLabel==null || domainLabel.length()>120
                || !"FOUNDATION".equals(difficulty)) throw new IllegalArgumentException("Invalid twin evidence");
            statement(problem); options.forEach(AttemptLearningEvidence::statement);
        }
    }
    public record Method(String methodId,List<JsonNode> steps,String evidenceVersion) {
        public Method {
            steps=List.copyOf(steps);
            if(!Set.of("FACTORING","COMPLETING_SQUARE","QUADRATIC_FORMULA","SYMBOLIC_SIMPLIFICATION").contains(methodId)
                || !"verified-method-v1".equals(evidenceVersion) || steps.isEmpty() || steps.size()>4) throw new IllegalArgumentException("Invalid method evidence");
            steps.forEach(AttemptLearningEvidence::statement);
        }
    }
    public AttemptLearningEvidence {
        methods=List.copyOf(methods);
        if(!"attempt-learning-result-v1".equals(schemaVersion) || !"bounded-learning-v1".equals(verifierVersion)
            || !Set.of("TWIN","METHODS").contains(action) || !Set.of("VERIFIED","UNSUPPORTED","UNAVAILABLE").contains(status)
            || methods.size()>3) throw new IllegalArgumentException("Invalid learning evidence");
        if(status.equals("VERIFIED") ? (action.equals("TWIN")!=(twin!=null) || action.equals("METHODS")!=!methods.isEmpty()) : (twin!=null || !methods.isEmpty()))
            throw new IllegalArgumentException("Invalid learning artifact");
    }
    public void validateFor(UUID id,String expectedAction) {
        if(!id.equals(executionId) || !id.toString().equals(correlationId) || !expectedAction.equals(action)) throw new IllegalArgumentException("Learning evidence binding");
    }
    private static void statement(JsonNode n) {
        if(n==null || !n.isObject() || !Set.of("EXPRESSION","RELATION").contains(n.path("kind").asText()) || n.toString().length()>8000)
            throw new IllegalArgumentException("Invalid mathematical artifact");
    }
}
