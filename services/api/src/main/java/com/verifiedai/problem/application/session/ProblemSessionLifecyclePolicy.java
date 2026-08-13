package com.verifiedai.problem.application.session;

import com.verifiedai.problem.domain.model.session.ProblemSessionStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ProblemSessionLifecyclePolicy {
    private static final Map<ProblemSessionStatus, EnumSet<ProblemSessionStatus>> LEGAL_TRANSITIONS =
        new EnumMap<>(ProblemSessionStatus.class);

    static {
        LEGAL_TRANSITIONS.put(
            ProblemSessionStatus.CREATED,
            EnumSet.of(ProblemSessionStatus.ASSET_UPLOADED, ProblemSessionStatus.FAILED)
        );
        LEGAL_TRANSITIONS.put(
            ProblemSessionStatus.ASSET_UPLOADED,
            EnumSet.of(ProblemSessionStatus.PARSING, ProblemSessionStatus.REVIEW_REQUIRED, ProblemSessionStatus.FAILED)
        );
        LEGAL_TRANSITIONS.put(
            ProblemSessionStatus.PARSING,
            EnumSet.of(ProblemSessionStatus.PARSED, ProblemSessionStatus.REVIEW_REQUIRED, ProblemSessionStatus.FAILED)
        );
        LEGAL_TRANSITIONS.put(
            ProblemSessionStatus.REVIEW_REQUIRED,
            EnumSet.of(ProblemSessionStatus.PARSING, ProblemSessionStatus.PARSED, ProblemSessionStatus.FAILED)
        );
        LEGAL_TRANSITIONS.put(
            ProblemSessionStatus.FAILED,
            EnumSet.of(ProblemSessionStatus.ASSET_UPLOADED, ProblemSessionStatus.PARSING)
        );
    }

    public boolean canTransition(ProblemSessionStatus from, ProblemSessionStatus to) {
        if (from == to) {
            return true;
        }
        return LEGAL_TRANSITIONS
            .getOrDefault(from, EnumSet.noneOf(ProblemSessionStatus.class))
            .contains(to);
    }

    public void requireTransition(ProblemSessionStatus from, ProblemSessionStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException("Illegal problem session transition " + from + " -> " + to);
        }
    }
}
