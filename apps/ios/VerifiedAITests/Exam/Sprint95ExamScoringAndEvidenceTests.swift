import XCTest
@testable import VerifiedAI

@MainActor
final class Sprint95ExamScoringAndEvidenceTests: XCTestCase {

    func testDeterministicScoreSummaryComputationTopicAndEvidenceFiltering() async throws {
        let store = ExamOfflineSQLiteStore(inMemory: true)
        let service = FixtureExamAPIService()
        let userId = UUID(uuidString: "91000000-0000-4000-8000-000000000a01")!
        let assemblyId = UUID(uuidString: "94100000-0000-4000-8000-000000000001")!

        let sessionVM = ExamSessionViewModel(
            apiService: service,
            offlineStore: store,
            assemblyId: assemblyId,
            userId: userId
        )
        await sessionVM.startOrResumeSession()
        await sessionVM.selectOption("B")
        await sessionVM.toggleFlagForReview()
        await sessionVM.attachScratchpadPage(
            pageNumber: 1,
            captureSource: .paperDenemeBooklet,
            bookletPublisher: "Bilgi Sarmal TYT",
            payloadData: Data("mock-page-1".utf8)
        )
        await sessionVM.submitSession(reason: .userInitiated)
        guard let submittedSession = sessionVM.session else {
            XCTFail("Expected submitted session")
            return
        }

        let scoreVM = sessionVM.makeScoreSummaryViewModel()
        await scoreVM.loadScoreSummary(computeIfMissing: true)

        guard let summary = scoreVM.scoreSummary else {
            XCTFail("Expected computed score summary")
            return
        }
        XCTAssertEqual(summary.schemaVersion, "2026-04-01")
        XCTAssertEqual(summary.sessionId, submittedSession.sessionId)
        XCTAssertEqual(summary.llmTokensUsed, 0)
        XCTAssertEqual(summary.inferenceCostUsd, 0.0)
        XCTAssertFalse(summary.trainingEligible)
        XCTAssertFalse(scoreVM.showOfflineBanner)
        XCTAssertEqual(scoreVM.llmTokensUsedDisplay, 0)
        XCTAssertEqual(scoreVM.inferenceCostUsdDisplay, 0.0)
        XCTAssertEqual(summary.correctCount + summary.wrongCount + summary.blankCount, summary.totalQuestions)
        XCTAssertFalse(summary.topicBreakdowns.isEmpty)
        XCTAssertFalse(summary.difficultyBreakdowns.isEmpty)

        // Item 1 should carry F-28 scratchpad metadata
        let firstEval = summary.itemEvaluations.first { $0.positionIndex == 1 }
        XCTAssertEqual(firstEval?.f28ScratchpadStatus, .attachedMetadataOnly)
        XCTAssertEqual(firstEval?.scratchpadAssetCount, 1)
        XCTAssertEqual(firstEval?.flaggedForReview, true)

        // Filtering checks
        scoreVM.selectedFilter = .flagged
        XCTAssertEqual(scoreVM.filteredItemEvaluations.count, 1)
        XCTAssertEqual(scoreVM.filteredItemEvaluations.first?.positionIndex, 1)

        scoreVM.selectedFilter = .blank
        XCTAssertEqual(scoreVM.filteredItemEvaluations.count, summary.blankCount)

        scoreVM.selectedFilter = .wrong
        XCTAssertEqual(scoreVM.filteredItemEvaluations.count, summary.wrongCount)

        scoreVM.selectedFilter = .all
        if let topicCode = summary.topicBreakdowns.first?.topicCode {
            scoreVM.selectTopicFilter(topicCode)
            XCTAssertTrue(scoreVM.filteredItemEvaluations.allSatisfy { $0.topicCode == topicCode })
            scoreVM.selectTopicFilter(topicCode)
            XCTAssertNil(scoreVM.selectedTopicCode)
        }
    }

    func testOfflineFallbackFromSQLiteAndPrivacyPurge() async throws {
        let store = ExamOfflineSQLiteStore(inMemory: true)
        let service = FixtureExamAPIService()
        let userId = UUID(uuidString: "91000000-0000-4000-8000-000000000a01")!
        let assemblyId = UUID(uuidString: "94100000-0000-4000-8000-000000000002")!

        let sessionVM = ExamSessionViewModel(
            apiService: service,
            offlineStore: store,
            assemblyId: assemblyId,
            userId: userId
        )
        await sessionVM.startOrResumeSession()
        await sessionVM.selectOption("A")
        await sessionVM.submitSession(reason: .userInitiated)
        let sessionId = try XCTUnwrap(sessionVM.session?.sessionId)

        let onlineVM = sessionVM.makeScoreSummaryViewModel()
        await onlineVM.loadScoreSummary(computeIfMissing: true)
        XCTAssertNotNil(onlineVM.scoreSummary)

        // Go offline and reload via fresh ViewModel backed by SQLite cache
        service.simulateOffline = true
        let offlineVM = ExamScoreSummaryViewModel(
            sessionId: sessionId,
            userId: userId,
            highContrastMode: false,
            apiService: service,
            offlineStore: store
        )
        await offlineVM.loadScoreSummary(computeIfMissing: true)
        XCTAssertTrue(offlineVM.showOfflineBanner)
        XCTAssertEqual(offlineVM.scoreSummary?.scoreId, onlineVM.scoreSummary?.scoreId)

        await store.purgeUserData(userId: userId)
        let afterPurge = await store.loadScoreSummary(sessionId: sessionId, userId: userId)
        XCTAssertNil(afterPurge)
    }

    func testRegradeValidationVersionBumpAndActiveSessionRejection() async throws {
        let store = ExamOfflineSQLiteStore(inMemory: true)
        let service = FixtureExamAPIService()
        let userId = UUID(uuidString: "91000000-0000-4000-8000-000000000a01")!

        // Active (unsubmitted) session must reject scoring with 409 sessionNotScorable
        let activeSessionVM = ExamSessionViewModel(
            apiService: service,
            offlineStore: store,
            assemblyId: UUID(uuidString: "94100000-0000-4000-8000-000000000003")!,
            userId: userId
        )
        await activeSessionVM.startOrResumeSession()
        let blockedVM = activeSessionVM.makeScoreSummaryViewModel()
        await blockedVM.loadScoreSummary(computeIfMissing: true)
        XCTAssertNil(blockedVM.scoreSummary)
        XCTAssertEqual(blockedVM.errorLocalizationKey, "exam.score.error.not_scorable")

        // Submit session, load v1 score, reject empty detail, then trigger v2 regrade
        await activeSessionVM.selectOption("A")
        await activeSessionVM.submitSession(reason: .userInitiated)
        let readyVM = activeSessionVM.makeScoreSummaryViewModel()
        await readyVM.loadScoreSummary(computeIfMissing: true)
        XCTAssertEqual(readyVM.scoreSummary?.scoreVersion, 1)

        await readyVM.triggerRegrade(reasonCode: .answerKeyCorrection, reasonDetail: "   ")
        XCTAssertEqual(readyVM.errorLocalizationKey, "exam.score.error.regrade_invalid")

        await readyVM.triggerRegrade(
            reasonCode: .answerKeyCorrection,
            reasonDetail: "Option verification synced from SME panel",
            triggeredBy: "qa-auditor"
        )
        XCTAssertEqual(readyVM.scoreSummary?.scoreVersion, 2)
        XCTAssertTrue(readyVM.hasRegradeHistory)
        XCTAssertEqual(readyVM.regradeAudits.count, 1)
        XCTAssertEqual(readyVM.regradeAudits.first?.previousScoreVersion, 1)
        XCTAssertEqual(readyVM.regradeAudits.first?.newScoreVersion, 2)
        XCTAssertEqual(readyVM.regradeAudits.first?.trainingEligible, false)
    }
}
