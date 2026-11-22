package com.verifiedai.notification.domain;

/**
 * Lifecycle states of a persisted notification delivery record.
 */
public enum NotificationDeliveryStatus {
    SCHEDULED(false, true),
    DEFERRED_QUIET_HOURS(false, true),
    DISPATCHING(false, false),
    SENT(true, false),
    RETRY_SCHEDULED(false, true),
    SUPPRESSED(true, false),
    FAILED_PERMANENT(true, false),
    CANCELLED(true, false);

    private final boolean terminal;
    private final boolean dispatchable;

    NotificationDeliveryStatus(boolean terminal, boolean dispatchable) {
        this.terminal = terminal;
        this.dispatchable = dispatchable;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean isDispatchable() {
        return dispatchable;
    }
}
