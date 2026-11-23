package com.verifiedai.featureflags.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Enforces legal experiment lifecycle transitions (INV-FF-003).
 * KILLED -> RUNNING directly is explicitly prohibited; operators must inspect and transition via PAUSED.
 * CONCLUDED is strictly terminal.
 */
public final class ExperimentStateMachine {

    private static final Map<ExperimentStatus, Set<ExperimentStatus>> LEGAL_TRANSITIONS = buildTransitions();

    private ExperimentStateMachine() {
    }

    private static Map<ExperimentStatus, Set<ExperimentStatus>> buildTransitions() {
        Map<ExperimentStatus, Set<ExperimentStatus>> map = new EnumMap<>(ExperimentStatus.class);
        map.put(ExperimentStatus.DRAFT, EnumSet.of(ExperimentStatus.RUNNING, ExperimentStatus.KILLED));
        map.put(ExperimentStatus.RUNNING, EnumSet.of(ExperimentStatus.PAUSED, ExperimentStatus.KILLED, ExperimentStatus.CONCLUDED));
        map.put(ExperimentStatus.PAUSED, EnumSet.of(ExperimentStatus.RUNNING, ExperimentStatus.KILLED, ExperimentStatus.CONCLUDED));
        map.put(ExperimentStatus.KILLED, EnumSet.of(ExperimentStatus.PAUSED, ExperimentStatus.CONCLUDED));
        map.put(ExperimentStatus.CONCLUDED, EnumSet.noneOf(ExperimentStatus.class));
        return Map.copyOf(map);
    }

    public static boolean canTransition(ExperimentStatus from, ExperimentStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return LEGAL_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static void validateTransition(ExperimentStatus from, ExperimentStatus to) {
        Objects.requireNonNull(from, "from status must not be null");
        Objects.requireNonNull(to, "to status must not be null");
        if (!canTransition(from, to)) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.FEATURE_FLAG_INVALID_TRANSITION,
                "Illegal experiment transition from " + from + " to " + to,
                false,
                "CHECK_EXPERIMENT_STATE_MACHINE"
            );
        }
    }

    /**
     * Validates an admin update request, allowing rollout updates within non-terminal active/paused/draft states
     * while enforcing strict state transition rules whenever status changes.
     */
    public static void validateUpdate(ExperimentStatus from, ExperimentStatus to) {
        Objects.requireNonNull(from, "from status must not be null");
        Objects.requireNonNull(to, "to status must not be null");
        if (from == to) {
            if (from == ExperimentStatus.CONCLUDED || from == ExperimentStatus.KILLED) {
                throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.FEATURE_FLAG_INVALID_TRANSITION,
                    "Cannot modify experiment while in " + from + " state",
                    false,
                    "CHECK_EXPERIMENT_STATE_MACHINE"
                );
            }
            return;
        }
        validateTransition(from, to);
    }
}
