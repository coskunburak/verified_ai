package com.verifiedai.mistake;

import java.math.BigDecimal;

public enum MistakeRecurrenceState {
    OBSERVED_ONCE, RECURRING_PATTERN, IN_REMEDIATION, RESOLVED_PENDING_RETENTION, STABLE_RESOLVED, RELAPSED;

    /** Sprint 7.5 projects observations only. No remediation or retention evidence exists here. */
    public static MistakeRecurrenceState project(EmpiricalBayesMistakeRateCalculator.Rate rate) {
        return rate.recentErrors()>=2 && rate.propensity().compareTo(new BigDecimal("0.22"))>=0
            ? RECURRING_PATTERN : OBSERVED_ONCE;
    }
    boolean isObservation() { return this == OBSERVED_ONCE || this == RECURRING_PATTERN; }
    public void requireObservationTransition(MistakeRecurrenceState next) {
        if((this!=OBSERVED_ONCE && this!=RECURRING_PATTERN) || (next!=OBSERVED_ONCE && next!=RECURRING_PATTERN))
            throw new IllegalArgumentException("Remediation requires future authoritative evidence");
    }
}
