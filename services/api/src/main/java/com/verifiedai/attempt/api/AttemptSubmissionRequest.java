package com.verifiedai.attempt.api;

import com.fasterxml.jackson.databind.*;
import com.verifiedai.attempt.domain.*;
import com.verifiedai.sharedkernel.contracts.VersionedJsonContract;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import java.io.IOException;
import java.util.*;
import org.springframework.http.HttpStatus;

/** Typed HTTP command. The bounded filter validates the exact wire contract before MVC binding.
 * Jackson-2 is used only for the existing bundled-contract boundary; MVC may use Jackson-3. */
public record AttemptSubmissionRequest(AttemptSourceIdentity source,List<String> steps,Boolean realDomainConfirmed,
    AttemptCaptureReview captureReview,String finalAnswer,String schemaVersion) {
    private static final ObjectMapper STRICT=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    public AttemptSubmissionRequest {
        if(source==null || steps==null) throw invalid();
        steps=List.copyOf(steps);
        if(schemaVersion==null) {
            if(finalAnswer!=null) throw invalid();
            AttemptAnalysisPolicy.submission("validation-key",steps);
            if(captureReview!=null && captureReview.rawLines().size()!=steps.size()) throw invalid();
        } else {
            if(!"attempt-submission-v3".equals(schemaVersion) || captureReview!=null || realDomainConfirmed!=null) throw invalid();
            AttemptAnalysisPolicy.answerSubmission("validation-key",steps,finalAnswer);
        }
    }
    public static AttemptSubmissionRequest parse(JsonNode node) {
        try {
            String version=node.has("schemaVersion")?"attempt-submission-v3":"attempt-submission-v2";
            VersionedJsonContract.validate(version,node.toString());
            var source=STRICT.treeToValue(node.get("source"),AttemptSourceIdentity.class);
            var steps=new ArrayList<String>(); node.get("steps").forEach(s->steps.add(s.textValue()));
            var review=node.has("captureReview")?STRICT.treeToValue(node.get("captureReview"),AttemptCaptureReview.class):null;
            return new AttemptSubmissionRequest(source,steps,node.has("realDomainConfirmed")?node.get("realDomainConfirmed").booleanValue():null,
                review,node.has("finalAnswer")?node.get("finalAnswer").textValue():null,
                node.has("schemaVersion")?node.get("schemaVersion").textValue():null);
        } catch(IOException | IllegalArgumentException | NullPointerException ex) { throw invalid(); }
    }
    private static com.verifiedai.sharedkernel.error.ApiProblemException invalid() {
        return AttemptAnalysisPolicy.error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
