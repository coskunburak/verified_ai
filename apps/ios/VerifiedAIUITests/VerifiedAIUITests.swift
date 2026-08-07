import XCTest

@MainActor
final class VerifiedAIUITests: XCTestCase {
    func testLaunchShowsPlatformShell() {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing"]
        app.launch()

        XCTAssertTrue(app.staticTexts["Verified AI"].exists)
    }
}
