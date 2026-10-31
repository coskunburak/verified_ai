import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class PrimarySolveContractTests: XCTestCase {
    func testAllLifecycleStatesDecodeAndOnlyCandidateStatesExposeAnswers() throws {
        for state in SolveState.allCases {
            let job = try FixturePrimarySolveAPI.decode(state: state)
            XCTAssertEqual(job.state, state)
            XCTAssertEqual(job.visibleCandidate != nil, state.showsCandidate)
            XCTAssertFalse(job.verificationNotice.contains("VERIFIED"))
        }
    }
    func testMissingFieldsUnknownStateAndUntrustedVerificationNoticeFailClosed() throws {
        let valid = FixturePrimarySolveAPI.data(state: .generatedUnverified)
        for mutation in ["missing", "unknown", "verified", "lineage", "order"] {
            var json = try XCTUnwrap(JSONSerialization.jsonObject(with: valid) as? [String: Any])
            switch mutation {
            case "missing": json.removeValue(forKey: "jobId")
            case "unknown": json["state"] = "VERIFIED"
            case "verified": json["verificationNotice"] = "100% correct"
            case "lineage": json["problemParseRevision"] = 2
            default:
                var candidate = json["candidate"] as! [String: Any]
                var steps = candidate["steps"] as! [[String: Any]]
                steps[0]["order"] = 2; candidate["steps"] = steps; json["candidate"] = candidate
            }
            let data = try JSONSerialization.data(withJSONObject: json)
            XCTAssertThrowsError(try JSONDecoder().decode(SolveJobSnapshot.self, from: data).validated())
        }
    }
    func testTypedLifecycleUsesExactPathsMethodsAndIdempotencyKeys() async throws {
        let job = UUID(), session = UUID()
        let body = FixturePrimarySolveAPI.data(state: .queued, jobId: job)
        let transport = SolveContractSession(responses: [Data("{\"job\":null}".utf8), body, body, body, body])
        let api = client(transport)
        let current = try await api.current(sessionId: session); XCTAssertNil(current)
        _ = try await api.start(sessionId: session, key: "start_original")
        _ = try await api.read(sessionId: session, jobId: job)
        _ = try await api.retry(sessionId: session, jobId: job, key: "retry_fresh_key")
        _ = try await api.cancel(sessionId: session, jobId: job)
        let requests = await transport.requests
        let base = "/api/v1/problem-sessions/\(session.uuidString)/solves"
        XCTAssertEqual(requests.map { $0.url!.path }, [base+"/current", base, base+"/\(job)", base+"/\(job)/retry", base+"/\(job)/cancel"])
        XCTAssertEqual(requests.map(\.httpMethod), ["GET", "POST", "GET", "POST", "POST"])
        XCTAssertEqual(requests[1].value(forHTTPHeaderField: "Idempotency-Key"), "start_original")
        XCTAssertEqual(requests[3].value(forHTTPHeaderField: "Idempotency-Key"), "retry_fresh_key")
        XCTAssertNil(requests[4].value(forHTTPHeaderField: "Idempotency-Key"))
    }
    func testCurrentLookupMissingJobFieldDoesNotBecomePermissionToStart() async {
        do { _ = try await client(SolveContractSession(responses: [Data("{}".utf8)])).current(sessionId: UUID()); XCTFail() }
        catch { XCTAssertEqual(PrimarySolveViewModel.message(for: error), "solve.error.contract") }
    }
    func testStartServerErrorsDoNotReplayMutatingRequests() async throws {
        for (code, status, key) in [("AUTH_TOKEN_EXPIRED",401,"auth"),("SOLVE_DISABLED",409,"disabled"),("SOLVE_STATE_CONFLICT",409,"conflict")] {
            let json = Data("{\"type\":\"test\",\"title\":\"private backend detail\",\"status\":\(status),\"code\":\"\(code)\"}".utf8)
            let transport = SolveContractSession(responses: [json], status: status)
            do { _ = try await client(transport).start(sessionId: UUID(), key: "stable_request"); XCTFail("Expected rejection") }
            catch { XCTAssertEqual(PrimarySolveViewModel.message(for: error), "solve.error." + key) }
            let calls = await transport.requests
            XCTAssertEqual(calls.count, 1)
        }
    }
    func testErrorCodesMapWithoutAssumingHTTPRetryPermission() {
        for (code,key) in [("SOLVE_DISABLED","disabled"),("SOLVE_SOURCE_NOT_READY","source"),("SOLVE_STATE_CONFLICT","conflict"),
                          ("SOLVE_RETRY_LIMIT","limit"),("PROBLEM_UNSUPPORTED","unsupported"),("IDEMPOTENCY_KEY_REUSED","conflict"),("AUTH_TOKEN_EXPIRED","auth")] {
            let error = NetworkError.server(problem: .init(type: "test", title: "raw secret", status: 409, code: code, traceId: nil, details: nil))
            XCTAssertEqual(PrimarySolveViewModel.message(for: error), "solve.error."+key)
        }
    }
    func testVerificationFixturesHaveExplicitScopeAndNeverChangeGenerationState() throws {
        let job = try FixturePrimarySolveAPI.decode(state: .generatedUnverified)
        for scenario in ["VERIFIED", "PARTIALLY_VERIFIED", "UNVERIFIED", "CONTRADICTION"] {
            let decision = try FixturePrimarySolveAPI.decisionFixture(scenario).validated(for: job)
            XCTAssertEqual(decision.hasConflict, scenario == "CONTRADICTION")
            XCTAssertEqual(job.state, .generatedUnverified)
            XCTAssertTrue(decision.uncertainty.contains("EXPLANATION_STEPS_UNCHECKED"))
            XCTAssertFalse(decision.status.titleKey.isEmpty)
            XCTAssertFalse(decision.recovery.titleKey.isEmpty)
        }
    }
    func testVerificationTransportIsReadOnlyAndMissingDecisionFieldFailsClosed() async throws {
        let session = UUID(), job = UUID()
        let transport = SolveContractSession(responses: [Data("{\"decision\":null}".utf8)])
        let result = try await client(transport).verification(sessionId: session, jobId: job)
        XCTAssertNil(result)
        let calls = await transport.requests
        XCTAssertEqual(calls.count, 1); XCTAssertEqual(calls[0].httpMethod, "GET")
        XCTAssertEqual(calls[0].url?.path, "/api/v1/problem-sessions/\(session)/solves/\(job)/verification")
        do {
            _ = try await client(SolveContractSession(responses: [Data("{}".utf8)])).verification(sessionId: session, jobId: job)
            XCTFail("Missing decision must not silently decode as absent")
        } catch { XCTAssertEqual(PrimarySolveViewModel.message(for: error), "solve.error.contract") }
    }
    func testSharedDecisionFixturesAndUnknownContractValues() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let folder = root.appendingPathComponent("packages/test-fixtures/verification/decision-v1")
        let job = try FixturePrimarySolveAPI.decode(state: .generatedUnverified)
        for name in ["verified", "partial", "unverified", "contradiction", "calculus-derivative", "calculus-integral", "calculus-limit"] {
            let data = try Data(contentsOf: folder.appendingPathComponent(name + ".json"))
            _ = try JSONDecoder().decode(VerificationDecisionSnapshot.self, from: data).validated(for: job)
        }
        let data = try Data(contentsOf: folder.appendingPathComponent("verified.json"))
        for field in ["status", "policyVersion", "scope", "recovery", "problemParseId", "reasons"] {
            var json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
            if field == "reasons" { json[field] = ["UNKNOWN"] } else { json[field] = "UNKNOWN" }
            XCTAssertThrowsError(try JSONDecoder().decode(VerificationDecisionSnapshot.self,
                from: JSONSerialization.data(withJSONObject: json)).validated(for: job))
        }
    }
    func testCalculusDecisionsCannotClaimGenericFinalAnswerOrAnotherCalculusScope() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let folder = root.appendingPathComponent("packages/test-fixtures/verification/decision-v1")
        let job = try FixturePrimarySolveAPI.decode(state: .generatedUnverified)
        for (name, correctScope) in [("calculus-derivative", "OPEN_INTERVAL"),
            ("calculus-integral", "ANTIDERIVATIVE_REPRESENTATIVE"), ("calculus-limit", "PUNCTURED_NEIGHBORHOOD")] {
            let data = try Data(contentsOf: folder.appendingPathComponent(name + ".json"))
            let base = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
            for wrong in ["FINAL_ANSWER", "OPEN_INTERVAL", "ANTIDERIVATIVE_REPRESENTATIVE", "PUNCTURED_NEIGHBORHOOD"] where wrong != correctScope {
                var json = base; json["scope"] = wrong
                XCTAssertThrowsError(try JSONDecoder().decode(VerificationDecisionSnapshot.self,
                    from: JSONSerialization.data(withJSONObject: json)).validated(for: job))
            }
            var mixed = base; mixed["unchecked"] = ["DOMAIN_CONSTRAINT_CHECK"]
            XCTAssertThrowsError(try JSONDecoder().decode(VerificationDecisionSnapshot.self,
                from: JSONSerialization.data(withJSONObject: mixed)).validated(for: job))
        }
    }
    private func client(_ transport: SolveContractSession) -> PrimarySolveAPI {
        PrimarySolveAPI(client: APIClient(baseURL: URL(string: "https://example.invalid")!, session: transport,
            authTokenProvider: InMemorySecureStorage(), correlationContext: CorrelationContext()))
    }
}

private actor SolveContractSession: HTTPSession {
    var responses: [Data]
    private(set) var requests: [URLRequest] = []
    let status: Int
    init(responses: [Data], status: Int = 200) { self.responses = responses; self.status = status }
    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        requests.append(request)
        return (responses.removeFirst(), HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!)
    }
}
