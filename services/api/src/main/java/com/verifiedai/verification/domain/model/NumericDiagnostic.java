package com.verifiedai.verification.domain.model;

import java.nio.charset.StandardCharsets;
import java.util.*;

/** Ephemeral diagnostics. A proof outcome/descriptor is intentionally not representable. */
public final class NumericDiagnostic {
    private NumericDiagnostic() { }
    public static final String REQUEST_VERSION = "numeric-diagnostic-v1";
    public static final String RESPONSE_VERSION = "numeric-diagnostic-result-v1";
    public static final String POLICY = "equivalence-binary64-v1";
    public static final String CAPABILITY = "numeric-capabilities-v1";
    public enum Status { AGREEMENT, MISMATCH, INCONCLUSIVE, UNSUPPORTED, BLOCKED, EXECUTION_FAILED }
    public enum Reason {
        SAMPLES_AGREE, SAMPLES_DIFFER, INSUFFICIENT_SAMPLES, UNSTABLE_SAMPLES,
        UNSUPPORTED_SUBSET, COMPLEXITY_LIMIT, UNKNOWN_DOMAIN, INTEGER_DOMAIN,
        TIMEOUT, RESOURCE_FAILURE, WORKER_FAILURE, ADMISSION_LIMIT, DISABLED, INVALID_RESPONSE
    }
    public record Request(UUID scopeId, MathematicalExecution.Request input) {
        public Request {
            Objects.requireNonNull(scopeId); Objects.requireNonNull(input);
            if (input.method() != VerificationMethod.NUMERIC_SAMPLING)
                throw new IllegalArgumentException("Numeric diagnostic requires its own method");
        }
    }
    public static UUID scope(VerificationPlan plan, PlanningSource source) {
        return UUID.nameUUIDFromBytes((REQUEST_VERSION + "/" + plan.id() + "/" + source.identity()
            + "/" + plan.solutionId() + "/" + plan.solutionVersion()).getBytes(StandardCharsets.UTF_8));
    }
    public record Result(UUID executionId, String correlationId, UUID scopeId, Status status, Reason reasonCode,
        List<String> restrictionIds, int attempted, int admissible, int evaluated, int agreeing, int disagreeing, int unstable) {
        public Result {
            Objects.requireNonNull(executionId); Objects.requireNonNull(scopeId);
            Objects.requireNonNull(status); Objects.requireNonNull(reasonCode);
            restrictionIds = List.copyOf(restrictionIds);
            if (correlationId == null || !correlationId.matches("[A-Za-z0-9_-]{1,64}")
                || restrictionIds.size() > 48 || new HashSet<>(restrictionIds).size() != restrictionIds.size()
                || restrictionIds.stream().anyMatch(id -> !id.matches("[A-Za-z0-9_-]{1,96}"))
                || attempted < 0 || attempted > 16 || admissible < 0 || evaluated < 0 || agreeing < 0
                || disagreeing < 0 || unstable < 0 || admissible > attempted || evaluated > admissible
                || agreeing > 16 || disagreeing > 16 || unstable > 16
                || agreeing + disagreeing != evaluated || evaluated + unstable > attempted)
                throw new IllegalArgumentException("Invalid diagnostic bounds");
            var allowed = switch (status) {
                case AGREEMENT -> Set.of(Reason.SAMPLES_AGREE);
                case MISMATCH -> Set.of(Reason.SAMPLES_DIFFER);
                case INCONCLUSIVE -> Set.of(Reason.INSUFFICIENT_SAMPLES, Reason.UNSTABLE_SAMPLES);
                case UNSUPPORTED -> Set.of(Reason.UNSUPPORTED_SUBSET, Reason.COMPLEXITY_LIMIT, Reason.INTEGER_DOMAIN);
                case BLOCKED -> Set.of(Reason.UNKNOWN_DOMAIN);
                case EXECUTION_FAILED -> Set.of(Reason.TIMEOUT, Reason.RESOURCE_FAILURE, Reason.WORKER_FAILURE,
                    Reason.ADMISSION_LIMIT, Reason.DISABLED, Reason.INVALID_RESPONSE);
            };
            if (!allowed.contains(reasonCode)) throw new IllegalArgumentException("Diagnostic status/reason mismatch");
            if ((status == Status.AGREEMENT || status == Status.MISMATCH)
                && (attempted != 16 || evaluated < 4 || unstable != 0 || (status == Status.AGREEMENT) != (disagreeing == 0)))
                throw new IllegalArgumentException("Insufficient stable samples");
            if (Set.of(Status.BLOCKED, Status.UNSUPPORTED, Status.EXECUTION_FAILED).contains(status) && attempted != 0)
                throw new IllegalArgumentException("Unavailable diagnostic has samples");
            if (status == Status.INCONCLUSIVE && (attempted != 16 || (reasonCode == Reason.UNSTABLE_SAMPLES) != (unstable > 0)
                || reasonCode == Reason.INSUFFICIENT_SAMPLES && evaluated >= 4))
                throw new IllegalArgumentException("Invalid inconclusive diagnostic");
        }
        public void validateFor(Request request) {
            var input = request.input();
            if (attempted > 0 && (input.variables().size() > 1 || input.variables().values().stream().anyMatch(d -> d != PlanningSource.Domain.REAL)
                || !(input.original() instanceof com.verifiedai.solving.domain.model.canonical.MathStatement.Expression)
                || !(input.candidate() instanceof com.verifiedai.solving.domain.model.canonical.MathStatement.Expression)))
                throw new IllegalArgumentException("Samples outside supported numeric scope");
            if (!executionId.equals(input.executionId()) || !correlationId.equals(input.correlationId())
                || !scopeId.equals(request.scopeId())
                || !restrictionIds.equals(input.restrictions().stream().map(PlanningSource.Restriction::id).toList()))
                throw new IllegalArgumentException("Diagnostic scope mismatch");
        }
        public static Result failed(Request r, Reason reason) {
            return new Result(r.input().executionId(), r.input().correlationId(), r.scopeId(), Status.EXECUTION_FAILED,
                reason, r.input().restrictions().stream().map(PlanningSource.Restriction::id).toList(), 0, 0, 0, 0, 0, 0);
        }
    }
    public record Evidence(Request request, Result result) {
        public Evidence { Objects.requireNonNull(request); Objects.requireNonNull(result); result.validateFor(request); }
    }
}
