import XCTest

@MainActor
final class VerifiedAIUITests: XCTestCase {
    func testLaunchShowsPlatformShell() {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing"]
        app.launch()

        XCTAssertTrue(app.staticTexts["Verified AI"].exists)
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

    private func launchAuthenticatedApp(extraArguments: [String] = []) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "--ui-testing-authenticated"] + extraArguments
        app.launch()
        return app
    }
}
