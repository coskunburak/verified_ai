import XCTest

@MainActor
final class ExamSessionRuntimeUITests: XCTestCase {

    override func setUpWithError() throws {
        try super.setUpWithError()
        continueAfterFailure = false
    }

    func testTimedMockExamSessionFlowScratchpadAttachAndSubmit() {
        let app = XCUIApplication()
        app.launchArguments = [
            "--ui-testing",
            "--ui-testing-authenticated",
            "--ui-testing-candidate-profile",
            "--solve-fixture",
            "--solve-reset",
            "--exam-fixture",
            "-AppleLanguages", "(en)",
            "-AppleLocale", "en_US"
        ]
        app.launch()
        openExamsTab(app)

        let blueprintNavButton = app.buttons["home.blueprintAssembly.button"]
        scrollToElementIfNeeded(blueprintNavButton, in: app)
        XCTAssertTrue(blueprintNavButton.waitForExistence(timeout: 8))
        blueprintNavButton.tap()

        let diagCard = app.buttons["exam.assembly.blueprintCard.BP-TYT-MATH-DIAG-2026-V1"]
        if diagCard.waitForExistence(timeout: 5) && diagCard.isHittable {
            diagCard.tap()
        }

        let assembleButton = app.buttons["exam.assembly.assembleButton"]
        scrollToElementIfNeeded(assembleButton, in: app)
        XCTAssertTrue(assembleButton.waitForExistence(timeout: 6))
        assembleButton.tap()

        let startSessionButton = app.buttons["exam.assembly.startTimedSessionButton"]
        scrollToElementIfNeeded(startSessionButton, in: app)
        XCTAssertTrue(startSessionButton.waitForExistence(timeout: 6))
        startSessionButton.tap()

        let sessionScreen = app.descendants(matching: .any)["exam.session.screen"]
        XCTAssertTrue(sessionScreen.waitForExistence(timeout: 6))
        XCTAssertTrue(app.descendants(matching: .any)["exam.session.timerPill"].waitForExistence(timeout: 4))
        XCTAssertTrue(app.descendants(matching: .any)["exam.session.noHintBadge"].exists)
        XCTAssertTrue(app.descendants(matching: .any)["exam.session.navigatorGrid"].exists)
        XCTAssertTrue(app.descendants(matching: .any)["exam.session.questionStemCard"].exists)

        let optionB = app.buttons["exam.session.optionRow.B"]
        scrollToElementIfNeeded(optionB, in: app)
        if optionB.waitForExistence(timeout: 4) {
            optionB.tap()
        }

        let attachButton = app.buttons["exam.session.attachScratchpadButton"]
        scrollToElementIfNeeded(attachButton, in: app)
        XCTAssertTrue(attachButton.waitForExistence(timeout: 4))
        attachButton.tap()

        let queuedBadge = app.descendants(matching: .any)["exam.session.scratchpadQueuedBadge"]
        scrollToElementIfNeeded(queuedBadge, in: app, requireHittable: false)
        XCTAssertTrue(queuedBadge.waitForExistence(timeout: 5))

        let submitButton = app.buttons["exam.session.submitExamButton"]
        scrollToElementIfNeeded(submitButton, in: app)
        XCTAssertTrue(submitButton.waitForExistence(timeout: 4))
        submitButton.tap()

        let confirmById = app.buttons["exam.session.confirmSubmitButton"]
        let confirmByLabel = app.buttons["Submit Mock Exam"]
        if confirmById.waitForExistence(timeout: 3) {
            confirmById.tap()
        } else {
            XCTAssertTrue(confirmByLabel.waitForExistence(timeout: 4))
            confirmByLabel.tap()
        }

        let receiptCard = app.descendants(matching: .any)["exam.session.submitReceiptCard"]
        if !receiptCard.waitForExistence(timeout: 4) {
            app.swipeDown()
        }
        XCTAssertTrue(receiptCard.waitForExistence(timeout: 6))
    }

    private func openExamsTab(_ app: XCUIApplication) {
        let examsTab = app.tabBars.buttons["Exams"]
        if examsTab.waitForExistence(timeout: 4) {
            examsTab.tap()
        } else {
            let trTab = app.tabBars.buttons["Sınavlar"]
            if trTab.waitForExistence(timeout: 2) {
                trTab.tap()
            } else {
                let byIndex = app.tabBars.buttons.element(boundBy: 2)
                XCTAssertTrue(byIndex.waitForExistence(timeout: 5))
                byIndex.tap()
            }
        }
    }

    private func scrollToElementIfNeeded(_ element: XCUIElement, in app: XCUIApplication, requireHittable: Bool = true) {
        var attempts = 0
        while (!element.exists || (requireHittable && !element.isHittable)) && attempts < 6 {
            app.swipeUp()
            attempts += 1
        }
    }
}
