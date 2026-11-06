package com.verifiedai.attempt.application;

import com.verifiedai.attempt.domain.*;
import com.verifiedai.problem.application.attempt.ManualStepNormalizer;
import java.util.*;
import org.springframework.stereotype.Component;

/** Reuses the existing parser. No algebraic simplification or guessed OCR repairs. */
@Component
public class AttemptWorkNormalizer {
    private final ManualStepNormalizer parser;
    public AttemptWorkNormalizer(ManualStepNormalizer parser) { this.parser=parser; }
    public List<AttemptStructuredWork.Step> normalize(AttemptWorkInput input,Set<String> variables) {
        var output=new ArrayList<AttemptStructuredWork.Step>();
        for(var step:input.steps()) {
            boolean review=!step.confirmed() || step.role()==AttemptWorkInput.Role.AMBIGUOUS
                || step.text().contains("\n") || step.text().contains("\r");
            String statement=review || variables==null ? null : parser.normalizeV2(step.text(),variables);
            // Do not extend this sprint to calculus or to synthesized domain/solution-set proofs.
            if(statement!=null && (statement.length()>1800 || statement.matches("(?s).*\"function\":\"(DIFF|LIMIT|INTEGRAL)\".*"))) statement=null;
            var status=review?AttemptStructuredWork.NormalizationStatus.REVIEW_REQUIRED:
                statement==null?AttemptStructuredWork.NormalizationStatus.UNSUPPORTED:AttemptStructuredWork.NormalizationStatus.NORMALIZED;
            output.add(new AttemptStructuredWork.Step(step.id(),output.size()+1,step.role(),
                input.engine()==AttemptWorkInput.Engine.MANUAL?step.text():null,step.text(),step.observationIds(),statement,status,
                review?AttemptStructuredWork.ReviewStatus.REQUIRED:AttemptStructuredWork.ReviewStatus.CONFIRMED));
        }
        return List.copyOf(output);
    }
}
