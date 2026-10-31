package com.verifiedai.verification.domain.policy;

import com.verifiedai.verification.domain.model.VerificationMethod;
import com.verifiedai.verification.domain.model.VerificationPlan.*;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Versioned runtime contract inventory, not a health check or an execution adapter. */
@Component
public final class VerificationCapabilities {
    public static final String VERSION = "canonical-capabilities-v2-disabled";
    private final boolean adapterInstalled;
    private final Set<VerificationMethod> approved;
    public VerificationCapabilities() { this(false, false, ""); }
    private VerificationCapabilities(boolean present) { adapterInstalled = present; approved = Set.of(); }
    @org.springframework.beans.factory.annotation.Autowired
    public VerificationCapabilities(
        @org.springframework.beans.factory.annotation.Value("${app.verification.execution-enabled:false}") boolean enabled,
        @org.springframework.beans.factory.annotation.Value("${app.verification.owner-accepted:false}") boolean accepted,
        @org.springframework.beans.factory.annotation.Value("${app.verification.methods:}") String methods) {
        adapterInstalled = true;
        var selected = java.util.EnumSet.noneOf(VerificationMethod.class);
        if (!methods.isBlank()) for (var name : methods.split(",")) {
            var method = VerificationMethod.valueOf(name.trim());
            if (method == VerificationMethod.FINITE_LIMIT || method == VerificationMethod.NUMERIC_SAMPLING || method == VerificationMethod.DERIVATIVE_BACKCHECK
                || method == VerificationMethod.INTEGRAL_DIFFERENTIATION_BACKCHECK)
                throw new IllegalArgumentException("Unapproved executable method");
            selected.add(method);
        }
        approved = enabled && accepted ? Set.copyOf(selected) : Set.of();
    }
    /** Availability inventory only: numerical sampling is deliberately absent from approved exact methods. */
    public record NumericCapability(String version, String algorithm, boolean implemented, boolean ownerAccepted,
        boolean runtimeEnabled, boolean productionApproved) { }
    private boolean numericAccepted;
    private boolean numericEnabled;
    @org.springframework.beans.factory.annotation.Autowired
    public void configureNumeric(
        @org.springframework.beans.factory.annotation.Value("${app.verification.numeric-enabled:false}") boolean enabled,
        @org.springframework.beans.factory.annotation.Value("${app.verification.numeric-owner-accepted:false}") boolean accepted,
        @org.springframework.beans.factory.annotation.Value("${app.environment:local}") String environment) {
        if (enabled && !Set.of("local", "test").contains(environment))
            throw new IllegalArgumentException("Numeric production rollout is not approved");
        numericAccepted = accepted; numericEnabled = enabled;
    }
    public NumericCapability numericCapability() {
        return new NumericCapability(com.verifiedai.verification.domain.model.NumericDiagnostic.CAPABILITY,
            com.verifiedai.verification.domain.model.NumericDiagnostic.POLICY, true, numericAccepted, numericEnabled, false);
    }
    public static VerificationCapabilities none() { return new VerificationCapabilities(false); }
    private boolean calculusApproved;
    private volatile boolean calculusReady;
    public static final String CALCULUS_VERSION = "calculus-capabilities-v1";
    @org.springframework.beans.factory.annotation.Autowired
    public void configureCalculus(
        @org.springframework.beans.factory.annotation.Value("${app.verification.calculus-enabled:false}") boolean enabled,
        @org.springframework.beans.factory.annotation.Value("${app.verification.calculus-owner-accepted:false}") boolean accepted,
        @org.springframework.beans.factory.annotation.Value("${app.verification.calculus-algorithm:bounded-calculus-v1}") String algorithm,
        @org.springframework.beans.factory.annotation.Value("${app.environment:local}") String environment) {
        if (enabled && (!java.util.Set.of("local","test").contains(environment) || !"bounded-calculus-v1".equals(algorithm)))
            throw new IllegalArgumentException("Calculus activation requires local/test and supported algorithm");
        calculusApproved=enabled && accepted;
    }
    public void calculusReadiness(boolean ready) { calculusReady=ready; }
    public Availability calculusAvailability() {
        return calculusApproved && calculusReady ? Availability.AVAILABLE : Availability.BLOCKED_BY_PRECONDITION;
    }
    public String calculusVersion() { return CALCULUS_VERSION + (calculusAvailability()==Availability.AVAILABLE ? "-available" : "-disabled"); }
    public String version() {
        if (approved.isEmpty()) return adapterInstalled ? VERSION : "canonical-capabilities-none-v2";
        String names = approved.stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(","));
        return "canonical-capabilities-v2-" + java.util.UUID.nameUUIDFromBytes(names.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    public record Subset(String algorithmVersion, String scope) { }
    public Subset subset(VerificationMethod method) {
        return switch (method) {
            case ARITHMETIC_EVALUATION -> new Subset("canonical-executor-v1", "Exact rational AST, integer powers <=12, 1024-bit intermediates");
            case SYMBOLIC_EQUIVALENCE -> new Subset("canonical-executor-v1", "One real/integer variable, degree <=12 rational polynomials, linear domain restrictions");
            case EQUATION_SUBSTITUTION -> new Subset("canonical-executor-v1", "One exact rational assignment, original equality and source restrictions");
            case DOMAIN_CONSTRAINT_CHECK -> new Subset("canonical-executor-v1", "Exact rational constraints and decidable real function domains; global polynomial obligations");
            case SOLUTION_SET_COMPLETENESS -> new Subset("canonical-executor-v1", "Degree <=2, rational roots, singleton assignment and original domain filtering");
            default -> new Subset("unavailable", "No canonical executor");
        };
    }
    public Set<VerificationMethod> availableMethods() { return approved; }
    public Availability availability(VerificationMethod method) {
        if (approved.contains(method)) return Availability.AVAILABLE;
        return adapterInstalled && executable(method) ? Availability.BLOCKED_BY_PRECONDITION : Availability.PLANNED_NOT_EXECUTABLE;
    }
    private static boolean executable(VerificationMethod method) {
        return method != VerificationMethod.FINITE_LIMIT && method != VerificationMethod.NUMERIC_SAMPLING && method != VerificationMethod.DERIVATIVE_BACKCHECK
            && method != VerificationMethod.INTEGRAL_DIFFERENTIATION_BACKCHECK;
    }
    public Reason reason(VerificationMethod method) {
        if (approved.contains(method)) return Reason.METHODS_SELECTED;
        return availability(method) == Availability.BLOCKED_BY_PRECONDITION
            ? Reason.CAPABILITY_RELEASE_NOT_APPROVED : Reason.CAPABILITY_NOT_IMPLEMENTED;
    }
}
