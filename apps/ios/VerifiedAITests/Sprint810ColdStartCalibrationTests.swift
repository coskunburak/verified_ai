import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class Sprint810ColdStartCalibrationTests: XCTestCase {

    func testColdStartV1JSONDecodingMatchesBackendContract() throws {
        let json = """
        {
          "schemaVersion": "cold-start-v1",
          "diagnosticId": "81000000-0000-4000-8000-000000000001",
          "learnerId": "81000000-0000-4000-8000-000000000002",
          "locale": "en-US",
          "status": "COMPLETED",
          "baselineTier": "CALIBRATED_MIXED",
          "correctCount": 2,
          "totalItems": 3,
          "calibrationMode": "EXPLORATION",
          "confidenceTier": "PRELIMINARY",
          "goalConflictResolved": true,
          "recommendedDailyBudgetMinutes": 10,
          "inactiveDecayApplied": false,
          "explicitPreferenceOverride": true,
          "declaredExamTarget": "AYT",
          "declaredDailyBudgetMinutes": 10,
          "preferredModality": "SOCRATIC_COACH",
          "revalidationPromptKey": null,
          "detectedTrapArchetypes": ["INEQUALITY_POLARITY_TRAP"],
          "itemOutcomes": [
            {
              "itemId": "DIAG_ITEM_1_NUMBER_SENSE",
              "skillCode": "MATH.ARITHMETIC.ORDER_OF_OPERATIONS",
              "correct": true,
              "verdict": "CORRECT_FAST",
              "detectedTrapArchetype": null,
              "assignedPrior": 0.65,
              "elapsedSeconds": 32
            },
            {
              "itemId": "DIAG_ITEM_2_QUADRATIC_INEQUALITY",
              "skillCode": "MATH.EQUATIONS.INEQUALITIES_BASIC",
              "correct": false,
              "verdict": "TRAP_DISTRACTOR",
              "detectedTrapArchetype": "INEQUALITY_POLARITY_TRAP",
              "assignedPrior": 0.15,
              "elapsedSeconds": 48
            },
            {
              "itemId": "DIAG_ITEM_3_FUNCTION_COMPOSITION",
              "skillCode": "MATH.FUNCTIONS.COMPOSITION",
              "correct": true,
              "verdict": "CORRECT_SLOW",
              "detectedTrapArchetype": null,
              "assignedPrior": 0.50,
              "elapsedSeconds": 82
            }
          ],
          "initializedPriors": [
            {
              "skillCode": "MATH.ARITHMETIC.ORDER_OF_OPERATIONS",
              "priorProbability": 0.65,
              "evidenceCount": 1,
              "confidenceTier": "PRELIMINARY",
              "decayApplied": false
            },
            {
              "skillCode": "MATH.EQUATIONS.INEQUALITIES_BASIC",
              "priorProbability": 0.15,
              "evidenceCount": 1,
              "confidenceTier": "PRELIMINARY",
              "decayApplied": false
            },
            {
              "skillCode": "MATH.FUNCTIONS.COMPOSITION",
              "priorProbability": 0.50,
              "evidenceCount": 1,
              "confidenceTier": "PRELIMINARY",
              "decayApplied": false
            }
          ],
          "rationales": [
            {
              "code": "PRELIMINARY_SPARSE_EVIDENCE",
              "localizedMessage": "Preliminary calibration is active while verified evidence builds across your first three attempts per skill."
            },
            {
              "code": "GOAL_CAPACITY_CONFLICT_RESOLVED",
              "localizedMessage": "Your AYT goal has been paced into focused single-skill 10-minute daily blocks so momentum stays sustainable."
            }
          ],
          "attemptDurationSeconds": 162,
          "llmTokensUsed": 0,
          "trainingEligible": false,
          "idempotentReplay": false,
          "createdAt": "2026-11-13T12:00:00Z",
          "updatedAt": "2026-11-13T12:00:00Z"
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(ColdStartDiagnosticResult.self, from: json)
        XCTAssertEqual(decoded.schemaVersion, "cold-start-v1")
        XCTAssertEqual(decoded.baselineTier, .calibratedMixed)
        XCTAssertEqual(decoded.confidenceTier, .preliminary)
        XCTAssertEqual(decoded.calibrationMode, .exploration)
        XCTAssertTrue(decoded.goalConflictResolved)
        XCTAssertFalse(decoded.inactiveDecayApplied)
        XCTAssertTrue(decoded.explicitPreferenceOverride)
        XCTAssertEqual(decoded.llmTokensUsed, 0)
        XCTAssertFalse(decoded.trainingEligible)
        XCTAssertEqual(decoded.detectedTrapArchetypes, ["INEQUALITY_POLARITY_TRAP"])
        XCTAssertEqual(decoded.itemOutcomes.count, 3)
        XCTAssertEqual(decoded.initializedPriors.count, 3)
    }

    func testViewModelThreeStepProgressionAndElapsedTiming() async {
        final class ClockBox: @unchecked Sendable {
            var now = Date(timeIntervalSince1970: 1_700_000_000)
        }
        let clock = ClockBox()
        let learnerId = UUID(uuidString: "81000000-0000-4000-8000-000000000010")!
        let mockService = MockColdStartDiagnosticService()
        let cache = ColdStartSQLiteCache(inMemory: true)
        let viewModel = ColdStartDiagnosticViewModel(
            learnerId: learnerId,
            service: mockService,
            cacheStore: cache,
            localeProvider: { "en-US" },
            nowProvider: { clock.now }
        )

        viewModel.startDiagnosticFlow()
        viewModel.declaredExamTarget = .ayt
        viewModel.declaredDailyBudgetMinutes = 10
        viewModel.preferredModality = .socraticCoach
        viewModel.explicitPreferenceOverride = true
        viewModel.beginQuestions()

        clock.now = clock.now.addingTimeInterval(25)
        viewModel.selectOption("A", for: .numberSense)
        await viewModel.advanceToNextQuestionOrSubmit()

        clock.now = clock.now.addingTimeInterval(40)
        viewModel.selectOption("B", for: .quadraticInequality)
        await viewModel.advanceToNextQuestionOrSubmit()

        clock.now = clock.now.addingTimeInterval(75)
        viewModel.selectOption("A", for: .functionComposition)
        await viewModel.advanceToNextQuestionOrSubmit()

        XCTAssertEqual(viewModel.flowStage, .result)
        XCTAssertEqual(mockService.submitCalls.count, 1)
        let submitted = mockService.submitCalls[0].request
        XCTAssertEqual(submitted.declaredExamTarget, "AYT")
        XCTAssertEqual(submitted.declaredDailyBudgetMinutes, 10)
        XCTAssertEqual(submitted.responses.map(\.elapsedSeconds), [25, 40, 75])

        XCTAssertEqual(viewModel.latestResult?.baselineTier, .calibratedMixed)
        XCTAssertEqual(viewModel.latestResult?.llmTokensUsed, 0)
        XCTAssertEqual(viewModel.latestResult?.trainingEligible, false)
        XCTAssertFalse(viewModel.hasPendingOfflineSync)

        let cached = await cache.loadLatestResult(for: learnerId)
        XCTAssertEqual(cached?.correctCount, 2)
    }

    func testOfflineSubmissionQueuesLocallyAndFlushesOnRetry() async {
        let learnerId = UUID(uuidString: "81000000-0000-4000-8000-000000000020")!
        let mockService = MockColdStartDiagnosticService()
        mockService.stubbedSubmitResult = .failure(ColdStartServiceError.offline)
        let cache = ColdStartSQLiteCache(inMemory: true)
        let viewModel = ColdStartDiagnosticViewModel(
            learnerId: learnerId,
            service: mockService,
            cacheStore: cache,
            localeProvider: { "tr-TR" }
        )

        viewModel.startDiagnosticFlow()
        viewModel.beginQuestions()
        viewModel.selectOption("A", for: .numberSense)
        await viewModel.advanceToNextQuestionOrSubmit()
        viewModel.selectOption("A", for: .quadraticInequality)
        await viewModel.advanceToNextQuestionOrSubmit()
        viewModel.selectOption("A", for: .functionComposition)
        await viewModel.advanceToNextQuestionOrSubmit()

        XCTAssertTrue(viewModel.hasPendingOfflineSync)
        XCTAssertEqual(viewModel.syncNoticeKey, "cold_start.offline.saved_message")
        XCTAssertEqual(viewModel.latestResult?.baselineTier, .foundationSolid)
        XCTAssertEqual(viewModel.latestResult?.llmTokensUsed, 0)
        XCTAssertEqual(viewModel.latestResult?.trainingEligible, false)

        let queued = await cache.dequeuePendingSubmission(for: learnerId)
        XCTAssertNotNil(queued)
        XCTAssertEqual(queued?.locale, "tr-TR")

        mockService.stubbedSubmitResult = nil
        await viewModel.flushPendingIfNeeded()

        XCTAssertFalse(viewModel.hasPendingOfflineSync)
        XCTAssertNil(viewModel.syncNoticeKey)
        let remainingQueue = await cache.dequeuePendingSubmission(for: learnerId)
        XCTAssertNil(remainingQueue)
    }

    func testSQLiteCacheRoundTripAndGDPRPurgeIsolatesLearners() async {
        let learnerA = UUID(uuidString: "81000000-0000-4000-8000-000000000031")!
        let learnerB = UUID(uuidString: "81000000-0000-4000-8000-000000000032")!
        let cache = ColdStartSQLiteCache(inMemory: true)

        let requestA = ColdStartDiagnosticRequest(
            idempotencyKey: "coldstart-test-a-001",
            locale: "en-US",
            declaredExamTarget: "TYT",
            declaredDailyBudgetMinutes: 15,
            preferredModality: "MIXED",
            explicitPreferenceOverride: true,
            responses: [
                ColdStartItemSubmission(itemId: .numberSense, selectedOptionId: "A", elapsedSeconds: 20),
                ColdStartItemSubmission(itemId: .quadraticInequality, selectedOptionId: "A", elapsedSeconds: 25),
                ColdStartItemSubmission(itemId: .functionComposition, selectedOptionId: "A", elapsedSeconds: 30)
            ]
        )
        let previewA = ColdStartLocalEvaluator.buildOfflinePreview(learnerId: learnerA, request: requestA)
        let previewB = ColdStartLocalEvaluator.buildOfflinePreview(learnerId: learnerB, request: requestA)

        await cache.saveLatestResult(previewA, for: learnerA)
        await cache.enqueuePendingSubmission(requestA, for: learnerA)
        await cache.saveLatestResult(previewB, for: learnerB)

        await cache.purgeUserData(for: learnerA)

        let purgedLatest = await cache.loadLatestResult(for: learnerA)
        let purgedPending = await cache.dequeuePendingSubmission(for: learnerA)
        let intactB = await cache.loadLatestResult(for: learnerB)
        XCTAssertNil(purgedLatest)
        XCTAssertNil(purgedPending)
        XCTAssertNotNil(intactB)
    }

    func testRevalidationStatusFromServerSurfacedOnViewModel() async {
        let learnerId = UUID(uuidString: "81000000-0000-4000-8000-000000000040")!
        let mockService = MockColdStartDiagnosticService()
        let baseRequest = ColdStartDiagnosticRequest(
            idempotencyKey: "coldstart-reval-001",
            locale: "en-US",
            declaredExamTarget: "SAT",
            declaredDailyBudgetMinutes: 15,
            preferredModality: "SOCRATIC_COACH",
            explicitPreferenceOverride: false,
            daysSinceLastVerifiedAttempt: 35,
            responses: [
                ColdStartItemSubmission(itemId: .numberSense, selectedOptionId: "A", elapsedSeconds: 20),
                ColdStartItemSubmission(itemId: .quadraticInequality, selectedOptionId: "A", elapsedSeconds: 25),
                ColdStartItemSubmission(itemId: .functionComposition, selectedOptionId: "A", elapsedSeconds: 30)
            ]
        )
        let base = ColdStartLocalEvaluator.buildOfflinePreview(learnerId: learnerId, request: baseRequest)
        let revalResult = ColdStartDiagnosticResult(
            diagnosticId: base.diagnosticId,
            learnerId: learnerId,
            locale: "en-US",
            status: .revalidationRequired,
            baselineTier: base.baselineTier,
            correctCount: base.correctCount,
            totalItems: base.totalItems,
            calibrationMode: .revalidationRequired,
            confidenceTier: .preliminary,
            goalConflictResolved: base.goalConflictResolved,
            recommendedDailyBudgetMinutes: base.recommendedDailyBudgetMinutes,
            inactiveDecayApplied: true,
            explicitPreferenceOverride: false,
            declaredExamTarget: base.declaredExamTarget,
            declaredDailyBudgetMinutes: base.declaredDailyBudgetMinutes,
            preferredModality: base.preferredModality,
            revalidationPromptKey: "cold_start.revalidation.banner",
            detectedTrapArchetypes: base.detectedTrapArchetypes,
            itemOutcomes: base.itemOutcomes,
            initializedPriors: base.initializedPriors,
            rationales: base.rationales,
            attemptDurationSeconds: base.attemptDurationSeconds,
            llmTokensUsed: 0,
            trainingEligible: false,
            idempotentReplay: false,
            createdAt: base.createdAt,
            updatedAt: base.updatedAt
        )
        mockService.stubbedFetchResult = .success(revalResult)

        let viewModel = ColdStartDiagnosticViewModel(
            learnerId: learnerId,
            service: mockService,
            cacheStore: ColdStartSQLiteCache(inMemory: true)
        )
        await viewModel.load(daysInactive: 35)

        XCTAssertTrue(viewModel.requiresRevalidation)
        XCTAssertTrue(viewModel.isPreliminaryConfidence)
        XCTAssertEqual(viewModel.latestResult?.inactiveDecayApplied, true)
        XCTAssertEqual(viewModel.latestResult?.revalidationPromptKey, "cold_start.revalidation.banner")
    }
}
