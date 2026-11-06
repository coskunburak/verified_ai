import Foundation
import Observation

@MainActor @Observable
final class TutorViewModel {
    let owner: UUID
    let problem: UUID
    private let service: TutorServicing
    private let store: TutorPendingStore
    private let network: NetworkMonitoring
    private let ownsAccount: @MainActor () async -> Bool
    @ObservationIgnored private var task: Task<Void, Never>?
    private var generation = 0
    private var active = false
    private(set) var busy = false
    private(set) var loaded = false
    private(set) var blocked = false
    private(set) var sessions: [TutorSession] = []
    private(set) var session: TutorSession?
    private(set) var messages: [TutorMessage] = []
    private(set) var pending: TutorPending?
    private(set) var errorKey: String?
    private(set) var turns: [TutorTurn] = []
    private(set) var hintHistory: TutorHintHistory?
    var selectedIntent: TutorIntent = .unknown
    var phase: TutorPhase { hintHistory?.progress.phase ?? turns.last?.progress.phase ?? .clarifyGoal }
    var draft = ""
    init(owner: UUID, problem: UUID, service: TutorServicing, store: TutorPendingStore,
         network: NetworkMonitoring, ownsAccount: @escaping @MainActor () async -> Bool) {
        self.owner = owner; self.problem = problem; self.service = service; self.store = store
        self.network = network; self.ownsAccount = ownsAccount
    }
    var canSend: Bool { active && loaded && !phase.terminal && !busy && !blocked && pending == nil && errorKey == nil && session?.status == .active && session?.sourceCurrent == true && !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && draft.unicodeScalars.count <= 2000 && draft.utf8.count <= 8000 }
    func resume() {
        active = true
        refresh()
    }
    func suspend() { active = false; generation += 1; task?.cancel(); task = nil; busy = false }
    func invalidate() { suspend(); blocked = true; errorKey = "tutor.authorization"; session = nil; sessions = []; messages = []; turns = []; hintHistory = nil; draft = ""; pending = nil }
    func refresh() {
        run {
            self.pending = try self.store.load(owner: self.owner, problem: self.problem)
            if let pending = self.pending, let content = pending.content {
                self.draft = content
                self.selectedIntent = pending.intent ?? .unknown
            }
            let sessions = try await self.service.list(problem: self.problem)
            guard await self.validAccount() else { return }
            self.sessions = sessions
            let selected = self.pending?.session ?? self.session?.id ?? sessions.last?.id
            if let selected { try await self.reload(selected) }
            self.loaded = true
        }
    }
    func select(_ id: UUID) { guard pending == nil else { return }; run { try await self.reload(id) } }
    func start() {
        guard loaded, pending == nil else { return }
        run {
            let operation = TutorPending(key: UUID().uuidString, session: nil, content: nil)
            try self.store.save(operation, owner: self.owner, problem: self.problem)
            self.pending = operation
            try await self.resolve(operation)
        }
    }
    func send() {
        guard canSend, let session else { return }
        run {
            let operation = TutorPending(key: UUID().uuidString, session: session.id, content: self.draft, kind: .turn, intent: self.selectedIntent)
            try self.store.save(operation, owner: self.owner, problem: self.problem)
            self.pending = operation
            try await self.resolve(operation)
        }
    }
    var isSyntheticFixture: Bool {
        #if DEBUG
        return service is FixtureTutorAPI
        #else
        return false
        #endif
    }
    var canRequestHint: Bool { active && loaded && !busy && !blocked && pending == nil && errorKey == nil && session?.status == .active && session?.sourceCurrent == true && hintHistory?.availability == .available }
    var canRequestReveal: Bool { active && loaded && !busy && !blocked && pending == nil && errorKey == nil && !phase.terminal && session?.status == .active && session?.sourceCurrent == true }
    func requestHint() {
        guard canRequestHint, let session, let level = hintHistory?.nextLevel else { return }
        run {
            let operation = TutorPending(key: UUID().uuidString, session: session.id, content: nil, kind: .hint, level: level)
            try self.store.save(operation, owner: self.owner, problem: self.problem)
            self.pending = operation
            try await self.resolve(operation)
        }
    }
    // Called only after the dedicated confirmation dialog; H3 never calls this.
    func confirmReveal() {
        guard canRequestReveal, let session else { return }
        run {
            let operation = TutorPending(key: UUID().uuidString, session: session.id, content: nil, kind: .reveal)
            try self.store.save(operation, owner: self.owner, problem: self.problem)
            self.pending = operation
            try await self.resolve(operation)
        }
    }
    func retry() { guard let pending else { refresh(); return }; run { try await self.resolve(pending) } }
    func close() {
        guard pending == nil, let session else { return }
        run {
            let closed = try await self.service.close(problem: self.problem, session: session.id)
            guard await self.validAccount() else { return }
            self.session = closed
            let hints = try await self.service.hints(problem: self.problem, session: closed.id)
            guard await self.validAccount() else { return }
            self.hintHistory = hints
        }
    }
    private func resolve(_ operation: TutorPending) async throws {
        let id: UUID
        if operation.kind == .hint, let session = operation.session, let level = operation.level {
            _ = try await service.requestHint(problem: problem, session: session, key: operation.key, level: level)
            id = session
        } else if operation.kind == .reveal, let session = operation.session {
            try await service.reveal(problem: problem, session: session, key: operation.key)
            id = session
        } else if let session = operation.session, let content = operation.content {
            if operation.kind == .turn {
                _ = try await service.submitTurn(problem: problem, session: session, key: operation.key, content: content, intent: operation.intent ?? .unknown)
            } else {
                _ = try await service.append(problem: problem, session: session, key: operation.key, content: content)
            }
            id = session
        } else {
            id = try await service.create(problem: problem, key: operation.key).id
        }
        guard await validAccount() else { return }
        try store.remove(owner: owner, problem: problem)
        pending = nil
        if operation.kind != .hint && operation.kind != .reveal { draft = ""; selectedIntent = .unknown }
        try await reload(id)
        loaded = true
    }
    private func reload(_ id: UUID) async throws {
        let snapshot = try await service.read(problem: problem, session: id)
        guard await validAccount() else { return }
        guard (0...100).contains(snapshot.messageCount) else { throw NetworkError.invalidResponse }
        var history: [TutorMessage] = []
        while history.count < snapshot.messageCount {
            let page = try await service.messages(problem: problem, session: id, after: history.count)
            guard await validAccount() else { return }
            guard !page.isEmpty, history.count + page.count <= snapshot.messageCount else { throw NetworkError.invalidResponse }
            history += page
        }
        let replies = try await service.turns(problem: problem, session: id, after: 0)
        guard replies.allSatisfy({ reply in history.contains { $0.id == reply.learnerMessageId && $0.sequenceNumber == reply.learnerSequenceNumber } }) else { throw NetworkError.invalidResponse }
        guard await validAccount() else { return }
        let hints = try await service.hints(problem: problem, session: id)
        guard await validAccount() else { return }
        session = snapshot; messages = history; turns = replies; hintHistory = hints
    }
    private func validAccount() async -> Bool {
        let expected = generation
        let owns = await ownsAccount()
        guard active, !Task.isCancelled, expected == generation else { return false }
        if !owns { invalidate(); return false }
        return true
    }
    private func run(_ work: @escaping @MainActor () async throws -> Void) {
        guard active, !busy, !blocked else { return }
        guard network.isReachable else { errorKey = "tutor.offline"; return }
        busy = true; errorKey = nil
        let expected = generation
        task = Task {
            guard await validAccount() else {
                if generation == expected { busy = false }
                return
            }
            do { try await work() }
            catch {
                guard active, !Task.isCancelled, generation == expected, await validAccount() else { return }
                if case NetworkError.server(let problem) = error {
                    if ([400,409,413,422].contains(problem.status) || problem.code == "TUTOR_CAPABILITY_UNAVAILABLE"), let rejected = pending {
                        // A definitive rejection acknowledges non-commit; uncertain transport errors keep the operation.
                        do { try store.remove(owner: owner, problem: self.problem); pending = nil; if rejected.kind != .hint && rejected.kind != .reveal { draft = rejected.content ?? "" } }
                        catch { blocked = true }
                    }
                    if [401,403,404].contains(problem.status) { blocked = true; errorKey = "tutor.authorization" }
                    else if problem.code == "TUTOR_CAPABILITY_UNAVAILABLE" { errorKey = "tutor.unavailable" }
                    else if problem.code == "TUTOR_REVEAL_UNAVAILABLE" { errorKey = "tutor.reveal.unavailable" }
                    else if problem.code == "TUTOR_HINT_LEVEL_BLOCKED" { errorKey = "tutor.hints.conflict" }
                    else if problem.code == "TUTOR_PEDAGOGY_STOPPED" { errorKey = "tutor.stopped" }
                    else if problem.code == "TUTOR_SOURCE_STALE" { errorKey = "tutor.stale" }
                    else if problem.code == "TUTOR_SESSION_CLOSED" { errorKey = "tutor.closed" }
                    else if [400,409,413,422].contains(problem.status) { errorKey = "tutor.rejected" }
                    else { errorKey = "tutor.retry" }
                } else if case NetworkError.httpStatus(let status) = error, [401,403,404].contains(status) {
                    blocked = true; errorKey = "tutor.authorization"
                } else { errorKey = network.isReachable ? "tutor.retry" : "tutor.offline" }
            }
            if active && generation == expected { busy = false }
        }
    }
}

/// Synthetic transports have no stored credentials. Live transports must match both authorities.
@MainActor
enum TutorAccountAuthorization {
    static func owns(owner: UUID, sessionId: UUID, service: TutorServicing,
                     store: AuthenticationSessionStore,
                     presentation: @MainActor () -> AuthenticationState) async -> Bool {
        func matchesPresentation() -> Bool {
            guard case .authenticated(let current) = presentation() else { return false }
            return current.userId == owner && current.sessionId == sessionId
        }
        guard matchesPresentation() else { return false }
        #if DEBUG
        if service is FixtureTutorAPI { return true }
        #endif
        let stored = try? await store.loadSession()
        return matchesPresentation() && stored?.userId == owner && stored?.sessionId == sessionId
    }
}
