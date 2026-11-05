import Foundation

struct VisualizationViewport: Codable, Equatable, Sendable {
    var xMin = -10.0; var xMax = 10.0; var yMin = -10.0; var yMax = 10.0
    var valid: Bool {
        [xMin, xMax, yMin, yMax].allSatisfy { $0.isFinite && abs($0) <= 1000 }
            && (0.01...2000).contains(xMax - xMin) && (0.01...2000).contains(yMax - yMin)
    }
    func scaled(_ factor: Double) -> Self {
        let cx = (xMin + xMax) / 2, cy = (yMin + yMax) / 2
        let dx = (xMax - xMin) * factor / 2, dy = (yMax - yMin) * factor / 2
        let next = Self(xMin: cx-dx, xMax: cx+dx, yMin: cy-dy, yMax: cy+dy)
        return next.valid ? next : self
    }
    func shifted(_ direction: Double) -> Self {
        let shift = (xMax-xMin) * direction / 4
        let next = Self(xMin: xMin+shift, xMax: xMax+shift, yMin: yMin, yMax: yMax)
        return next.valid ? next : self
    }
}
enum VisualizationKind: String, Codable, CaseIterable, Sendable {
    case function = "FUNCTION", equation = "EQUATION", derivative = "DERIVATIVE", numberLine = "NUMBER_LINE", story = "STORY_BRIDGE"
    var key: String { "visualization.type." + rawValue }
}
struct MathVisualization: Decodable, Sendable {
    enum Status: String, Decodable, Sendable { case supported = "SUPPORTED", unsupported = "UNSUPPORTED", unavailable = "UNAVAILABLE" }
    struct Sample: Decodable, Sendable { let x: Double; let y: Double }
    struct Segment: Decodable, Identifiable, Sendable {
        let id: String; let curve: String; let points: [Sample]
    }
    struct Band: Decodable, Sendable {
        let lower: String; let upper: String; let lowerValue: Double?; let upperValue: Double?
        let leftOpen: Bool; let rightOpen: Bool
        var exact: String { "\(leftOpen ? "(" : "[")\(lower), \(upper)\(rightOpen ? ")" : "]")" }
    }
    struct Feature: Decodable, Sendable { let code: String; let x: String; let y: String }
    struct Result: Decodable, Sendable {
        let schemaVersion: String; let policyVersion: String; let executionId: UUID; let correlationId: String
        let identity: AttemptSourceIdentity; let type: VisualizationKind; let viewport: VisualizationViewport; let resolution: Int
        let status: Status; let reasonCode: String; let expression: AttemptJSON?; let derivativeExpression: AttemptJSON?; let naturalDomain: AttemptJSON?; let sourceDomain: AttemptJSON?; let solutionSet: AttemptJSON?
        let numberLine: [Band]; let features: [Feature]; let segments: [Segment]; let concepts: [String]
        let axes: String; let sampleSemantics: String; let trainingEligible: Bool
    }
    let schemaVersion: String; let problemId: UUID; let source: AttemptSourceIdentity; let freshness: String
    let trainingEligible: Bool; let result: Result
    init(from decoder: Decoder) throws {
        let raw = try AttemptJSON(from: decoder)
        func keys(_ value: AttemptJSON, _ names: String) throws {
            guard case .object(let fields) = value, Set(fields.keys) == Set(names.split(separator: " ").map(String.init)) else { throw NetworkError.invalidResponse }
        }
        try keys(raw, "schemaVersion problemId source freshness trainingEligible result")
        try keys(raw["source"], "canonicalId canonicalRevision parseId parseRevision")
        let r = raw["result"]
        try keys(r, "schemaVersion policyVersion executionId correlationId identity type viewport resolution status reasonCode expression derivativeExpression naturalDomain sourceDomain solutionSet numberLine features segments concepts axes sampleSemantics trainingEligible")
        try keys(r["identity"], "canonicalId canonicalRevision parseId parseRevision")
        try keys(r["viewport"], "xMin xMax yMin yMax")
        for segment in r["segments"].items {
            try keys(segment, "id curve points")
            for point in segment["points"].items { try keys(point, "x y") }
        }
        for band in r["numberLine"].items { try keys(band, "lower upper lowerValue upperValue leftOpen rightOpen") }
        for feature in r["features"].items { try keys(feature, "code x y") }
        func node(_ value: AttemptJSON, depth: Int = 0) throws {
            if case .null = value { return }
            guard depth < 24, case .object(let fields) = value else { throw NetworkError.invalidResponse }
            let shapes: [String: Set<String>] = [
                "NUMBER": ["kind", "value", "numericType"], "CONSTANT": ["kind", "value"], "VARIABLE": ["kind", "symbol"],
                "UNARY": ["kind", "operator", "operand"], "BINARY": ["kind", "operator", "left", "right"],
                "FUNCTION": ["kind", "function", "args"], "REALS": ["kind"], "EMPTY_SET": ["kind"],
                "FINITE_SET": ["kind", "args"], "UNION": ["kind", "args"], "INTERSECTION": ["kind", "args"],
                "INTERVAL": ["kind", "left", "right", "leftOpen", "rightOpen"]]
            guard let shape = shapes[value["kind"].text] else { throw NetworkError.invalidResponse }
            let present = Set(fields.compactMap { key, item -> String? in if case .null = item { return nil }; return key })
            guard present.subtracting(["tokenRange"]) == shape,
                  Set(fields.keys).isSubset(of: Set("kind value numericType symbol operator function operand left right args leftOpen rightOpen tokenRange branches".split(separator: " ").map(String.init))) else { throw NetworkError.invalidResponse }
            if case .null = value["tokenRange"] { } else { try keys(value["tokenRange"], "start end") }
            if value["kind"].text == "NUMBER", !["INTEGER", "DECIMAL", "RATIONAL"].contains(value["numericType"].text) { throw NetworkError.invalidResponse }
            if value["kind"].text == "FUNCTION", value["function"].text != "SQRT" { throw NetworkError.invalidResponse }
            if value["kind"].text == "BINARY", !["ADD", "SUBTRACT", "MULTIPLY", "DIVIDE", "POWER"].contains(value["operator"].text) { throw NetworkError.invalidResponse }
            for key in ["operand", "left", "right"] { try node(value[key], depth: depth+1) }
            for item in value["args"].items { try node(item, depth: depth+1) }
        }
        for key in ["expression", "derivativeExpression", "naturalDomain", "sourceDomain", "solutionSet"] { try node(r[key]) }
        let c = try decoder.container(keyedBy: CodingKeys.self)
        schemaVersion = try c.decode(String.self, forKey: .schemaVersion)
        problemId = try c.decode(UUID.self, forKey: .problemId)
        source = try c.decode(AttemptSourceIdentity.self, forKey: .source)
        freshness = try c.decode(String.self, forKey: .freshness)
        trainingEligible = try c.decode(Bool.self, forKey: .trainingEligible)
        result = try c.decode(Result.self, forKey: .result)
    }
    private enum CodingKeys: String, CodingKey { case schemaVersion, problemId, source, freshness, trainingEligible, result }
    func validated(problem: UUID, source expected: AttemptSourceIdentity, kind: VisualizationKind, viewport: VisualizationViewport) throws -> Self {
        let r = result
        guard schemaVersion == "math-visualization-public-v1", problemId == problem, source == expected,
              freshness == "CURRENT_AT_PUBLICATION", !trainingEligible, !r.trainingEligible,
              r.schemaVersion == "math-visualization-result-v1", r.policyVersion == "math-visualization-policy-v1",
              r.identity == expected, r.type == kind, r.viewport == viewport, viewport.valid, r.resolution == 48,
              r.correlationId.lowercased() == r.executionId.uuidString.lowercased(),
              r.axes == "X_Y_UNITLESS", r.sampleSemantics == "APPROXIMATE_DISPLAY_ONLY",
              r.numberLine.count <= 24, r.features.count <= 12, r.segments.count <= 128, r.concepts.count <= 9,
              Set(r.segments.map(\.id)).count == r.segments.count,
              Set(r.concepts).count == r.concepts.count,
              r.segments.reduce(0, { $0 + $1.points.count }) <= 2*r.resolution else { throw NetworkError.invalidResponse }
        let reasons: [Status: Set<String>] = [.supported: ["BOUNDED_EXACT_MODEL"], .unsupported: ["UNSUPPORTED_SOURCE", "PROVENANCE_UNAVAILABLE"],
            .unavailable: ["TIMEOUT", "RESOURCE_FAILURE", "WORKER_FAILURE", "ADMISSION_LIMIT", "INVALID_RESPONSE", "DISABLED"]]
        guard reasons[r.status]?.contains(r.reasonCode) == true,
              r.concepts.allSatisfy({ ["APPROXIMATE_SAMPLE", "ROOT_INTERCEPT", "POSITIVE_SLOPE", "NEGATIVE_SLOPE", "ZERO_DERIVATIVE", "DOMAIN_HOLE", "QUADRATIC_VERTEX", "OPEN_BOUNDARY", "CLOSED_BOUNDARY"].contains($0) }) else { throw NetworkError.invalidResponse }
        if r.status == .supported {
            guard kind != .story, kind == .numberLine || r.expression != nil,
                  kind != .numberLine || r.segments.isEmpty,
                  (r.derivativeExpression != nil) == (kind == .derivative) else { throw NetworkError.invalidResponse }
            guard let natural = r.naturalDomain, let scope = r.sourceDomain,
                  AttemptMathPresentation.latex(natural) != nil, AttemptMathPresentation.latex(scope) != nil else { throw NetworkError.invalidResponse }
        } else {
            guard r.expression == nil, r.derivativeExpression == nil, r.naturalDomain == nil, r.sourceDomain == nil, r.solutionSet == nil, r.numberLine.isEmpty,
                  r.features.isEmpty, r.segments.isEmpty, r.concepts.isEmpty else { throw NetworkError.invalidResponse }
        }
        for domain in [r.naturalDomain, r.sourceDomain, r.solutionSet].compactMap({ $0 }) {
            guard ["REALS", "EMPTY_SET", "FINITE_SET", "INTERVAL", "UNION", "INTERSECTION"].contains(domain["kind"].text),
                  AttemptMathPresentation.latex(domain) != nil else { throw NetworkError.invalidResponse }
        }
        for s in r.segments {
            guard s.id.range(of: "^[fd][0-9]{1,3}$", options: .regularExpression) != nil,
                  ["FUNCTION", "DERIVATIVE"].contains(s.curve), s.curve != "DERIVATIVE" || kind == .derivative,
                  (1...64).contains(s.points.count) else { throw NetworkError.invalidResponse }
            var previous = -Double.infinity
            for p in s.points {
                guard p.x.isFinite, p.y.isFinite, p.x > previous, (viewport.xMin...viewport.xMax).contains(p.x),
                      (viewport.yMin...viewport.yMax).contains(p.y) else { throw NetworkError.invalidResponse }
                previous = p.x
            }
        }
        for b in r.numberLine {
            guard (b.lowerValue == nil) == (b.lower == "-oo"), (b.upperValue == nil) == (b.upper == "oo"),
                  b.lowerValue != nil || b.leftOpen, b.upperValue != nil || b.rightOpen,
                  [b.lower, b.upper].allSatisfy({ !$0.isEmpty && $0.count <= 96 }),
                  [b.lowerValue, b.upperValue].compactMap({ $0 }).allSatisfy({ $0.isFinite && abs($0) <= 1e12 }) else { throw NetworkError.invalidResponse }
            if let a = b.lowerValue, let z = b.upperValue {
                guard a <= z, a != z || (!b.leftOpen && !b.rightOpen) else { throw NetworkError.invalidResponse }
            }
        }
        for f in r.features {
            guard ["ROOT_INTERCEPT", "Y_INTERCEPT", "QUADRATIC_VERTEX", "SLOPE"].contains(f.code),
                  [f.x, f.y].allSatisfy({ !$0.isEmpty && $0.count <= 96 }) else { throw NetworkError.invalidResponse }
        }
        return self
    }
}

protocol MathVisualizationServicing: Sendable {
    func context(problem: UUID) async throws -> AttemptSourceIdentity
    func load(problem: UUID, source: AttemptSourceIdentity, kind: VisualizationKind, viewport: VisualizationViewport) async throws -> MathVisualization
}
final class MathVisualizationAPI: MathVisualizationServicing, @unchecked Sendable {
    private let client: APIClient
    init(client: APIClient) { self.client = client }
    func context(problem: UUID) async throws -> AttemptSourceIdentity {
        struct Context: Decodable { let schemaVersion: String; let enabled: Bool; let source: AttemptSourceIdentity }
        let response: HTTPResponse<Context> = try await client.send(HTTPRequest(endpoint: Endpoint(path: "/api/v1/problem-sessions/\(problem)/visualizations/context")))
        guard response.body.schemaVersion == "math-visualization-context-v1", response.body.source.canonicalRevision > 0,
              response.body.source.parseRevision > 0 else { throw NetworkError.invalidResponse }
        guard response.body.enabled else { throw NetworkError.httpStatus(503) }
        return response.body.source
    }
    func load(problem: UUID, source: AttemptSourceIdentity, kind: VisualizationKind, viewport: VisualizationViewport) async throws -> MathVisualization {
        struct Query: Encodable {
            let schemaVersion = "math-visualization-source-v1"; let source: AttemptSourceIdentity
            let type: VisualizationKind; let viewport: VisualizationViewport; let resolution = 48
        }
        guard viewport.valid else { throw NetworkError.invalidResponse }
        let response: HTTPResponse<MathVisualization> = try await client.send(HTTPRequest(
            endpoint: Endpoint(path: "/api/v1/problem-sessions/\(problem)/visualizations", method: .post),
            body: try JSONEncoder().encode(Query(source: source, type: kind, viewport: viewport)), timeout: 8))
        return try response.body.validated(problem: problem, source: source, kind: kind, viewport: viewport)
    }
}

#if DEBUG
actor FixtureVisualizationAPI: MathVisualizationServicing {
    private var failures: Int
    private let delay: UInt64
    init(failFirst: Bool = false, delay: UInt64 = 0) { failures = failFirst ? 1 : 0; self.delay = delay }
    static func data() throws -> Data {
        guard let url = Bundle.main.url(forResource: "math-visualization-public-v1", withExtension: "json") else { throw NetworkError.invalidResponse }
        return try Data(contentsOf: url)
    }
    func context(problem: UUID) async throws -> AttemptSourceIdentity { try JSONDecoder().decode(MathVisualization.self, from: Self.data()).source }
    func load(problem: UUID, source: AttemptSourceIdentity, kind: VisualizationKind, viewport: VisualizationViewport) async throws -> MathVisualization {
        if failures > 0 { failures -= 1; throw URLError(.notConnectedToInternet) }
        if delay > 0 { try? await Task.sleep(nanoseconds: delay) }
        var raw = try JSONSerialization.jsonObject(with: Self.data()) as! [String: Any]
        raw["problemId"] = problem.uuidString
        var result = raw["result"] as! [String: Any]
        result["type"] = kind.rawValue
        result["viewport"] = try JSONSerialization.jsonObject(with: JSONEncoder().encode(viewport))
        // Fixture preserves valid clipping; production never uses this adapter.
        result["segments"] = [["id": "f0", "curve": "FUNCTION", "points": [["x": (viewport.xMin+viewport.xMax)/2, "y": (viewport.yMin+viewport.yMax)/2]]]]
        if kind == .derivative {
            result["derivativeExpression"] = ["kind": "NUMBER", "value": "1", "numericType": "INTEGER"]
        }
        if kind == .numberLine { result["segments"] = [Any]() }
        if kind == .story {
            result["status"] = "UNSUPPORTED"; result["reasonCode"] = "PROVENANCE_UNAVAILABLE"
            for key in ["expression", "derivativeExpression", "naturalDomain", "sourceDomain", "solutionSet"] { result[key] = NSNull() }
            for key in ["segments", "features", "numberLine", "concepts"] { result[key] = [Any]() }
        }
        raw["result"] = result
        return try JSONDecoder().decode(MathVisualization.self, from: JSONSerialization.data(withJSONObject: raw))
    }
}
#endif
