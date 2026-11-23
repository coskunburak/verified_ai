import XCTest

@MainActor
final class VerifiedAIUITests: XCTestCase {
    func testLaunchShowsPlatformShell() {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing"]
        app.launch()

        XCTAssertTrue(app.staticTexts["Verified AI"].waitForExistence(timeout: 5))
    }

    func testProblemCaptureEntryShowsSourceChoices() {
        let app = launchAuthenticatedApp()

        let scanButton = app.buttons["home.scanProblem"]
        XCTAssertTrue(scanButton.waitForExistence(timeout: 6))
        scanButton.tap()

        XCTAssertTrue(app.buttons["problemCapture.source.camera"].waitForExistence(timeout: 4))
        XCTAssertTrue(app.buttons["problemCapture.source.photoLibrary"].exists)
        XCTAssertTrue(app.buttons["problemCapture.source.file"].exists)
    }

    func testProblemCaptureReviewCanAcceptLocalAsset() {
        let app = launchAuthenticatedApp(extraArguments: ["--ui-testing-problem-capture-review"])

        let scanButton = app.buttons["home.scanProblem"]
        XCTAssertTrue(scanButton.waitForExistence(timeout: 6))
        scanButton.tap()

        let acceptButton = app.buttons["problemCapture.review.accept"]
        XCTAssertTrue(acceptButton.waitForExistence(timeout: 6))
        XCTAssertTrue(app.staticTexts["Review capture"].exists)

        acceptButton.tap()

        XCTAssertTrue(app.buttons["problemCapture.accepted.done"].waitForExistence(timeout: 4))
        XCTAssertTrue(app.staticTexts["Problem asset ready"].exists)
    }

    func testColdStartBaselineDiagnosticCardAndThreeStepFlow() {
        let app = launchAuthenticatedApp()

        let planTab = app.tabBars.buttons["Plan"]
        if planTab.waitForExistence(timeout: 4) {
            planTab.tap()
        } else {
            let byIndex = app.tabBars.buttons.element(boundBy: 1)
            XCTAssertTrue(byIndex.waitForExistence(timeout: 5))
            byIndex.tap()
        }

        let card = app.descendants(matching: .any)["coachHub.coldStartDiagnosticCard"]
        XCTAssertTrue(card.waitForExistence(timeout: 6))

        let startCTA = app.buttons["coldStart.card.cta"]
        XCTAssertTrue(startCTA.waitForExistence(timeout: 4))
        startCTA.tap()

        let beginButton = app.buttons["coldStart.flow.begin"]
        XCTAssertTrue(beginButton.waitForExistence(timeout: 4))
        beginButton.tap()

        for optionId in ["A", "B", "A"] {
            let optionButton = app.buttons["coldStart.option.\(optionId)"]
            XCTAssertTrue(optionButton.waitForExistence(timeout: 4))
            optionButton.tap()

            let nextOrFinish = app.buttons["coldStart.flow.nextOrFinish"]
            XCTAssertTrue(nextOrFinish.isEnabled)
            nextOrFinish.tap()
        }

        let doneButton = app.buttons["coldStart.result.done"]
        XCTAssertTrue(doneButton.waitForExistence(timeout: 5))
        doneButton.tap()

        XCTAssertTrue(card.waitForExistence(timeout: 4))
    }

    private func launchAuthenticatedApp(extraArguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "--ui-testing-authenticated"] + extraArguments
        app.launch()
        return app
    }
}
