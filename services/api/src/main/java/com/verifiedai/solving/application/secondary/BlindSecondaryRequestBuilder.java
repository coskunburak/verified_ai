package com.verifiedai.solving.application.secondary;

import com.verifiedai.ai.application.AiSolveRequest;
import com.verifiedai.problem.application.solving.SolveSource;
import com.verifiedai.solving.application.CandidateRejected;
import com.verifiedai.solving.application.SolutionCandidateValidator;
import java.util.Set;
import org.springframework.stereotype.Component;

/** No primary candidate, view, transcript, store or arbitrary request input is accepted here. */
@Component
public final class BlindSecondaryRequestBuilder {
    private static final Set<String> CANONICAL_FIELDS=Set.of("schemaVersion","sourceParse","subjectId","topicId",
        "problemType","taskType","variables","statements","sourceConstraints","sourceAssumptions",
        "derivedRestrictions","complexityPolicy","display");
    private final SolutionCandidateValidator validator;
    public BlindSecondaryRequestBuilder(SolutionCandidateValidator validator) { this.validator=validator; }
    public AiSolveRequest build(SolveSource source) {
        var canonical=validator.sourceDocument(source);
        canonical.fieldNames().forEachRemaining(key->{
            if(!CANONICAL_FIELDS.contains(key)) throw new CandidateRejected("NON_CANONICAL_INPUT");
        });
        return new AiSolveRequest(source.canonicalProblemId(),source.problemParseId(),
            source.problemParseRevision(),validator.serialize(canonical));
    }
}
