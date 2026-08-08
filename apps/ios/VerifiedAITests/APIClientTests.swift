import Foundation
import XCTest
@testable import VerifiedAI

private struct HealthResponse: Decodable, Equatable {
    let status: String
}

private final class MockSession: HTTPSession {
    let data: Data
    let response: URLResponse

    init(data: Data, response: URLResponse) {
        self.data = data
        self.response = response
    }

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        XCTAssertEqual(request.value(forHTTPHeaderField: "X-Request-Id")?.isEmpty, false)
        return (data, response)
    }
}

final class APIClientTests: XCTestCase {
    func testDecodesSuccessfulHealthResponse() async throws {
        let data = #"{"status":"UP"}"#.data(using: .utf8)!
        let url = URL(string: "http://localhost:8080/api/v1/platform/health")!
        let response = HTTPURLResponse(url: url, statusCode: 200, httpVersion: nil, headerFields: [:])!
        let storage = InMemorySecureStorage()
        let client = APIClient(
            baseURL: URL(string: "http://localhost:8080")!,
            session: MockSession(data: data, response: response),
            authTokenProvider: storage,
            correlationContext: CorrelationContext()
        )

        let result: HTTPResponse<HealthResponse> = try await client.send(HTTPRequest(endpoint: .platformHealth))

        XCTAssertEqual(result.body, HealthResponse(status: "UP"))
    }

    func testProblemDetailsErrorIsRecoverableWhenContractSaysSo() {
        let problem = ProblemDetails(
            type: "https://errors.verified-ai-learning.example/temporary-unavailable",
            title: "Temporary unavailable",
            status: 503,
            code: "TEMPORARY_UNAVAILABLE",
            traceId: "trace",
            details: .init(recoverable: true, userAction: "RETRY")
        )

        XCTAssertTrue(NetworkError.server(problem: problem).recoverable)
    }

    func testTokenExpiredResponseRefreshesAndRetriesEligibleGetOnce() async throws {
        let expiredProblem = #"{"type":"https://errors.verified-ai-learning.example/auth-token-expired","title":"expired","status":401,"code":"AUTH_TOKEN_EXPIRED","traceId":"trace","details":{"recoverable":false,"userAction":"SIGN_IN"}}"#
            .data(using: .utf8)!
        let successData = #"{"status":"UP"}"#.data(using: .utf8)!
        let url = URL(string: "http://localhost:8080/api/v1/platform/health")!
        let expiredResponse = HTTPURLResponse(url: url, statusCode: 401, httpVersion: nil, headerFields: [:])!
        let successResponse = HTTPURLResponse(url: url, statusCode: 200, httpVersion: nil, headerFields: [:])!
        let session = SequencedSession(responses: [
            (expiredProblem, expiredResponse),
            (successData, successResponse)
        ])
        let tokenProvider = RefreshingTokenProvider()
        let client = APIClient(
            baseURL: URL(string: "http://localhost:8080")!,
            session: session,
            authTokenProvider: tokenProvider,
            correlationContext: CorrelationContext()
        )

        let result: HTTPResponse<HealthResponse> = try await client.send(HTTPRequest(endpoint: .platformHealth))

        let requests = await session.requests
        let refreshCount = await tokenProvider.refreshCount
        XCTAssertEqual(result.body, HealthResponse(status: "UP"))
        XCTAssertEqual(refreshCount, 1)
        XCTAssertEqual(requests[0].value(forHTTPHeaderField: "Authorization"), "Bearer old-token")
        XCTAssertEqual(requests[1].value(forHTTPHeaderField: "Authorization"), "Bearer new-token")
    }
}

private actor RefreshingTokenProvider: AuthTokenProvider {
    private var token = "old-token"
    private(set) var refreshCount = 0

    func accessToken() async throws -> String? {
        token
    }

    func refreshAccessToken() async throws -> String? {
        refreshCount += 1
        token = "new-token"
        return token
    }
}

private actor SequencedSession: HTTPSession {
    private var responses: [(Data, URLResponse)]
    private(set) var requests: [URLRequest] = []

    init(responses: [(Data, URLResponse)]) {
        self.responses = responses
    }

    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        requests.append(request)
        return responses.removeFirst()
    }
}
