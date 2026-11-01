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
    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        requests.append(request)
        return (data, HTTPURLResponse(url: request.url!, statusCode: 200, httpVersion: nil, headerFields: nil)!)
    }
}
