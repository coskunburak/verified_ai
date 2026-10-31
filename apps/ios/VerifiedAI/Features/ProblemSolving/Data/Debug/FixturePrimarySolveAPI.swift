#if DEBUG
import Foundation

/// Synthetic transport double. Explicit launch gate only; never a fallback from a live error.
@MainActor
final class FixturePrimarySolveAPI: PrimarySolveServicing {
    nonisolated static let sessionId = UUID(uuidString: "00000000-0000-0000-0000-000000005401")!
    nonisolated static let canonicalId = UUID(uuidString: "00000000-0000-0000-0000-000000005402")!
    nonisolated static let parseId = UUID(uuidString: "00000000-0000-0000-0000-000000005403")!
    nonisolated static let supportedVerificationScenarios: Set<String> = [
        "VERIFIED", "PARTIALLY_VERIFIED", "UNVERIFIED", "CONTRADICTION",
        "CALCULUS_DERIVATIVE", "CALCULUS_INTEGRAL", "CALCULUS_LIMIT",
        "EVIDENCE_CONFLICT", "SECONDARY_DISAGREEMENT", "SECONDARY_AGREEMENT",
        "CAPABILITY_UNAVAILABLE", "DOMAIN_UNCHECKED", "COMPLETENESS_UNCHECKED",
        "EXECUTION_FAILURE", "RELEASE_DISABLED", "NUMERIC_SAMPLING_UNPROVEN"
    ]
    private let defaults: UserDefaults
    private let scenario: String
    let controlledProgression: Bool
    private(set) var startRequests = 0
    private var releasedState: SolveState?
    private var readGate: (id: UUID, state: SolveState, continuation: AsyncStream<Void>.Continuation)?
    var waitingForAdvance: SolveState? { readGate?.state }

    init(scenario: String = "GENERATED_UNVERIFIED", defaults: UserDefaults = .standard, reset: Bool = false,
         controlledProgression: Bool = false) {
        self.scenario = scenario; self.defaults = defaults; self.controlledProgression = controlledProgression
        if reset { defaults.removeObject(forKey: "solve.fixture.job"); defaults.removeObject(forKey: "solve.fixture.attempt"); defaults.removeObject(forKey: "solve.fixture.state") }
    }
    func verification(sessionId: UUID, jobId: UUID) async throws -> VerificationDecisionSnapshot? {
        if scenario == "VERIFICATION_UNAVAILABLE" {
            throw NetworkError.transport("Synthetic verification lookup unavailable")
        }
        if scenario == "VERIFICATION_MALFORMED" {
            throw SolveContractError.invalidResponse
        }
        guard Self.supportedVerificationScenarios.contains(scenario) else { return nil }
        return Self.decisionFixture(scenario)
    }
    nonisolated static func decisionFixture(_ scenario: String) -> VerificationDecisionSnapshot {
        switch scenario {
        case "CALCULUS_DERIVATIVE", "CALCULUS_INTEGRAL", "CALCULUS_LIMIT":
            let scope: VerificationDecisionSnapshot.Scope = switch scenario {
            case "CALCULUS_DERIVATIVE": .openInterval
            case "CALCULUS_INTEGRAL": .antiderivative
            default: .neighborhood
            }
            let method = scope.calculusMethod!
            return makeDecision(
                status: .verified,
                scope: scope,
                reasons: ["REQUIRED_CLAIM_PROVEN"],
                checked: [method],
                unchecked: [],
                contradictions: [],
                uncertainty: ["EXPLANATION_STEPS_UNCHECKED"],
                recovery: .none
            )
        case "VERIFIED":
            return makeDecision(
                status: .verified,
                scope: .finalAnswer,
                reasons: ["REQUIRED_CLAIM_PROVEN"],
                checked: [.domain, .root, .completeness],
                unchecked: [],
                contradictions: [],
                uncertainty: ["EXPLANATION_STEPS_UNCHECKED"],
                recovery: .none
            )
        case "PARTIALLY_VERIFIED", "COMPLETENESS_UNCHECKED":
            return makeDecision(
                status: .partiallyVerified,
                scope: .subset,
                reasons: ["COMPLETENESS_UNCHECKED", "SUBSET_PROVEN"],
                checked: [.domain, .root],
                unchecked: [.completeness],
                contradictions: [],
                uncertainty: ["EXPLANATION_STEPS_UNCHECKED", "COMPLETENESS_UNRESOLVED"],
                recovery: .evidence
            )
        case "DOMAIN_UNCHECKED":
            return makeDecision(
                status: .partiallyVerified,
                scope: .subset,
                reasons: ["DOMAIN_UNCHECKED", "SUBSET_PROVEN"],
                checked: [.root],
                unchecked: [.domain, .completeness],
                contradictions: [],
                uncertainty: ["EXPLANATION_STEPS_UNCHECKED", "DOMAIN_UNRESOLVED", "COMPLETENESS_UNRESOLVED"],
                recovery: .evidence
            )
        case "CONTRADICTION":
            return makeDecision(
                status: .unverified,
                scope: .none,
                reasons: ["DETERMINISTIC_CONTRADICTION", "MISSING_PROOF"],
                checked: [],
                unchecked: [.domain, .root, .completeness],
                contradictions: [.root],
                uncertainty: ["EXPLANATION_STEPS_UNCHECKED", "MISSING_MATHEMATICAL_PROOF"],
                recovery: .reviewContradiction
            )
        case "EVIDENCE_CONFLICT":
            return makeDecision(
                status: .unverified,
                scope: .none,
                reasons: ["EVIDENCE_CONFLICT", "MISSING_PROOF"],
                checked: [.root],
                unchecked: [.domain, .completeness],
                contradictions: [],
                uncertainty: ["EXPLANATION_STEPS_UNCHECKED", "DOMAIN_UNRESOLVED", "MISSING_MATHEMATICAL_PROOF"],
                recovery: .reviewContradiction
            )
        case "SECONDARY_DISAGREEMENT":
            return makeDecision(
                status: .unverified,
                scope: .none,
                reasons: ["SECONDARY_DISAGREEMENT", "MISSING_PROOF"],
                checked: [],
                unchecked: [.domain, .root, .completeness],
                contradictions: [],
                uncertainty: ["EXPLANATION_STEPS_UNCHECKED", "SECONDARY_CONFLICT", "MISSING_MATHEMATICAL_PROOF"],
                recovery: .reconcile
            )
        case "SECONDARY_AGREEMENT":
            return makeDecision(
                status: .unverified,
                scope: .none,
                reasons: ["MISSING_PROOF", "SECONDARY_AGREEMENT"],
                checked: [],
                unchecked: [.domain, .root, .completeness],
                contradictions: [],
                uncertainty: ["EXPLANATION_STEPS_UNCHECKED", "MISSING_MATHEMATICAL_PROOF"],
                recovery: .evidence
            )
        case "CAPABILITY_UNAVAILABLE":
            return makeDecision(
                status: .unverified,
                scope: .none,
                reasons: ["CAPABILITY_UNAVAILABLE", "MISSING_PROOF"],
                checked: [],
                unchecked: [.symbolic],
                contradictions: [],
                uncertainty: ["EXPLANATION_STEPS_UNCHECKED", "MISSING_MATHEMATICAL_PROOF"],
                recovery: .unsupported
            )
        case "EXECUTION_FAILURE":
            return makeDecision(
                status: .unverified,
                scope: .none,
                reasons: ["EXECUTION_FAILURE", "MISSING_PROOF"],
                checked: [],
                unchecked: [.domain, .root, .completeness],
                contradictions: [],
                uncertainty: ["EXPLANATION_STEPS_UNCHECKED", "EXECUTION_UNAVAILABLE", "MISSING_MATHEMATICAL_PROOF"],
                recovery: .retry
            )
        case "RELEASE_DISABLED":
            return makeDecision(
                status: .unverified,
                scope: .none,
                reasons: ["RELEASE_DISABLED"],
                checked: [.domain, .root, .completeness],
                unchecked: [],
                contradictions: [],
                uncertainty: ["EXPLANATION_STEPS_UNCHECKED", "RELEASE_NOT_ACCEPTED"],
                recovery: .release
            )
        case "NUMERIC_SAMPLING_UNPROVEN":
            return makeDecision(
                status: .unverified,
                scope: .none,
                reasons: ["MISSING_PROOF", "SECONDARY_AGREEMENT"],
                checked: [],
                unchecked: [.symbolic, .numeric],
                contradictions: [],
                uncertainty: ["EXPLANATION_STEPS_UNCHECKED", "MISSING_MATHEMATICAL_PROOF"],
                recovery: .evidence
            )
        default:
            return makeDecision(
                status: .unverified,
                scope: .none,
                reasons: ["MISSING_PROOF"],
                checked: [],
                unchecked: [.domain, .completeness],
                contradictions: [],
                uncertainty: ["EXPLANATION_STEPS_UNCHECKED", "MISSING_MATHEMATICAL_PROOF"],
                recovery: .evidence
            )
        }
    }
    nonisolated private static func makeDecision(
        status: VerificationDecisionSnapshot.Status,
        scope: VerificationDecisionSnapshot.Scope,
        reasons: [String],
        checked: [VerificationDecisionSnapshot.Method],
        unchecked: [VerificationDecisionSnapshot.Method],
        contradictions: [VerificationDecisionSnapshot.Method],
        uncertainty: [String],
        recovery: VerificationDecisionSnapshot.Recovery
    ) -> VerificationDecisionSnapshot {
        VerificationDecisionSnapshot(
            schemaVersion: "verification-decision-v1",
            id: UUID(),
            policyVersion: "verification-policy-v1",
            solutionId: UUID(),
            canonicalProblemId: canonicalId,
            problemParseId: parseId,
            problemParseRevision: 1,
            runId: UUID(),
            evidenceIds: [UUID()],
            status: status,
            scope: scope,
            reasons: reasons,
            checked: checked,
            unchecked: unchecked,
            contradictions: contradictions,
            uncertainty: uncertainty,
            recovery: recovery,
            correlationId: "synthetic-decision",
            createdAt: "2026-10-30T00:00:00Z"
        )
    }
    func current(sessionId: UUID) async throws -> SolveJobSnapshot? {
        guard let id = defaults.string(forKey: "solve.fixture.job"), let job = UUID(uuidString: id) else { return nil }
        return try snapshot(job: job, state: defaults.string(forKey: "solve.fixture.state").flatMap(SolveState.init(rawValue:)) ?? terminalState)
    }
    func start(sessionId: UUID, key: String) async throws -> SolveJobSnapshot {
        startRequests += 1
        if let existing = try await current(sessionId: sessionId) { return existing }
        let job = UUID()
        defaults.set(job.uuidString, forKey: "solve.fixture.job")
        defaults.set(1, forKey: "solve.fixture.attempt"); releasedState = nil
        return try record(job: job, state: .queued)
    }
    func read(sessionId: UUID, jobId: UUID) async throws -> SolveJobSnapshot {
        guard let current = try await current(sessionId: sessionId), current.jobId == jobId else {
            throw SolveContractError.invalidResponse
        }
        guard current.state.isActive else { return current }
        if controlledProgression { try await waitForAdvance(from: current.state) }
        try Task.checkCancellation()
        return try record(job: jobId, state: current.state == .queued ? .running : terminalState)
    }
    func retry(sessionId: UUID, jobId: UUID, key: String) async throws -> SolveJobSnapshot {
        defaults.set(2, forKey: "solve.fixture.attempt"); releasedState = nil
        let job = UUID(); defaults.set(job.uuidString, forKey: "solve.fixture.job")
        return try record(job: job, state: .queued)
    }
    func cancel(sessionId: UUID, jobId: UUID) async throws -> SolveJobSnapshot { try record(job: jobId, state: .cancelled) }

    /// Test-server stimulus only: the real ViewModel poll must consume this permit and render the next state.
    /// A phase cannot disappear while XCTest is still observing it, regardless of simulator speed.
    func advance(from state: SolveState) {
        guard controlledProgression, state.isActive,
              defaults.string(forKey: "solve.fixture.state") == state.rawValue else { return }
        if let gate = readGate, gate.state == state {
            // A just-cancelled old screen may not have cleared its gate yet. Keep the acknowledgement
            // for the resumed screen's next GET rather than losing it to a terminated stream.
            if case .terminated = gate.continuation.yield(()) { releasedState = state }
            gate.continuation.finish()
        } else {
            // The UI can acknowledge a phase before its next polling request begins.
            releasedState = state
        }
    }
    private func waitForAdvance(from state: SolveState) async throws {
        if releasedState == state { releasedState = nil; return }
        let (events, continuation) = AsyncStream<Void>.makeStream(bufferingPolicy: .bufferingNewest(1))
        let id = UUID()
        readGate?.continuation.finish()
        readGate = (id, state, continuation)
        defer {
            continuation.finish()
            if readGate?.id == id { readGate = nil }
        }
        var iterator = events.makeAsyncIterator()
        // AsyncStream unblocks on task cancellation, so dismiss/background/relaunch cannot leak a pending GET.
        guard await iterator.next() != nil else { throw CancellationError() }
        try Task.checkCancellation()
    }
    private var terminalState: SolveState {
        if defaults.integer(forKey: "solve.fixture.attempt") > 1 { return .generatedUnverified }
        return SolveState(rawValue: scenario) ?? .generatedUnverified
    }
    private func record(job: UUID, state: SolveState) throws -> SolveJobSnapshot {
        defaults.set(state.rawValue, forKey: "solve.fixture.state")
        return try snapshot(job: job, state: state)
    }
    private func snapshot(job: UUID, state: SolveState) throws -> SolveJobSnapshot {
        if scenario == "MALFORMED", state.showsCandidate { throw SolveContractError.invalidResponse }
        if scenario == "UNSUPPORTED_CANDIDATE_V2", state.showsCandidate {
            return try JSONDecoder().decode(SolveJobSnapshot.self, from: Self.unsupportedV2Data(jobId: job)).validated()
        }
        return try Self.decode(state: state, jobId: job, attempt: max(1, defaults.integer(forKey: "solve.fixture.attempt")))
    }
    static func decode(state: SolveState, jobId: UUID = UUID(), attempt: Int = 1) throws -> SolveJobSnapshot {
        try JSONDecoder().decode(SolveJobSnapshot.self, from: data(state: state, jobId: jobId, attempt: attempt)).validated()
    }
    static func data(state: SolveState, jobId: UUID = UUID(), attempt: Int = 1) -> Data {
        let candidate = state.showsCandidate || state == .stale ? candidateJSON(review: state == .reviewRequired) : "null"
        return Data("""
        {"jobId":"\(jobId)","canonicalProblemId":"\(canonicalId)","problemParseId":"\(parseId)",
        "problemParseRevision":1,"attempt":\(attempt),"state":"\(state.rawValue)","createdAt":"2026-10-26T12:00:00Z",
        "candidate":\(candidate),"provider":"LOCAL_FIXTURE","model":"local-fixture-solver-v1","promptVersion":"v001",
        "routePolicyVersion":"solve-local-fixture-v1","verificationNotice":"AI-generated candidate; mathematical verification has not been performed."}
        """.utf8)
    }
    static func unsupportedV2Data(jobId: UUID = UUID()) -> Data {
        Data("""
        {"jobId":"\(jobId)","canonicalProblemId":"\(canonicalId)","problemParseId":"\(parseId)",
        "problemParseRevision":1,"attempt":1,"state":"GENERATED_UNVERIFIED","createdAt":"2026-10-26T12:00:00Z",
        "candidate":{"schemaVersion":"solution-candidate-v2","canonicalProblemId":"\(canonicalId)","problemParseId":"\(parseId)",
        "problemParseRevision":1,"candidateVersion":2,"canonicalRevision":1,"generationOrigin":"LOCAL_FIXTURE",
        "problemType":"DERIVATIVE","taskType":"DIFFERENTIATE","method":"DERIVATIVE_BACKCHECK",
        "steps":[{"id":"s1","order":1,"category":"CONCLUDE","claim":{"kind":"EXPRESSION","expression":{"kind":"VARIABLE","symbol":"x"}},
        "explanation":"Derivative candidate","dependsOn":[]}],"finalAnswer":{"kind":"EXPRESSION","expression":{"kind":"VARIABLE","symbol":"x"}},
        "sourceAssumptionIds":[],"sourceConstraintIds":[],"derivedRestrictionIds":[],"ambiguities":[],"unresolvedIssues":[]},
        "provider":"LOCAL_FIXTURE","model":"owner-calculus-fixture-v1","promptVersion":"v001",
        "routePolicyVersion":"calculus-fixture-v1","verificationNotice":"AI-generated candidate; mathematical verification has not been performed."}
        """.utf8)
    }
    private static func candidateJSON(review: Bool) -> String {
        """
        {"schemaVersion":"solution-candidate-v1","canonicalProblemId":"\(canonicalId)","problemParseId":"\(parseId)",
        "problemParseRevision":1,"candidateVersion":1,"generationOrigin":"LOCAL_FIXTURE","problemType":"EQUATION",
        "taskType":"SOLVE_EQUATION","method":"ISOLATE_VARIABLE",
        "steps":[{"id":"s1","order":1,"category":"TRANSFORM","claim":{"kind":"RELATION","relation":"EQUALS",
        "left":{"kind":"VARIABLE","symbol":"x"},"right":{"kind":"BINARY","operator":"SUBTRACT","left":{"kind":"NUMBER","numericType":"INTEGER","value":"2"},"right":{"kind":"NUMBER","numericType":"INTEGER","value":"1"}}},
        "explanation":"Her iki taraftan 1 çıkarılır. / Subtract 1 from both sides.","dependsOn":[]},
        {"id":"s2","order":2,"category":"CONCLUDE","claim":{"kind":"RELATION","relation":"EQUALS","left":{"kind":"VARIABLE","symbol":"x"},"right":{"kind":"NUMBER","numericType":"INTEGER","value":"1"}},
        "explanation":"Önerilen sonuç x = 1. / The proposed answer is x = 1.","dependsOn":["s1"]}],
        "finalAnswer":{"kind":"RELATION","relation":"EQUALS","left":{"kind":"VARIABLE","symbol":"x"},"right":{"kind":"NUMBER","numericType":"INTEGER","value":"1"}},
        "sourceAssumptionIds":[],"sourceConstraintIds":[],"derivedRestrictionIds":[],
        "ambiguities":\(review ? "[\"Örnek belirsizlik: kaynağı gözden geçirin. / Example uncertainty: review the source.\"]" : "[]"),"unresolvedIssues":[]}
        """
    }
}

@MainActor
struct FixtureSolveNetworkMonitor: NetworkMonitoring {
    let isReachable = true
}

struct FixtureSolveHistoryAPI: ProblemSessionHistoryServicing {
    func history(limit: Int, cursor: String?) async throws -> ProblemSessionHistoryPage {
        ProblemSessionHistoryPage(items: [try await detail(problemSessionId: FixturePrimarySolveAPI.sessionId).historyItem], nextCursor: nil)
    }
    func detail(problemSessionId: UUID) async throws -> ProblemSessionDetail {
        await MainActor.run {
            ProblemSessionDetail(problemSessionId: FixturePrimarySolveAPI.sessionId, status: .parsed, stage: .readyForSolve,
                inputMode: "SYNTHETIC_FIXTURE", nextAction: .readyForSolve, retryable: false, reviewRequired: false,
                failureCode: nil, currentParse: .init(problemParseId: FixturePrimarySolveAPI.parseId, revision: 1, source: "USER", supportStatus: "SUPPORTED", reviewRequired: false),
                canonicalProblem: .init(canonicalProblemId: FixturePrimarySolveAPI.canonicalId, revision: 1, problemParseId: FixturePrimarySolveAPI.parseId, problemParseRevision: 1, problemType: "EQUATION", taskType: "SOLVE_EQUATION"),
                classification: nil, activeJob: nil, createdAt: Date(timeIntervalSince1970: 1793016000),
                updatedAt: Date(timeIntervalSince1970: 1793016000), completedAt: nil, version: 1)
        }
    }
}

@MainActor
final class FixtureSolveHistoryCache: ProblemSessionCaching {
    func loadSummaries(limit: Int) throws -> [ProblemSessionHistoryItem] { [] }
    func save(page: ProblemSessionHistoryPage) throws { }
    func save(detail: ProblemSessionDetail) throws { }
    func clear() throws { }
}
#endif
