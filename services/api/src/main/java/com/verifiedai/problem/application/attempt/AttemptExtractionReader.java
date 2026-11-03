package com.verifiedai.problem.application.attempt;

import com.verifiedai.problem.application.recognition.*;
import com.verifiedai.sharedkernel.error.*;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Reuses the owned upload/preprocessing/recognition job and its schema-validated gateway evidence. */
@Component
public class AttemptExtractionReader {
    private final ProblemRecognitionApplicationService recognition;
    private final boolean enabled;
    public AttemptExtractionReader(ProblemRecognitionApplicationService recognition,@Value("${verifiedai.attempt.vision-enabled:false}") boolean enabled) {
        this.recognition=recognition; this.enabled=enabled;
    }
    public record Line(int index,String rawTranscription,String normalizedStatement,String latex,
        RecognitionBoundingBoxResult boundingBox,BigDecimal confidence,boolean reviewRequired) { }
    public record Extraction(String schemaVersion,UUID assetSessionId,UUID recognitionJobId,String status,
        String provider,String model,String upstreamSchemaVersion,String promptVersion,List<Line> steps,boolean trainingEligible) { }
    public Extraction read(UUID owner,UUID assetSession,Set<String> variables,ManualStepNormalizer normalizer) {
        if(!enabled) throw unavailable();
        var result=recognition.getRecognition(owner,assetSession);
        if(!"SUCCEEDED".equals(result.status()) || result.provider()==null || result.provider().contains("FIXTURE")) throw unavailable();
        var ordered=result.blocks().stream().sorted(Comparator.comparingInt(RecognitionBlockResult::readingOrder)).toList();
        var lines=new ArrayList<Line>();
        for(var block:ordered) {
            // Preserve complete blocks: silently flattening superscripts/multiline layout is unsafe.
            if(block.text()==null || block.text().isBlank() || block.text().length()>256 || block.text().contains("\n") || lines.size()>=12) throw unavailable();
            String normalized=normalizer.normalizeV2(block.text(),variables);
            lines.add(new Line(lines.size()+1,block.text(),normalized,null,block.boundingBox(),block.normalizedConfidence(),true));
        }
        if(lines.isEmpty()) throw unavailable();
        return new Extraction("attempt-extraction-v1",assetSession,result.recognitionJobId(),"REVIEW_REQUIRED",
            result.provider(),result.model(),result.schemaVersion(),result.promptVersion(),List.copyOf(lines),false);
    }
    private static ApiProblemException unavailable() { return new ApiProblemException(HttpStatus.SERVICE_UNAVAILABLE,ApiErrorCode.ATTEMPT_UNAVAILABLE,"Extraction requires supported, reviewed recognition evidence",true,"RETRY"); }
}
