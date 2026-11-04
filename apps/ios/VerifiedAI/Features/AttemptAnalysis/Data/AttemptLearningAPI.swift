import Foundation

struct CounterfactualTwin: Codable, Identifiable, Sendable {
    let schemaVersion: String
    let id: UUID
    let attemptId: UUID
    let problem: AttemptJSON
    let options: [AttemptJSON]
    let domainLabel: String
    let difficulty: String
    let status: String
    let selectedOption: Int?
    let correct: Bool?
    let correctOption: Int?
    let explanationKey: String?
    let assistance: String?
    let createdAt: Date
    let completedAt: Date?
    let trainingEligible: Bool
    func validated(attempt: UUID? = nil) throws -> Self {
        guard schemaVersion == "counterfactual-twin-v1", !trainingEligible, options.count == 4,
              attempt == nil || attemptId == attempt,
              ["OPEN", "COMPLETED"].contains(status), (status == "COMPLETED") == (completedAt != nil) else { throw NetworkError.invalidResponse }
        if status == "OPEN" {
            guard correct == nil, correctOption == nil, selectedOption == nil, explanationKey == nil, assistance == nil else { throw NetworkError.invalidResponse }
        } else {
            guard let choice = selectedOption, let answer = correctOption, (0...3).contains(choice), (0...3).contains(answer),
                  correct == (choice == answer), ["INDEPENDENT", "HINT_ASSISTED"].contains(assistance ?? ""), explanationKey?.hasPrefix("attempt.mistake.") == true else { throw NetworkError.invalidResponse }
        }
        return self
    }
}
struct AttemptAlternativeMethods: Decodable, Sendable {
    struct Comparison: Decodable, Sendable {
        let policyVersion: String; let presentationVersion: String; let applicability: String
        let reasonCode: String; let prerequisite: String; let advantage: String; let limitation: String
        let domainCautions: [String]; let structuralStepCount: Int; let compactStepIndices: [Int]
        let manipulationLevel: String; let recommendation: String
    }
    struct Method: Decodable, Identifiable, Sendable {
        let methodId: String; let steps: [AttemptJSON]; let evidenceVersion: String
        let stepReasons: [String]?
        let proofScope: String?; let transitionStatus: String?; let outcomeStatus: String?
        let comparison: Comparison?
        var id: String { methodId }
        func indices(compact: Bool) -> [Int] {
            compact ? (comparison?.compactStepIndices ?? Array(steps.indices)) : Array(steps.indices)
        }
    }
    let schemaVersion: String; let verifierVersion: String; let action: String; let status: String
    let executionId: UUID?; let correlationId: String?; let reasonCode: String?
    let source: AttemptJSON?; let naturalDomain: AttemptJSON?
    let methods: [Method]
    func validated() throws -> Self {
        let v2 = schemaVersion == "method-comparison-result-v2"
        guard v2 || schemaVersion == "attempt-learning-result-v1",
              verifierVersion == (v2 ? "bounded-methods-v2" : "bounded-learning-v1"), action == "METHODS",
              ["VERIFIED", "UNSUPPORTED", "UNAVAILABLE", "NO_ALTERNATIVE"].contains(status), methods.count <= 3,
              (status == "VERIFIED") == !methods.isEmpty, Set(methods.map(\.id)).count == methods.count else { throw NetworkError.invalidResponse }
        if v2 {
            guard let executionId, correlationId?.lowercased() == executionId.uuidString.lowercased(), let source,
                  source["original"]["kind"].text != "", source["restrictions"].items.count <= 48 else { throw NetworkError.invalidResponse }
            let reasons = ["VERIFIED": ["EXACT_METHODS"], "UNSUPPORTED": ["UNSUPPORTED_SOURCE"], "NO_ALTERNATIVE": ["NO_DISTINCT_DERIVATION"],
                           "UNAVAILABLE": ["TIMEOUT", "RESOURCE_FAILURE", "WORKER_FAILURE", "ADMISSION_LIMIT", "INVALID_RESPONSE", "DISABLED"]]
            guard reasons[status]?.contains(reasonCode ?? "") == true else { throw NetworkError.invalidResponse }
            if status == "VERIFIED" {
                guard let naturalDomain, ["REALS", "FINITE_SET", "INTERVAL", "UNION", "INTERSECTION"].contains(naturalDomain["kind"].text),
                      AttemptMathPresentation.latex(naturalDomain) != nil, source["restrictions"].items.allSatisfy({
                          AttemptMathPresentation.latex(.object(["kind": .string("RELATION"), "relation": $0["relation"], "left": $0["left"], "right": $0["right"]])) != nil
                      }), ["SOURCE_EXPLICIT", "REVIEWED_REAL_TASK"].contains(source["domainBasis"].text),
                      AttemptMathPresentation.latex(source["original"]) != nil else { throw NetworkError.invalidResponse }
            }
        }
        for method in methods {
            guard ["FACTORING", "COMPLETING_SQUARE", "QUADRATIC_FORMULA", "SYMBOLIC_SIMPLIFICATION", "POLYNOMIAL_EXPANSION", "DIRECT_ALGEBRA", "RATIONAL_CLEARING", "ABSOLUTE_CASES"].contains(method.methodId),
                  method.evidenceVersion == (v2 ? "verified-method-v2" : "verified-method-v1"), (1...4).contains(method.steps.count),
                  method.steps.allSatisfy({ AttemptMathPresentation.latex($0) != nil }) else { throw NetworkError.invalidResponse }
            if v2 {
                let policy: [String: [String]] = [
                    "FACTORING": ["ZERO_PRODUCT", "FACTORING", "VISIBLE_FACTORS", "FACTOR_DISCOVERY", "LOW"],
                    "COMPLETING_SQUARE": ["SQUARE_IDENTITY", "SQUARE_IDENTITIES", "VISIBLE_VERTEX", "FRACTION_ARITHMETIC", "MODERATE"],
                    "QUADRATIC_FORMULA": ["DISCRIMINANT_FORMULA", "QUADRATIC_FORMULA", "GENERAL_QUADRATIC", "FORMULA_SUBSTITUTION", "MODERATE"],
                    "SYMBOLIC_SIMPLIFICATION": ["EXACT_SIMPLIFICATION", "POLYNOMIAL_ALGEBRA", "NORMALIZED_EXPRESSION", "BOUNDED_POLYNOMIAL", "LOW"],
                    "POLYNOMIAL_EXPANSION": ["EXPAND_COLLECT", "POLYNOMIAL_ALGEBRA", "VISIBLE_COEFFICIENTS", "EXPANSION_GROWTH", "LOW"],
                    "DIRECT_ALGEBRA": ["ISOLATE_VARIABLE", "LINEAR_ALGEBRA", "DIRECT_OUTCOME", "LINEAR_ONLY", "LOW"],
                    "RATIONAL_CLEARING": ["CLEAR_DENOMINATORS", "DOMAIN_AND_FACTORS", "VISIBLE_NUMERATOR", "EXCLUDED_ROOTS", "MODERATE"],
                    "ABSOLUTE_CASES": ["SPLIT_ABSOLUTE_CASES", "PIECEWISE_SIGNS", "EXPLICIT_BRANCHES", "AFFINE_ABSOLUTE_ONLY", "MODERATE"]]
                let recommendations = [
                    "FACTORING": ["REPEATED_ROOT_STRUCTURE", "FACTORABLE_OVER_RATIONALS"],
                    "COMPLETING_SQUARE": ["NEGATIVE_DISCRIMINANT", "DIRECT_SQUARE", "VERTEX_FORM"],
                    "QUADRATIC_FORMULA": ["EXACT_RADICAL_FORMULA", "EQUALLY_VALID"],
                    "SYMBOLIC_SIMPLIFICATION": ["EXPOSE_FACTORS"], "POLYNOMIAL_EXPANSION": ["COLLECT_TERMS"],
                    "DIRECT_ALGEBRA": ["ISOLATE_LINEAR", "RESOLVE_CONSTANT"],
                    "RATIONAL_CLEARING": ["PRESERVE_DOMAIN_HOLES"], "ABSOLUTE_CASES": ["SPLIT_ABSOLUTE_CASES"]]
                let applications = [
                    "FACTORING": ["RATIONAL_QUADRATIC"], "COMPLETING_SQUARE": ["RATIONAL_QUADRATIC"], "QUADRATIC_FORMULA": ["RATIONAL_QUADRATIC"],
                    "SYMBOLIC_SIMPLIFICATION": ["RATIONAL_POLYNOMIAL"], "POLYNOMIAL_EXPANSION": ["RATIONAL_POLYNOMIAL"],
                    "DIRECT_ALGEBRA": ["RATIONAL_LINEAR", "CONSTANT_EQUATION"], "RATIONAL_CLEARING": ["RATIONAL_EQUATION"], "ABSOLUTE_CASES": ["ABSOLUTE_AFFINE"]]
                let expression = ["SYMBOLIC_SIMPLIFICATION", "POLYNOMIAL_EXPANSION"].contains(method.methodId)
                let warnings = ["REAL_DOMAIN"] + ((source?["restrictions"].items.isEmpty == false) ? ["SOURCE_RESTRICTIONS"] : [])
                    + (naturalDomain?["kind"].text == "REALS" ? [] : ["NATURAL_DOMAIN_EXCLUSIONS"])
                guard let c = method.comparison, c.policyVersion == "method-comparison-policy-v3", c.presentationVersion == "method-presentation-v2",
                      policy[method.methodId] == [c.reasonCode, c.prerequisite, c.advantage, c.limitation, c.manipulationLevel],
                      recommendations[method.methodId]?.contains(c.recommendation) == true,
                      applications[method.methodId]?.contains(c.applicability) == true,
                      method.proofScope == "SOURCE_DOMAIN_COMPLETE_OUTCOME", method.transitionStatus == "EXACT_SYMBOLIC",
                      method.outcomeStatus == (expression ? "EXPRESSION_IDENTITY" : "COMPLETE_SOLUTION_SET"),
                      method.stepReasons?.last == (expression ? c.reasonCode : "COMPLETE_REAL_SET"),
                      method.stepReasons?.allSatisfy({ ["NORMALIZE_ZERO", "ZERO_PRODUCT", "SQUARE_IDENTITY", "DISCRIMINANT_FORMULA", "COMPLETE_REAL_SET", "EXACT_SIMPLIFICATION", "EXPAND_COLLECT", "ISOLATE_VARIABLE", "CLEAR_DENOMINATORS", "SPLIT_ABSOLUTE_CASES"].contains($0) }) == true,
                      c.structuralStepCount == method.steps.count, method.stepReasons?.count == method.steps.count,
                      c.compactStepIndices == Array(Set(c.compactStepIndices)).sorted(), !c.compactStepIndices.isEmpty,
                      c.compactStepIndices.last == method.steps.count - 1,
                      c.compactStepIndices.allSatisfy({ method.steps.indices.contains($0) }), c.domainCautions == warnings else { throw NetworkError.invalidResponse }
            } else if !["FACTORING", "COMPLETING_SQUARE", "QUADRATIC_FORMULA", "SYMBOLIC_SIMPLIFICATION"].contains(method.methodId) {
                throw NetworkError.invalidResponse
            }
        }
        return self
    }
}
struct MethodComparison: Decodable, Sendable {
    let schemaVersion: String; let problemId: UUID; let attemptId: UUID?
    let source: AttemptSourceIdentity; let freshness: String; let trainingEligible: Bool
    let evidence: AttemptAlternativeMethods
    func validated(problem: UUID, attempt: UUID?, expected: AttemptSourceIdentity) throws -> Self {
        guard schemaVersion == "method-comparison-public-v2", problemId == problem, attemptId == attempt,
              source == expected, freshness == "CURRENT_AT_PUBLICATION", !trainingEligible,
              evidence.schemaVersion == "method-comparison-result-v2" else { throw NetworkError.invalidResponse }
        _ = try evidence.validated()
        return self
    }
}
protocol AttemptLearningServicing: Sendable {
    func twins(problem: UUID) async throws -> [CounterfactualTwin]
    func create(problem: UUID, attempt: UUID) async throws -> CounterfactualTwin
    func answer(problem: UUID, attempt: UUID, drill: UUID, choice: Int) async throws -> CounterfactualTwin
    func passport(problem: UUID) async throws -> MistakePassport
    func context(problem: UUID) async throws -> AttemptContext
    func comparison(problem: UUID, attempt: UUID?, source: AttemptSourceIdentity) async throws -> MethodComparison
}
struct MistakePassport: Decodable, Sendable {
    struct Event: Decodable, Identifiable, Sendable {
        let id: UUID; let attempt_id: UUID; let drill_id: UUID?; let mistake_code: String
        let correct: Bool; let assistance: String; let assessed_at: Date
    }
    struct Mastery: Decodable, Identifiable, Sendable {
        let skill_id: String; let mastery_version: String; let score: Double; let confidence: Double
        let evidence_count: Int; let half_life_days: Double; let last_assessed_at: Date
        var id: String { skill_id }
    }
    let schemaVersion: String; let trainingEligible: Bool; let events: [Event]; let mastery: [Mastery]
}
final class AttemptLearningAPI: AttemptLearningServicing, @unchecked Sendable {
    private let client: APIClient
    init(client: APIClient) { self.client = client }
    private func path(_ problem: UUID) -> String { "/api/v1/problem-sessions/\(problem)/attempts" }
    func twins(problem: UUID) async throws -> [CounterfactualTwin] {
        let r: HTTPResponse<[CounterfactualTwin]> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem) + "/drills")))
        guard r.body.count <= 30 else { throw NetworkError.invalidResponse }
        return try r.body.map { try $0.validated() }
    }
    func create(problem: UUID, attempt: UUID) async throws -> CounterfactualTwin {
        let r: HTTPResponse<CounterfactualTwin> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem) + "/\(attempt)/counterfactual-twin", method: .post),
            body: Data("{}".utf8), idempotencyKey: "twin_\(attempt)", timeout: 15))
        return try r.body.validated(attempt: attempt)
    }
    func answer(problem: UUID, attempt: UUID, drill: UUID, choice: Int) async throws -> CounterfactualTwin {
        let r: HTTPResponse<CounterfactualTwin> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem) + "/\(attempt)/counterfactual-twin/answer", method: .post),
            body: try JSONEncoder().encode(["selectedOption": choice]), idempotencyKey: "answer_\(drill)", timeout: 15))
        let value = try r.body.validated(attempt: attempt)
        guard value.id == drill, value.selectedOption == choice, value.status == "COMPLETED" else { throw NetworkError.invalidResponse }
        return value
    }
    func methods(problem: UUID, attempt: UUID) async throws -> AttemptAlternativeMethods {
        let r: HTTPResponse<AttemptAlternativeMethods> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem) + "/\(attempt)/alternative-methods", method: .post),
            body: Data("{}".utf8), idempotencyKey: "methods_\(attempt)", timeout: 15))
        guard r.body.schemaVersion == "attempt-learning-result-v1", r.body.verifierVersion == "bounded-learning-v1",
              r.body.status == "VERIFIED", r.body.action == "METHODS", (1...3).contains(r.body.methods.count),
              r.body.methods.allSatisfy({ $0.evidenceVersion == "verified-method-v1" && (1...4).contains($0.steps.count) }) else { throw NetworkError.invalidResponse }
        return try r.body.validated()
    }
    func context(problem: UUID) async throws -> AttemptContext {
        try await AttemptAnalysisAPI(client: client).context(problem: problem)
    }
    func comparison(problem: UUID, attempt: UUID?, source: AttemptSourceIdentity) async throws -> MethodComparison {
        struct SourceRequest: Encodable { let schemaVersion = "method-comparison-v2"; let source: AttemptSourceIdentity }
        let endpoint = attempt.map { path(problem) + "/\($0)/alternative-methods" }
            ?? "/api/v1/problem-sessions/\(problem)/alternative-methods"
        let body: Data
        if attempt == nil { body = try JSONEncoder().encode(SourceRequest(source: source)) }
        else { body = try JSONEncoder().encode(["schemaVersion": "method-comparison-v2"]) }
        let r: HTTPResponse<MethodComparison> = try await client.send(HTTPRequest(endpoint: Endpoint(path: endpoint, method: .post),
            body: body, idempotencyKey: "methods_v2_\(source.canonicalId)_\(source.canonicalRevision)", timeout: 15))
        return try r.body.validated(problem: problem, attempt: attempt, expected: source)
    }
    func passport(problem: UUID) async throws -> MistakePassport {
        let r: HTTPResponse<MistakePassport> = try await client.send(HTTPRequest(endpoint: Endpoint(path: path(problem) + "/mistake-passport")))
        guard r.body.schemaVersion == "mistake-passport-v1", !r.body.trainingEligible, r.body.events.count <= 60,
              r.body.mastery.count <= 17, r.body.mastery.allSatisfy({ $0.mastery_version == "bkt-lite-v1" && (0.05...0.99).contains($0.score) && (0...1).contains($0.confidence) && (1...90).contains($0.half_life_days) && (1...60).contains($0.evidence_count) }) else { throw NetworkError.invalidResponse }
        return r.body
    }
}

#if DEBUG
/// Synthetic UI/unit fixture, available only in explicit debug fixture mode.
actor FixtureMethodComparisonAPI: AttemptLearningServicing {
    private var failures: Int
    private let status: String
    private let single: Bool
    private let delay: UInt64
    init(failFirst: Bool = false, status: String = "VERIFIED", single: Bool = false, delay: UInt64 = 0) {
        failures = failFirst ? 1 : 0; self.status = status; self.single = single; self.delay = delay
    }
    static func fixtureData() throws -> Data {
        guard let url = Bundle.main.url(forResource: "method-comparison-public-v2", withExtension: "json") else { throw NetworkError.invalidResponse }
        return try Data(contentsOf: url)
    }
    static func fixture() throws -> MethodComparison { try JSONDecoder().decode(MethodComparison.self, from: fixtureData()) }
    func context(problem: UUID) async throws -> AttemptContext {
        let f = try Self.fixture()
        return AttemptContext(schemaVersion: "attempt-context-v2", source: f.source, originalStatement: nil, enabled: true,
                              handwriting: "NATIVE_REVIEW", twinDrill: "VERIFIED_TEMPLATES", methodComparison: "VERIFIED_METHODS")
    }
    func comparison(problem: UUID, attempt: UUID?, source: AttemptSourceIdentity) async throws -> MethodComparison {
        if delay > 0 { try await Task.sleep(nanoseconds: delay) }
        if failures > 0 { failures -= 1; throw NetworkError.transport("synthetic failure") }
        var root = try JSONSerialization.jsonObject(with: Self.fixtureData()) as! [String: Any]
        root["problemId"] = problem.uuidString
        root["attemptId"] = attempt.map { $0.uuidString as Any } ?? NSNull()
        var evidence = root["evidence"] as! [String: Any]
        if single { evidence["methods"] = Array((evidence["methods"] as! [[String: Any]]).prefix(1)) }
        if status != "VERIFIED" {
            evidence["methods"] = [] as [String]; evidence["status"] = status
            evidence["reasonCode"] = status == "NO_ALTERNATIVE" ? "NO_DISTINCT_DERIVATION" : "UNSUPPORTED_SOURCE"
        }
        root["evidence"] = evidence
        let result = try JSONDecoder().decode(MethodComparison.self, from: JSONSerialization.data(withJSONObject: root))
        return try result.validated(problem: problem, attempt: attempt, expected: source)
    }
    func twins(problem: UUID) async throws -> [CounterfactualTwin] { [] }
    func create(problem: UUID, attempt: UUID) async throws -> CounterfactualTwin { throw NetworkError.invalidResponse }
    func answer(problem: UUID, attempt: UUID, drill: UUID, choice: Int) async throws -> CounterfactualTwin { throw NetworkError.invalidResponse }
    func passport(problem: UUID) async throws -> MistakePassport { throw NetworkError.invalidResponse }
}
#endif
