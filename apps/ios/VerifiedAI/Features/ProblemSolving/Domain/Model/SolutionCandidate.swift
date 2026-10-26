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
}

enum SolveContractError: Error { case invalidResponse, invalidExpression, recoveryStorage }

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

    var mayRetry: Bool { state == .retryableFailure && attempt < 3 }
    var visibleCandidate: SolutionCandidate? { state.showsCandidate ? candidate : nil }

    func validated() throws -> Self {
        let date = ISO8601DateFormatter()
        date.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let fractional = date.date(from: createdAt)
        date.formatOptions = [.withInternetDateTime]
        guard problemParseRevision > 0, (1...3).contains(attempt),
              fractional != nil || date.date(from: createdAt) != nil,
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

    var hasSourceReferences: Bool { !sourceAssumptionIds.isEmpty || !sourceConstraintIds.isEmpty || !derivedRestrictionIds.isEmpty }
    func validate() throws {
        guard schemaVersion == "solution-candidate-v1", candidateVersion == 1, problemParseRevision > 0,
              (1...24).contains(steps.count), steps.map(\.order) == Array(1...steps.count),
              Set(steps.map(\.id)).count == steps.count,
              bounded(sourceAssumptionIds, count: 16, length: 96), bounded(sourceConstraintIds, count: 16, length: 96),
              bounded(derivedRestrictionIds, count: 32, length: 96),
              bounded(ambiguities, count: 8, length: 500), bounded(unresolvedIssues, count: 8, length: 500) else {
            throw SolveContractError.invalidResponse
        }
        var previous = Set<String>()
        for step in steps {
            guard !step.id.isEmpty, step.id.count <= 32, !step.explanation.isEmpty, step.explanation.count <= 1000,
                  bounded(step.dependsOn, count: 24, length: 32), Set(step.dependsOn).isSubset(of: previous) else {
                throw SolveContractError.invalidResponse
            }
            try step.claim.validate()
            previous.insert(step.id)
        }
        try finalAnswer.validate()
    }
    private func bounded(_ values: [String], count: Int, length: Int) -> Bool {
        values.count <= count && values.allSatisfy { !$0.isEmpty && $0.count <= length }
    }
}

struct SolutionStep: Decodable, Equatable, Identifiable, Sendable {
    enum Category: String, Decodable, Sendable { case restate = "RESTATE", transform = "TRANSFORM", conclude = "CONCLUDE" }
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
