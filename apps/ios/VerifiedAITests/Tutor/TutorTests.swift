import XCTest
@testable import VerifiedAI

@MainActor
final class TutorTests: XCTestCase {
    private func store() -> TutorPendingStore {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        return TutorPendingStore(directory: directory)
    }
    private func settle(_ model: TutorViewModel) async {
        for _ in 0..<200 { if !model.busy { return }; await Task.yield() }
        XCTAssertFalse(model.busy)
    }
    func testLostAcknowledgementReusesDurableKeyAfterModelRestart() async throws {
        let api = FixtureTutorAPI(failFirst: true), store = store(), network = TutorTestNetwork()
        let owner = UUID(), problem = UUID()
        let first = TutorViewModel(owner: owner, problem: problem, service: api, store: store, network: network, ownsAccount: { true })
        first.resume(); await settle(first); first.start(); await settle(first)
        first.draft = "Synthetic question"; first.send(); await settle(first)
        let key = try XCTUnwrap(first.pending?.key)
        XCTAssertEqual(first.errorKey, "tutor.retry")
        first.suspend()
        let restored = TutorViewModel(owner: owner, problem: problem, service: api, store: store, network: network, ownsAccount: { true })
        restored.resume(); await settle(restored)
        XCTAssertEqual(restored.pending?.key, key)
        restored.retry(); await settle(restored)
        XCTAssertEqual(restored.messages.map(\.sequenceNumber), [1])
        XCTAssertNil(restored.pending)
        XCTAssertNil(try store.load(owner: owner, problem: problem))
    }
    func testOfflineRecoveryCloseAndDuplicateSendGuard() async {
        let network = TutorTestNetwork(); network.isReachable = false
        let model = TutorViewModel(owner: UUID(), problem: UUID(), service: FixtureTutorAPI(), store: store(), network: network, ownsAccount: { true })
        model.resume(); XCTAssertEqual(model.errorKey, "tutor.offline")
        network.isReachable = true; model.refresh(); await settle(model)
        model.start(); await settle(model)
        model.draft = "Question"; model.send(); model.send(); await settle(model)
        XCTAssertEqual(model.messages.count, 1)
        model.close(); await settle(model)
        XCTAssertEqual(model.session?.status, .closed); XCTAssertFalse(model.canSend)
    }
    func testAccountInvalidationCannotPublishOrSend() async {
        var owns = true
        let model = TutorViewModel(owner: UUID(), problem: UUID(), service: FixtureTutorAPI(), store: store(), network: TutorTestNetwork(), ownsAccount: { owns })
        model.resume(); await settle(model); model.start(); await settle(model)
        owns = false; model.refresh(); await settle(model)
        XCTAssertNil(model.session); XCTAssertTrue(model.blocked); XCTAssertFalse(model.canSend)
    }
    func testStaleSourceIsReadOnlyAndPaginatedHistoryReloads() async throws {
        let service = FixtureTutorAPI(), problem = UUID()
        let session = try await service.create(problem: problem, key: "create-key")
        for index in 0..<60 { _ = try await service.append(problem: problem, session: session.id, key: "message-\(index)", content: "Synthetic \(index)") }
        await service.markStale()
        let model = TutorViewModel(owner: UUID(), problem: problem, service: service, store: store(), network: TutorTestNetwork(), ownsAccount: { true })
        model.resume(); await settle(model)
        XCTAssertEqual(model.messages.count, 60)
        XCTAssertEqual(model.messages.map(\.sequenceNumber), Array(1...60))
        XCTAssertEqual(model.session?.sourceCurrent, false)
        model.draft = "Cannot append"; XCTAssertFalse(model.canSend)
    }
    func testFixtureAuthorizationRequiresMatchingVisibleAccountAndSession() async {
        let owner = UUID(), sessionId = UUID()
        let session = AuthSession(userId: owner, sessionId: sessionId, accessToken: "synthetic-access",
            accessTokenExpiresAt: Date().addingTimeInterval(3600), refreshToken: "synthetic-refresh",
            refreshTokenExpiresAt: Date().addingTimeInterval(7200))
        let storage = AuthenticationSessionStore(secureStorage: InMemorySecureStorage())
        let service = FixtureTutorAPI()
        let allowed = await TutorAccountAuthorization.owns(owner: owner, sessionId: sessionId,
            service: service, store: storage, presentation: { .authenticated(session) })
        XCTAssertTrue(allowed, "The actual local fixture must work without stored credentials")
        let foreignOwner = await TutorAccountAuthorization.owns(owner: UUID(), sessionId: sessionId,
            service: service, store: storage, presentation: { .authenticated(session) })
        let foreignSession = await TutorAccountAuthorization.owns(owner: owner, sessionId: UUID(),
            service: service, store: storage, presentation: { .authenticated(session) })
        let loggedOut = await TutorAccountAuthorization.owns(owner: owner, sessionId: sessionId,
            service: service, store: storage, presentation: { .unauthenticated })
        XCTAssertFalse(foreignOwner); XCTAssertFalse(foreignSession); XCTAssertFalse(loggedOut)
    }
    func testLiveTutorTransportStillRequiresMatchingStoredCredentials() async throws {
        let owner = UUID(), sessionId = UUID()
        let session = AuthSession(userId: owner, sessionId: sessionId, accessToken: "synthetic-access",
            accessTokenExpiresAt: Date().addingTimeInterval(3600), refreshToken: "synthetic-refresh",
            refreshTokenExpiresAt: Date().addingTimeInterval(7200))
        let credentials = InMemorySecureStorage()
        let storage = AuthenticationSessionStore(secureStorage: credentials)
        let service = TutorAPI(client: APIClient(baseURL: URL(string: "https://example.invalid")!,
            session: TutorTestTransport(data: Data()), authTokenProvider: credentials, correlationContext: CorrelationContext()))
        let missing = await TutorAccountAuthorization.owns(owner: owner, sessionId: sessionId,
            service: service, store: storage, presentation: { .authenticated(session) })
        XCTAssertFalse(missing)
        try await storage.save(session)
        let matching = await TutorAccountAuthorization.owns(owner: owner, sessionId: sessionId,
            service: service, store: storage, presentation: { .authenticated(session) })
        XCTAssertTrue(matching)
        let loggedOut = await TutorAccountAuthorization.owns(owner: owner, sessionId: sessionId,
            service: service, store: storage, presentation: { .unauthenticated })
        XCTAssertFalse(loggedOut, "Stored credentials alone must not authorize a signed-out screen")
        try await storage.clear()
        let cleared = await TutorAccountAuthorization.owns(owner: owner, sessionId: sessionId,
            service: service, store: storage, presentation: { .authenticated(session) })
        XCTAssertFalse(cleared)
    }
    func testPendingStorageIsOwnerScoped() throws {
        let store = store(), owner = UUID(), problem = UUID()
        let pending = TutorPending(key: "persisted-key", session: UUID(), content: "Synthetic")
        try store.save(pending, owner: owner, problem: problem)
        XCTAssertEqual(try store.load(owner: owner, problem: problem), pending)
        XCTAssertNil(try store.load(owner: UUID(), problem: problem))
        try store.clearAll(); XCTAssertNil(try store.load(owner: owner, problem: problem))
    }
    func testAppendWireContractAndTypedDecoding() async throws {
        let problem = UUID(), session = UUID(), message = UUID()
        let data = Data("""
        {"schemaVersion":"tutor-message-v1","id":"\(message)","tutorSessionId":"\(session)","role":"LEARNER","sequenceNumber":1,"content":"Synthetic","createdAt":"2026-11-01T10:00:00Z"}
        """.utf8)
        let transport = TutorTestTransport(data: data)
        let api = TutorAPI(client: APIClient(baseURL: URL(string: "https://example.invalid")!, session: transport,
            authTokenProvider: InMemorySecureStorage(), correlationContext: CorrelationContext()))
        let result = try await api.append(problem: problem, session: session, key: "message-key", content: "Synthetic")
        XCTAssertEqual(result.id, message)
        let requests = await transport.requests
        XCTAssertEqual(requests.count, 1)
        XCTAssertEqual(requests[0].httpMethod, "POST")
        XCTAssertEqual(requests[0].value(forHTTPHeaderField: "Idempotency-Key"), "message-key")
        XCTAssertEqual(requests[0].url?.path, "/api/v1/problem-sessions/\(problem)/tutor-sessions/\(session)/messages")
        let body = try JSONSerialization.jsonObject(with: XCTUnwrap(requests[0].httpBody)) as! [String: String]
        XCTAssertEqual(body, ["content":"Synthetic"])
    }
    func testSocraticTurnRestoresSpeakerPhaseAndUncertaintyAfterLostAcknowledgement() async throws {
        let api = FixtureTutorAPI(failFirst: true), pending = store(), owner = UUID(), problem = UUID()
        let model = TutorViewModel(owner: owner, problem: problem, service: api, store: pending, network: TutorTestNetwork(), ownsAccount: { true })
        model.resume(); await settle(model); model.start(); await settle(model)
        model.draft = "Synthetic step"; model.selectedIntent = .attemptStep; model.send(); await settle(model)
        XCTAssertEqual(model.pending?.kind, .turn)
        XCTAssertEqual(model.pending?.intent, .attemptStep)
        model.suspend()
        let restored = TutorViewModel(owner: owner, problem: problem, service: api, store: pending, network: TutorTestNetwork(), ownsAccount: { true })
        restored.resume(); await settle(restored); restored.retry(); await settle(restored)
        XCTAssertEqual(restored.messages.count, 1); XCTAssertEqual(restored.turns.count, 1)
        let turn = try XCTUnwrap(restored.turns.first)
        XCTAssertEqual(turn.role, "TUTOR"); XCTAssertTrue(turn.uncertain)
        XCTAssertEqual(turn.correctness, "UNKNOWN"); XCTAssertEqual(turn.referenceStatus, "UNAVAILABLE")
        XCTAssertEqual(turn.learnerMessageId, restored.messages.first?.id)
        XCTAssertEqual(restored.phase, .elicitConcept); XCTAssertFalse(restored.busy)
    }
    func testUnavailablePolicyKeepsDraftAndStopsLoading() async {
        let model = TutorViewModel(owner: UUID(), problem: UUID(), service: FixtureTutorAPI(available: false), store: store(), network: TutorTestNetwork(), ownsAccount: { true })
        model.resume(); await settle(model); model.start(); await settle(model)
        model.draft = "Synthetic question"; model.send(); await settle(model)
        XCTAssertEqual(model.errorKey, "tutor.unavailable"); XCTAssertFalse(model.busy)
        XCTAssertNil(model.pending); XCTAssertEqual(model.draft, "Synthetic question")
        XCTAssertTrue(model.turns.isEmpty); XCTAssertTrue(model.messages.isEmpty)
    }
    func testLegacyPendingAppendIsNeverReinterpretedAsSocraticTurn() async throws {
        let api = FixtureTutorAPI(), pending = store(), owner = UUID(), problem = UUID()
        let session = try await api.create(problem: problem, key: "legacy-session")
        try pending.save(TutorPending(key: "legacy-message", session: session.id, content: "Old pending text"), owner: owner, problem: problem)
        let model = TutorViewModel(owner: owner, problem: problem, service: api, store: pending, network: TutorTestNetwork(), ownsAccount: { true })
        model.resume(); await settle(model); model.retry(); await settle(model)
        XCTAssertEqual(model.messages.count, 1); XCTAssertTrue(model.turns.isEmpty)
        XCTAssertNil(model.pending)
    }
    func testChallengeStopsFurtherSubmissionAndPreservesReviewHistory() async {
        let model = TutorViewModel(owner: UUID(), problem: UUID(), service: FixtureTutorAPI(), store: store(), network: TutorTestNetwork(), ownsAccount: { true })
        model.resume(); await settle(model); model.start(); await settle(model)
        model.draft = "Synthetic challenge"; model.selectedIntent = .challenge; model.send(); await settle(model)
        XCTAssertEqual(model.phase, .needsReview)
        model.draft = "Try again"; XCTAssertFalse(model.canSend)
        model.refresh(); await settle(model); XCTAssertEqual(model.phase, .needsReview)
    }
    func testSocraticHistorySurvivesStaleSourceAndClose() async throws {
        let api = FixtureTutorAPI(), problem = UUID()
        let model = TutorViewModel(owner: UUID(), problem: problem, service: api, store: store(), network: TutorTestNetwork(), ownsAccount: { true })
        model.resume(); await settle(model); model.start(); await settle(model)
        model.draft = "Synthetic step"; model.send(); await settle(model)
        let replyID = try XCTUnwrap(model.turns.first?.replyId)
        await api.markStale(); model.refresh(); await settle(model)
        XCTAssertEqual(model.session?.sourceCurrent, false); XCTAssertEqual(model.turns.first?.replyId, replyID)
        model.draft = "Another step"; XCTAssertFalse(model.canSend)
        model.close(); await settle(model)
        XCTAssertEqual(model.session?.status, .closed); XCTAssertEqual(model.turns.first?.replyId, replyID)
    }
    func testTurnWireContractAndSchemaAuthorityGuards() async throws {
        let fixture = FixtureTutorAPI(), problem = UUID()
        let session = try await fixture.create(problem: problem, key: "fixture-create")
        let expected = try await fixture.submitTurn(problem: problem, session: session.id, key: "fixture-turn", content: "Synthetic", intent: .unknown)
        let data = try JSONEncoder().encode(expected)
        let transport = TutorTestTransport(data: data)
        let api = TutorAPI(client: APIClient(baseURL: URL(string: "https://example.invalid")!, session: transport, authTokenProvider: InMemorySecureStorage(), correlationContext: CorrelationContext()))
        let reply = try await api.submitTurn(problem: problem, session: session.id, key: "durable-key", content: "Synthetic", intent: .unknown)
        XCTAssertEqual(reply, expected)
        let captured = await transport.lastRequest()
        let request = try XCTUnwrap(captured)
        XCTAssertTrue(request.url!.path.hasSuffix("/turns")); XCTAssertEqual(request.httpMethod, "POST")
        XCTAssertEqual(request.timeoutInterval, 15)
        XCTAssertEqual(request.value(forHTTPHeaderField: "Idempotency-Key"), "durable-key")
        let body = try JSONSerialization.jsonObject(with: XCTUnwrap(request.httpBody)) as! [String: String]
        XCTAssertEqual(body, ["content": "Synthetic", "intent": "UNKNOWN"])
        for field in ["schemaVersion", "role", "correctness", "referenceStatus", "provenance", "policyVersion"] {
            var invalid = try JSONSerialization.jsonObject(with: data) as! [String: Any]
            invalid[field] = "FORGED"
            let decoded = try JSONDecoder().decode(TutorTurn.self, from: JSONSerialization.data(withJSONObject: invalid))
            XCTAssertThrowsError(try decoded.validated(session: session.id))
        }
    }
    func testPendingResponseCannotPublishAcrossLogoutOrCancellation() async throws {
        let delayed = DelayedTutorTestAPI(), pending = store()
        var owns = true
        let model = TutorViewModel(owner: UUID(), problem: UUID(), service: delayed, store: pending, network: TutorTestNetwork(), ownsAccount: { owns })
        model.resume(); await settle(model); model.start(); await settle(model)
        model.draft = "Synthetic pending"; model.send()
        for _ in 0..<200 { if await delayed.waiting { break }; await Task.yield() }
        let waiting = await delayed.waiting
        XCTAssertTrue(waiting); XCTAssertTrue(model.busy); XCTAssertNotNil(model.pending)
        owns = false; model.invalidate()
        await delayed.release()
        for _ in 0..<20 { await Task.yield() }
        XCTAssertTrue(model.turns.isEmpty); XCTAssertNil(model.session); XCTAssertFalse(model.busy)
    }
    func testHintsProgressOnlyFromServerAndDuplicateTapDoesNotSkip() async throws {
        let api = FixtureTutorAPI(), problem = UUID()
        let model = TutorViewModel(owner: UUID(), problem: problem, service: api, store: store(), network: TutorTestNetwork(), ownsAccount: { true })
        model.resume(); await settle(model); model.start(); await settle(model)
        XCTAssertEqual(model.hintHistory?.nextLevel, .h1)
        model.draft = "Keep this draft"
        model.requestHint(); model.requestHint(); await settle(model)
        XCTAssertEqual(model.hintHistory?.hints.count, 1); XCTAssertEqual(model.hintHistory?.nextLevel, .h2)
        XCTAssertEqual(model.phase, .minimalSupport); XCTAssertEqual(model.draft, "Keep this draft")
        model.requestHint(); await settle(model); model.requestHint(); await settle(model)
        XCTAssertEqual(model.hintHistory?.currentLevel, .h3); XCTAssertNil(model.hintHistory?.nextLevel)
        XCTAssertFalse(model.canRequestHint); XCTAssertTrue(model.canRequestReveal)
        XCTAssertTrue(model.messages.isEmpty)
        model.refresh(); await settle(model)
        XCTAssertEqual(model.hintHistory?.hints.map(\.level), [.h1,.h2,.h3])
    }
    func testHintLostAcknowledgementRestoresSameKeyAfterRestart() async throws {
        let api = FixtureTutorAPI(failFirst: true), pending = store(), owner = UUID(), problem = UUID()
        let model = TutorViewModel(owner: owner, problem: problem, service: api, store: pending, network: TutorTestNetwork(), ownsAccount: { true })
        model.resume(); await settle(model); model.start(); await settle(model)
        model.requestHint(); await settle(model)
        let key = try XCTUnwrap(model.pending?.key)
        XCTAssertEqual(model.pending?.kind, .hint); XCTAssertEqual(model.pending?.level, .h1)
        XCTAssertFalse(model.canRequestHint); model.suspend()
        let restored = TutorViewModel(owner: owner, problem: problem, service: api, store: pending, network: TutorTestNetwork(), ownsAccount: { true })
        restored.resume(); await settle(restored)
        XCTAssertEqual(restored.pending?.key, key); restored.retry(); await settle(restored)
        XCTAssertEqual(restored.hintHistory?.hints.count, 1); XCTAssertNil(restored.pending)
        XCTAssertNil(try pending.load(owner: owner, problem: problem))
    }
    func testHintOfflineClosedStaleAndRevealDenialAreHonest() async throws {
        let api = FixtureTutorAPI(), network = TutorTestNetwork(), problem = UUID()
        let model = TutorViewModel(owner: UUID(), problem: problem, service: api, store: store(), network: network, ownsAccount: { true })
        model.resume(); await settle(model); model.start(); await settle(model)
        network.isReachable = false; model.requestHint()
        XCTAssertEqual(model.errorKey, "tutor.offline"); XCTAssertNil(model.pending)
        network.isReachable = true; model.refresh(); await settle(model)
        model.draft = "Preserve while revealing"; model.confirmReveal(); await settle(model)
        XCTAssertEqual(model.errorKey, "tutor.reveal.unavailable"); XCTAssertNil(model.pending)
        XCTAssertEqual(model.draft, "Preserve while revealing"); XCTAssertTrue(model.hintHistory?.hints.isEmpty == true)
        await api.markStale(); model.refresh(); await settle(model)
        XCTAssertFalse(model.canRequestHint); XCTAssertFalse(model.canRequestReveal)
        model.close(); await settle(model); XCTAssertFalse(model.canRequestHint)
    }
    func testHintWireAndAssistanceContractRejectForgedValues() async throws {
        let api = FixtureTutorAPI(), problem = UUID()
        let session = try await api.create(problem: problem, key: "create-fixture")
        let expected = try await api.requestHint(problem: problem, session: session.id, key: "hint-fixture", level: .h1)
        let data = try JSONEncoder().encode(expected), transport = TutorTestTransport(data: try JSONEncoder().encode(expected))
        let live = TutorAPI(client: APIClient(baseURL: URL(string: "https://example.invalid")!, session: transport, authTokenProvider: InMemorySecureStorage(), correlationContext: CorrelationContext()))
        let result = try await live.requestHint(problem: problem, session: session.id, key: "hint-wire-key", level: .h1)
        XCTAssertEqual(result, expected)
        let captured = await transport.lastRequest(); let request = try XCTUnwrap(captured)
        XCTAssertTrue(request.url!.path.hasSuffix("/hints")); XCTAssertEqual(request.timeoutInterval, 15)
        XCTAssertEqual(request.value(forHTTPHeaderField: "Idempotency-Key"), "hint-wire-key")
        XCTAssertEqual(try JSONSerialization.jsonObject(with: XCTUnwrap(request.httpBody)) as? [String:String], ["level":"H1"])
        for field in ["schemaVersion","policyVersion","costPolicyVersion","assistance","masteryEvidence","inference","commercial","referenceStatus"] {
            var invalid = try JSONSerialization.jsonObject(with: data) as! [String:Any]; invalid[field] = "FORGED"
            let decoded = try JSONDecoder().decode(TutorHint.self, from: JSONSerialization.data(withJSONObject: invalid))
            XCTAssertThrowsError(try decoded.validated(session: session.id))
        }
        let history = try await api.hints(problem: problem, session: session.id)
        XCTAssertEqual(try JSONDecoder().decode(TutorHintHistory.self, from: JSONEncoder().encode(history)).validated(session: session.id), history)
        var invalidHistory = try JSONSerialization.jsonObject(with: JSONEncoder().encode(history)) as! [String:Any]
        invalidHistory["nextLevel"] = "H3"
        let inconsistent = try JSONDecoder().decode(TutorHintHistory.self, from: JSONSerialization.data(withJSONObject: invalidHistory))
        XCTAssertThrowsError(try inconsistent.validated(session: session.id))
    }
    func testHintPendingCannotCarryForgedContentAndIsClearedWithAccountStore() throws {
        let pending = store(), owner = UUID(), problem = UUID()
        try pending.save(TutorPending(key: "pending-hint-key", session: UUID(), content: "forged", kind: .hint, level: .h3), owner: owner, problem: problem)
        XCTAssertThrowsError(try pending.load(owner: owner, problem: problem))
        try pending.clearAll(); XCTAssertNil(try pending.load(owner: owner, problem: problem))
    }
    func testHintResponseCannotPublishAfterAccountInvalidation() async throws {
        let delayed = DelayedTutorTestAPI(), pending = store()
        let model = TutorViewModel(owner: UUID(), problem: UUID(), service: delayed, store: pending, network: TutorTestNetwork(), ownsAccount: { true })
        model.resume(); await settle(model); model.start(); await settle(model)
        model.requestHint()
        for _ in 0..<200 { if await delayed.waiting { break }; await Task.yield() }
        let waiting = await delayed.waiting; XCTAssertTrue(waiting)
        model.invalidate(); await delayed.release()
        for _ in 0..<20 { await Task.yield() }
        XCTAssertNil(model.hintHistory); XCTAssertNil(model.session); XCTAssertFalse(model.busy)
    }
    func testAssistantAndFutureSchemasAreRejected() throws {
        let session = UUID()
        let message = TutorMessage(schemaVersion: "tutor-message-v1", id: UUID(), tutorSessionId: session, role: "ASSISTANT", sequenceNumber: 1, content: "Synthetic", createdAt: Date().ISO8601Format())
        XCTAssertThrowsError(try message.validated(session: session))
        let future = TutorMessage(schemaVersion: "future", id: UUID(), tutorSessionId: session, role: "LEARNER", sequenceNumber: 1, content: "Synthetic", createdAt: Date().ISO8601Format())
        XCTAssertThrowsError(try future.validated(session: session))
    }
}
@MainActor private final class TutorTestNetwork: NetworkMonitoring { var isReachable = true }
private actor TutorTestTransport: HTTPSession {
    let data: Data
    var requests: [URLRequest] = []
    init(data: Data) { self.data = data }
    func lastRequest() -> URLRequest? { requests.last }
    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        requests.append(request)
        return (data, HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!)
    }
}

private actor DelayedTutorTestAPI: TutorServicing {
    private let base = FixtureTutorAPI()
    private var continuation: CheckedContinuation<Void, Never>?
    var waiting: Bool { continuation != nil }
    func release() { continuation?.resume(); continuation = nil }
    func list(problem: UUID) async throws -> [TutorSession] { try await base.list(problem: problem) }
    func create(problem: UUID, key: String) async throws -> TutorSession { try await base.create(problem: problem, key: key) }
    func read(problem: UUID, session: UUID) async throws -> TutorSession { try await base.read(problem: problem, session: session) }
    func messages(problem: UUID, session: UUID, after: Int) async throws -> [TutorMessage] { try await base.messages(problem: problem, session: session, after: after) }
    func append(problem: UUID, session: UUID, key: String, content: String) async throws -> TutorMessage { try await base.append(problem: problem, session: session, key: key, content: content) }
    func close(problem: UUID, session: UUID) async throws -> TutorSession { try await base.close(problem: problem, session: session) }
    func hints(problem: UUID, session: UUID) async throws -> TutorHintHistory { try await base.hints(problem: problem, session: session) }
    func requestHint(problem: UUID, session: UUID, key: String, level: TutorHintLevel) async throws -> TutorHint {
        let result = try await base.requestHint(problem: problem, session: session, key: key, level: level)
        await withCheckedContinuation { continuation = $0 }
        return result
    }
    func reveal(problem: UUID, session: UUID, key: String) async throws { try await base.reveal(problem: problem, session: session, key: key) }
    func turns(problem: UUID, session: UUID, after: Int) async throws -> [TutorTurn] { try await base.turns(problem: problem, session: session, after: after) }
    func submitTurn(problem: UUID, session: UUID, key: String, content: String, intent: TutorIntent) async throws -> TutorTurn {
        let result = try await base.submitTurn(problem: problem, session: session, key: key, content: content, intent: intent)
        await withCheckedContinuation { continuation = $0 }
        return result
    }
}
