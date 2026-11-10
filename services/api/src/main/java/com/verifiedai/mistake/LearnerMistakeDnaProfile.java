package com.verifiedai.mistake;

import java.math.BigDecimal;
import java.time.Instant;

public record LearnerMistakeDnaProfile(String mistakeCode, String skillId, String taxonomyVersion,
    String categoryVersion, String policyVersion, BigDecimal decayedErrorCount, BigDecimal decayedExposureCount,
    BigDecimal smoothedPropensity, int recentErrorCount, MistakeRecurrenceState recurrenceState,
    Instant lastOccurredAt, Instant asOf) { }
