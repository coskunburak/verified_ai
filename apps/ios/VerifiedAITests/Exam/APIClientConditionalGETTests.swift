import XCTest
@testable import VerifiedAI

private final class ConditionalSession: HTTPSession {
    let status: Int
    init(_ status: Int) { self.status = status }
    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        XCTAssertEqual(request.value(forHTTPHeaderField: "If-None-Match"), "\"synthetic\"")
        return (Data(), HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: ["ETag": "\"synthetic\""])!)
    }
}
final class APIClientConditionalGETTests: XCTestCase {
    func test304DoesNotDecodeEmptyBody() async throws {
        let client = APIClient(baseURL: URL(string: "https://synthetic.invalid")!, session: ConditionalSession(304),
            authTokenProvider: InMemorySecureStorage(), correlationContext: CorrelationContext())
        let result = try await client.sendConditional(HTTPRequest<EmptyHTTPBody>(endpoint: .platformHealth, ifNoneMatch: "\"synthetic\""))
        guard case .notModified(let tag) = result else { return XCTFail("Expected empty 304") }
        XCTAssertEqual(tag, "\"synthetic\"")
    }
    func test403WithEmptyBodyRemainsAuthorizationFailure() async throws {
        let client = APIClient(baseURL: URL(string: "https://synthetic.invalid")!, session: ConditionalSession(403),
            authTokenProvider: InMemorySecureStorage(), correlationContext: CorrelationContext())
        do {
            _ = try await client.sendConditional(HTTPRequest<EmptyHTTPBody>(endpoint: .platformHealth, ifNoneMatch: "\"synthetic\""))
            XCTFail("Expected 403")
        } catch { XCTAssertEqual(error as? NetworkError, .httpStatus(403)) }
    }
}
