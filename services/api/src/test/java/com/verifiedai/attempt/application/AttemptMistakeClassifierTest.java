package com.verifiedai.attempt.application;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.verifiedai.attempt.domain.*;
import com.verifiedai.curriculum.application.CurriculumTaxonomyCatalog;
import com.verifiedai.mistake.*;
import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttemptMistakeClassifierTest {
    final ObjectMapper mapper=new ObjectMapper();
    final CurriculumTaxonomyCatalog skills=mock(CurriculumTaxonomyCatalog.class);
    final AttemptMistakeClassifier classifier=new AttemptMistakeClassifier(skills);
    ObjectNode fixture(String name) throws Exception {
        try(var in=getClass().getResourceAsStream("/schemas/fixtures/"+name+".json")) { return (ObjectNode)mapper.readTree(in); }
    }
    AttemptEvaluation evaluation(ObjectNode node) throws Exception { return mapper.treeToValue(node,AttemptEvaluation.class); }
    @Test void seventeenSignaturesMatchSharedCategoryAndOnlyActiveCanonicalSkills() throws Exception {
        JsonNode mapping;
        try(var in=getClass().getResourceAsStream("/schemas/mistake-category-v1.json")) { mapping=mapper.readTree(in); }
        assertThat(MistakeTaxonomy.CATEGORIES).hasSize(17);
        when(skills.isActiveSkill(anyString())).thenReturn(true);
        for(var row:mapping.path("mappings")) {
            var e=fixture("attempt-mistake-evaluation-v1"); String code=row.path("code").asText();
            var step=(ObjectNode)e.path("analysis").path("steps").get(0);
            step.put("mistakeCode",code); ((ObjectNode)step.path("diagnosis")).put("code",code).put("explanationKey","attempt.mistake."+code);
            var result=classifier.classify(evaluation(e),Instant.EPOCH);
            assertThat(result.decision().category().name()).isEqualTo(row.path("category").asText());
            assertThat(result.decision().skillId()).isEqualTo(row.path("skillId").isNull()?null:row.path("skillId").asText());
            assertThat(result.firstWrongStepIndex()).isEqualTo(1);
            assertThat(result.trainingEligible()).isFalse();
        }
    }
    @Test void inactiveOrAmbiguousSkillIsNeverInvented() throws Exception {
        var result=classifier.classify(evaluation(fixture("attempt-mistake-evaluation-v1")),Instant.EPOCH);
        assertThat(result.decision().skillId()).isNull();
        assertThat(MistakeTaxonomy.SKILLS).doesNotContainKey("CALC_PRODUCT_QUOTIENT_NAIVE");
    }
    @Test void genericDisproofNeverBecomesConceptOrReviewedOther() throws Exception {
        var e=fixture("attempt-mistake-evaluation-v1"); var step=(ObjectNode)e.path("analysis").path("steps").get(0);
        step.put("mistakeCode","INVALID_TRANSFORMATION"); ((ObjectNode)step.path("diagnosis")).put("code","INVALID_TRANSFORMATION").put("explanationKey","attempt.mistake.INVALID_TRANSFORMATION");
        var value=classifier.classify(evaluation(e),Instant.EPOCH).decision();
        assertThat(value.status()).isEqualTo(MistakeClassificationDecision.Status.INVALID_TRANSFORMATION);
        assertThat(value.evidenceStrength()).isEqualTo(MistakeClassificationDecision.Strength.EXACT_DISPROOF);
        assertThat(value.category()).isNull(); assertThat(value.skillId()).isNull();
        assertThat(value.reviewStatus()).isEqualTo(MistakeClassificationDecision.Review.REQUIRED);
    }
    @Test void historicalSignatureWithoutPolicyKeepsDisproofButRequiresReview() throws Exception {
        var node=fixture("attempt-mistake-evaluation-v1");
        ((ObjectNode)node.path("analysis").path("steps").get(0).path("diagnosis")).remove("classifierPolicyVersion");
        var e=evaluation(node); var result=classifier.classify(e,Instant.EPOCH);
        assertThat(result.decision().status()).isEqualTo(MistakeClassificationDecision.Status.INVALID_TRANSFORMATION);
        assertThat(result.decision().code()).isEqualTo("INVALID_TRANSFORMATION");
        assertThat(result.decision().category()).isNull(); assertThat(result.decision().skillId()).isNull();
        assertThat(result.supportingEvidenceId()).isEqualTo(e.executionId()+":1");
        result.validateFor(e);
        assertThat(e.analysis().steps().getFirst().mistakeCode()).isEqualTo(MistakeCode.ALG_SIGN_FLIP_DISTRIBUTE);
    }
    @Test void incompleteValidWorkIsNotAConfirmedIncompleteSolutionError() throws Exception {
        var result=classifier.classify(evaluation(fixture("attempt-evaluation-v1")),Instant.EPOCH);
        assertThat(result.decision().status()).isEqualTo(MistakeClassificationDecision.Status.INCOMPLETE_WORK);
        assertThat(result.decision().category()).isNull(); assertThat(result.firstWrongStepIndex()).isNull();
    }
    @Test void uncertaintyUnsupportedAndSourceReviewRemainDistinct() throws Exception {
        for(var pair:Map.of("UNKNOWN_DOMAIN","UNCERTAIN","UNSUPPORTED_MATH","UNSUPPORTED","UNDEFINED_SOURCE","SOURCE_REVIEW_REQUIRED").entrySet()) {
            var e=fixture("attempt-evaluation-v1"); var a=(ObjectNode)e.path("analysis");
            a.put("outcome","NEEDS_REVIEW").put("domain","UNSUPPORTED").put("domainBasis","UNRESOLVED");
            ((ObjectNode)a.path("steps").get(0)).put("verdict","UNCERTAIN").put("reasonCode",pair.getKey());
            ((ObjectNode)a.path("steps").get(1)).put("verdict","NOT_ASSESSED").put("reasonCode","EARLIER_UNCERTAINTY");
            var result=classifier.classify(evaluation(e),Instant.EPOCH);
            assertThat(result.decision().status().name()).isEqualTo(pair.getValue()); assertThat(result.supportingEvidenceId()).isNull();
        }
    }
    @Test void forgedEvidenceIdentityAndUncertainPrefixCannotBeClassified() throws Exception {
        var e=fixture("attempt-mistake-evaluation-v1");
        ((ObjectNode)e.path("analysis").path("steps").get(0).path("diagnosis")).put("evidenceId",UUID.randomUUID()+":1");
        var forged=e; assertThatThrownBy(()->evaluation(forged)).isInstanceOf(Exception.class);
        e=fixture("attempt-mistake-evaluation-v1");
        var steps=(com.fasterxml.jackson.databind.node.ArrayNode)e.path("analysis").path("steps");
        var wrong=steps.get(0).deepCopy(); var prefix=(ObjectNode)steps.get(1).deepCopy();
        prefix.put("index",1).put("verdict","UNCERTAIN").put("reasonCode","UNKNOWN_DOMAIN");
        ((ObjectNode)wrong).put("index",2); ((ObjectNode)wrong.path("diagnosis")).put("evidenceId",e.path("executionId").asText()+":2");
        steps.set(0,prefix); steps.set(1,wrong); ((ObjectNode)e.path("analysis")).put("firstWrongStepIndex",2);
        var bad=e; assertThatThrownBy(()->evaluation(bad)).isInstanceOf(Exception.class);
    }
    @Test void validPrefixLocatesTheSecondStepAndNeverDuplicatesDownstreamErrors() throws Exception {
        var node=fixture("attempt-mistake-evaluation-v1");
        var a=(ObjectNode)node.path("analysis"); var steps=(com.fasterxml.jackson.databind.node.ArrayNode)a.path("steps");
        var wrong=(ObjectNode)steps.get(0).deepCopy();
        var valid=(ObjectNode)fixture("attempt-evaluation-v1").path("analysis").path("steps").get(0).deepCopy();
        wrong.put("index",2); ((ObjectNode)wrong.path("diagnosis")).put("evidenceId",node.path("executionId").asText()+":2");
        steps.set(0,valid); steps.set(1,wrong); a.put("firstWrongStepIndex",2);
        var e=evaluation(node); var result=classifier.classify(e,Instant.EPOCH);
        assertThat(result.firstWrongStepIndex()).isEqualTo(2); assertThat(result.firstWrongStepId()).isEqualTo(e.stepIds().get(1));
        assertThat(result.supportingEvidenceId()).endsWith(":2");
    }
    @Test void unconfirmedCaptureRequiresReviewAndCannotAcquireProof() throws Exception {
        var workNode=fixture("attempt-evaluation-work-v1"); var first=(ObjectNode)workNode.path("steps").get(0);
        first.put("reviewStatus","REQUIRED").put("normalizationStatus","REVIEW_REQUIRED").putNull("normalizedStatement");
        var work=mapper.treeToValue(workNode,AttemptStructuredWork.class);
        var node=fixture("attempt-evaluation-v1"); var analysis=(ObjectNode)node.path("analysis");
        analysis.put("outcome","NEEDS_REVIEW").put("domain","UNSUPPORTED").put("domainBasis","UNRESOLVED");
        ((ObjectNode)analysis.path("steps").get(0)).put("verdict","UNCERTAIN").put("reasonCode","UNSUPPORTED_MATH");
        ((ObjectNode)analysis.path("steps").get(1)).put("verdict","NOT_ASSESSED").put("reasonCode","EARLIER_UNCERTAINTY");
        assertThat(classifier.classify(evaluation(node),work,Instant.EPOCH).decision().status())
            .isEqualTo(MistakeClassificationDecision.Status.SOURCE_REVIEW_REQUIRED);
        assertThatThrownBy(()->classifier.classify(evaluation(fixture("attempt-mistake-evaluation-v1")),work,Instant.EPOCH))
            .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void strictContractRejectsAuthorityProbabilityTrainingAndMissingEvidence() throws Exception {
        String name="attempt-mistake-classification-v1"; var fixture=fixture(name);
        VersionedJsonContract.validate(name,fixture.toString());
        for(String authority:List.of("MODEL_SUGGESTION","USER_CORRECTION","HUMAN_REVIEW")) {
            var bad=fixture.deepCopy(); ((ObjectNode)bad.path("decision")).put("authority",authority);
            assertThatThrownBy(()->VersionedJsonContract.validate(name,bad.toString())).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(()->mapper.treeToValue(bad,AttemptMistakeClassification.class)).isInstanceOf(Exception.class);
        }
        for(String field:List.of("firstWrongStepId","supportingEvidenceId","evaluationId","policyVersion")) {
            var bad=fixture.deepCopy(); bad.remove(field);
            assertThatThrownBy(()->VersionedJsonContract.validate(name,bad.toString())).isInstanceOf(IllegalArgumentException.class);
        }
        var bad=fixture.deepCopy(); ((ObjectNode)bad.path("decision")).put("confidence",0.95);
        assertThatThrownBy(()->VersionedJsonContract.validate(name,bad.toString())).isInstanceOf(IllegalArgumentException.class);
        var training=fixture.deepCopy().put("trainingEligible",true);
        assertThatThrownBy(()->VersionedJsonContract.validate(name,training.toString())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void disputeUnknownTaxonomyAndWrongEvaluationFailClosed() throws Exception {
        var e=evaluation(fixture("attempt-mistake-evaluation-v1")); var value=classifier.classify(e,Instant.EPOCH);
        assertThatThrownBy(()->value.validateFor(evaluation(fixture("attempt-evaluation-v1")))).isInstanceOf(IllegalArgumentException.class);
        for(String key:List.of("taxonomyVersion","categoryVersion","policyVersion")) {
            var bad=fixture("attempt-mistake-classification-v1").put(key,"future-v99");
            assertThatThrownBy(()->mapper.treeToValue(bad,AttemptMistakeClassification.class)).isInstanceOf(Exception.class);
        }
        var bad=fixture("attempt-mistake-classification-v1"); ((ObjectNode)bad.path("decision")).put("reviewStatus","USER_DISPUTED");
        assertThatThrownBy(()->VersionedJsonContract.validate("attempt-mistake-classification-v1",bad.toString())).isInstanceOf(IllegalArgumentException.class);
    }
}
