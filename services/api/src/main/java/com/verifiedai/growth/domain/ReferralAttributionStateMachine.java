package com.verifiedai.growth.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Pure deterministic state machine for referral attribution lifecycle (INV-GRW-002).
 */
public final class ReferralAttributionStateMachine {

    private static final Map<ReferralAttributionStatus, Set<ReferralAttributionStatus>> ALLOWED =
        new EnumMap<>(ReferralAttributionStatus.class);

    static {
        ALLOWED.put(
            ReferralAttributionStatus.ATTRIBUTED,
            EnumSet.of(
                ReferralAttributionStatus.QUALIFIED,
                ReferralAttributionStatus.REJECTED_ABUSE,
                ReferralAttributionStatus.EXPIRED
            )
        );
        ALLOWED.put(
            ReferralAttributionStatus.QUALIFIED,
            EnumSet.of(
                ReferralAttributionStatus.REWARD_GRANTED,
                ReferralAttributionStatus.REJECTED_ABUSE
            )
        );
        ALLOWED.put(ReferralAttributionStatus.REWARD_GRANTED, EnumSet.noneOf(ReferralAttributionStatus.class));
        ALLOWED.put(ReferralAttributionStatus.REJECTED_ABUSE, EnumSet.noneOf(ReferralAttributionStatus.class));
        ALLOWED.put(ReferralAttributionStatus.EXPIRED, EnumSet.noneOf(ReferralAttributionStatus.class));
    }

    private ReferralAttributionStateMachine() {
    }

    public static void assertTransitionAllowed(ReferralAttributionStatus from, ReferralAttributionStatus to) {
        if (from == null || to == null) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.GROWTH_INVALID_TRANSITION,
                "Referral attribution status cannot be null",
                false,
                "CHECK_REFERRAL_STATUS"
            );
        }
        Set<ReferralAttributionStatus> allowed = ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new ApiProblemException(
                HttpStatus.CONFLICT,
                ApiErrorCode.GROWTH_INVALID_TRANSITION,
                "Illegal referral attribution transition from " + from + " to " + to,
                false,
                "CHECK_REFERRAL_STATUS"
            );
        }
    }
}
