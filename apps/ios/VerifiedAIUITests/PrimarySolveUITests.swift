import Foundation
import XCTest

@MainActor
final class PrimarySolveUITests: XCTestCase {
    private let fixtureScope = UUID().uuidString
    private var application: XCUIApplication?
    private var checkpoint = "setup"

    override func setUpWithError() throws {
        try super.setUpWithError()
        continueAfterFailure = false
    }
    private func launch(_ scenario: String = "GENERATED_UNVERIFIED", reset: Bool = true, extra: [String] = []) -> XCUIApplication {
        if application == nil {
            // Register once per test, from the MainActor UI context. XCTest executes its
            // synchronous teardown blocks on the main thread, including after a failure.
            // Read the latest app here so deliberate relaunches share the same cleanup.
            addTeardownBlock { [self] in
                if let app = application, (testRun?.failureCount ?? 0) > 0 {
                    captureDiagnostics(app, reason: "teardown after \(checkpoint)")
                }
                application?.terminate()
                application = nil
            }
        }
        application?.terminate()
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "--ui-testing-authenticated", "--solve-fixture",
            "--solve-fixture-scope=" + fixtureScope, "--solve-scenario=" + scenario]
            + (reset ? ["--solve-reset"] : []) + extra
        application = app
        phase("launch \(scenario), reset=\(reset)")
        app.launch()
        return app
    }
    private func openSolve(_ app: XCUIApplication) {
        let row = app.buttons["history.session.00000000-0000-0000-0000-000000005401"]
        requireExists(row, in: app, timeout: 6, phase: "history session ready")
        tapVisible(row, in: app)
        let entry = app.buttons["history.solve"]
        requireExists(entry, in: app, timeout: 4, phase: "history solve entry ready")
        tapVisible(entry, in: app)
    }
    private func start(_ app: XCUIApplication) {
        let button = app.buttons["solve.start"]
        requireExists(button, in: app, timeout: 4, phase: "authoritative current lookup allows start")
        tapVisible(button, in: app)
    }
    private func tapVisible(_ element: XCUIElement, in app: XCUIApplication,
                            file: StaticString = #filePath, line: UInt = #line) {
        // Resolving identifier before existence would itself fail during a navigation transition.
        requireExists(element, in: app, timeout: 4, phase: "tap requested control", file: file, line: line)
        guard element.exists else { return }
        phase("tap \(element.identifier)")
        for _ in 0..<6 { if element.isHittable { break }; app.swipeUp() }
        let ready = element.isHittable && element.isEnabled
        if !ready { captureDiagnostics(app, reason: "control not hittable/enabled: \(element.identifier)") }
        XCTAssertTrue(ready, "[\(checkpoint)] Expected enabled, hittable control \(element.identifier)", file: file, line: line)
        guard ready else { return }
        element.tap()
    }
    private func phase(_ name: String) {
        checkpoint = name
        print("PRIMARY_SOLVE_UI checkpoint: \(name)")
    }
    private func requireExists(_ element: XCUIElement, in app: XCUIApplication, timeout: TimeInterval,
                               phase name: String, file: StaticString = #filePath, line: UInt = #line) {
        phase(name)
        let exists = element.waitForExistence(timeout: timeout)
        if !exists { captureDiagnostics(app, reason: "missing expected accessibility element at \(name)") }
        XCTAssertTrue(exists, "[\(name)] Expected accessibility element to exist", file: file, line: line)
    }
    private func captureDiagnostics(_ app: XCUIApplication, reason: String) {
        print("PRIMARY_SOLVE_UI failure: \(reason); appState=\(app.state.rawValue)")
        let description = app.state == .notRunning ? "Application is not running" : app.debugDescription
        let hierarchy = XCTAttachment(string: "Checkpoint: \(checkpoint)\nReason: \(reason)\nArguments: \(app.launchArguments)\n\(description)")
        hierarchy.name = "PrimarySolve UI hierarchy — \(checkpoint)"
        hierarchy.lifetime = .keepAlways
        add(hierarchy)
        let screenshot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        screenshot.name = "PrimarySolve screenshot — \(checkpoint)"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }
    func testHistoryQueuedRunningUnverifiedStepsAndReopen() {
        // Acknowledgements drive the synthetic server, never the ViewModel snapshot. The real poll
        // still has to deliver each response. XCTest cannot race a two/three-second display window.
        let app = launch(extra: ["--solve-controlled-progression"]); openSolve(app); start(app)
        requireExists(app.descendants(matching: .any)["solve.status.QUEUED"], in: app, timeout: 2, phase: "QUEUED rendered")
        tapVisible(app.buttons["solve.fixture.advance.QUEUED"], in: app)
        requireExists(app.descendants(matching: .any)["solve.status.RUNNING"], in: app, timeout: 4, phase: "RUNNING rendered by poll")
        tapVisible(app.buttons["solve.fixture.advance.RUNNING"], in: app)
        requireExists(app.descendants(matching: .any)["solve.unverified"], in: app, timeout: 8, phase: "unverified candidate rendered")
        XCTAssertTrue(app.descendants(matching: .any)["solve.answer"].exists)
        XCTAssertTrue(app.descendants(matching: .any)["solve.fixture"].exists)
        for order in 1...2 {
            let step = app.descendants(matching: .any)["solve.step.\(order)"]
            for _ in 0..<6 { if step.exists { break }; app.swipeUp() }
            requireExists(step, in: app, timeout: 4, phase: "solution step \(order) rendered")
        }
        tapVisible(app.buttons["solve.close"], in: app)
        requireExists(app.buttons["history.solve"], in: app, timeout: 4, phase: "history detail restored after close")
        tapVisible(app.buttons["history.solve"], in: app)
        requireExists(app.descendants(matching: .any)["solve.unverified"], in: app, timeout: 4, phase: "persisted candidate reopened")
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
