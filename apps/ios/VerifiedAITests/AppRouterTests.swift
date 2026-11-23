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
        XCTAssertEqual(router.route(for: try XCTUnwrap(URL(string: "verified-ai://study-plan/today"))), .studyPlanToday)
        XCTAssertEqual(router.route(for: try XCTUnwrap(URL(string: "verified-ai://spaced-review"))), .spacedReview)
        XCTAssertEqual(router.route(for: try XCTUnwrap(URL(string: "verified-ai://exam-readiness"))), .examReadiness)
        XCTAssertEqual(router.route(for: try XCTUnwrap(URL(string: "verified-ai://subscription/manage"))), .subscriptionManage)
        XCTAssertEqual(router.route(for: try XCTUnwrap(URL(string: "verified-ai://account/security"))), .accountSecurity)
        XCTAssertNil(router.route(for: try XCTUnwrap(URL(string: "https://example.com"))))
        XCTAssertNil(router.route(for: try XCTUnwrap(URL(string: "verified-ai://study-plan/../admin"))))
    }
}

