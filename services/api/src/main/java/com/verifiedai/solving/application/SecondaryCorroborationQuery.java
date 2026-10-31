package com.verifiedai.solving.application;

import com.verifiedai.problem.application.solving.SolveSourceAccess;
import com.verifiedai.solving.application.secondary.BlindAgreementAnalyzer;
import com.verifiedai.solving.domain.model.canonical.CanonicalSolution;
import com.verifiedai.solving.infrastructure.persistence.SolveStore;
import com.verifiedai.solving.infrastructure.persistence.SecondarySolveStore;
import com.verifiedai.solving.domain.model.secondary.SecondaryState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Bounded, owned read of existing corroboration. No secondary dispatch or usage access. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class SecondaryCorroborationQuery {
    public enum Signal { NONE, AGREEMENT, DISAGREEMENT, INDETERMINATE, INCOMPATIBLE_ASSUMPTIONS,
        INVALID_INDEPENDENCE, STALE_LINEAGE, PENDING }
    public record Snapshot(List<UUID> references, List<Signal> signals) {
        public Snapshot { references = List.copyOf(references); signals = List.copyOf(signals); }
    }
    private final SolveSourceAccess sources;
    private final SolveStore primary;
    private final SecondarySolveStore store;
    private final ObjectMapper mapper = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
    public SecondaryCorroborationQuery(SolveSourceAccess sources, SolveStore primary, SecondarySolveStore store) {
        this.sources = sources; this.primary = primary; this.store = store;
    }
    public boolean currentPrimary(UUID user, UUID session, UUID job) {
        sources.lockOwner(user, session);
        return primary.latestForSession(user, session).map(j -> j.id().equals(job)
            && j.state() == com.verifiedai.solving.domain.model.SolveState.GENERATED_UNVERIFIED).orElse(false);
    }
    public Snapshot snapshot(CanonicalSolution solution) {
        var l = solution.lineage();
        sources.lockOwner(l.userId(), l.sessionId());
        UUID parent = l.origin().role() == CanonicalSolution.Role.PRIMARY ? l.origin().jobId() : l.parentPrimaryJobId();
        var refs = new ArrayList<UUID>();
        var signals = EnumSet.noneOf(Signal.class);
        var rows = store.corroboration(l.userId(), l.sessionId(), parent);
        if (rows.size() > 16) return new Snapshot(List.of(), List.of(Signal.INVALID_INDEPENDENCE));
        for (var row : rows) {
            refs.add(row.id());
            if (!l.canonicalProblemId().equals(row.canonicalId()) || !l.parseId().equals(row.parseId())
                || l.parseRevision() != row.parseRevision()
                || l.canonicalRevision() != row.canonicalRevision()) {
                signals.add(Signal.STALE_LINEAGE); continue;
            }
            String json = row.agreement();
            if (json == null) {
                if (Set.of(SecondaryState.QUEUED, SecondaryState.RUNNING).contains(row.state())) signals.add(Signal.PENDING);
                else if (row.state() == SecondaryState.GENERATED_UNVERIFIED) signals.add(Signal.INVALID_INDEPENDENCE);
                else if (row.state() == SecondaryState.STALE) signals.add(Signal.STALE_LINEAGE);
                continue;
            }
            try {
                if (json.length() > 4096 || row.state() != SecondaryState.GENERATED_UNVERIFIED) throw new IllegalArgumentException();
                var document = mapper.readTree(json);
                if (!document.path("sameMethod").isBoolean() || !document.path("mathematicalVerificationPerformed").isBoolean())
                    throw new IllegalArgumentException("Missing typed corroboration flags");
                for (var field : List.of("primaryStepCount", "secondaryStepCount")) {
                    var count = document.path(field);
                    if (!count.isIntegralNumber() || !count.canConvertToInt() || count.intValue() < 1 || count.intValue() > 24)
                        throw new IllegalArgumentException("Invalid corroboration step count");
                }
                for (var field : List.of("schemaVersion", "comparisonPolicyVersion", "outcome", "reason", "independence")) {
                    var value = document.path(field);
                    if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 128)
                        throw new IllegalArgumentException("Invalid corroboration metadata");
                }
                var e = mapper.treeToValue(document, BlindAgreementAnalyzer.Evidence.class);
                if (!"blind-agreement-v1".equals(e.schemaVersion()) || !"exact-visible-answer-v1".equals(e.comparisonPolicyVersion())
                    || e.mathematicalVerificationPerformed() || !"APPROVED_DISTINCT_MODEL_SHARED_FAILURE_RISK".equals(e.independence()))
                    signals.add(Signal.INVALID_INDEPENDENCE);
                else signals.add(Signal.valueOf(e.outcome().name()));
            } catch (Exception e) { signals.add(Signal.INVALID_INDEPENDENCE); }
        }
        if (signals.isEmpty()) signals.add(Signal.NONE);
        return new Snapshot(refs, List.copyOf(signals));
    }
}
