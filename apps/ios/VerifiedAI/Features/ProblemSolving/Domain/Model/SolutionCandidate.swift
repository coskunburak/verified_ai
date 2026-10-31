import Foundation

// This is a presentation contract. No expression is evaluated and no verification verdict exists.
enum SolveState: String, Decodable, CaseIterable, Sendable {
    case queued = "QUEUED", running = "RUNNING", generatedUnverified = "GENERATED_UNVERIFIED"
    case reviewRequired = "REVIEW_REQUIRED", retryableFailure = "RETRYABLE_FAILURE"
    case terminalFailure = "TERMINAL_FAILURE", policyBlocked = "POLICY_BLOCKED", unsupported = "UNSUPPORTED"
    case stale = "STALE", cancelled = "CANCELLED", outcomeUnknown = "OUTCOME_UNKNOWN"
    var isActive: Bool { self == .queued || self == .running }
    var showsCandidate: Bool { self == .generatedUnverified || self == .reviewRequired }
    var titleKey: String { "solve.state.\(rawValue)" }
    var detailKey: String { "solve.detail.\(rawValue)" }
    var symbolName: String {
        switch self {
        case .queued, .running: "hourglass"
        case .generatedUnverified: "doc.text"
        case .reviewRequired: "exclamationmark.bubble"
        case .retryableFailure, .outcomeUnknown: "arrow.clockwise.circle"
        case .terminalFailure, .policyBlocked, .unsupported: "exclamationmark.octagon"
        case .stale: "arrow.triangle.2.circlepath"
        case .cancelled: "xmark.circle"
        }
    }
}

enum SolveContractError: Error, Equatable {
    case invalidResponse
    case invalidExpression
    case recoveryStorage
    case unsupportedCandidateSchema
}

struct SolveJobSnapshot: Decodable, Equatable, Sendable {
    let jobId: UUID
    let canonicalProblemId: UUID
    let problemParseId: UUID
    let problemParseRevision: Int
    let attempt: Int
    let state: SolveState
    let failureCode: String?
    let createdAt: String
    let candidate: SolutionCandidate?
    let provider: String?
    let model: String?
    let promptVersion: String?
    let routePolicyVersion: String?
    let verificationNotice: String
    private var hasUnsupportedCandidateSchema = false

    private enum Keys: String, CodingKey {
        case jobId, canonicalProblemId, problemParseId, problemParseRevision, attempt, state, failureCode
        case createdAt, candidate, provider, model, promptVersion, routePolicyVersion, verificationNotice
    }

    private struct CandidateVersionHeader: Decodable {
        let schemaVersion: String
        let candidateVersion: Int
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Keys.self)
        jobId = try c.decode(UUID.self, forKey: .jobId)
        canonicalProblemId = try c.decode(UUID.self, forKey: .canonicalProblemId)
        problemParseId = try c.decode(UUID.self, forKey: .problemParseId)
        problemParseRevision = try c.decode(Int.self, forKey: .problemParseRevision)
        attempt = try c.decode(Int.self, forKey: .attempt)
        state = try c.decode(SolveState.self, forKey: .state)
        failureCode = try c.decodeIfPresent(String.self, forKey: .failureCode)
        createdAt = try c.decode(String.self, forKey: .createdAt)
        provider = try c.decodeIfPresent(String.self, forKey: .provider)
        model = try c.decodeIfPresent(String.self, forKey: .model)
        promptVersion = try c.decodeIfPresent(String.self, forKey: .promptVersion)
        routePolicyVersion = try c.decodeIfPresent(String.self, forKey: .routePolicyVersion)
        verificationNotice = try c.decode(String.self, forKey: .verificationNotice)
        if c.contains(.candidate), try !c.decodeNil(forKey: .candidate) {
            let header = try c.decode(CandidateVersionHeader.self, forKey: .candidate)
            if header.schemaVersion == "solution-candidate-v1" && header.candidateVersion == 1 {
                candidate = try c.decode(SolutionCandidate.self, forKey: .candidate)
            } else {
                candidate = nil
                hasUnsupportedCandidateSchema = true
            }
        } else {
            candidate = nil
        }
    }

    var mayRetry: Bool { state == .retryableFailure && attempt < 3 }
    var visibleCandidate: SolutionCandidate? { state.showsCandidate ? candidate : nil }

    func validated() throws -> Self {
        if hasUnsupportedCandidateSchema {
            throw SolveContractError.unsupportedCandidateSchema
        }
        guard problemParseRevision > 0, (1...3).contains(attempt),
              Self.isValidISO8601(createdAt),
              verificationNotice == "AI-generated candidate; mathematical verification has not been performed.",
              !state.showsCandidate || candidate != nil,
              [provider, model, promptVersion, routePolicyVersion, failureCode].allSatisfy({ ($0?.count ?? 0) <= 256 }) else {
            throw SolveContractError.invalidResponse
        }
        if let candidate {
            try candidate.validate()
            guard candidate.canonicalProblemId == canonicalProblemId, candidate.problemParseId == problemParseId,
                  candidate.problemParseRevision == problemParseRevision else { throw SolveContractError.invalidResponse }
        }
        return self
    }

    fileprivate static func isValidISO8601(_ value: String) -> Bool {
        guard !value.isEmpty, value.count <= 40 else { return false }
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if formatter.date(from: value) != nil { return true }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value) != nil
    }
}

struct SolutionCandidate: Decodable, Equatable, Sendable {
    enum Origin: String, Decodable, Sendable { case aiModel = "AI_MODEL", localFixture = "LOCAL_FIXTURE" }
    enum Problem: String, Decodable, Sendable {
        case arithmetic = "ARITHMETIC_EXPRESSION", algebra = "ALGEBRAIC_EXPRESSION", equation = "EQUATION", inequality = "INEQUALITY"
    }
    enum TaskKind: String, Decodable, Sendable {
        case evaluate = "EVALUATE", simplify = "SIMPLIFY", equation = "SOLVE_EQUATION", inequality = "SOLVE_INEQUALITY"
    }
    enum Method: String, Decodable, Sendable {
        case arithmetic = "ARITHMETIC", algebra = "ALGEBRAIC_TRANSFORMATION"
        case isolate = "ISOLATE_VARIABLE", inequality = "INEQUALITY_TRANSFORMATION"
        var titleKey: String { "solve.method.\(rawValue)" }
    }
    let schemaVersion: String
    let canonicalProblemId: UUID
    let problemParseId: UUID
    let problemParseRevision: Int
    let candidateVersion: Int
    let generationOrigin: Origin
    let problemType: Problem
    let taskType: TaskKind
    let method: Method
    let steps: [SolutionStep]
    let finalAnswer: MathStatement
    let sourceAssumptionIds: [String]
    let sourceConstraintIds: [String]
    let derivedRestrictionIds: [String]
    let ambiguities: [String]
    let unresolvedIssues: [String]

    private enum Keys: String, CodingKey {
        case schemaVersion, canonicalProblemId, problemParseId, problemParseRevision, candidateVersion
        case generationOrigin, problemType, taskType, method, steps, finalAnswer
        case sourceAssumptionIds, sourceConstraintIds, derivedRestrictionIds, ambiguities, unresolvedIssues
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Keys.self)
        schemaVersion = try c.decode(String.self, forKey: .schemaVersion)
        candidateVersion = try c.decode(Int.self, forKey: .candidateVersion)
        guard schemaVersion == "solution-candidate-v1", candidateVersion == 1 else {
            throw SolveContractError.unsupportedCandidateSchema
        }
        canonicalProblemId = try c.decode(UUID.self, forKey: .canonicalProblemId)
        problemParseId = try c.decode(UUID.self, forKey: .problemParseId)
        problemParseRevision = try c.decode(Int.self, forKey: .problemParseRevision)
        generationOrigin = try c.decode(Origin.self, forKey: .generationOrigin)
        problemType = try c.decode(Problem.self, forKey: .problemType)
        taskType = try c.decode(TaskKind.self, forKey: .taskType)
        method = try c.decode(Method.self, forKey: .method)
        steps = try c.decode([SolutionStep].self, forKey: .steps)
        finalAnswer = try c.decode(MathStatement.self, forKey: .finalAnswer)
        sourceAssumptionIds = try c.decode([String].self, forKey: .sourceAssumptionIds)
        sourceConstraintIds = try c.decode([String].self, forKey: .sourceConstraintIds)
        derivedRestrictionIds = try c.decode([String].self, forKey: .derivedRestrictionIds)
        ambiguities = try c.decode([String].self, forKey: .ambiguities)
        unresolvedIssues = try c.decode([String].self, forKey: .unresolvedIssues)
    }

    var hasSourceReferences: Bool { !sourceAssumptionIds.isEmpty || !sourceConstraintIds.isEmpty || !derivedRestrictionIds.isEmpty }

    func dependencyNumbers(for step: SolutionStep) -> String? {
        guard !step.dependsOn.isEmpty else { return nil }
        let numbers = steps.filter { step.dependsOn.contains($0.id) }.map { String($0.order) }
        return numbers.isEmpty ? nil : numbers.joined(separator: ", ")
    }

    func validate() throws {
        guard schemaVersion == "solution-candidate-v1", candidateVersion == 1 else {
            throw SolveContractError.unsupportedCandidateSchema
        }
        guard problemParseRevision > 0,
              (1...24).contains(steps.count), steps.map(\.order) == Array(1...steps.count),
              Set(steps.map(\.id)).count == steps.count,
              bounded(sourceAssumptionIds, count: 16, length: 96, unique: true),
              bounded(sourceConstraintIds, count: 16, length: 96, unique: true),
              bounded(derivedRestrictionIds, count: 32, length: 96, unique: true),
              bounded(ambiguities, count: 8, length: 500),
              bounded(unresolvedIssues, count: 8, length: 500) else {
            throw SolveContractError.invalidResponse
        }
        var previous = Set<String>()
        for step in steps {
            guard !step.id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, step.id.count <= 32,
                  !step.explanation.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, step.explanation.count <= 1000,
                  bounded(step.dependsOn, count: 24, length: 32, unique: true),
                  Set(step.dependsOn).isSubset(of: previous) else {
                throw SolveContractError.invalidResponse
            }
            try step.claim.validate()
            previous.insert(step.id)
        }
        try finalAnswer.validate()
    }

    private func bounded(_ values: [String], count: Int, length: Int, unique: Bool = false) -> Bool {
        values.count <= count
            && (!unique || Set(values).count == values.count)
            && values.allSatisfy { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && $0.count <= length }
    }
}

struct SolutionStep: Decodable, Equatable, Identifiable, Sendable {
    enum Category: String, Decodable, Sendable {
        case restate = "RESTATE", transform = "TRANSFORM", conclude = "CONCLUDE"
        var titleKey: String { "solve.step.category.\(rawValue)" }
    }
    let id: String
    let order: Int
    let category: Category
    let claim: MathStatement
    let explanation: String
    let dependsOn: [String]
}

indirect enum MathExpression: Decodable, Equatable, Sendable {
    enum NumericType: String, Decodable, Sendable { case integer = "INTEGER", decimal = "DECIMAL", rational = "RATIONAL" }
    enum Binary: String, Decodable, Sendable {
        case add = "ADD", subtract = "SUBTRACT", multiply = "MULTIPLY", divide = "DIVIDE", power = "POWER"
        var symbol: String {
            switch self { case .add: "+"; case .subtract: "−"; case .multiply: "×"; case .divide: "/"; case .power: "^" }
        }
    }
    enum Function: String, Decodable, Sendable {
        case sqrt = "SQRT", sin = "SIN", cos = "COS", tan = "TAN", log = "LOG", exp = "EXP"
    }
    case number(String, NumericType)
    case variable(String)
    case negate(MathExpression)
    case binary(Binary, MathExpression, MathExpression)
    case function(Function, MathExpression)

    private enum Keys: String, CodingKey { case kind, numericType, value, symbol, `operator`, operand, left, right, function, args }
    init(from decoder: Decoder) throws {
        guard decoder.codingPath.count <= 72 else { throw SolveContractError.invalidExpression }
        let c = try decoder.container(keyedBy: Keys.self)
        switch try c.decode(String.self, forKey: .kind) {
        case "NUMBER":
            let value = try c.decode(String.self, forKey: .value)
            guard value.count <= 80, value.range(of: #"^-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?$"#, options: .regularExpression) != nil else {
                throw SolveContractError.invalidExpression
            }
            self = .number(value, try c.decode(NumericType.self, forKey: .numericType))
        case "VARIABLE":
            let symbol = try c.decode(String.self, forKey: .symbol)
            guard symbol.range(of: #"^[A-Za-z][A-Za-z0-9_]{0,15}$"#, options: .regularExpression) != nil else { throw SolveContractError.invalidExpression }
            self = .variable(symbol)
        case "UNARY":
            guard try c.decode(String.self, forKey: .operator) == "NEGATE" else { throw SolveContractError.invalidExpression }
            self = .negate(try c.decode(Self.self, forKey: .operand))
        case "BINARY": self = .binary(try c.decode(Binary.self, forKey: .operator), try c.decode(Self.self, forKey: .left), try c.decode(Self.self, forKey: .right))
        case "FUNCTION":
            let args = try c.decode([Self].self, forKey: .args)
            guard args.count == 1 else { throw SolveContractError.invalidExpression }
            self = .function(try c.decode(Function.self, forKey: .function), args[0])
        default: throw SolveContractError.invalidExpression
        }
    }
    func validate(depth: Int = 1) throws -> Int {
        guard depth <= 32 else { throw SolveContractError.invalidExpression }
        let count: Int
        switch self {
        case .number, .variable: count = 1
        case .negate(let x), .function(_, let x): count = try 1 + x.validate(depth: depth + 1)
        case .binary(_, let a, let b): count = try 1 + a.validate(depth: depth + 1) + b.validate(depth: depth + 1)
        }
        guard count <= 120 else { throw SolveContractError.invalidExpression }
        return count
    }
}

enum MathStatement: Decodable, Equatable, Sendable {
    enum Relation: String, Decodable, Sendable {
        case equal = "EQUALS", less = "LESS_THAN", lessEqual = "LESS_THAN_OR_EQUAL"
        case greater = "GREATER_THAN", greaterEqual = "GREATER_THAN_OR_EQUAL"
        var symbol: String {
            switch self { case .equal: "="; case .less: "<"; case .lessEqual: "≤"; case .greater: ">"; case .greaterEqual: "≥" }
        }
    }
    case expression(MathExpression)
    case relation(Relation, MathExpression, MathExpression)
    private enum Keys: String, CodingKey { case kind, expression, relation, left, right }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Keys.self)
        switch try c.decode(String.self, forKey: .kind) {
        case "EXPRESSION": self = .expression(try c.decode(MathExpression.self, forKey: .expression))
        case "RELATION": self = .relation(try c.decode(Relation.self, forKey: .relation), try c.decode(MathExpression.self, forKey: .left), try c.decode(MathExpression.self, forKey: .right))
        default: throw SolveContractError.invalidExpression
        }
    }
    func validate() throws {
        switch self {
        case .expression(let x): _ = try x.validate()
        case .relation(_, let a, let b):
            guard try a.validate() + b.validate() <= 120 else { throw SolveContractError.invalidExpression }
        }
    }
}

/// Server verdict is separate from generation state. This model never calculates correctness.
struct VerificationDecisionSnapshot: Decodable, Equatable, Sendable {
    enum Status: String, Decodable, CaseIterable, Sendable {
        case verified = "VERIFIED", partiallyVerified = "PARTIALLY_VERIFIED", unverified = "UNVERIFIED"
        var titleKey: String { "verification.status.\(rawValue)" }
    }
    enum Scope: String, Decodable, CaseIterable, Sendable {
        case none = "NONE", subset = "CHECKED_METHOD_CLAIMS", finalAnswer = "FINAL_ANSWER"
        case openInterval = "OPEN_INTERVAL", antiderivative = "ANTIDERIVATIVE_REPRESENTATIVE", neighborhood = "PUNCTURED_NEIGHBORHOOD"
        var titleKey: String { "verification.scope.\(rawValue)" }
        var calculusMethod: Method? {
            switch self {
            case .openInterval: return .derivative
            case .antiderivative: return .integral
            case .neighborhood: return .limit
            default: return nil
            }
        }
    }
    enum Reason: String, Decodable, CaseIterable, Sendable {
        case invalidEvidence = "INVALID_EVIDENCE"
        case staleLineage = "STALE_LINEAGE"
        case reviewRequired = "REVIEW_REQUIRED"
        case compositeUnavailable = "COMPOSITE_UNAVAILABLE"
        case sourceScopeUnresolved = "SOURCE_SCOPE_UNRESOLVED"
        case deterministicContradiction = "DETERMINISTIC_CONTRADICTION"
        case evidenceConflict = "EVIDENCE_CONFLICT"
        case secondaryDisagreement = "SECONDARY_DISAGREEMENT"
        case secondaryAssumptions = "SECONDARY_ASSUMPTIONS"
        case secondaryInvalid = "SECONDARY_INVALID"
        case secondaryStale = "SECONDARY_STALE"
        case capabilityUnavailable = "CAPABILITY_UNAVAILABLE"
        case domainUnchecked = "DOMAIN_UNCHECKED"
        case completenessUnchecked = "COMPLETENESS_UNCHECKED"
        case executionFailure = "EXECUTION_FAILURE"
        case missingProof = "MISSING_PROOF"
        case releaseDisabled = "RELEASE_DISABLED"
        case subsetProven = "SUBSET_PROVEN"
        case requiredClaimProven = "REQUIRED_CLAIM_PROVEN"
        case secondaryAgreement = "SECONDARY_AGREEMENT"

        var titleKey: String { "verification.reason.\(rawValue)" }
        var isSecondaryConflict: Bool {
            [.secondaryDisagreement, .secondaryAssumptions, .secondaryInvalid, .secondaryStale].contains(self)
        }
    }
    enum Uncertainty: String, Decodable, CaseIterable, Sendable {
        case explanationStepsUnchecked = "EXPLANATION_STEPS_UNCHECKED"
        case domainUnresolved = "DOMAIN_UNRESOLVED"
        case completenessUnresolved = "COMPLETENESS_UNRESOLVED"
        case missingMathematicalProof = "MISSING_MATHEMATICAL_PROOF"
        case secondaryConflict = "SECONDARY_CONFLICT"
        case executionUnavailable = "EXECUTION_UNAVAILABLE"
        case releaseNotAccepted = "RELEASE_NOT_ACCEPTED"

        var titleKey: String {
            self == .explanationStepsUnchecked ? "verification.stepsUnchecked" : "verification.uncertainty.\(rawValue)"
        }
    }
    enum Recovery: String, Decodable, CaseIterable, Sendable {
        case none = "NONE", reviewSource = "REVIEW_SOURCE", reviewContradiction = "REVIEW_CONTRADICTION"
        case reconcile = "RECONCILE_SOLVERS", fresh = "FRESH_CALCULATION", evidence = "ADDITIONAL_EVIDENCE"
        case unsupported = "UNSUPPORTED_CAPABILITY", retry = "RETRY_VERIFICATION", limit = "RETRY_LIMIT"
        case budget = "BUDGET_BLOCKED", rate = "RATE_LIMITED", release = "RELEASE_BLOCKED", invalid = "INVALID_INPUT"
        var titleKey: String { "verification.recovery.\(rawValue)" }
    }
    enum Method: String, Decodable, CaseIterable, Sendable {
        case arithmetic = "ARITHMETIC_EVALUATION", symbolic = "SYMBOLIC_EQUIVALENCE", root = "EQUATION_SUBSTITUTION"
        case domain = "DOMAIN_CONSTRAINT_CHECK", completeness = "SOLUTION_SET_COMPLETENESS"
        case numeric = "NUMERIC_SAMPLING", derivative = "DERIVATIVE_BACKCHECK"
        case integral = "INTEGRAL_DIFFERENTIATION_BACKCHECK", limit = "FINITE_LIMIT"
        var titleKey: String { "verification.method.\(rawValue)" }
    }
    let schemaVersion: String
    let id: UUID
    let policyVersion: String
    let solutionId: UUID
    let canonicalProblemId: UUID
    let problemParseId: UUID
    let problemParseRevision: Int
    let runId: UUID
    let evidenceIds: [UUID]
    let status: Status
    let scope: Scope
    let reasons: [String]
    let checked: [Method]
    let unchecked: [Method]
    let contradictions: [Method]
    let uncertainty: [String]
    let recovery: Recovery
    let correlationId: String
    let createdAt: String

    var typedReasons: [Reason] { reasons.compactMap(Reason.init(rawValue:)) }
    var typedUncertainty: [Uncertainty] { uncertainty.compactMap(Uncertainty.init(rawValue:)) }
    var hasConflict: Bool {
        !contradictions.isEmpty
            || reasons.contains(Reason.deterministicContradiction.rawValue)
            || reasons.contains(Reason.evidenceConflict.rawValue)
            || uncertainty.contains(Uncertainty.secondaryConflict.rawValue)
            || typedReasons.contains(where: \.isSecondaryConflict)
    }
    var statusSymbolName: String {
        switch status {
        case .verified: "checkmark.shield"
        case .partiallyVerified: "exclamationmark.shield"
        case .unverified: hasConflict ? "exclamationmark.triangle" : "info.circle"
        }
    }
    var requiresProblemReview: Bool {
        [.reviewSource, .fresh, .reviewContradiction, .invalid].contains(recovery)
    }
    var evidencePresentation: VerificationEvidencePresentation {
        VerificationEvidencePresentation(decision: self)
    }

    func validated(for job: SolveJobSnapshot) throws -> Self {
        let parsedReasons = reasons.compactMap(Reason.init(rawValue:))
        let parsedUncertainty = uncertainty.compactMap(Uncertainty.init(rawValue:))
        guard schemaVersion == "verification-decision-v1", policyVersion == "verification-policy-v1",
              job.state == .generatedUnverified, canonicalProblemId == job.canonicalProblemId,
              problemParseId == job.problemParseId, problemParseRevision == job.problemParseRevision,
              (1...5).contains(evidenceIds.count), Set(evidenceIds).count == evidenceIds.count,
              checked.count <= 5, unchecked.count <= 5, contradictions.count <= 5,
              Set(checked).count == checked.count, Set(unchecked).count == unchecked.count,
              Set(contradictions).count == contradictions.count,
              Set(checked).isSubset(of: [.arithmetic, .symbolic, .root, .domain, .completeness, .derivative, .integral, .limit]),
              checked.count + unchecked.count <= 5, Set(contradictions).isSubset(of: unchecked),
              Set(checked).isDisjoint(with: unchecked), Set(checked).isDisjoint(with: contradictions),
              (1...Reason.allCases.count).contains(reasons.count), Set(reasons).count == reasons.count,
              parsedReasons.count == reasons.count,
              (1...Uncertainty.allCases.count).contains(uncertainty.count), Set(uncertainty).count == uncertainty.count,
              parsedUncertainty.count == uncertainty.count,
              parsedUncertainty.contains(.explanationStepsUnchecked),
              correlationId.range(of: #"^[A-Za-z0-9_-]{1,64}$"#, options: .regularExpression) != nil,
              SolveJobSnapshot.isValidISO8601(createdAt) else { throw SolveContractError.invalidResponse }

        let reasonSet = Set(parsedReasons)
        let uncertaintySet = Set(parsedUncertainty)
        let calculusMethods: Set<Method> = [.derivative, .integral, .limit]
        let obligations = checked + unchecked
        if !calculusMethods.isDisjoint(with: obligations) {
            guard obligations.count == 1 else { throw SolveContractError.invalidResponse }
        }
        guard contradictions.isEmpty == !reasonSet.contains(.deterministicContradiction) else {
            throw SolveContractError.invalidResponse
        }
        if !contradictions.isEmpty {
            guard status == .unverified, recovery == .reviewContradiction else { throw SolveContractError.invalidResponse }
        }
        if reasonSet.contains(where: \.isSecondaryConflict) || uncertaintySet.contains(.secondaryConflict) {
            guard reasonSet.contains(where: \.isSecondaryConflict),
                  uncertaintySet.contains(.secondaryConflict),
                  status == .unverified else { throw SolveContractError.invalidResponse }
        }
        if reasonSet.contains(.secondaryAgreement) {
            guard !reasonSet.contains(where: \.isSecondaryConflict),
                  !uncertaintySet.contains(.secondaryConflict) else { throw SolveContractError.invalidResponse }
        }

        switch status {
        case .verified:
            let validScope = scope.calculusMethod.map { checked == [$0] }
                ?? (scope == .finalAnswer && calculusMethods.isDisjoint(with: checked))
            guard validScope, checked.contains(where: { $0 != .domain }), unchecked.isEmpty, !hasConflict,
                  recovery == .none, parsedUncertainty == [.explanationStepsUnchecked],
                  reasonSet.contains(.requiredClaimProven),
                  reasonSet.isSubset(of: [.requiredClaimProven, .secondaryAgreement]) else {
                throw SolveContractError.invalidResponse
            }
        case .partiallyVerified:
            let forbiddenPartialReasons: Set<Reason> = [
                .invalidEvidence, .staleLineage, .reviewRequired, .compositeUnavailable,
                .deterministicContradiction, .evidenceConflict, .secondaryDisagreement,
                .secondaryAssumptions, .secondaryInvalid, .secondaryStale,
                .missingProof, .releaseDisabled, .requiredClaimProven
            ]
            guard scope == .subset, checked.contains(where: { $0 != .domain }), !unchecked.isEmpty, !hasConflict,
                  recovery != .none, recovery != .reviewContradiction, recovery != .reconcile, recovery != .release,
                  reasonSet.contains(.subsetProven),
                  reasonSet.isDisjoint(with: forbiddenPartialReasons),
                  !uncertaintySet.contains(.missingMathematicalProof),
                  !uncertaintySet.contains(.secondaryConflict),
                  !uncertaintySet.contains(.releaseNotAccepted) else {
                throw SolveContractError.invalidResponse
            }
        case .unverified:
            guard scope == .none, recovery != .none,
                  !reasonSet.contains(.requiredClaimProven),
                  !reasonSet.contains(.subsetProven) else {
                throw SolveContractError.invalidResponse
            }
            if reasonSet.contains(.releaseDisabled) {
                guard uncertaintySet.contains(.releaseNotAccepted),
                      recovery == .release,
                      !reasonSet.contains(.missingProof) else { throw SolveContractError.invalidResponse }
            } else {
                guard reasonSet.contains(.missingProof),
                      uncertaintySet.contains(.missingMathematicalProof),
                      !uncertaintySet.contains(.releaseNotAccepted) else { throw SolveContractError.invalidResponse }
            }
        }
        return self
    }
}

/// Deterministic presentation-only projection of a validated server verification decision.
/// Never synthesizes a mathematical verdict or promotes an unverified candidate.
struct VerificationEvidencePresentation: Equatable, Sendable {
    enum Severity: String, Equatable, Sendable {
        case verified
        case caution
        case warning
        case neutral
    }

    let statusTitleKey: String
    let statusIdentifier: String
    let statusSymbolName: String
    let severity: Severity
    let scopeKey: String
    let scopeIdentifier: String?
    let summaryKey: String
    let hasConflict: Bool
    let conflictWarningKey: String?
    let contradictedMethodKeys: [String]
    let checkedMethodKeys: [String]
    let uncheckedMethodKeys: [String]
    let reasonKeys: [String]
    let uncertaintyKeys: [String]
    let showsSecondaryAgreementNotice: Bool
    let showsNumericSamplingNotice: Bool
    let recoveryKey: String?
    let requiresProblemReview: Bool
    let hasExpandableEvidence: Bool

    init(decision: VerificationDecisionSnapshot) {
        let isScopedCalculus = decision.scope.calculusMethod != nil
        statusTitleKey = isScopedCalculus ? "verification.scopedStatus" : decision.status.titleKey
        statusIdentifier = "verification.status.\(decision.status.rawValue)"
        statusSymbolName = decision.statusSymbolName
        if decision.hasConflict {
            severity = .warning
        } else {
            switch decision.status {
            case .verified: severity = .verified
            case .partiallyVerified: severity = .caution
            case .unverified: severity = decision.requiresProblemReview ? .warning : .neutral
            }
        }
        scopeKey = decision.scope.titleKey
        scopeIdentifier = "verification.scope.\(decision.scope.rawValue)"
        if decision.hasConflict {
            summaryKey = "verification.summary.CONFLICT"
        } else if isScopedCalculus && decision.status == .verified {
            summaryKey = "verification.summary.SCOPED_VERIFIED"
        } else {
            summaryKey = "verification.summary.\(decision.status.rawValue)"
        }
        hasConflict = decision.hasConflict
        conflictWarningKey = decision.hasConflict ? "verification.conflict" : nil
        contradictedMethodKeys = decision.contradictions.map(\.titleKey)
        checkedMethodKeys = decision.checked.map(\.titleKey)
        uncheckedMethodKeys = decision.unchecked.map(\.titleKey)
        reasonKeys = decision.typedReasons.map(\.titleKey)
        let orderedUncertainty: [VerificationDecisionSnapshot.Uncertainty] = [.explanationStepsUnchecked]
            + decision.typedUncertainty.filter { $0 != .explanationStepsUnchecked }
        uncertaintyKeys = orderedUncertainty.map(\.titleKey)
        showsSecondaryAgreementNotice = decision.typedReasons.contains(.secondaryAgreement)
        showsNumericSamplingNotice = decision.unchecked.contains(.numeric) || decision.contradictions.contains(.numeric)
        recoveryKey = decision.recovery.titleKey
        requiresProblemReview = decision.requiresProblemReview
        hasExpandableEvidence = true
    }

    static func absent(unavailable: Bool, invalidContract: Bool = false) -> Self {
        let scopeKey = invalidContract
            ? "verification.invalid"
            : (unavailable ? "verification.unavailable" : "verification.absent")
        let summaryKey = invalidContract
            ? "verification.summary.INVALID"
            : (unavailable ? "verification.summary.UNAVAILABLE" : "verification.summary.ABSENT")
        return Self(
            statusTitleKey: "solve.unverified",
            statusIdentifier: "solve.unverified",
            statusSymbolName: invalidContract ? "exclamationmark.triangle" : "info.circle",
            severity: invalidContract ? .warning : .neutral,
            scopeKey: scopeKey,
            scopeIdentifier: nil,
            summaryKey: summaryKey,
            hasConflict: false,
            conflictWarningKey: nil,
            contradictedMethodKeys: [],
            checkedMethodKeys: [],
            uncheckedMethodKeys: [],
            reasonKeys: [],
            uncertaintyKeys: ["verification.stepsUnchecked"],
            showsSecondaryAgreementNotice: false,
            showsNumericSamplingNotice: false,
            recoveryKey: nil,
            requiresProblemReview: false,
            hasExpandableEvidence: false
        )
    }

    private init(
        statusTitleKey: String,
        statusIdentifier: String,
        statusSymbolName: String,
        severity: Severity,
        scopeKey: String,
        scopeIdentifier: String?,
        summaryKey: String,
        hasConflict: Bool,
        conflictWarningKey: String?,
        contradictedMethodKeys: [String],
        checkedMethodKeys: [String],
        uncheckedMethodKeys: [String],
        reasonKeys: [String],
        uncertaintyKeys: [String],
        showsSecondaryAgreementNotice: Bool,
        showsNumericSamplingNotice: Bool,
        recoveryKey: String?,
        requiresProblemReview: Bool,
        hasExpandableEvidence: Bool
    ) {
        self.statusTitleKey = statusTitleKey
        self.statusIdentifier = statusIdentifier
        self.statusSymbolName = statusSymbolName
        self.severity = severity
        self.scopeKey = scopeKey
        self.scopeIdentifier = scopeIdentifier
        self.summaryKey = summaryKey
        self.hasConflict = hasConflict
        self.conflictWarningKey = conflictWarningKey
        self.contradictedMethodKeys = contradictedMethodKeys
        self.checkedMethodKeys = checkedMethodKeys
        self.uncheckedMethodKeys = uncheckedMethodKeys
        self.reasonKeys = reasonKeys
        self.uncertaintyKeys = uncertaintyKeys
        self.showsSecondaryAgreementNotice = showsSecondaryAgreementNotice
        self.showsNumericSamplingNotice = showsNumericSamplingNotice
        self.recoveryKey = recoveryKey
        self.requiresProblemReview = requiresProblemReview
        self.hasExpandableEvidence = hasExpandableEvidence
    }
}
