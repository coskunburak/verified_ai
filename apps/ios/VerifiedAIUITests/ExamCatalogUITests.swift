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
        openExamsTab(app)

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
        openExamsTab(app)

        let entryButton = app.buttons["home.examCatalog.button"]
        XCTAssertTrue(entryButton.waitForExistence(timeout: 8))
        entryButton.tap()

        let offlineBanner = app.descendants(matching: .any)["exam.offline.banner"]
        XCTAssertTrue(offlineBanner.waitForExistence(timeout: 5))

        let tytCard = app.buttons["exam.card.TR_YKS_TYT_MATH"]
        XCTAssertTrue(tytCard.waitForExistence(timeout: 5))
    }

    func testCandidateGoalSetupAccommodationsAndHorizonSave() {
        let app = XCUIApplication()
        app.launchArguments = [
            "--ui-testing",
            "--ui-testing-authenticated",
            "--solve-fixture",
            "--solve-reset",
            "--exam-fixture",
            "--ui-testing-candidate-profile"
        ]
        app.launch()
        openExamsTab(app)

        let entryButton = app.buttons["home.candidateProfile.button"]
        XCTAssertTrue(entryButton.waitForExistence(timeout: 8))
        entryButton.tap()

        let saveButton = app.buttons["candidate.profile.saveButton"]
        for _ in 0..<4 where !saveButton.exists || !saveButton.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(saveButton.waitForExistence(timeout: 5))
        saveButton.tap()

        let savedBanner = app.descendants(matching: .any)["candidate.profile.savedBanner"]
        for _ in 0..<4 where !savedBanner.exists {
            app.swipeDown()
        }
        XCTAssertTrue(savedBanner.waitForExistence(timeout: 5))
    }

    func testSnapToPlanAmbiguousPdfDisambiguationAndConfirm() {
        let app = XCUIApplication()
        app.launchArguments = [
            "--ui-testing",
            "--ui-testing-authenticated",
            "--solve-fixture",
            "--solve-reset",
            "--exam-fixture",
            "--ui-testing-candidate-profile"
        ]
        app.launch()
        openExamsTab(app)

        let entryButton = app.buttons["home.candidateProfile.button"]
        XCTAssertTrue(entryButton.waitForExistence(timeout: 8))
        entryButton.tap()

        let syllabusButton = app.buttons["candidate.profile.openSyllabusSheetButton"]
        for _ in 0..<4 where !syllabusButton.exists || !syllabusButton.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(syllabusButton.waitForExistence(timeout: 5))
        syllabusButton.tap()

        let sampleAmbiguousBtn = app.buttons["syllabus.sampleAmbiguousUploadButton"]
        XCTAssertTrue(sampleAmbiguousBtn.waitForExistence(timeout: 5))
        sampleAmbiguousBtn.tap()

        let confirmButton = app.buttons["syllabus.confirmMappingsButton"]
        for _ in 0..<4 where !confirmButton.exists || !confirmButton.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(confirmButton.waitForExistence(timeout: 5))
        confirmButton.tap()

        let statusBanner = app.descendants(matching: .any)["syllabus.statusBanner"]
        for _ in 0..<3 where !statusBanner.exists {
            app.swipeDown()
        }
        XCTAssertTrue(statusBanner.waitForExistence(timeout: 5))
    }

    func testBlueprintSelectionAndDeterministicAssemblyPreview() {
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

        let entryButton = app.buttons["home.blueprintAssembly.button"]
        for _ in 0..<3 where !entryButton.exists || !entryButton.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(entryButton.waitForExistence(timeout: 8))
        entryButton.tap()

        let diagCard = app.buttons["exam.assembly.blueprintCard.BP-TYT-MATH-DIAG-2026-V1"]
        XCTAssertTrue(diagCard.waitForExistence(timeout: 5))
        diagCard.tap()

        let assembleButton = app.buttons["exam.assembly.assembleButton"]
        for _ in 0..<4 where !assembleButton.exists || !assembleButton.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(assembleButton.waitForExistence(timeout: 5))
        assembleButton.tap()

        let readyBanner = app.descendants(matching: .any)["exam.assembly.statusReadyBanner"]
        for _ in 0..<3 where !readyBanner.exists {
            app.swipeUp()
        }
        XCTAssertTrue(readyBanner.waitForExistence(timeout: 5))

        let hashLabel = app.descendants(matching: .any)["exam.assembly.reproducibilityHashLabel"]
        XCTAssertTrue(hashLabel.waitForExistence(timeout: 5))
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
