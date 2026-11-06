import XCTest

@MainActor final class MathVisualizationUITests: XCTestCase {
    private func open(extra: [String] = [], methods: Bool = false, turkish: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--ui-testing", "--ui-testing-authenticated", "--solve-fixture", "--solve-reset",
            "--solve-fixture-scope=" + UUID().uuidString, "--visualization-fixture", "--solve-scenario=GENERATED_UNVERIFIED",
            "-AppleLanguages", turkish ? "(tr)" : "(en)", "-AppleLocale", turkish ? "tr_TR" : "en_US"] + extra
        app.launch()
        let row = app.buttons["history.session.00000000-0000-0000-0000-000000005401"]
        XCTAssertTrue(row.waitForExistence(timeout: 8)); row.tap()
        let solve = app.buttons["history.solve"]; XCTAssertTrue(solve.waitForExistence(timeout: 5)); solve.tap()
        if methods {
            let comparison = app.buttons["attempt.methods.open"]
            XCTAssertTrue(comparison.waitForExistence(timeout: 5)); comparison.tap()
        }
        let entry = app.buttons[methods ? "visualization.methods.open" : "visualization.open"];
        if methods {
            XCTAssertTrue(entry.waitForExistence(timeout: 5))
            for _ in 0..<8 where !entry.isHittable { app.swipeUp() }
        }
        XCTAssertTrue(entry.waitForExistence(timeout: 5)); entry.tap()
        addTeardownBlock { app.terminate() }; return app
    }
    func testCurrentSolutionNavigationAndAccessibleChart() {
        let app = open()
        XCTAssertTrue(app.otherElements["visualization.chart"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.buttons["Zoom in"].exists)
        app.buttons["Zoom in"].tap()
        XCTAssertTrue(app.otherElements["visualization.chart"].waitForExistence(timeout: 5))
    }
    func testMethodComparisonEntryUsesCurrentSource() {
        let app = open(extra: ["--attempt-fixture", "--methods-fixture"], methods: true)
        XCTAssertTrue(app.otherElements["visualization.chart"].waitForExistence(timeout: 5))
    }
    func testOfflineRetry() {
        let app = open(extra: ["--visualization-fail-first"])
        let retry = app.buttons["visualization.retry"]; XCTAssertTrue(retry.waitForExistence(timeout: 5)); retry.tap()
        XCTAssertTrue(app.otherElements["visualization.chart"].waitForExistence(timeout: 5))
    }
    func testTurkishLargeTextApproximateSampleControls() {
        let app = open(extra: ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"], turkish: true)
        let next = app.buttons["visualization.next"]
        XCTAssertTrue(next.waitForExistence(timeout: 5))
        for _ in 0..<12 where !next.isHittable { app.swipeUp() }
        XCTAssertTrue(next.isHittable); next.tap()
        XCTAssertTrue(app.staticTexts["visualization.selection"].label.contains("Yaklaşık"))
        XCTAssertFalse(next.label.contains("visualization."))
    }

}
