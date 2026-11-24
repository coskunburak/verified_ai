package com.verifiedai.growth.domain;

public enum ReferralAttributionStatus {
    ATTRIBUTED,
    QUALIFIED,
    REWARD_GRANTED,
    REJECTED_ABUSE,
    EXPIRED;

    public boolean isTerminal() {
        return this == REWARD_GRANTED || this == REJECTED_ABUSE || this == EXPIRED;
    }
}
