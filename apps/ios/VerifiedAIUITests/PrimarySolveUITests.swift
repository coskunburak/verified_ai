import XCTest

@MainActor
final class PrimarySolveUITests: XCTestCase {
    override func setUpWithError() throws {
        try super.setUpWithError()
        continueAfterFailure = false
    }

    private func launch(_ scenario: String = "GENERATED_UNVERIFIED", reset: Bool = true, extra: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "--ui-testing-authenticated", "--solve-fixture", "--solve-scenario=" + scenario]
            + (reset ? ["--solve-reset"] : []) + extra
        app.launch()
        return app
    }
    private func openSolve(_ app: XCUIApplication) {
        let row = app.buttons["history.session.00000000-0000-0000-0000-000000005401"]
        XCTAssertTrue(row.waitForExistence(timeout: 6))
        tapVisible(row, in: app)
        let entry = app.buttons["history.solve"]
        XCTAssertTrue(entry.waitForExistence(timeout: 4)); tapVisible(entry, in: app)
    }
    private func start(_ app: XCUIApplication) {
        let button = app.buttons["solve.start"]
        XCTAssertTrue(button.waitForExistence(timeout: 4)); tapVisible(button, in: app)
    }
    private func tapVisible(_ element: XCUIElement, in app: XCUIApplication) {
        for _ in 0..<6 { if element.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(element.isHittable); element.tap()
    }
    func testHistoryQueuedRunningUnverifiedStepsAndReopen() {
        let app = launch(); openSolve(app); start(app)
        XCTAssertTrue(app.descendants(matching: .any)["solve.status.QUEUED"].waitForExistence(timeout: 2))
        XCTAssertTrue(app.descendants(matching: .any)["solve.status.RUNNING"].waitForExistence(timeout: 4))
        XCTAssertTrue(app.descendants(matching: .any)["solve.unverified"].waitForExistence(timeout: 8))
        XCTAssertTrue(app.descendants(matching: .any)["solve.answer"].exists)
        XCTAssertTrue(app.descendants(matching: .any)["solve.fixture"].exists)
        app.buttons["solve.close"].tap()
        tapVisible(app.buttons["history.solve"], in: app)
        XCTAssertTrue(app.descendants(matching: .any)["solve.unverified"].waitForExistence(timeout: 4))
        XCTAssertFalse(app.buttons["solve.start"].exists)
    }
    func testReviewRequiredDisplaysUncertainty() {
        let app = launch("REVIEW_REQUIRED"); openSolve(app); start(app)
        XCTAssertTrue(app.descendants(matching: .any)["solve.status.REVIEW_REQUIRED"].waitForExistence(timeout: 10))
        app.swipeUp()
        XCTAssertTrue(app.otherElements["solve.uncertainty"].exists || app.descendants(matching: .any)["solve.uncertainty"].exists)
    }
    func testRetryRequiresUserAction() {
        let app = launch("RETRYABLE_FAILURE"); openSolve(app); start(app)
        let retry = app.buttons["solve.retry"]
        XCTAssertTrue(retry.waitForExistence(timeout: 10)); tapVisible(retry, in: app)
        XCTAssertTrue(app.descendants(matching: .any)["solve.unverified"].waitForExistence(timeout: 10))
    }
    func testStaleHidesAnswerAndUnknownOutcomeHasNoRetry() {
        let app = launch("STALE"); openSolve(app); start(app)
        XCTAssertTrue(app.descendants(matching: .any)["solve.status.STALE"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.descendants(matching: .any)["solve.answer"].exists)
        app.terminate()
        let unknown = launch("OUTCOME_UNKNOWN"); openSolve(unknown); start(unknown)
        XCTAssertTrue(unknown.descendants(matching: .any)["solve.status.OUTCOME_UNKNOWN"].waitForExistence(timeout: 10))
        XCTAssertFalse(unknown.buttons["solve.retry"].exists)
    }
    func testRelaunchRecoversJobWithoutNewStart() {
        let app = launch(); openSolve(app); start(app)
        XCTAssertTrue(app.descendants(matching: .any)["solve.unverified"].waitForExistence(timeout: 10)); app.terminate()
        let restored = launch(reset: false); openSolve(restored)
        XCTAssertTrue(restored.descendants(matching: .any)["solve.unverified"].waitForExistence(timeout: 5))
        XCTAssertFalse(restored.buttons["solve.start"].exists)
    }
    func testMalformedResponseHasNoFakeAnswer() {
        let app = launch("MALFORMED"); openSolve(app); start(app)
        XCTAssertTrue(app.descendants(matching: .any)["solve.error"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.descendants(matching: .any)["solve.answer"].exists)
    }
    func testTurkishLargeTextKeepsNoticeAndControlsAccessible() {
        let app = launch(extra: ["-AppleLanguages", "(tr)", "-AppleLocale", "tr_TR", "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"])
        openSolve(app); start(app)
        XCTAssertTrue(app.descendants(matching: .any)["solve.unverified"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["solve.close"].isHittable)
        // VoiceOver order, Reduce Motion and long wrapping additionally require the owner manual gate.
    }
}
