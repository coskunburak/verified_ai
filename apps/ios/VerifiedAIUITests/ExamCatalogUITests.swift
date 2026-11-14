import XCTest

@MainActor
final class ExamCatalogUITests: XCTestCase {

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testExamCatalogInspectionKeyboardDismissalAndTargetBinding() {
        let app = XCUIApplication()
        app.launchArguments = [
            "--ui-testing",
            "--ui-testing-authenticated",
            "--solve-fixture",
            "--solve-reset",
            "--exam-fixture"
        ]
        app.launch()

        let entryButton = app.buttons["home.examCatalog.button"]
        XCTAssertTrue(entryButton.waitForExistence(timeout: 8))
        entryButton.tap()

        let catalogList = app.descendants(matching: .any)["exam.catalog.list"]
        XCTAssertTrue(catalogList.waitForExistence(timeout: 5))

        let tytCard = app.buttons["exam.card.TR_YKS_TYT_MATH"]
        XCTAssertTrue(tytCard.waitForExistence(timeout: 5))
        tytCard.tap()

        let weightsSection = app.descendants(matching: .any)["exam.topicWeights.section"]
        XCTAssertTrue(weightsSection.waitForExistence(timeout: 5))

        let scoreField = app.textFields["exam.target.scoreField"]
        for _ in 0..<4 where !scoreField.exists || !scoreField.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(scoreField.waitForExistence(timeout: 5))
        scoreField.tap()
        if let existing = scoreField.value as? String, !existing.isEmpty {
            let deleteString = String(repeating: XCUIKeyboardKey.delete.rawValue, count: existing.count)
            scoreField.typeText(deleteString)
        }
        scoreField.typeText("35")

        let keyboardDone = app.buttons["exam.target.keyboardDone"]
        if keyboardDone.waitForExistence(timeout: 3) {
            keyboardDone.tap()
        } else {
            app.swipeUp()
        }

        let saveButton = app.buttons["exam.target.saveButton"]
        for _ in 0..<3 where !saveButton.exists || !saveButton.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(saveButton.waitForExistence(timeout: 5))
        saveButton.tap()

        let savedBanner = app.staticTexts["exam.target.savedBanner"]
        XCTAssertTrue(savedBanner.waitForExistence(timeout: 5))
    }

    func testExamCatalogOfflineCacheBannerRendersWhenOffline() {
        let app = XCUIApplication()
        app.launchArguments = [
            "--ui-testing",
            "--ui-testing-authenticated",
            "--solve-fixture",
            "--solve-reset",
            "--exam-fixture",
            "--exam-offline"
        ]
        app.launch()

        let entryButton = app.buttons["home.examCatalog.button"]
        XCTAssertTrue(entryButton.waitForExistence(timeout: 8))
        entryButton.tap()

        let offlineBanner = app.descendants(matching: .any)["exam.offline.banner"]
        XCTAssertTrue(offlineBanner.waitForExistence(timeout: 5))

        let tytCard = app.buttons["exam.card.TR_YKS_TYT_MATH"]
        XCTAssertTrue(tytCard.waitForExistence(timeout: 5))
    }
}
