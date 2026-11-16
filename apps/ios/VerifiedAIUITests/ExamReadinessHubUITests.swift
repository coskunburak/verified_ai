import XCTest

@MainActor
final class ExamReadinessHubUITests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testExamReadinessHubRendersCredibleIntervalTrapsSimulatorAndRoiList() {
        let app = XCUIApplication()
        app.launchArguments = [
            "--ui-testing",
            "--ui-testing-authenticated",
            "--solve-fixture",
            "--solve-reset",
            "--exam-fixture"
        ]
        app.launch()
        openExamsTab(app)

        let readinessButton = app.buttons["home.examReadiness.button"]
        for _ in 0..<4 where !readinessButton.exists || !readinessButton.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(readinessButton.waitForExistence(timeout: 8))
        readinessButton.tap()

        let screen = app.descendants(matching: .any)["exam.readiness.screen"]
        XCTAssertTrue(screen.waitForExistence(timeout: 6))

        let intervalCard = app.descendants(matching: .any)["exam.readiness.intervalCard"]
        XCTAssertTrue(intervalCard.waitForExistence(timeout: 5))

        let trapsCard = app.descendants(matching: .any)["exam.readiness.trapsCard"]
        for _ in 0..<3 where !trapsCard.exists {
            app.swipeUp()
        }
        XCTAssertTrue(trapsCard.waitForExistence(timeout: 5))

        let roiList = app.descendants(matching: .any)["exam.readiness.roiList"]
        for _ in 0..<4 where !roiList.exists {
            app.swipeUp()
        }
        XCTAssertTrue(roiList.waitForExistence(timeout: 5))
    }

    func testPreliminaryReadinessDisplaysCalibrationWarningBanner() {
        let app = XCUIApplication()
        app.launchArguments = [
            "--ui-testing",
            "--ui-testing-authenticated",
            "--solve-fixture",
            "--solve-reset",
            "--exam-fixture",
            "--exam-readiness-preliminary"
        ]
        app.launch()
        openExamsTab(app)

        let readinessButton = app.buttons["home.examReadiness.button"]
        for _ in 0..<4 where !readinessButton.exists || !readinessButton.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(readinessButton.waitForExistence(timeout: 8))
        readinessButton.tap()

        let screen = app.descendants(matching: .any)["exam.readiness.screen"]
        XCTAssertTrue(screen.waitForExistence(timeout: 6))

        let preliminaryBanner = app.descendants(matching: .any)["exam.readiness.preliminaryBanner"]
        XCTAssertTrue(preliminaryBanner.waitForExistence(timeout: 5))
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
}
