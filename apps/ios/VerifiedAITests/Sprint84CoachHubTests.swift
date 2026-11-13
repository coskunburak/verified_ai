import XCTest
@testable import VerifiedAI

final class Sprint84CoachHubTests: XCTestCase {

    // MARK: - Test 1: JSON Decoding of daily-coach-v1.valid.json Fixture
    func testDailyCoachSummaryJsonDecoding() throws {
        let fixtureJSON = """
        {
          "sessionId": "84000000-1111-4444-8888-000000000001",
          "userId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
          "localDate": "2026-11-13",
          "budgetMinutes": 15,
          "status": "IN_PROGRESS",
          "isDownshifted": false,
          "isColdStart": false,
          "completedTasksCount": 0,
          "totalTasksCount": 3,
          "briefingText": "Bugün 15 dakikada 1 aktif tuzak onarımı, 1 aralıklı tekrar ve 1 sınav sentezi göreviyle ivmeni koruyoruz.",
          "preferredMode": "SOCRATIC_COACH_MODE",
          "streakDays": 5,
          "tasks": [
            {
              "taskId": "84000000-2222-4444-8888-000000000001",
              "taskOrder": 1,
              "taskType": "TRAP_REPAIR",
              "targetSkillCode": "MATH.ALG.RATIONAL_EQ",
              "targetMistakeCode": "ALG_DOMAIN_EXCLUSION_MISSED",
              "assignedTwinId": "twin_84_a1b2c3d4e5f6",
              "estimatedMinutes": 5,
              "whyThisTaskRationale": "growth_opportunity: Rasyonel sadeleştirme adımlarında payda kısıtını (x != 2) doğrulamak AYT netlerini güçlendirir.",
              "promptPreviewLatex": "\\\\frac{x^2 - 4}{x - 2} = x + 2, \\\\quad x \\\\neq 2",
              "status": "PENDING",
              "completedAt": null
            },
            {
              "taskId": "84000000-2222-4444-8888-000000000002",
              "taskOrder": 2,
              "taskType": "SPACED_RETENTION",
              "targetSkillCode": "MATH.ALG.QUADRATIC_DISCRIMINANT",
              "targetMistakeCode": "TRAP_SIGN_DISTRIB_NEG",
              "assignedTwinId": "twin_84_b2c3d4e5f6a1",
              "estimatedMinutes": 5,
              "whyThisTaskRationale": "immunity_check: 7 günlük kalıcılık kontrolü ile diskriminant işaret dağılımını pekiştiriyoruz.",
              "promptPreviewLatex": "2x^2 - 5x - 3 = 0 \\\\implies \\\\Delta = (-5)^2 - 4(2)(-3)",
              "status": "PENDING",
              "completedAt": null
            },
            {
              "taskId": "84000000-2222-4444-8888-000000000003",
              "taskOrder": 3,
              "taskType": "EXAM_SYNTHESIS",
              "targetSkillCode": "MATH.CALC.CHAIN_RULE_COMPOSITE",
              "targetMistakeCode": null,
              "assignedTwinId": "twin_84_c3d4e5f6a1b2",
              "estimatedMinutes": 5,
              "whyThisTaskRationale": "retention_refresh: AYT yüksek getirili bileşke fonksiyon türevi sentezi.",
              "promptPreviewLatex": "\\\\frac{d}{dx}\\\\left[(3x^2 + 1)^4\\\\right] = 24x(3x^2 + 1)^3",
              "status": "PENDING",
              "completedAt": null
            }
          ],
          "activeTrapRadar": [
            {
              "skillCode": "MATH.ALG.RATIONAL_EQ",
              "mistakeCode": "ALG_DOMAIN_EXCLUSION_MISSED",
              "title": "Payda Tanım Kümesi Kısıtı",
              "badgeKey": "growth_opportunity",
              "statusLabel": "active_trap_pattern"
            }
          ],
          "coldStartDemo": null
        }
        """

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let data = try XCTUnwrap(fixtureJSON.data(using: .utf8))
        let summary = try decoder.decode(DailyCoachSummaryModel.self, from: data)

        XCTAssertEqual(summary.localDate, "2026-11-13")
        XCTAssertEqual(summary.budgetMinutes, 15)
        XCTAssertEqual(summary.status, .inProgress)
        XCTAssertFalse(summary.isDownshifted)
        XCTAssertFalse(summary.isColdStart)
        XCTAssertEqual(summary.preferredMode, .socraticCoachMode)
        XCTAssertEqual(summary.tasks.count, 3)
        XCTAssertEqual(summary.tasks[0].taskType, .trapRepair)
        XCTAssertEqual(summary.tasks[1].taskType, .spacedRetention)
        XCTAssertEqual(summary.tasks[2].taskType, .examSynthesis)
        XCTAssertEqual(summary.activeTrapRadar.count, 1)
    }

    // MARK: - Test 2: Cold-Start Synthesis & Offline Fallback
    @MainActor
    func testColdStartFallbackAndRenderingState() async {
        let userId = UUID()
        let cache = DailyCoachSQLiteCache(inMemory: true)
        let viewModel = DailyCoachViewModel(userId: userId, cacheStore: cache)

        await viewModel.loadTodaySummary(localDate: "2026-11-13")

        XCTAssertNotNil(viewModel.summary)
        XCTAssertTrue(viewModel.isOfflineBannerVisible)
        XCTAssertEqual(viewModel.summary?.isColdStart, true)
        XCTAssertEqual(viewModel.summary?.budgetMinutes, 15)
        XCTAssertEqual(viewModel.summary?.tasks.count, 3)
        XCTAssertNotNil(viewModel.summary?.coldStartDemo)
        XCTAssertFalse(viewModel.summary?.coldStartDemo?.samplePromptLatex.isEmpty ?? true)
    }

    // MARK: - Test 3: Dual-Mode Switcher Persistence (SOLVER_MODE <-> SOCRATIC_COACH_MODE)
    @MainActor
    func testDualModePersistenceInSQLiteCache() async {
        let userId = UUID()
        let cache = DailyCoachSQLiteCache(inMemory: true)
        let viewModel = DailyCoachViewModel(userId: userId, cacheStore: cache)

        await viewModel.loadTodaySummary(localDate: "2026-11-13")
        XCTAssertEqual(viewModel.selectedMode, .socraticCoachMode)

        await viewModel.toggleMode(.solverMode)
        XCTAssertEqual(viewModel.selectedMode, .solverMode)
        XCTAssertEqual(viewModel.summary?.preferredMode, .solverMode)

        let reloaded = await cache.loadSummary(for: userId, localDate: "2026-11-13")
        XCTAssertEqual(reloaded?.preferredMode, .solverMode)

        await viewModel.toggleMode(.socraticCoachMode)
        let reloadedAgain = await cache.loadSummary(for: userId, localDate: "2026-11-13")
        XCTAssertEqual(reloadedAgain?.preferredMode, .socraticCoachMode)
    }

    // MARK: - Test 4: 5-Minute Busy-Day Downshift Contraction
    @MainActor
    func testBusyDayDownshiftContractsMissionToSinglePriorityTask() async {
        let userId = UUID()
        let cache = DailyCoachSQLiteCache(inMemory: true)
        let initialSummary = DailyCoachSummaryModel(
            sessionId: UUID(),
            userId: userId,
            localDate: "2026-11-13",
            budgetMinutes: 15,
            status: .inProgress,
            isDownshifted: false,
            isColdStart: false,
            completedTasksCount: 0,
            totalTasksCount: 3,
            briefingText: "Bugün 15 dakikada 3 görev hazır.",
            preferredMode: .socraticCoachMode,
            streakDays: 4,
            tasks: [
                DailyCoachTaskModel(
                    taskId: UUID(),
                    taskOrder: 1,
                    taskType: .trapRepair,
                    targetSkillCode: "MATH.ALG.RATIONAL_EQ",
                    targetMistakeCode: "ALG_DOMAIN_EXCLUSION_MISSED",
                    assignedTwinId: "twin_84_a1",
                    estimatedMinutes: 5,
                    whyThisTaskRationale: "Öncelik 1: payda tanım kümesi.",
                    promptPreviewLatex: "\\frac{x^2 - 9}{x - 3} = 6",
                    status: .pending
                ),
                DailyCoachTaskModel(
                    taskId: UUID(),
                    taskOrder: 2,
                    taskType: .spacedRetention,
                    targetSkillCode: "MATH.ALG.QUADRATIC_DISCRIMINANT",
                    targetMistakeCode: nil,
                    assignedTwinId: "twin_84_b2",
                    estimatedMinutes: 5,
                    whyThisTaskRationale: "Öncelik 2: kalıcılık tekrar ikizi.",
                    promptPreviewLatex: "x^2 - 4x + 4 = 0",
                    status: .pending
                ),
                DailyCoachTaskModel(
                    taskId: UUID(),
                    taskOrder: 3,
                    taskType: .examSynthesis,
                    targetSkillCode: "MATH.CALC.CHAIN_RULE_COMPOSITE",
                    targetMistakeCode: nil,
                    assignedTwinId: "twin_84_c3",
                    estimatedMinutes: 5,
                    whyThisTaskRationale: "Öncelik 3: sınav sentezi.",
                    promptPreviewLatex: "\\frac{d}{dx}(2x+1)^3",
                    status: .pending
                )
            ],
            activeTrapRadar: [],
            coldStartDemo: nil
        )
        await cache.saveSummary(initialSummary, for: userId)

        let viewModel = DailyCoachViewModel(userId: userId, cacheStore: cache)
        await viewModel.loadTodaySummary(localDate: "2026-11-13")
        await viewModel.triggerBusyDayDownshift()

        XCTAssertEqual(viewModel.summary?.budgetMinutes, 5)
        XCTAssertEqual(viewModel.summary?.isDownshifted, true)
        XCTAssertEqual(viewModel.summary?.totalTasksCount, 1)
        XCTAssertEqual(viewModel.summary?.tasks[0].status, .pending)
        XCTAssertEqual(viewModel.summary?.tasks[1].status, .skipped)
        XCTAssertEqual(viewModel.summary?.tasks[2].status, .skipped)
    }

    // MARK: - Test 5: SQLite Cache Round-Trip Latency (< 180 ms) & Privacy Purge
    func testSQLiteCacheRoundTripLatencyAndPurge() async {
        let userId = UUID()
        let cache = DailyCoachSQLiteCache(inMemory: true)
        let summary = await MainActor.run {
            DailyCoachViewModel.deterministicColdStartSummary(userId: userId, localDate: "2026-11-13")
        }

        let start = CFAbsoluteTimeGetCurrent()
        await cache.saveSummary(summary, for: userId)
        let loaded = await cache.loadSummary(for: userId, localDate: "2026-11-13")
        let elapsedMs = (CFAbsoluteTimeGetCurrent() - start) * 1000.0

        XCTAssertEqual(loaded?.sessionId, summary.sessionId)
        XCTAssertLessThan(elapsedMs, 180.0, "Offline SQLite round-trip must stay below 180 ms")

        await cache.purgeUserData(for: userId)
        let afterPurge = await cache.loadSummary(for: userId, localDate: "2026-11-13")
        XCTAssertNil(afterPurge)
    }
}
