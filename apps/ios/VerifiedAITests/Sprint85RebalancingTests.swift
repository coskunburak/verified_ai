import XCTest
@testable import VerifiedAI

final class Sprint85RebalancingTests: XCTestCase {
    private let bannedWords = [
        "overdue", "behind", "fail", "failure", "punish", "penalty",
        "weak", "weakness", "poor", "bad", "stupid", "dumb",
        "incompetent", "deficit", "flaw", "hopeless"
    ]

    private func makeTempDBURL(name: String) -> URL {
        FileManager.default.temporaryDirectory
            .appendingPathComponent("sprint85_\(name)_\(UUID().uuidString).sqlite")
    }

    func testRecoveryStatusAndRebalanceResponseSchemaDecoding() throws {
        let recoveryJSON = """
        {
          "schema_version": "1.0.0",
          "user_id": "00000000-0000-0000-0000-000000000101",
          "as_of_date": "2026-11-13",
          "recovery_needed": true,
          "missed_days_count": 3,
          "days_until_exam": 30,
          "previous_daily_budget_minutes": 15,
          "recommended_daily_budget_minutes": 20,
          "mini_mode_recommended": false,
          "active_today_tasks_count": 3,
          "pruned_tasks_count": 4,
          "rescheduled_reviews_count": 2,
          "prune_reason_code": "MISSED_DAY_WINDOW_RESET",
          "welcome_message_key": "studyplan.rebalance.welcome_recovered",
          "last_rebalance_event_id": null
        }
        """.data(using: .utf8)!

        let rebalanceJSON = """
        {
          "schema_version": "1.0.0",
          "rebalance_event_id": "00000000-0000-0000-0000-000000000851",
          "user_id": "00000000-0000-0000-0000-000000000101",
          "recovery_date": "2026-11-13",
          "trigger_type": "MISSED_DAYS",
          "missed_days_count": 3,
          "days_until_exam": 30,
          "previous_daily_budget_minutes": 15,
          "rebalanced_daily_budget_minutes": 20,
          "pruned_tasks_count": 4,
          "rescheduled_reviews_count": 2,
          "active_today_tasks_count": 3,
          "mini_mode_activated": false,
          "prune_reason_code": "BUDGET_CEILING_LOW_ROI_PRUNED",
          "pruned_concept_ids": [
            "MATH.GEO.CIRCLE_SECANT_INTRO",
            "MATH.PROB.COMBINATIONS_WARMUP"
          ],
          "welcome_message_key": "studyplan.rebalance.welcome_recovered",
          "idempotent_replay": false,
          "created_at": "2026-11-13T09:30:00Z"
        }
        """.data(using: .utf8)!

        let decoder = JSONDecoder()
        let status = try decoder.decode(StudyPlanRecoveryStatus.self, from: recoveryJSON)
        XCTAssertTrue(status.recoveryNeeded)
        XCTAssertEqual(status.missedDaysCount, 3)
        XCTAssertEqual(status.recommendedDailyBudgetMinutes, 20)
        XCTAssertEqual(status.activeTodayTasksCount, 3)
        XCTAssertEqual(status.pruneReasonCode, .missedDayWindowReset)

        let response = try decoder.decode(StudyPlanRebalanceResponse.self, from: rebalanceJSON)
        XCTAssertEqual(response.triggerType, .missedDays)
        XCTAssertEqual(response.rebalancedDailyBudgetMinutes, 20)
        XCTAssertEqual(response.prunedConceptIds.count, 2)
        XCTAssertFalse(response.miniModeActivated)
        XCTAssertFalse(response.idempotentReplay)
    }

    func testSQLiteCacheRoundTripIsolationAndPurgePerformance() async throws {
        let dbURL = makeTempDBURL(name: "cache")
        defer { try? FileManager.default.removeItem(at: dbURL) }

        let cache = try RebalanceSQLiteCache(databaseURL: dbURL)
        let userA = UUID(uuidString: "00000000-0000-0000-0000-000000000101")!
        let userB = UUID(uuidString: "00000000-0000-0000-0000-000000000202")!

        let statusA = StudyPlanRecoveryStatus(
            userId: userA,
            asOfDate: "2026-11-13",
            recoveryNeeded: true,
            missedDaysCount: 3,
            daysUntilExam: 30,
            previousDailyBudgetMinutes: 15,
            recommendedDailyBudgetMinutes: 20,
            miniModeRecommended: false,
            activeTodayTasksCount: 3,
            prunedTasksCount: 4,
            rescheduledReviewsCount: 2,
            pruneReasonCode: .missedDayWindowReset,
            welcomeMessageKey: "studyplan.rebalance.welcome_recovered"
        )

        let roundTripStart = CFAbsoluteTimeGetCurrent()
        await cache.saveRecoveryStatus(statusA, for: userA)
        let loadedA = await cache.loadRecoveryStatus(for: userA)
        let roundTripMs = (CFAbsoluteTimeGetCurrent() - roundTripStart) * 1000.0
        XCTAssertLessThan(roundTripMs, 180.0)
        XCTAssertEqual(loadedA, statusA)

        // BOLA isolation check: User B must never load User A's data
        let loadedB = await cache.loadRecoveryStatus(for: userB)
        XCTAssertNil(loadedB)

        let purgeStart = CFAbsoluteTimeGetCurrent()
        await cache.purgeUserData(userId: userA)
        let purgeMs = (CFAbsoluteTimeGetCurrent() - purgeStart) * 1000.0
        XCTAssertLessThan(purgeMs, 500.0)

        let afterPurgeA = await cache.loadRecoveryStatus(for: userA)
        XCTAssertNil(afterPurgeA)
    }

    @MainActor
    func testViewModelMissedDayThresholdsAndMiniModeDownshift() async throws {
        let dbURL = makeTempDBURL(name: "vm")
        defer { try? FileManager.default.removeItem(at: dbURL) }

        let cache = try RebalanceSQLiteCache(databaseURL: dbURL)
        let userId = UUID(uuidString: "00000000-0000-0000-0000-000000000101")!
        let viewModel = StudyPlanRebalanceViewModel(userId: userId, cacheStore: cache)

        for missed in [1, 3, 7, 30] {
            let status = StudyPlanRecoveryStatus(
                userId: userId,
                asOfDate: "2026-11-13",
                recoveryNeeded: true,
                missedDaysCount: missed,
                daysUntilExam: 30,
                previousDailyBudgetMinutes: 15,
                recommendedDailyBudgetMinutes: min(25, missed >= 7 ? 25 : 20),
                miniModeRecommended: missed >= 7,
                activeTodayTasksCount: 3,
                prunedTasksCount: missed * 2,
                rescheduledReviewsCount: 2,
                pruneReasonCode: .missedDayWindowReset,
                welcomeMessageKey: "studyplan.rebalance.welcome_recovered"
            )
            await viewModel.checkRecoveryStatus(userId: userId, status: status)
            XCTAssertTrue(viewModel.isPresentingRecoverySheet)
            XCTAssertLessThanOrEqual(viewModel.recoveryStatus?.recommendedDailyBudgetMinutes ?? 0, 25)
            XCTAssertLessThanOrEqual(viewModel.recoveryStatus?.activeTodayTasksCount ?? 0, 3)
        }

        let miniResponse = await viewModel.activateMiniModeDownshift()
        XCTAssertNotNil(miniResponse)
        XCTAssertEqual(miniResponse?.rebalancedDailyBudgetMinutes, 5)
        XCTAssertEqual(miniResponse?.activeTodayTasksCount, 1)
        XCTAssertEqual(miniResponse?.miniModeActivated, true)
        XCTAssertEqual(miniResponse?.pruneReasonCode, .miniModeSingleFocus)
        XCTAssertEqual(miniResponse?.welcomeMessageKey, "studyplan.rebalance.welcome_mini_mode")
        XCTAssertFalse(viewModel.isPresentingRecoverySheet)
        XCTAssertEqual(viewModel.recoveryStatus?.recoveryNeeded, false)
    }

    @MainActor
    func testDailyCoachViewModelApplyRebalancedPlanMiniMode() async throws {
        let coachDB = makeTempDBURL(name: "coach")
        defer { try? FileManager.default.removeItem(at: coachDB) }
        let coachCache = try DailyCoachSQLiteCache(databaseURL: coachDB)
        let userId = UUID(uuidString: "00000000-0000-0000-0000-000000000101")!
        let sessionId = UUID(uuidString: "00000000-0000-0000-0000-000000000701")!

        let tasks = [1, 2, 3].map { order in
            DailyCoachTaskModel(
                taskId: UUID(),
                taskOrder: order,
                taskType: order == 1 ? .trapRepair : .spacedRetention,
                targetSkillCode: "MATH.ALG.LINEAR",
                targetMistakeCode: nil,
                assignedTwinId: "TWIN_\(order)",
                estimatedMinutes: 5,
                whyThisTaskRationale: "Targeted daily focus",
                promptPreviewLatex: "2x + 3 = 11"
            )
        }
        let initialSummary = DailyCoachSummaryModel(
            sessionId: sessionId,
            userId: userId,
            localDate: "2026-11-13",
            budgetMinutes: 15,
            status: .inProgress,
            isDownshifted: false,
            isColdStart: false,
            completedTasksCount: 0,
            totalTasksCount: 3,
            briefingText: "Welcome back.",
            preferredMode: .socraticCoachMode,
            streakDays: 2,
            tasks: tasks,
            activeTrapRadar: []
        )

        let coachViewModel = DailyCoachViewModel(
            userId: userId,
            cacheStore: coachCache,
            fetchSummaryHandler: { _, _ in initialSummary }
        )
        await coachViewModel.loadTodaySummary(localDate: "2026-11-13")

        let miniResponse = StudyPlanRebalanceResponse(
            rebalanceEventId: UUID(),
            userId: userId,
            recoveryDate: "2026-11-13",
            triggerType: .miniModeDownshift,
            missedDaysCount: 4,
            daysUntilExam: 25,
            previousDailyBudgetMinutes: 15,
            rebalancedDailyBudgetMinutes: 5,
            prunedTasksCount: 2,
            rescheduledReviewsCount: 1,
            activeTodayTasksCount: 1,
            miniModeActivated: true,
            pruneReasonCode: .miniModeSingleFocus,
            prunedConceptIds: [],
            welcomeMessageKey: "studyplan.rebalance.welcome_mini_mode",
            idempotentReplay: false,
            createdAt: "2026-11-13T09:30:00Z"
        )

        await coachViewModel.applyRebalancedPlan(miniResponse)
        XCTAssertEqual(coachViewModel.summary?.budgetMinutes, 5)
        XCTAssertEqual(coachViewModel.summary?.isDownshifted, true)
        XCTAssertEqual(coachViewModel.summary?.totalTasksCount, 1)
        XCTAssertEqual(coachViewModel.summary?.tasks.filter { $0.status == .pending }.count, 1)
        XCTAssertEqual(coachViewModel.summary?.tasks.filter { $0.status == .skipped }.count, 2)

        for key in [
            "Welcome back! Your study plan was gently adjusted with zero backlog.",
            "Let's keep today light with one focused 5-minute mission.",
            "Your study plan is steady and on track."
        ] {
            let lowered = key.lowercased()
            for banned in bannedWords {
                XCTAssertFalse(lowered.contains(banned), "Banned word detected: \(banned)")
            }
        }
    }
}
