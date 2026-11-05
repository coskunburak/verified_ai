import XCTest
@MainActor
final class AttemptAnalysisUITests: XCTestCase {
    func testManualWrongStepAndCounterexample() { run(turkish: false, lostAck: false) }
    func testTurkishLostAcknowledgementRecovery() { run(turkish: true, lostAck: true) }
    func testMethodComparisonEnglishSelectionAndCompact() { methods(turkish: false, flags: []) }
    func testMethodComparisonTurkishRetry() { methods(turkish: true, flags: ["--methods-fail-first"]) }
    func testMethodComparisonUnsupportedAndSingle() {
        methods(turkish: false, flags: ["--methods-unsupported"])
        methods(turkish: false, flags: ["--methods-single"])
        methods(turkish: false, flags: ["--methods-empty"])
    }
    func testFinalAnswerSavedWithoutVerificationEnglish() { answer(turkish: false, lostAck: false) }
    func testFinalAnswerTurkishLostAcknowledgementRecovery() { answer(turkish: true, lostAck: true) }
    private func answer(turkish: Bool, lostAck: Bool) {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "--ui-testing-authenticated", "--solve-fixture", "--attempt-fixture", "--solve-reset",
            "--solve-fixture-scope=" + UUID().uuidString, "-AppleLanguages", turkish ? "(tr)" : "(en)"] + (lostAck ? ["--attempt-lost-ack"] : [])
        app.launch(); defer { app.terminate() }
        tap(app.buttons["history.session.00000000-0000-0000-0000-000000005401"], app)
        tap(app.buttons["history.solve"], app); tap(app.buttons["attempt.open"], app)
        tap(app.switches["attempt.answer.mode"], app)
        let input = app.textFields["attempt.answer.input"]
        tap(input, app); input.typeText("4\n")
        tap(app.buttons["attempt.submit"], app)
        XCTAssertTrue(app.buttons["attempt.answer.save"].waitForExistence(timeout: 5))
        tap(app.buttons["attempt.answer.save"], app)
        if lostAck { XCTAssertTrue(app.descendants(matching: .any)["attempt.error"].waitForExistence(timeout: 5)); tap(app.buttons["attempt.retry"], app) }
        let saved = app.descendants(matching: .any)["attempt.answer.saved"].firstMatch
        for _ in 0..<5 { if saved.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(saved.waitForExistence(timeout: 5))
        XCTAssertTrue(saved.label.contains(turkish ? "Değerlendirilmedi" : "Not assessed"))
        XCTAssertEqual(app.staticTexts["attempt.answer.original"].label, "4")
        XCTAssertFalse(app.staticTexts["VERIFIED"].exists); XCTAssertFalse(app.staticTexts["attempt.outcome"].exists)
    }
    private func methods(turkish: Bool, flags: [String]) {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "--ui-testing-authenticated", "--solve-fixture", "--attempt-fixture", "--methods-fixture", "--solve-reset",
            "--solve-fixture-scope=" + UUID().uuidString, "-AppleLanguages", turkish ? "(tr)" : "(en)"] + flags
        app.launch(); defer { app.terminate() }
        tap(app.buttons["history.session.00000000-0000-0000-0000-000000005401"], app)
        tap(app.buttons["history.solve"], app); tap(app.buttons["attempt.methods.open"], app)
        if flags.contains("--methods-fail-first") { tap(app.buttons["attempt.methods.retry"], app) }
        let scope = app.staticTexts["attempt.methods.scope"]
        XCTAssertTrue(scope.waitForExistence(timeout: 6))
        XCTAssertTrue(scope.label.contains(turkish ? "bağımsız" : "independently"))
        if flags.contains("--methods-unsupported") || flags.contains("--methods-empty") {
            XCTAssertTrue(app.descendants(matching: .any)["attempt.methods.empty"].exists)
            XCTAssertFalse(app.descendants(matching: .any)["attempt.methods.evidence"].exists)
            return
        }
        let picker = app.descendants(matching: .any)["attempt.methods.select"].firstMatch
        XCTAssertTrue(picker.exists)
        if !flags.contains("--methods-single") {
            tap(picker, app)
            let title = turkish ? "İkinci derece denklem formülü" : "Quadratic formula"
            tap(app.buttons[title], app)
        }
        let toggle = app.switches["attempt.methods.compact"]
        if !toggle.isHittable { app.swipeUp() }
        XCTAssertTrue(toggle.waitForExistence(timeout: 3)); toggle.tap()
        for _ in 0..<6 {
            if app.descendants(matching: .any)["attempt.methods.evidence"].isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(app.descendants(matching: .any)["attempt.methods.evidence"].exists)
        XCTAssertFalse(app.staticTexts["VERIFIED"].exists)
    }
    private func run(turkish: Bool, lostAck: Bool) {
        continueAfterFailure = false
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "--ui-testing-authenticated", "--solve-fixture", "--attempt-fixture", "--solve-reset",
            "--solve-fixture-scope=" + UUID().uuidString, "-AppleLanguages", turkish ? "(tr)" : "(en)"] + (lostAck ? ["--attempt-lost-ack"] : [])
        app.launch(); defer { app.terminate() }
        tap(app.buttons["history.session.00000000-0000-0000-0000-000000005401"], app)
        tap(app.buttons["history.solve"], app); tap(app.buttons["attempt.open"], app)
        XCTAssertTrue(app.descendants(matching: .any)["attempt.handwriting.unavailable"].waitForExistence(timeout: 6))
        let editor = app.textViews["attempt.editor"]
        enterSteps("x=5", in: editor, app: app)
        let done = app.buttons["attempt.keyboard.done"]
        XCTAssertTrue(done.waitForExistence(timeout: 5)); done.tap()
        let dismissed = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: app.keyboards.firstMatch)
        XCTAssertEqual(XCTWaiter.wait(for: [dismissed], timeout: 5), .completed)
        tap(app.buttons["attempt.submit"], app)
        if lostAck { XCTAssertTrue(app.descendants(matching: .any)["attempt.error"].waitForExistence(timeout: 5)); tap(app.buttons["attempt.retry"], app) }
        let outcome = app.staticTexts["attempt.outcome"]
        XCTAssertTrue(outcome.waitForExistence(timeout: 5))
        XCTAssertEqual(outcome.label, turkish ? "Eşdeğer olmayan bir dönüşüm kanıtlandı" : "A non-equivalent transformation was proven")
        let domain = app.staticTexts["attempt.domain"]
        XCTAssertTrue(domain.exists)
        XCTAssertTrue(domain.label.hasPrefix(turkish ? "Kapsam:" : "Scope:"))
        let verdict = app.descendants(matching: .any)["attempt.step.1.FIRST_WRONG_STEP"].firstMatch
        XCTAssertTrue(verdict.waitForExistence(timeout: 5))
        XCTAssertEqual(verdict.label, turkish ? "İlk kanıtlanmış geçersiz dönüşüm" : "First proven invalid transformation")
        let proof = app.descendants(matching: .any)["attempt.counterexample"].firstMatch
        let screen = app.scrollViews["attempt.screen"]
        for _ in 0..<5 { if proof.isHittable { break }; screen.swipeUp() }
        XCTAssertTrue(proof.waitForExistence(timeout: 5))
        XCTAssertTrue(proof.isHittable)
        XCTAssertTrue(proof.label.contains(turkish ? "Bu değerde denklemlerden biri doğru, diğeri yanlıştır." : "At this value, one equation is true and the other is false."))
        XCTAssertFalse(app.staticTexts["VERIFIED"].exists)
    }
    private func enterSteps(_ text: String, in editor: XCUIElement, app: XCUIApplication) {
        XCTAssertTrue(editor.waitForExistence(timeout: 5))
        XCTAssertEqual(editor.value as? String, "", "A fresh fixture scope must not restore another test's draft")
        let editable = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true AND hittable == true"), object: editor)
        XCTAssertEqual(XCTWaiter.wait(for: [editable], timeout: 5), .completed)
        editor.tap()
        // A fresh simulator can finish the tap before its first keyboard becomes ready.
        // Reacquire focus once inside the text inset; never send or duplicate text without a keyboard.
        let keyboard = app.keyboards.firstMatch
        if !keyboard.waitForExistence(timeout: 5) {
            editor.coordinate(withNormalizedOffset: CGVector(dx: 0.1, dy: 0.1)).tap()
            XCTAssertTrue(keyboard.waitForExistence(timeout: 5), "The manual editor must acquire keyboard focus before typing")
        }
        editor.typeText(text)
        XCTAssertEqual(editor.value as? String, text, "The submitted learner text must match the typed input")
    }
    private func tap(_ item: XCUIElement, _ app: XCUIApplication) {
        XCTAssertTrue(item.waitForExistence(timeout: 6))
        for _ in 0..<5 { if item.isHittable { break }; app.swipeUp() }
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "enabled == true AND hittable == true"), object: item)
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 6), .completed); item.tap()
    }
}
