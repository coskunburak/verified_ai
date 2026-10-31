import Foundation
import Observation

@MainActor
@Observable
final class PrimarySolveViewModel {
    let sessionId: UUID
    let isFixture: Bool
    private let userId: UUID
    private let service: PrimarySolveServicing
    private let recovery: SolveRecoveryStoring
    private let network: NetworkMonitoring
    private let sleep: @Sendable (UInt64) async throws -> Void
    @ObservationIgnored private var task: Task<Void, Never>?
    private var generation = 0
    private var active = false
    private(set) var busy = false
    private(set) var loaded = false
    private(set) var verification: VerificationDecisionSnapshot?
    private(set) var verificationUnavailable = false
    private(set) var snapshot: SolveJobSnapshot?
    private(set) var pending: SolveRecoveryMarker?
    private(set) var errorKey: String?
    private(set) var pollingPaused = false
    private var blockedMutation = false

    init(userId: UUID, sessionId: UUID, service: PrimarySolveServicing, recovery: SolveRecoveryStoring,
         network: NetworkMonitoring, isFixture: Bool = false,
         sleep: @escaping @Sendable (UInt64) async throws -> Void = { try await Task.sleep(nanoseconds: $0) }) {
        self.userId = userId; self.sessionId = sessionId; self.service = service
        self.recovery = recovery; self.network = network; self.isFixture = isFixture; self.sleep = sleep
    }
    var canStart: Bool { active && loaded && errorKey == nil && snapshot == nil && pending == nil && !busy && !blockedMutation }
    var canRetry: Bool { active && errorKey == nil && snapshot?.mayRetry == true && pending == nil && !busy && !blockedMutation }
    var canRecover: Bool { active && pending != nil && !busy && !blockedMutation }
    var canCancel: Bool { active && errorKey == nil && snapshot?.state.isActive == true && !busy }
    var visibleCandidate: SolutionCandidate? { errorKey == nil ? snapshot?.visibleCandidate : nil }

    #if DEBUG
    /// Explicit synthetic UI-test gate; ordinary fixture runs and live services keep automatic polling.
    var controlledFixtureState: SolveState? {
        guard isFixture, (service as? FixturePrimarySolveAPI)?.controlledProgression == true,
              let state = snapshot?.state, state.isActive else { return nil }
        return state
    }
    func advanceFixture() {
        guard let state = controlledFixtureState else { return }
        (service as? FixturePrimarySolveAPI)?.advance(from: state)
    }
    #endif

    func resume() {
        active = true
        refresh()
    }
    func suspend() {
        active = false; generation += 1; task?.cancel(); task = nil; busy = false
        // Never display a cached candidate as current after a background/source/account transition.
        snapshot = nil; verification = nil; loaded = false
    }
    func refresh() {
        launch { model, token in
            model.pending = try model.recovery.load(userId: model.userId, sessionId: model.sessionId)
            let current = try await model.service.current(sessionId: model.sessionId)
            try model.check(token)
            try model.accept(current)
            model.loaded = true
            await model.poll(token)
            await model.loadVerification(token)
        }
    }
    func start() {
        guard canStart else { return }
        submit(retryOf: nil)
    }
    func retry() {
        guard canRetry, let job = snapshot else { return }
        submit(retryOf: job.jobId)
    }
    func recoverRequest() {
        guard canRecover, let pending else { return }
        submit(retryOf: pending.retryOf, existing: pending)
    }
    private func submit(retryOf: UUID?, existing: SolveRecoveryMarker? = nil) {
        launch { model, token in
            let marker = existing ?? SolveRecoveryMarker(key: "solve_" + UUID().uuidString, retryOf: retryOf, jobId: nil)
            // Must reach protected storage before a POST is sent.
            try model.recovery.save(marker, userId: model.userId, sessionId: model.sessionId)
            model.pending = marker
            let result: SolveJobSnapshot
            if let retryOf = marker.retryOf {
                result = try await model.service.retry(sessionId: model.sessionId, jobId: retryOf, key: marker.key)
            } else {
                result = try await model.service.start(sessionId: model.sessionId, key: marker.key)
            }
            try model.check(token)
            try model.accept(result)
            model.loaded = true
            await model.poll(token)
            await model.loadVerification(token)
        }
    }
    func cancel() {
        guard canCancel, let job = snapshot else { return }
        launch { model, token in
            let result = try await model.service.cancel(sessionId: model.sessionId, jobId: job.jobId)
            try model.check(token)
            try model.accept(result)
            await model.poll(token)
            await model.loadVerification(token)
        }
    }
    /// One task owns both request and bounded polling; cancelling a screen never POSTs /cancel.
    private func launch(_ operation: @escaping @MainActor (PrimarySolveViewModel, Int) async throws -> Void) {
        guard active, !busy else { return }
        guard network.isReachable else { errorKey = "solve.error.offline"; snapshot = nil; verification = nil; loaded = false; return }
        verification = nil; verificationUnavailable = false
        generation += 1; let token = generation
        task?.cancel(); busy = true; errorKey = nil; pollingPaused = false
        task = Task { [weak self] in
            guard let self else { return }
            do { try await operation(self, token) }
            catch {
                if token == generation && !Task.isCancelled {
                    errorKey = Self.message(for: error)
                    if case NetworkError.server(let problem) = error {
                        blockedMutation = ["SOLVE_DISABLED", "SOLVE_SOURCE_NOT_READY", "SOLVE_STATE_CONFLICT", "SOLVE_RETRY_LIMIT",
                            "PROBLEM_UNSUPPORTED", "IDEMPOTENCY_KEY_REUSED"].contains(problem.code)
                        if problem.code.hasPrefix("AUTH_") { blockedMutation = true; snapshot = nil }
                    }
                }
            }
            if token == generation { busy = false; task = nil }
        }
    }
    private func loadVerification(_ token: Int) async {
        guard errorKey == nil, let job = snapshot, job.state == .generatedUnverified else { return }
        do {
            let result = try await service.verification(sessionId: sessionId, jobId: job.jobId)
            try check(token)
            guard snapshot?.jobId == job.jobId else { return }
            verification = try result?.validated(for: job)
        } catch {
            if token == generation && active && !Task.isCancelled { verification = nil; verificationUnavailable = true }
        }
    }
    private func check(_ token: Int) throws {
        try Task.checkCancellation()
        guard token == generation && active else { throw CancellationError() }
    }
    private func accept(_ value: SolveJobSnapshot?) throws {
        snapshot = try value?.validated()
        if let value, let pending, pending.retryOf != value.jobId {
            try recovery.remove(userId: userId, sessionId: sessionId)
            self.pending = nil
        }
        // Successful authoritative reconciliation can restore controls, except an unresolved mutation.
        blockedMutation = false
    }
    private func poll(_ token: Int) async {
        do {
            for index in 0..<15 {
                guard snapshot?.state.isActive == true else { return }
                // Controls become available between GETs; a user action cancels this task before replacing it.
                busy = false
                try await sleep(UInt64(min(2 + index, 5)) * 1_000_000_000)
                try check(token)
                guard network.isReachable else { errorKey = "solve.error.offline"; return }
                guard let job = snapshot else { return }
                busy = true
                let result = try await service.read(sessionId: sessionId, jobId: job.jobId)
                try check(token)
                try accept(result)
            }
            pollingPaused = snapshot?.state.isActive == true
        } catch {
            if token == generation && !Task.isCancelled { errorKey = Self.message(for: error) }
        }
    }
    static func message(for error: Error) -> String {
        if error is SolveContractError { return "solve.error.contract" }
        if case NetworkError.server(let problem) = error {
            switch problem.code {
            case "SOLVE_DISABLED": return "solve.error.disabled"
            case "SOLVE_SOURCE_NOT_READY": return "solve.error.source"
            case "SOLVE_STATE_CONFLICT", "IDEMPOTENCY_KEY_REUSED": return "solve.error.conflict"
            case "SOLVE_RETRY_LIMIT": return "solve.error.limit"
            case "PROBLEM_UNSUPPORTED": return "solve.error.unsupported"
            default:
                if problem.code.hasPrefix("AUTH_") { return "solve.error.auth" }
                if problem.code.contains("ENTITLEMENT") || problem.code.contains("CAPABILITY") || problem.status == 403 { return "solve.error.access" }
                return "solve.error.server"
            }
        }
        if case NetworkError.decoding = error { return "solve.error.contract" }
        return "solve.error.connection"
    }
}
