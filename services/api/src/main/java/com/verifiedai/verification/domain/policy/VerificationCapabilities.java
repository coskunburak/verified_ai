package com.verifiedai.verification.domain.policy;

import com.verifiedai.verification.domain.model.VerificationMethod;
import com.verifiedai.verification.domain.model.VerificationPlan.*;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Versioned runtime contract inventory, not a health check or an execution adapter. */
@Component
public final class VerificationCapabilities {
    public static final String VERSION = "canonical-capabilities-v1";
    private final boolean equivalenceContractPresent;
    public VerificationCapabilities() { this(true); }
    private VerificationCapabilities(boolean present) { equivalenceContractPresent = present; }
    public static VerificationCapabilities none() { return new VerificationCapabilities(false); }
    public String version() { return equivalenceContractPresent ? VERSION : "canonical-capabilities-none-v1"; }
    public Set<VerificationMethod> availableMethods() { return Set.of(); }
    public Availability availability(VerificationMethod method) {
        return equivalenceContractPresent && method == VerificationMethod.SYMBOLIC_EQUIVALENCE
            ? Availability.BLOCKED_BY_PRECONDITION : Availability.PLANNED_NOT_EXECUTABLE;
    }
    public Reason reason(VerificationMethod method) {
        return availability(method) == Availability.BLOCKED_BY_PRECONDITION
            ? Reason.CANONICAL_EXECUTION_ADAPTER_UNAVAILABLE : Reason.CAPABILITY_NOT_IMPLEMENTED;
    }
}
