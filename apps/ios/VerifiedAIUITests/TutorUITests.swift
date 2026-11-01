import XCTest

@MainActor
final class TutorUITests: XCTestCase {
    override func setUpWithError() throws {
        try super.setUpWithError()
        continueAfterFailure = false
    }
    private func waitForReady(_ element: XCUIElement) {
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == true AND enabled == true AND hittable == true"), object: element)
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 6), .completed,
                       "Control must be enabled and hittable before input: \(element.identifier)")
    }
    private func open(failure: Bool = false, turkish: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "--ui-testing-authenticated", "--solve-fixture", "--tutor-fixture", "--solve-reset",
            "--solve-fixture-scope=" + UUID().uuidString, "-AppleLanguages", turkish ? "(tr)" : "(en)"] + (failure ? ["--tutor-send-failure"] : [])
        app.launch()
        addTeardownBlock { [self] in
            if (testRun?.failureCount ?? 0) > 0 {
                let hierarchy = XCTAttachment(string: app.debugDescription)
                hierarchy.name = "Tutor accessibility hierarchy"
                hierarchy.lifetime = .keepAlways
                add(hierarchy)
                let screenshot = XCTAttachment(screenshot: app.screenshot())
                screenshot.name = "Tutor failure screen"
                screenshot.lifetime = .keepAlways
                add(screenshot)
            }
            app.terminate()
        }
        tap(app.buttons["history.session.00000000-0000-0000-0000-000000005401"], app)
        tap(app.buttons["history.solve"], app)
        tap(app.buttons["tutor.open"], app)
        XCTAssertTrue(app.staticTexts["tutor.notice"].waitForExistence(timeout: 5))
        tap(app.buttons["tutor.start"], app)
        XCTAssertTrue(app.staticTexts["tutor.empty"].waitForExistence(timeout: 5))
        return app
    }
    private func tap(_ element: XCUIElement, _ app: XCUIApplication) {
        XCTAssertTrue(element.waitForExistence(timeout: 6))
        for _ in 0..<5 { if element.isHittable { break }; app.swipeUp() }
        waitForReady(element); element.tap()
    }
    private func send(_ text: String, _ app: XCUIApplication) {
        let field = app.textFields["tutor.composer"].exists ? app.textFields["tutor.composer"] : app.textViews["tutor.composer"]
        XCTAssertTrue(field.waitForExistence(timeout: 4)); waitForReady(field); field.tap(); field.typeText(text)
        tap(app.buttons["tutor.send"], app)
    }
    private func message(_ sequence: Int, in app: XCUIApplication) -> XCUIElement {
        // SwiftUI may expose a combined text group as StaticText rather than Other.
        // The stable accessibility identifier is the contract, not its platform element type.
        app.descendants(matching: .any).matching(identifier: "tutor.message.\(sequence)").firstMatch
    }
    private func requireMessage(_ sequence: Int, content: String, in app: XCUIApplication) {
        let row = message(sequence, in: app)
        XCTAssertTrue(row.waitForExistence(timeout: 5))
        XCTAssertTrue(row.label.contains(content), "The persisted message must contain the submitted text")
        XCTAssertTrue(row.label.contains("You"), "The message must identify its learner sender")
        XCTAssertTrue(row.label.contains("Saved on server"), "The message must be confirmed, not a pending draft")
        XCTAssertFalse(app.staticTexts["tutor.error"].exists)
    }
    func testStartOrderedMessagesResumeAndClose() {
        let app = open()
        send("Synthetic first", app)
        requireMessage(1, content: "Synthetic first", in: app)
        send("Synthetic second", app)
        requireMessage(2, content: "Synthetic second", in: app)
        XCTAssertLessThan(message(1, in: app).frame.minY, message(2, in: app).frame.minY)
        tap(app.buttons["tutor.retry"], app)
        requireMessage(1, content: "Synthetic first", in: app)
        tap(app.buttons["tutor.close"], app)
        XCTAssertTrue(app.staticTexts["tutor.closed"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["tutor.send"].isEnabled)
    }
    func testRecoverableSendFailureReplaysWithoutDuplicate() {
        let app = open(failure: true)
        send("Synthetic uncertain send", app)
        XCTAssertTrue(app.staticTexts["tutor.error"].waitForExistence(timeout: 5))
        tap(app.buttons["tutor.retry"], app)
        requireMessage(1, content: "Synthetic uncertain send", in: app)
        XCTAssertFalse(message(2, in: app).exists)
    }
    func testTurkishAccessibilityLabelsAndPersistenceNotice() {
        let app = open(turkish: true)
        XCTAssertTrue(app.staticTexts["tutor.notice"].label.contains("Mesajlarınız"))
        XCTAssertFalse(app.buttons["tutor.send"].label.isEmpty)
        XCTAssertTrue(app.buttons["tutor.close"].isEnabled)
    }
}
