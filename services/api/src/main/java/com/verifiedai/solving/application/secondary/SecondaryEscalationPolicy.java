package com.verifiedai.solving.application.secondary;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

/** Priority ordered, server-only signals. No model confidence or invented verification coverage. */
public final class SecondaryEscalationPolicy {
    public enum Outcome { SKIP, ESCALATE, BLOCKED, INSUFFICIENT_EVIDENCE }
    public record Decision(String policyVersion, Outcome outcome, String reason) { }
    public record Inputs(UUID canonicalId, boolean current, boolean supported, boolean primaryPresent,
        boolean primaryIssues, boolean enabled, boolean approvedModel, boolean budgetAvailable) { }
    private final int sampleBasisPoints;
    private final String version;
    public SecondaryEscalationPolicy(int sampleBasisPoints) {
        if(sampleBasisPoints<0 || sampleBasisPoints>1000) throw new IllegalArgumentException("QC sample must be 0..1000 basis points");
        this.sampleBasisPoints=sampleBasisPoints;
        this.version="secondary-escalation-v1-qc"+sampleBasisPoints;
    }
    public String version() { return version; }
    public Decision decide(Inputs i) {
        if(!i.current()) return decision(Outcome.BLOCKED,"SOURCE_STALE");
        if(!i.supported()) return decision(Outcome.SKIP,"UNSUPPORTED_TASK");
        if(!i.primaryPresent() || i.canonicalId()==null) return decision(Outcome.INSUFFICIENT_EVIDENCE,"PRIMARY_EVIDENCE_MISSING");
        boolean sampled=sampled(i.canonicalId());
        if(!i.primaryIssues() && !sampled) return decision(Outcome.SKIP,"NO_ESCALATION_SIGNAL");
        if(!i.enabled()) return decision(Outcome.BLOCKED,"SECONDARY_DISABLED");
        if(!i.approvedModel()) return decision(Outcome.BLOCKED,"NO_APPROVED_INDEPENDENT_MODEL");
        if(!i.budgetAvailable()) return decision(Outcome.BLOCKED,"SECONDARY_BUDGET_UNAVAILABLE");
        return decision(Outcome.ESCALATE,i.primaryIssues()?"PRIMARY_UNRESOLVED":"BOUNDED_QC_SAMPLE");
    }
    public boolean sampled(UUID canonicalId) {
        try {
            byte[] digest=MessageDigest.getInstance("SHA-256").digest((canonicalId+":"+version).getBytes(StandardCharsets.UTF_8));
            long bucket=((digest[0]&255L)<<24)|((digest[1]&255L)<<16)|((digest[2]&255L)<<8)|(digest[3]&255L);
            return bucket%10000<sampleBasisPoints;
        } catch(java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private Decision decision(Outcome o,String reason) { return new Decision(version,o,reason); }
}
