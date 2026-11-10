package com.verifiedai.attempt.application;

import com.verifiedai.attempt.domain.*;
import com.verifiedai.mistake.LearnerMistakeDnaService;
import com.verifiedai.sharedkernel.error.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttemptStepOcrCorrectionService {
    public record Input(String schemaVersion,AttemptSourceIdentity source,int workRevision,String correctedText) {
        public Input {
            if(!"attempt-ocr-correction-request-v1".equals(schemaVersion) || source==null || workRevision<1 || workRevision>30
                || !AttemptAnalysisPolicy.validText(correctedText) || correctedText.contains("\n") || correctedText.contains("\r"))
                throw AttemptAnalysisPolicy.error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
    public record Result(String schemaVersion,LearnerMistakeDnaService.Result operation,AttemptStructuredWork work) { }
    private final AttemptWorkService works;
    private final LearnerMistakeDnaService dna;
    public AttemptStepOcrCorrectionService(AttemptWorkService works,LearnerMistakeDnaService dna) { this.works=works; this.dna=dna; }
    /** Normalization and DB only. Evaluation is explicitly required on the returned replacement attempt. */
    @Transactional(timeout=5)
    public Result correct(UUID owner,UUID problem,UUID attempt,int step,String key,Input input) {
        var old=works.read(owner,problem,attempt); // account then session lock, including replay authorization
        if(step<1 || step>old.steps().size() || old.engine()==AttemptWorkInput.Engine.MANUAL
            || !old.source().equals(input.source()) || old.revision()!=input.workRevision())
            throw AttemptAnalysisPolicy.error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
        String hash=LearnerMistakeDnaService.hash("ocr:"+problem+":"+attempt+":"+step+":"+input.source()+":"+input.workRevision()+":"+input.correctedText());
        var replay=dna.correctionReplay(owner,key,hash);
        if(replay.isPresent()) return new Result("attempt-ocr-correction-result-v1",replay.get(),works.read(owner,problem,replay.get().replacementAttemptId()));
        if(old.steps().get(step-1).reviewedText().equals(input.correctedText()))
            throw AttemptAnalysisPolicy.error(ApiErrorCode.REQUEST_VALIDATION_FAILED,HttpStatus.UNPROCESSABLE_ENTITY);
        var revised=old.steps().stream().map(s->new AttemptWorkInput.Step(s.id(),s.index()==step?input.correctedText():s.reviewedText(),
            s.role(),s.observationIds(),s.index()==step || s.reviewStatus()==AttemptStructuredWork.ReviewStatus.CONFIRMED)).toList();
        var inputWork=new AttemptWorkInput("attempt-structured-work-request-v1",old.source(),old.engine(),old.captureId(),old.assetSessionId(),
            old.recognitionJobId(),attempt,old.observations(),revised);
        // Separate stable internal key namespace from ordinary authored-work keys.
        var replacement=works.submit(owner,problem,"ocr-"+LearnerMistakeDnaService.hash(key),inputWork);
        var operation=dna.corrected(owner,attempt,replacement.attemptId(),step,key,hash);
        return new Result("attempt-ocr-correction-result-v1",operation,replacement);
    }
}
