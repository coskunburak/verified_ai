import XCTest
@testable import VerifiedAI

@MainActor
final class AppRouterTests: XCTestCase {
    func testRouterDefaultsToHomeAndNavigatesDeterministically() {
        let router = AppRouter()
        XCTAssertEqual(router.currentRoute, .home)

        router.navigate(to: .platformHealth)
        router.navigate(to: .platformHealth)

        XCTAssertEqual(router.path, [.platformHealth])
    }

    func testDeepLinkRouterMapsKnownLinks() throws {
        let router = DeepLinkRouter()
        XCTAssertEqual(router.route(for: try XCTUnwrap(URL(string: "verified-ai://platform-health"))), .platformHealth)
        XCTAssertNil(router.route(for: try XCTUnwrap(URL(string: "https://example.com"))))
    }
}

