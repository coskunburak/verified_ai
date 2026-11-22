package com.verifiedai.notification.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Pure domain state-transition validator for {@link NotificationDeliveryStatus}.
 */
public final class NotificationDeliveryStateMachine {

    private static final Set<NotificationDeliveryStatus> INITIAL_TARGETS = EnumSet.of(
        NotificationDeliveryStatus.SCHEDULED,
        NotificationDeliveryStatus.DEFERRED_QUIET_HOURS,
        NotificationDeliveryStatus.SUPPRESSED
    );

    private static final Map<NotificationDeliveryStatus, Set<NotificationDeliveryStatus>> ALLOWED_TRANSITIONS =
        buildAllowedTransitions();

    private NotificationDeliveryStateMachine() {
    }

    public static boolean canTransition(NotificationDeliveryStatus from, NotificationDeliveryStatus to) {
        if (to == null) {
            return false;
        }
        if (from == null) {
            return INITIAL_TARGETS.contains(to);
        }
        return ALLOWED_TRANSITIONS.getOrDefault(from, EnumSet.noneOf(NotificationDeliveryStatus.class)).contains(to);
    }

    public static void assertTransition(NotificationDeliveryStatus from, NotificationDeliveryStatus to) {
        Objects.requireNonNull(to, "target delivery status must not be null");
        if (!canTransition(from, to)) {
            String fromLabel = from == null ? "NEW" : from.name();
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.NOTIFICATION_ILLEGAL_STATE_TRANSITION,
                "Illegal notification delivery state transition from " + fromLabel + " to " + to.name(),
                false,
                "REFRESH"
            );
        }
    }

    private static Map<NotificationDeliveryStatus, Set<NotificationDeliveryStatus>> buildAllowedTransitions() {
        Map<NotificationDeliveryStatus, Set<NotificationDeliveryStatus>> map =
            new EnumMap<>(NotificationDeliveryStatus.class);
        map.put(
            NotificationDeliveryStatus.SCHEDULED,
            EnumSet.of(
                NotificationDeliveryStatus.DISPATCHING,
                NotificationDeliveryStatus.DEFERRED_QUIET_HOURS,
                NotificationDeliveryStatus.SUPPRESSED,
                NotificationDeliveryStatus.CANCELLED
            )
        );
        map.put(
            NotificationDeliveryStatus.DEFERRED_QUIET_HOURS,
            EnumSet.of(
                NotificationDeliveryStatus.DISPATCHING,
                NotificationDeliveryStatus.SUPPRESSED,
                NotificationDeliveryStatus.CANCELLED
            )
        );
        map.put(
            NotificationDeliveryStatus.DISPATCHING,
            EnumSet.of(
                NotificationDeliveryStatus.SENT,
                NotificationDeliveryStatus.RETRY_SCHEDULED,
                NotificationDeliveryStatus.SUPPRESSED,
                NotificationDeliveryStatus.FAILED_PERMANENT,
                NotificationDeliveryStatus.SCHEDULED
            )
        );
        map.put(
            NotificationDeliveryStatus.RETRY_SCHEDULED,
            EnumSet.of(
                NotificationDeliveryStatus.DISPATCHING,
                NotificationDeliveryStatus.DEFERRED_QUIET_HOURS,
                NotificationDeliveryStatus.SUPPRESSED,
                NotificationDeliveryStatus.FAILED_PERMANENT,
                NotificationDeliveryStatus.CANCELLED
            )
        );
        map.put(NotificationDeliveryStatus.SENT, EnumSet.noneOf(NotificationDeliveryStatus.class));
        map.put(NotificationDeliveryStatus.SUPPRESSED, EnumSet.noneOf(NotificationDeliveryStatus.class));
        map.put(NotificationDeliveryStatus.FAILED_PERMANENT, EnumSet.noneOf(NotificationDeliveryStatus.class));
        map.put(NotificationDeliveryStatus.CANCELLED, EnumSet.noneOf(NotificationDeliveryStatus.class));
        return Map.copyOf(map);
    }
}
