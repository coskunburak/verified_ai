#if DEBUG
import Foundation

/// Synthetic transport double. Explicit launch gate only; never a fallback from a live error.
@MainActor
final class FixturePrimarySolveAPI: PrimarySolveServicing {
    nonisolated static let sessionId = UUID(uuidString: "00000000-0000-0000-0000-000000005401")!
    nonisolated static let canonicalId = UUID(uuidString: "00000000-0000-0000-0000-000000005402")!
    nonisolated static let parseId = UUID(uuidString: "00000000-0000-0000-0000-000000005403")!
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
        guard ["VERIFIED", "PARTIALLY_VERIFIED", "UNVERIFIED", "CONTRADICTION",
            "CALCULUS_DERIVATIVE", "CALCULUS_INTEGRAL", "CALCULUS_LIMIT"].contains(scenario) else { return nil }
        return Self.decisionFixture(scenario)
    }
    nonisolated static func decisionFixture(_ scenario: String) -> VerificationDecisionSnapshot {
        let calculusScope: VerificationDecisionSnapshot.Scope? = switch scenario {
        case "CALCULUS_DERIVATIVE": .openInterval
        case "CALCULUS_INTEGRAL": .antiderivative
        case "CALCULUS_LIMIT": .neighborhood
        default: nil
        }
        let status = calculusScope == nil ? VerificationDecisionSnapshot.Status(rawValue: scenario) ?? .unverified : .verified
        let contradiction = scenario == "CONTRADICTION"
        let verified = status == .verified, partial = status == .partiallyVerified
        return VerificationDecisionSnapshot(schemaVersion: "verification-decision-v1", id: UUID(), policyVersion: "verification-policy-v1",
            solutionId: UUID(), canonicalProblemId: canonicalId, problemParseId: parseId, problemParseRevision: 1,
            runId: UUID(), evidenceIds: [UUID()], status: status, scope: calculusScope ?? (verified ? .finalAnswer : partial ? .subset : .none),
            reasons: verified ? ["REQUIRED_CLAIM_PROVEN"] : partial ? ["SUBSET_PROVEN"] : contradiction ? ["DETERMINISTIC_CONTRADICTION"] : ["MISSING_PROOF"],
            checked: calculusScope?.calculusMethod.map { [$0] } ?? (verified ? [.domain, .root, .completeness] : partial ? [.root] : []),
            unchecked: verified ? [] : contradiction ? [.domain, .root, .completeness] : [.domain, .completeness],
            contradictions: contradiction ? [.root] : [],
            uncertainty: ["EXPLANATION_STEPS_UNCHECKED"], recovery: verified ? .none : contradiction ? .reviewContradiction : .evidence,
            correlationId: "synthetic-decision", createdAt: "2026-10-30T00:00:00Z")
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
