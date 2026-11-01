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
        let locale = extra.contains("-AppleLanguages") ? [] : ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launchArguments = ["--ui-testing", "--ui-testing-authenticated", "--solve-fixture",
            "--solve-fixture-scope=" + fixtureScope, "--solve-scenario=" + scenario]
            + (reset ? ["--solve-reset"] : []) + locale + extra
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
        if !entry.waitForExistence(timeout: 2), row.exists {
            tapVisible(row, in: app)
        }
        requireExists(entry, in: app, timeout: 4, phase: "history solve entry ready")
        tapVisible(entry, in: app)
        if !app.buttons["solve.close"].waitForExistence(timeout: 2), entry.exists && entry.isHittable {
            tapVisible(entry, in: app)
        }
    }
    private func start(_ app: XCUIApplication) {
        let button = app.buttons["solve.start"]
        requireExists(button, in: app, timeout: 4, phase: "authoritative current lookup allows start")
        tapVisible(button, in: app)
        let consumed = NSPredicate(format: "exists == false")
        let firstDelivery = XCTNSPredicateExpectation(predicate: consumed, object: button)
        if XCTWaiter.wait(for: [firstDelivery], timeout: 2) == .completed { return }
        // A cold navigation transition can expose a hittable button yet swallow the tap.
        // Re-deliver only while the authoritative ready screen and enabled start remain.
        // Never tap again after a queued/running/result/error state replaces that screen.
        if app.staticTexts["solve.ready"].exists && button.exists && button.isEnabled {
            tapVisible(button, in: app)
        }
        let confirmedDelivery = XCTNSPredicateExpectation(predicate: consumed, object: button)
        let delivered = XCTWaiter.wait(for: [confirmedDelivery], timeout: 4) == .completed
        if !delivered { captureDiagnostics(app, reason: "start tap did not leave authoritative ready state") }
        XCTAssertTrue(delivered, "Start must leave the ready screen before awaiting a solver result")
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
    func testServerVerificationStatesAndConflictHaveAccessibleHonestPresentation() {
        for scenario in ["VERIFIED", "PARTIALLY_VERIFIED", "UNVERIFIED", "CONTRADICTION"] {
            let app = launch(scenario); openSolve(app); start(app)
            let status = scenario == "CONTRADICTION" ? "UNVERIFIED" : scenario
            requireExists(app.descendants(matching: .any)["verification.status." + status], in: app,
                timeout: 15, phase: "server verdict rendered separately from generation")
            XCTAssertTrue(app.descendants(matching: .any)["solve.fixture"].exists)
            XCTAssertTrue(app.descendants(matching: .any)["solve.status.GENERATED_UNVERIFIED"].exists)
            XCTAssertTrue(app.descendants(matching: .any)["solve.answer"].exists)
            XCTAssertTrue(app.descendants(matching: .any)["solve.method"].exists)
            XCTAssertTrue(app.descendants(matching: .any)["verification.summary"].exists)
            if scenario == "CONTRADICTION" {
                requireExists(app.descendants(matching: .any)["verification.conflict"], in: app,
                    timeout: 3, phase: "contradiction warning accessible")
                XCTAssertTrue(app.descendants(matching: .any)["verification.contradictions.section"].exists)
            }
        }
    }
    func testVerificationEvidenceProgressiveDisclosureAndDegradedLookupRemainHonest() {
        let partialApp = launch("PARTIALLY_VERIFIED"); openSolve(partialApp); start(partialApp)
        requireExists(partialApp.descendants(matching: .any)["verification.status.PARTIALLY_VERIFIED"], in: partialApp,
            timeout: 15, phase: "partially verified header visible")
        XCTAssertFalse(partialApp.descendants(matching: .any)["verification.status.VERIFIED"].exists)
        let toggle = partialApp.buttons["verification.evidence.toggle"]
        requireExists(toggle, in: partialApp, timeout: 4, phase: "evidence disclosure toggle visible")
        XCTAssertFalse(partialApp.descendants(matching: .any)["verification.evidence.content"].exists)
        tapVisible(toggle, in: partialApp)
        requireExists(partialApp.descendants(matching: .any)["verification.evidence.content"], in: partialApp,
            timeout: 4, phase: "evidence expanded")
        XCTAssertTrue(partialApp.descendants(matching: .any)["verification.checked.section"].exists)
        XCTAssertTrue(partialApp.descendants(matching: .any)["verification.unchecked.section"].exists)
        XCTAssertTrue(partialApp.descendants(matching: .any)["verification.reasons.section"].exists)
        XCTAssertTrue(partialApp.descendants(matching: .any)["verification.limitations.section"].exists)
        tapVisible(toggle, in: partialApp)
        XCTAssertFalse(partialApp.descendants(matching: .any)["verification.evidence.content"].exists)

        let unavailableApp = launch("VERIFICATION_UNAVAILABLE"); openSolve(unavailableApp); start(unavailableApp)
        requireExists(unavailableApp.descendants(matching: .any)["solve.unverified"], in: unavailableApp,
            timeout: 15, phase: "unavailable verification preserves candidate as unverified")
        XCTAssertTrue(unavailableApp.descendants(matching: .any)["solve.answer"].exists)
        XCTAssertTrue(unavailableApp.descendants(matching: .any)["verification.unavailable"].exists)
        XCTAssertTrue(unavailableApp.buttons["verification.refresh"].exists)
        XCTAssertFalse(unavailableApp.descendants(matching: .any)["verification.status.VERIFIED"].exists)
    }
    func testHistoryQueuedRunningUnverifiedStepsAndReopen() {
        // Acknowledgements drive the synthetic server, never the ViewModel snapshot. The real poll
        // still has to deliver each response. XCTest cannot race a two/three-second display window.
        let app = launch(extra: ["--solve-controlled-progression"]); openSolve(app); start(app)
        requireExists(app.descendants(matching: .any)["solve.status.QUEUED"], in: app, timeout: 2, phase: "QUEUED rendered")
        tapVisible(app.scrollViews.buttons["solve.fixture.advance.QUEUED"], in: app)
        requireExists(app.descendants(matching: .any)["solve.status.RUNNING"], in: app, timeout: 4, phase: "RUNNING rendered by poll")
        tapVisible(app.scrollViews.buttons["solve.fixture.advance.RUNNING"], in: app)
        requireExists(app.descendants(matching: .any)["solve.unverified"], in: app, timeout: 8, phase: "unverified candidate rendered")
        XCTAssertTrue(app.descendants(matching: .any)["solve.answer"].exists)
        XCTAssertTrue(app.descendants(matching: .any)["solve.method"].exists)
        XCTAssertTrue(app.descendants(matching: .any)["solve.fixture"].exists)
        for order in 1...2 {
            let step = app.descendants(matching: .any)["solve.step.\(order)"]
            for _ in 0..<6 { if step.exists { break }; app.swipeUp() }
            requireExists(step, in: app, timeout: 4, phase: "solution step \(order) rendered")
            XCTAssertTrue(app.descendants(matching: .any)["solve.step.\(order).category"].exists)
        }
        tapVisible(app.buttons["solve.close"], in: app)
        requireExists(app.buttons["history.solve"], in: app, timeout: 4, phase: "history detail restored after close")
        tapVisible(app.buttons["history.solve"], in: app)
        if !app.buttons["solve.close"].waitForExistence(timeout: 2), app.buttons["history.solve"].exists && app.buttons["history.solve"].isHittable {
            tapVisible(app.buttons["history.solve"], in: app)
        }
        requireExists(app.descendants(matching: .any)["solve.unverified"], in: app, timeout: 4, phase: "persisted candidate reopened")
        XCTAssertFalse(app.buttons["solve.start"].exists)
    }
    func testCalculusProofScopesRemainExplicitAndSeparateFromGeneration() {
        for (scenario, scope) in [("CALCULUS_DERIVATIVE", "OPEN_INTERVAL"),
            ("CALCULUS_INTEGRAL", "ANTIDERIVATIVE_REPRESENTATIVE"), ("CALCULUS_LIMIT", "PUNCTURED_NEIGHBORHOOD")] {
            let app = launch(scenario); openSolve(app); start(app)
            requireExists(app.descendants(matching: .any)["verification.status.VERIFIED"], in: app,
                timeout: 15, phase: "calculus projection verified with bounded scope")
            let disclosure = app.descendants(matching: .any)["verification.scope." + scope]
            for _ in 0..<6 { if disclosure.exists { break }; app.swipeUp() }
            requireExists(disclosure, in: app, timeout: 4, phase: "calculus proof scope disclosed")
            XCTAssertTrue(app.descendants(matching: .any)["solve.fixture"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["verification.scope.FINAL_ANSWER"].exists)
        }
    }
    func testReviewRequiredDisplaysUncertainty() {
        let app = launch("REVIEW_REQUIRED"); openSolve(app); start(app)
        XCTAssertTrue(app.descendants(matching: .any)["solve.status.REVIEW_REQUIRED"].waitForExistence(timeout: 10))
        app.swipeUp()
        XCTAssertTrue(app.otherElements["solve.uncertainty"].exists || app.descendants(matching: .any)["solve.uncertainty"].exists)
        XCTAssertTrue(app.buttons["solve.review"].exists)
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
        XCTAssertTrue(app.buttons["solve.review"].exists)
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
    private func selectMode(_ mode: String, in app: XCUIApplication) {
        let button = app.buttons["explanation.mode." + mode]
        let scroll = app.scrollViews["solve.scroll"]
        requireExists(scroll, in: app, timeout: 4, phase: "solution scroll available for mode selection")
        for _ in 0..<20 {
            if button.exists && button.isHittable { break }
            // Search forward for undiscovered content. Scroll back only when the known
            // control lies above the viewport; an app-wide downward swipe can dismiss a sheet.
            if button.exists && button.frame.midY < scroll.frame.minY {
                scroll.swipeDown()
            } else {
                scroll.swipeUp()
            }
        }
        tapVisible(button, in: app)
        XCTAssertTrue(button.isSelected)
    }

    func testAllExplanationModesDiscloseOriginalContentAndRestoreSavedPreference() {
        let app = launch("PARTIALLY_VERIFIED"); openSolve(app); start(app)
        requireExists(app.descendants(matching: .any)["verification.status.PARTIALLY_VERIFIED"], in: app,
            timeout: 15, phase: "candidate ready for explanation modes")
        selectMode("QUICK", in: app)
        XCTAssertTrue(app.staticTexts["explanation.condensed"].exists)
        XCTAssertFalse(app.descendants(matching: .any)["solve.step.1"].exists)
        tapVisible(app.buttons["explanation.steps.toggle"], in: app)
        XCTAssertTrue(app.descendants(matching: .any)["solve.step.1"].exists)
        selectMode("STANDARD", in: app)
        XCTAssertTrue(app.staticTexts["explanation.content.1"].exists)
        selectMode("DEEP", in: app)
        XCTAssertTrue(app.descendants(matching: .any)["explanation.dependencies.2"].exists)
        selectMode("BEGINNER", in: app)
        XCTAssertTrue(app.staticTexts["explanation.prerequisite"].exists)
        XCTAssertFalse(app.staticTexts["explanation.content.1"].exists)
        tapVisible(app.buttons["explanation.disclosure.1"], in: app)
        requireExists(app.staticTexts["explanation.content.1"], in: app, timeout: 4, phase: "beginner explanation expanded")
        XCTAssertEqual(app.buttons["explanation.disclosure.1"].value as? String, "Expanded")
        tapVisible(app.buttons["explanation.disclosure.1"], in: app)
        XCTAssertFalse(app.staticTexts["explanation.content.1"].exists)
        XCTAssertEqual(app.buttons["explanation.disclosure.1"].value as? String, "Collapsed")
        tapVisible(app.buttons["explanation.disclosure.1"], in: app)
        requireExists(app.staticTexts["explanation.content.1"], in: app, timeout: 4, phase: "beginner explanation reopened")
        XCTAssertTrue(app.descendants(matching: .any)["verification.status.PARTIALLY_VERIFIED"].exists)
        XCTAssertTrue(app.buttons["verification.evidence.toggle"].exists)
        XCTAssertFalse(app.buttons["solve.start"].exists)
        let saved = NSPredicate(format: "label == %@", "Using your saved preference.")
        expectation(for: saved, evaluatedWith: app.staticTexts["explanation.preference.status"])
        waitForExpectations(timeout: 4)
        tapVisible(app.buttons["solve.close"], in: app)
        tapVisible(app.buttons["history.solve"], in: app)
        requireExists(app.buttons["explanation.mode.BEGINNER"], in: app, timeout: 5, phase: "saved mode reopened")
        XCTAssertTrue(app.buttons["explanation.mode.BEGINNER"].isSelected)
    }

    func testPreferenceFailureIsVisibleAndConflictWarningSurvivesEveryMode() {
        let app = launch("CONTRADICTION", extra: ["--profile-save-failure"])
        openSolve(app); start(app)
        requireExists(app.descendants(matching: .any)["verification.conflict"], in: app,
            timeout: 15, phase: "contradiction available")
        for mode in ["QUICK", "DEEP", "BEGINNER", "STANDARD"] {
            selectMode(mode, in: app)
            XCTAssertTrue(app.descendants(matching: .any)["verification.conflict"].exists)
            XCTAssertTrue(app.descendants(matching: .any)["verification.status.UNVERIFIED"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["verification.status.VERIFIED"].exists)
        }
        XCTAssertTrue(app.buttons["explanation.preference.reload"].waitForExistence(timeout: 4))
        XCTAssertTrue(app.staticTexts["explanation.preference.status"].label.contains("temporary"))
        tapVisible(app.buttons["explanation.preference.reload"], in: app)
        // Reload is read-only; the final temporary Standard selection matches the stored profile.
        let saved = NSPredicate(format: "label == %@", "Using your saved preference.")
        expectation(for: saved, evaluatedWith: app.staticTexts["explanation.preference.status"])
        waitForExpectations(timeout: 4)
    }

    func testTurkishLargeTextKeepsNoticeAndControlsAccessible() {
        let app = launch(extra: ["-AppleLanguages", "(tr)", "-AppleLocale", "tr_TR", "-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"])
        openSolve(app); start(app)
        XCTAssertTrue(app.descendants(matching: .any)["solve.unverified"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.descendants(matching: .any)["solve.method"].exists)
        XCTAssertTrue(app.buttons["solve.close"].isHittable)
        selectMode("BEGINNER", in: app)
        XCTAssertEqual(app.buttons["explanation.mode.BEGINNER"].label, "Başlangıç")
        XCTAssertEqual(app.buttons["explanation.mode.BEGINNER"].value as? String, "Seçili")
        XCTAssertTrue(app.staticTexts["explanation.prerequisite"].exists)
        // VoiceOver order, Reduce Motion and long wrapping additionally require the owner manual gate.
    }
}
