package com.verifiedai.attempt.domain;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Exact generated methods only. Never an attempt, mastery, solver or arbitration verdict. */
public record MethodComparisonEvidence(String schemaVersion,String verifierVersion,UUID executionId,String correlationId,
    String action,String status,String reasonCode,JsonNode source,JsonNode naturalDomain,List<Method> methods) {
    public record Comparison(String policyVersion,String presentationVersion,String applicability,String reasonCode,
        String prerequisite,String advantage,String limitation,List<String> domainCautions,int structuralStepCount,
        List<Integer> compactStepIndices,String manipulationLevel,String recommendation) {
        public Comparison {
            if(domainCautions==null || compactStepIndices==null) throw new IllegalArgumentException("Missing comparison conditions");
            domainCautions=List.copyOf(domainCautions); compactStepIndices=List.copyOf(compactStepIndices);
        }
    }
    public record Method(String methodId,String evidenceVersion,List<JsonNode> steps,List<String> stepReasons,
        String proofScope,String transitionStatus,String outcomeStatus,Comparison comparison) {
        public Method {
            if(steps==null || stepReasons==null || comparison==null) throw new IllegalArgumentException("Missing method evidence");
            steps=List.copyOf(steps); stepReasons=List.copyOf(stepReasons);
        }
    }
    public MethodComparisonEvidence {
        if(methods==null || source==null) throw new IllegalArgumentException("Missing source evidence");
        methods=List.copyOf(methods);
        if(!"method-comparison-result-v2".equals(schemaVersion) || !"bounded-methods-v2".equals(verifierVersion)
            || !"METHODS".equals(action) || executionId==null || !executionId.toString().equals(correlationId)
            || !Set.of("VERIFIED","UNSUPPORTED","NO_ALTERNATIVE","UNAVAILABLE").contains(status)
            || methods.size()>3 || "VERIFIED".equals(status)!=!methods.isEmpty()) throw new IllegalArgumentException("Invalid comparison");
        var rules=Map.of(
            "FACTORING",List.of("ZERO_PRODUCT","FACTORING","VISIBLE_FACTORS","FACTOR_DISCOVERY","LOW"),
            "COMPLETING_SQUARE",List.of("SQUARE_IDENTITY","SQUARE_IDENTITIES","VISIBLE_VERTEX","FRACTION_ARITHMETIC","MODERATE"),
            "QUADRATIC_FORMULA",List.of("DISCRIMINANT_FORMULA","QUADRATIC_FORMULA","GENERAL_QUADRATIC","FORMULA_SUBSTITUTION","MODERATE"),
            "SYMBOLIC_SIMPLIFICATION",List.of("EXACT_SIMPLIFICATION","POLYNOMIAL_ALGEBRA","NORMALIZED_EXPRESSION","BOUNDED_POLYNOMIAL","LOW"),
            "POLYNOMIAL_EXPANSION",List.of("EXPAND_COLLECT","POLYNOMIAL_ALGEBRA","VISIBLE_COEFFICIENTS","EXPANSION_GROWTH","LOW"),
            "DIRECT_ALGEBRA",List.of("ISOLATE_VARIABLE","LINEAR_ALGEBRA","DIRECT_OUTCOME","LINEAR_ONLY","LOW"),
            "RATIONAL_CLEARING",List.of("CLEAR_DENOMINATORS","DOMAIN_AND_FACTORS","VISIBLE_NUMERATOR","EXCLUDED_ROOTS","MODERATE"),
            "ABSOLUTE_CASES",List.of("SPLIT_ABSOLUTE_CASES","PIECEWISE_SIGNS","EXPLICIT_BRANCHES","AFFINE_ABSOLUTE_ONLY","MODERATE"));
        var recommendations=Map.of(
            "FACTORING",Set.of("REPEATED_ROOT_STRUCTURE","FACTORABLE_OVER_RATIONALS"),
            "COMPLETING_SQUARE",Set.of("NEGATIVE_DISCRIMINANT","DIRECT_SQUARE","VERTEX_FORM"),
            "QUADRATIC_FORMULA",Set.of("EXACT_RADICAL_FORMULA","EQUALLY_VALID"),
            "SYMBOLIC_SIMPLIFICATION",Set.of("EXPOSE_FACTORS"), "POLYNOMIAL_EXPANSION",Set.of("COLLECT_TERMS"),
            "DIRECT_ALGEBRA",Set.of("ISOLATE_LINEAR","RESOLVE_CONSTANT"),
            "RATIONAL_CLEARING",Set.of("PRESERVE_DOMAIN_HOLES"), "ABSOLUTE_CASES",Set.of("SPLIT_ABSOLUTE_CASES"));
        var applications=Map.of(
            "FACTORING",Set.of("RATIONAL_QUADRATIC"), "COMPLETING_SQUARE",Set.of("RATIONAL_QUADRATIC"), "QUADRATIC_FORMULA",Set.of("RATIONAL_QUADRATIC"),
            "SYMBOLIC_SIMPLIFICATION",Set.of("RATIONAL_POLYNOMIAL"), "POLYNOMIAL_EXPANSION",Set.of("RATIONAL_POLYNOMIAL"),
            "DIRECT_ALGEBRA",Set.of("RATIONAL_LINEAR","CONSTANT_EQUATION"),
            "RATIONAL_CLEARING",Set.of("RATIONAL_EQUATION"), "ABSOLUTE_CASES",Set.of("ABSOLUTE_AFFINE"));
        var statusReasons=Map.of("VERIFIED",Set.of("EXACT_METHODS"),"UNSUPPORTED",Set.of("UNSUPPORTED_SOURCE"),
            "NO_ALTERNATIVE",Set.of("NO_DISTINCT_DERIVATION"),"UNAVAILABLE",Set.of("TIMEOUT","RESOURCE_FAILURE","WORKER_FAILURE","ADMISSION_LIMIT","INVALID_RESPONSE","DISABLED"));
        if(!statusReasons.get(status).contains(reasonCode)) throw new IllegalArgumentException("Invalid outcome reason");
        var ids=new HashSet<String>();
        if(!methods.isEmpty() && (naturalDomain==null || !Set.of("REALS","FINITE_SET","INTERVAL","UNION","INTERSECTION").contains(naturalDomain.path("kind").asText())
            || !Set.of("SOURCE_EXPLICIT","REVIEWED_REAL_TASK").contains(source.path("domainBasis").asText())))
            throw new IllegalArgumentException("Invalid natural domain");
        for(var method:methods) {
            var c=method.comparison(); var indices=c.compactStepIndices();
            if(!ids.add(method.methodId()) || method.steps().isEmpty() || method.steps().size()>4
                || method.steps().size()!=method.stepReasons().size() || c.structuralStepCount()!=method.steps().size()
                || indices.isEmpty() || indices.getFirst()<0 || indices.getLast()!=method.steps().size()-1
                || !indices.equals(indices.stream().distinct().sorted().toList())) throw new IllegalArgumentException("Invalid derivation");
            if(!Objects.equals(rules.get(method.methodId()),List.of(c.reasonCode(),c.prerequisite(),c.advantage(),c.limitation(),c.manipulationLevel())))
                throw new IllegalArgumentException("Invalid method policy");
            boolean expression=Set.of("SYMBOLIC_SIMPLIFICATION","POLYNOMIAL_EXPANSION").contains(method.methodId());
            var cautions=new ArrayList<String>(); cautions.add("REAL_DOMAIN");
            if(!source.path("restrictions").isEmpty()) cautions.add("SOURCE_RESTRICTIONS");
            if(!"REALS".equals(naturalDomain.path("kind").asText())) cautions.add("NATURAL_DOMAIN_EXCLUSIONS");
            if(!"method-comparison-policy-v3".equals(c.policyVersion()) || !"method-presentation-v2".equals(c.presentationVersion())
                || !"verified-method-v2".equals(method.evidenceVersion()) || !"EXACT_SYMBOLIC".equals(method.transitionStatus())
                || !"SOURCE_DOMAIN_COMPLETE_OUTCOME".equals(method.proofScope())
                || !recommendations.get(method.methodId()).contains(c.recommendation()) || !cautions.equals(c.domainCautions())
                || !(expression?"EXPRESSION_IDENTITY":"COMPLETE_SOLUTION_SET").equals(method.outcomeStatus())
                || !applications.get(method.methodId()).contains(c.applicability())
                || !(expression?c.reasonCode():"COMPLETE_REAL_SET").equals(method.stepReasons().getLast()))
                throw new IllegalArgumentException("Invalid comparison scope");
        }
    }
    private static JsonNode normalized(JsonNode node) {
        if(node.isObject()) {
            var result=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            node.fields().forEachRemaining(e->{ if(!e.getValue().isNull()) result.set(e.getKey(),normalized(e.getValue())); });
            return result;
        }
        if(node.isArray()) {
            var result=com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            node.forEach(v->result.add(normalized(v))); return result;
        }
        return node;
    }
    public void validateFor(UUID id,JsonNode expectedSource) {
        if(!id.equals(executionId) || !normalized(source).equals(normalized(expectedSource))) throw new IllegalArgumentException("Comparison source binding");
    }
}
