import Foundation
import Observation

enum SolveScreenPhase: Equatable, Sendable {
    case checkingExisting
    case readyToStart
    case recoveryPending(SolveRecoveryMarker)
    case job(SolveState)
    case degraded(String)
}

@MainActor
@Observable
final class PrimarySolveViewModel {
    let sessionId: UUID
    let isFixture: Bool
    private let userId: UUID
    private let service: PrimarySolveServicing
    private let recovery: SolveRecoveryStoring
    private let network: NetworkMonitoring
    private let logger: AppLogger
    private let profileModel: OnboardingViewModel?
    private let explanationOwner = UUID()
    private let sleep: @Sendable (UInt64) async throws -> Void
    @ObservationIgnored private var task: Task<Void, Never>?
    private var generation = 0
    private var active = false
    private(set) var busy = false
    private(set) var loaded = false
    private(set) var verification: VerificationDecisionSnapshot?
    private(set) var verificationUnavailable = false
    private(set) var verificationContractInvalid = false
    private(set) var snapshot: SolveJobSnapshot?
    private(set) var pending: SolveRecoveryMarker?
    private(set) var errorKey: String?
    private(set) var pollingPaused = false
    private var blockedMutation = false

    init(userId: UUID, sessionId: UUID, service: PrimarySolveServicing, recovery: SolveRecoveryStoring,
         network: NetworkMonitoring, isFixture: Bool = false, profileModel: OnboardingViewModel? = nil,
         logger: AppLogger = AppLogger(subsystem: "com.verifiedai.learning", category: "solving"),
         sleep: @escaping @Sendable (UInt64) async throws -> Void = { try await Task.sleep(nanoseconds: $0) }) {
        self.userId = userId; self.sessionId = sessionId; self.service = service
        self.recovery = recovery; self.network = network; self.isFixture = isFixture
        self.logger = logger; self.sleep = sleep
        self.profileModel = profileModel
    }

    private var explanationProfile: OnboardingViewModel? {
        guard let profileModel, profileModel.ownsExplanationPresentation(owner: explanationOwner, userId: userId) else { return nil }
        return profileModel
    }
    var explanationDepth: ExplanationDepth { explanationProfile?.explanationDepth ?? .standard }
    var explanationPreferenceStatus: ExplanationPreferenceStatus {
        explanationProfile?.explanationPreferenceStatus ?? .fallback
    }
    var explanationPresentation: ExplanationPresentation? {
        visibleCandidate.map { ExplanationPresentation(candidate: $0, mode: explanationDepth) }
    }
    func selectExplanationDepth(_ depth: ExplanationDepth) {
        guard active else { return }
        profileModel?.selectExplanationDepth(depth, owner: explanationOwner)
    }
    func reloadExplanationPreference() { profileModel?.reloadExplanationPreference(owner: explanationOwner) }
    func saveExplanationPreference() { profileModel?.saveExplanationPreference(owner: explanationOwner) }

    var screenPhase: SolveScreenPhase {
        if let state = snapshot?.state { return .job(state) }
        if let errorKey { return .degraded(errorKey) }
        if let pending { return .recoveryPending(pending) }
        return loaded ? .readyToStart : .checkingExisting
    }

    var canStart: Bool { active && loaded && errorKey == nil && snapshot == nil && pending == nil && !busy && !blockedMutation }
    var canRetry: Bool { active && errorKey == nil && snapshot?.mayRetry == true && pending == nil && !busy && !blockedMutation }
    var canRecover: Bool { active && pending != nil && !busy && !blockedMutation }
    var canCancel: Bool { active && errorKey == nil && snapshot?.state.isActive == true && !busy }
    var canRefreshVerification: Bool {
        active && !busy && errorKey == nil && snapshot?.state == .generatedUnverified && network.isReachable
    }
    var visibleCandidate: SolutionCandidate? { errorKey == nil ? snapshot?.visibleCandidate : nil }
    var verificationPresentation: VerificationEvidencePresentation {
        if let verification {
            return verification.evidencePresentation
        }
        return .absent(unavailable: verificationUnavailable, invalidContract: verificationContractInvalid)
    }
    var needsProblemReview: Bool {
        snapshot.map { [SolveState.reviewRequired, .stale, .unsupported].contains($0.state) } == true
            || errorKey == "solve.error.source"
            || errorKey == "solve.error.unsupported"
            || verification?.requiresProblemReview == true
    }

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
        profileModel?.beginExplanationPresentation(owner: explanationOwner, userId: userId)
        refresh()
    }
    func suspend() {
        profileModel?.endExplanationPresentation(owner: explanationOwner)
        active = false; generation += 1; task?.cancel(); task = nil; busy = false
        // Never display a cached candidate or verification verdict after a background/source/account transition.
        snapshot = nil; verification = nil; verificationUnavailable = false; verificationContractInvalid = false; loaded = false
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
    /// Read-only verification lookup refresh; never starts or retries a solve job or dispatches evaluation work.
    func refreshVerification() {
        guard canRefreshVerification else { return }
        launch { model, token in
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
                model.logger.info("solve.submitted kind=retry")
                result = try await model.service.retry(sessionId: model.sessionId, jobId: retryOf, key: marker.key)
            } else {
                model.logger.info("solve.submitted kind=\(existing == nil ? "start" : "recover")")
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
            model.logger.info("solve.cancelled state=\(result.state.rawValue)")
            await model.poll(token)
            await model.loadVerification(token)
        }
    }
    /// One task owns both request and bounded polling; cancelling a screen never POSTs /cancel.
    private func launch(_ operation: @escaping @MainActor (PrimarySolveViewModel, Int) async throws -> Void) {
        guard active, !busy else { return }
        guard network.isReachable else {
            pending = try? recovery.load(userId: userId, sessionId: sessionId)
            errorKey = "solve.error.offline"; snapshot = nil; verification = nil
            verificationUnavailable = false; verificationContractInvalid = false; loaded = false
            logger.warning("solve.offline")
            return
        }
        verification = nil; verificationUnavailable = false; verificationContractInvalid = false
        generation += 1; let token = generation
        task?.cancel(); busy = true; errorKey = nil; pollingPaused = false
        task = Task { [weak self] in
            guard let self else { return }
            do { try await operation(self, token) }
            catch {
                if token == generation && !Task.isCancelled {
                    applyError(error)
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
            guard snapshot?.jobId == job.jobId,
                  snapshot?.canonicalProblemId == job.canonicalProblemId,
                  snapshot?.problemParseId == job.problemParseId,
                  snapshot?.problemParseRevision == job.problemParseRevision else { return }
            verification = try result?.validated(for: job)
            verificationUnavailable = false
            verificationContractInvalid = false
            if let decision = verification {
                logger.info("solve.verification.loaded status=\(decision.status.rawValue) scope=\(decision.scope.rawValue)")
            }
        } catch {
            if token == generation && active && !Task.isCancelled {
                verification = nil
                verificationUnavailable = true
                if error is SolveContractError {
                    verificationContractInvalid = true
                    logger.warning("solve.verification.invalid")
                } else if case NetworkError.decoding = error {
                    verificationContractInvalid = true
                    logger.warning("solve.verification.invalid")
                } else {
                    verificationContractInvalid = false
                    logger.warning("solve.verification.unavailable")
                }
            }
        }
    }
    private func check(_ token: Int) throws {
        try Task.checkCancellation()
        guard token == generation && active else { throw CancellationError() }
    }
    private func accept(_ value: SolveJobSnapshot?) throws {
        let validated = try value?.validated()
        if snapshot?.jobId != validated?.jobId
            || snapshot?.canonicalProblemId != validated?.canonicalProblemId
            || snapshot?.problemParseId != validated?.problemParseId
            || snapshot?.problemParseRevision != validated?.problemParseRevision
            || validated?.state != .generatedUnverified {
            verification = nil
            verificationUnavailable = false
            verificationContractInvalid = false
        }
        snapshot = validated
        if let validated, let pending, pending.retryOf != validated.jobId {
            try recovery.remove(userId: userId, sessionId: sessionId)
            self.pending = nil
        }
        if let validated {
            logger.info("solve.reconciled state=\(validated.state.rawValue) attempt=\(validated.attempt)")
        } else {
            logger.info("solve.reconciled state=NONE")
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
                guard network.isReachable else {
                    errorKey = "solve.error.offline"
                    logger.warning("solve.offline.poll")
                    return
                }
                guard let job = snapshot else { return }
                busy = true
                let result = try await service.read(sessionId: sessionId, jobId: job.jobId)
                try check(token)
                guard result.jobId == job.jobId,
                      result.canonicalProblemId == job.canonicalProblemId,
                      result.problemParseId == job.problemParseId,
                      result.problemParseRevision == job.problemParseRevision else {
                    throw SolveContractError.invalidResponse
                }
                try accept(result)
            }
            pollingPaused = snapshot?.state.isActive == true
            if pollingPaused { logger.info("solve.poll.paused") }
        } catch {
            if token == generation && !Task.isCancelled {
                applyError(error)
            }
        }
    }
    private func applyError(_ error: Error) {
        let key = Self.message(for: error)
        errorKey = key
        verification = nil
        verificationUnavailable = false
        verificationContractInvalid = false
        if let contractError = error as? SolveContractError {
            snapshot = nil
            if contractError == .unsupportedCandidateSchema {
                blockedMutation = true
            }
        } else if case NetworkError.decoding = error {
            snapshot = nil
        } else if case NetworkError.server(let problem) = error {
            blockedMutation = ["SOLVE_DISABLED", "SOLVE_SOURCE_NOT_READY", "SOLVE_STATE_CONFLICT", "SOLVE_RETRY_LIMIT",
                "PROBLEM_UNSUPPORTED", "IDEMPOTENCY_KEY_REUSED"].contains(problem.code)
                || problem.code.contains("ENTITLEMENT") || problem.code.contains("CAPABILITY") || problem.status == 403
            if problem.code.hasPrefix("AUTH_") {
                blockedMutation = true
                snapshot = nil
            }
        }
        logger.warning("solve.error key=\(key)")
    }
    static func message(for error: Error) -> String {
        if let contractError = error as? SolveContractError {
            return contractError == .unsupportedCandidateSchema ? "solve.error.unsupported" : "solve.error.contract"
        }
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
