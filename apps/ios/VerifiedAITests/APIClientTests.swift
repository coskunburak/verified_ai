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
}

