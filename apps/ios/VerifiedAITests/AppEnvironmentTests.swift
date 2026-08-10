import XCTest
@testable import VerifiedAI

final class AppEnvironmentTests: XCTestCase {
    func testEnvironmentBaseURLsAreStable() {
        XCTAssertEqual(AppEnvironment.development.apiBaseURL.absoluteString, "http://localhost:8080")
        XCTAssertTrue(AppEnvironment.staging.apiBaseURL.absoluteString.contains("staging"))
        XCTAssertTrue(AppEnvironment.production.apiBaseURL.absoluteString.contains("api.verified-ai-learning"))
    }

    func testConfiguredAPIBaseURLOverridesEnvironmentDefault() {
        let url = AppEnvironment.resolvedAPIBaseURL(
            configuredValue: "http://192.168.1.24:8080",
            environment: .development
        )

        XCTAssertEqual(url.absoluteString, "http://192.168.1.24:8080")
    }

    func testInvalidConfiguredAPIBaseURLFallsBackToEnvironmentDefault() {
        let url = AppEnvironment.resolvedAPIBaseURL(
            configuredValue: "not-a-url",
            environment: .staging
        )

        XCTAssertEqual(url, AppEnvironment.staging.apiBaseURL)
    }
}
