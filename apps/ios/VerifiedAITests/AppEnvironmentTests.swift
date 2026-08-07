import XCTest
@testable import VerifiedAI

final class AppEnvironmentTests: XCTestCase {
    func testEnvironmentBaseURLsAreStable() {
        XCTAssertEqual(AppEnvironment.development.apiBaseURL.absoluteString, "http://localhost:8080")
        XCTAssertTrue(AppEnvironment.staging.apiBaseURL.absoluteString.contains("staging"))
        XCTAssertTrue(AppEnvironment.production.apiBaseURL.absoluteString.contains("api.verified-ai-learning"))
    }
}

