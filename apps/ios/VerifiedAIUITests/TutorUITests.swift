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
    private func open(failure: Bool = false, turkish: Bool = false, unavailable: Bool = false, largeText: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "--ui-testing-authenticated", "--solve-fixture", "--tutor-fixture", "--solve-reset",
            "--solve-fixture-scope=" + UUID().uuidString, "-AppleLanguages", turkish ? "(tr)" : "(en)"] + (failure ? ["--tutor-send-failure"] : []) + (unavailable ? ["--tutor-unavailable"] : [])
        if largeText { app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"] }
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
        for _ in 0..<20 {
            if element.isHittable { break }
            let conversation = app.scrollViews["tutor.conversation"]
            if conversation.exists {
                if element.frame.minY < conversation.frame.minY { conversation.swipeDown() }
                else { conversation.swipeUp() }
            } else { app.swipeUp() }
        }
        waitForReady(element); element.tap()
    }
    private func send(_ text: String, _ app: XCUIApplication) {
        let field = app.textFields["tutor.composer"].exists ? app.textFields["tutor.composer"] : app.textViews["tutor.composer"]
        XCTAssertTrue(field.waitForExistence(timeout: 4)); tap(field, app); field.typeText(text)
        tap(app.buttons["tutor.send"], app)
        let keyboardDismissed = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: app.keyboards.firstMatch)
        XCTAssertEqual(XCTWaiter.wait(for: [keyboardDismissed], timeout: 5), .completed,
                       "Sending must dismiss the keyboard so conversation and recovery controls remain reachable")
    }
    private func message(_ sequence: Int, in app: XCUIApplication) -> XCUIElement {
        // SwiftUI may expose a combined text group as StaticText rather than Other.
        // The stable accessibility identifier is the contract, not its platform element type.
        app.descendants(matching: .any).matching(identifier: "tutor.message.\(sequence)").firstMatch
    }
    private func requireMessage(_ sequence: Int, content: String, in app: XCUIApplication) {
        let row = message(sequence, in: app)
        // Lazy rows outside the viewport are not necessarily in the accessibility tree.
        for _ in 0..<20 {
            if row.exists && row.isHittable { break }
            app.scrollViews["tutor.conversation"].swipeDown()
        }
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
        let conversation = app.scrollViews["tutor.conversation"]
        let intent = app.descendants(matching: .any).matching(identifier: "tutor.intent").firstMatch
        XCTAssertLessThanOrEqual(conversation.frame.maxY, intent.frame.minY,
                                 "The transcript viewport must end above the composer controls")
        tap(app.buttons["tutor.retry"], app)
        requireMessage(1, content: "Synthetic first", in: app)
        tap(app.buttons["tutor.close"], app)
        XCTAssertTrue(app.staticTexts["tutor.closed"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["tutor.closed"].isHittable,
                      "Confirmed closure must remain visible without scrolling back through the transcript")
        XCTAssertFalse(app.buttons["tutor.send"].isEnabled)
        tap(app.buttons["tutor.retry"], app)
        XCTAssertTrue(app.staticTexts["tutor.closed"].waitForExistence(timeout: 5),
                      "Refreshing must preserve the acknowledged closed session")
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
    func testSocraticReplyHasSpeakerAndUncertaintyAndResumes() {
        let app = open()
        send("Synthetic reasoning step", app)
        let reply = app.descendants(matching: .any).matching(identifier: "tutor.reply.1").firstMatch
        XCTAssertTrue(reply.waitForExistence(timeout: 5))
        XCTAssertTrue(reply.label.contains("Tutor")); XCTAssertTrue(reply.label.contains("concept or rule"))
        XCTAssertTrue(app.staticTexts["tutor.uncertain"].exists)
        XCTAssertTrue(app.staticTexts["tutor.phase"].exists)
        tap(app.buttons["tutor.retry"], app)
        XCTAssertTrue(reply.waitForExistence(timeout: 5))
        XCTAssertEqual(app.descendants(matching: .any).matching(identifier: "tutor.reply.1").count, 1)
    }
    func testTurkishSocraticCopyAndAccessibleIntent() {
        let app = open(turkish: true)
        send("Sentetik soru", app)
        let reply = app.descendants(matching: .any).matching(identifier: "tutor.reply.1").firstMatch
        XCTAssertTrue(reply.waitForExistence(timeout: 5))
        XCTAssertTrue(reply.label.contains("Rehber")); XCTAssertTrue(reply.label.contains("kavram"))
        XCTAssertTrue(app.staticTexts["tutor.uncertain"].label.contains("bilinmiyor"))
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "tutor.intent").firstMatch.exists)
    }

    func testUnavailableTutorKeepsDraftAndOffersRecoveryWithoutLoadingForever() {
        let app = open(unavailable: true)
        send("Synthetic unavailable draft", app)
        XCTAssertTrue(app.staticTexts["tutor.error"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["tutor.error"].label.contains("unavailable"))
        XCTAssertFalse(app.descendants(matching: .any).matching(identifier: "tutor.loading").firstMatch.exists)
        XCTAssertFalse(app.descendants(matching: .any).matching(identifier: "tutor.reply.1").firstMatch.exists)
        let composer = app.textFields["tutor.composer"].exists ? app.textFields["tutor.composer"] : app.textViews["tutor.composer"]
        XCTAssertEqual(composer.value as? String, "Synthetic unavailable draft")
        tap(app.buttons["tutor.retry"], app)
        XCTAssertFalse(app.staticTexts["tutor.error"].exists)
    }

    func testProgressiveHintsRemainSeparateFromConfirmedRevealAndResume() {
        let app = open()
        for level in ["H1","H2","H3"] {
            tap(app.buttons["tutor.hints.request"], app)
            let row = app.descendants(matching: .any).matching(identifier: "tutor.hint." + level).firstMatch
            XCTAssertTrue(row.waitForExistence(timeout: 5))
            XCTAssertFalse(row.label.contains("x = 4")); XCTAssertFalse(row.label.contains("x=4"))
        }
        XCTAssertFalse(app.buttons["tutor.hints.request"].exists)
        XCTAssertTrue(app.staticTexts["tutor.hints.status"].label.contains("hidden"))
        tap(app.buttons["tutor.reveal.request"], app)
        tap(app.buttons["Cancel"], app)
        XCTAssertFalse(app.staticTexts["tutor.error"].exists)
        tap(app.buttons["tutor.reveal.request"], app)
        tap(app.buttons["Confirm answer request"], app)
        XCTAssertTrue(app.staticTexts["tutor.error"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["tutor.error"].label.contains("verified reference"))
        tap(app.buttons["tutor.retry"], app)
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "tutor.hint.H3").firstMatch.exists)
        XCTAssertFalse(app.staticTexts["tutor.error"].exists)
    }
    func testLostHintAcknowledgementRecoversWithoutDuplicate() {
        let app = open(failure: true)
        tap(app.buttons["tutor.hints.request"], app)
        XCTAssertTrue(app.staticTexts["tutor.error"].waitForExistence(timeout: 5))
        tap(app.buttons["tutor.retry"], app)
        XCTAssertTrue(app.descendants(matching: .any).matching(identifier: "tutor.hint.H1").firstMatch.waitForExistence(timeout: 5))
        XCTAssertEqual(app.descendants(matching: .any).matching(identifier: "tutor.hint.H1").count, 1)
        XCTAssertFalse(app.descendants(matching: .any).matching(identifier: "tutor.hint.H2").firstMatch.exists)
    }
    func testTurkishHintAccessibilityAndNoEarlyAnswer() {
        let app = open(turkish: true)
        XCTAssertTrue(app.buttons["tutor.hints.request"].label.contains("İpucu"))
        tap(app.buttons["tutor.hints.request"], app)
        let row = app.descendants(matching: .any).matching(identifier: "tutor.hint.H1").firstMatch
        XCTAssertTrue(row.waitForExistence(timeout: 5)); XCTAssertTrue(row.label.contains("Bilinmeyeni"))
        XCTAssertFalse(row.label.contains("x=4")); XCTAssertFalse(app.buttons["tutor.reveal.request"].label.isEmpty)
    }

    func testEnglishAccessibilitySizeComposerAndRetryRemainReachable() {
        let app = open(failure: true, largeText: true)
        send("Synthetic large text retry", app)
        tap(app.buttons["tutor.retry"], app)
        let row = message(1, in: app)
        for _ in 0..<20 where !row.isHittable { app.scrollViews["tutor.conversation"].swipeDown() }
        requireMessage(1, content: "Synthetic large text retry", in: app)
        XCTAssertFalse(message(2, in: app).exists)
    }
    func testTurkishAccessibilitySizeComposerRemainsReachable() {
        let app = open(turkish: true, largeText: true)
        send("Sentetik büyük yazı", app)
        let row = message(1, in: app)
        for _ in 0..<20 where !row.isHittable { app.scrollViews["tutor.conversation"].swipeDown() }
        XCTAssertTrue(row.label.contains("Sentetik büyük yazı"))
        XCTAssertFalse(row.label.contains("tutor."))
    }

}
