import XCTest
@testable import VerifiedAI

final class Sprint88GamificationTests: XCTestCase {

    func testSummaryFixtureDecodesStrictlyWithSixAchievements() throws {
        let data = try loadSummaryFixtureData()
        let decoded = try GamificationJSONCoding.makeDecoder().decode(GamificationSummaryResponse.self, from: data)

        XCTAssertEqual(decoded.userId, UUID(uuidString: "00000000-0000-4000-8000-000000000880"))
        XCTAssertEqual(decoded.learnerTimezone, "Europe/Istanbul")
        XCTAssertEqual(decoded.currentStreakDays, 3)
        XCTAssertEqual(decoded.longestStreakDays, 5)
        XCTAssertEqual(decoded.streakFreezesRemaining, 1)
        XCTAssertEqual(decoded.maxMonthlyStreakFreezes, 2)
        XCTAssertEqual(decoded.freezesUsedThisMonth, 1)
        XCTAssertEqual(decoded.freezeQuotaMonth, "2026-11")
        XCTAssertEqual(decoded.streakStatus, .activeVerifiedToday)
        XCTAssertTrue(decoded.verifiedToday)
        XCTAssertTrue(decoded.continuityPreservedToday)
        XCTAssertFalse(decoded.trainingEligible)
        XCTAssertEqual(decoded.achievements.count, 6)
        XCTAssertEqual(decoded.recentDailyLedger.count, 3)

        // Default APIClient decoder without explicit dateStrategy also decodes cleanly.
        let defaultDecoded = try JSONDecoder().decode(GamificationSummaryResponse.self, from: data)
        XCTAssertEqual(defaultDecoded.achievements.count, 6)
        XCTAssertFalse(defaultDecoded.trainingEligible)
    }

    func testActivityRequestEncodesExactContractFields() throws {
        let occurredAt = ISO8601DateFormatter().date(from: "2026-11-10T17:15:00Z")
        let request = GamificationActivityRecordRequest(
            idempotencyKey: "00000000-0000-4000-8000-000000000881",
            evidenceType: .independent,
            evidenceReference: "twin_attempt:00000000-0000-4000-8000-000000000882",
            learnerTimezone: "Europe/Istanbul",
            occurredAtUtc: occurredAt,
            conceptId: "algebra.linear_equations.single_variable",
            misconceptionRepaired: true,
            spacedReviewCompleted: false,
            verifiedMasteredConceptCount: 2
        )

        let data = try GamificationJSONCoding.makeEncoder().encode(request)
        let jsonObject = try XCTUnwrap(try JSONSerialization.jsonObject(with: data) as? [String: Any])

        XCTAssertEqual(jsonObject["idempotencyKey"] as? String, "00000000-0000-4000-8000-000000000881")
        XCTAssertEqual(jsonObject["evidenceType"] as? String, "INDEPENDENT")
        XCTAssertEqual(jsonObject["evidenceReference"] as? String, "twin_attempt:00000000-0000-4000-8000-000000000882")
        XCTAssertEqual(jsonObject["learnerTimezone"] as? String, "Europe/Istanbul")
        XCTAssertEqual(jsonObject["occurredAtUtc"] as? String, "2026-11-10T17:15:00Z")
        XCTAssertEqual(jsonObject["conceptId"] as? String, "algebra.linear_equations.single_variable")
        XCTAssertEqual(jsonObject["misconceptionRepaired"] as? Bool, true)
        XCTAssertEqual(jsonObject["spacedReviewCompleted"] as? Bool, false)
        XCTAssertEqual(jsonObject["verifiedMasteredConceptCount"] as? Int, 2)
    }

    @MainActor
    func testIndependentActivityAdvancesVerifiedStreakInViewModel() async throws {
        let mockService = MockGamificationService()
        mockService.nextRecordResult = .success(makeSummary(
            currentStreakDays: 4,
            longestStreakDays: 5,
            freezesRemaining: 2,
            streakStatus: .activeVerifiedToday,
            verifiedToday: true,
            continuityPreservedToday: true,
            streakIncrementedOnEvaluation: true
        ))

        let viewModel = GamificationViewModel(service: mockService)
        await viewModel.recordVerifiedActivity(GamificationActivityRecordRequest(
            idempotencyKey: "ios-indep-key-01",
            evidenceType: .independent,
            evidenceReference: "twin_attempt:01",
            learnerTimezone: "Europe/Istanbul"
        ))

        XCTAssertEqual(viewModel.summary?.currentStreakDays, 4)
        XCTAssertEqual(viewModel.summary?.streakStatus, .activeVerifiedToday)
        XCTAssertEqual(viewModel.summary?.verifiedToday, true)
        XCTAssertEqual(viewModel.summary?.streakIncrementedOnEvaluation, true)
    }

    @MainActor
    func testHintAssistedActivityPreservesContinuityWithoutAdvancingStreakCount() async throws {
        let mockService = MockGamificationService()
        mockService.nextRecordResult = .success(makeSummary(
            currentStreakDays: 2,
            longestStreakDays: 4,
            freezesRemaining: 2,
            streakStatus: .continuityPreservedToday,
            verifiedToday: false,
            continuityPreservedToday: true,
            streakIncrementedOnEvaluation: false,
            lastActivityEvidenceType: .hintAssisted
        ))

        let viewModel = GamificationViewModel(service: mockService)
        await viewModel.recordVerifiedActivity(GamificationActivityRecordRequest(
            idempotencyKey: "ios-hint-key-02",
            evidenceType: .hintAssisted,
            evidenceReference: "attempt:hint:02",
            learnerTimezone: "Europe/Istanbul"
        ))

        XCTAssertEqual(viewModel.summary?.currentStreakDays, 2)
        XCTAssertEqual(viewModel.summary?.streakStatus, .continuityPreservedToday)
        XCTAssertEqual(viewModel.summary?.verifiedToday, false)
        XCTAssertEqual(viewModel.summary?.continuityPreservedToday, true)
        XCTAssertEqual(viewModel.summary?.streakIncrementedOnEvaluation, false)
    }

    @MainActor
    func testSolutionRevealedActivityPreservesContinuityWithoutAdvancingStreakCount() async throws {
        let mockService = MockGamificationService()
        mockService.nextRecordResult = .success(makeSummary(
            currentStreakDays: 2,
            longestStreakDays: 4,
            freezesRemaining: 2,
            streakStatus: .continuityPreservedToday,
            verifiedToday: false,
            continuityPreservedToday: true,
            streakIncrementedOnEvaluation: false,
            lastActivityEvidenceType: .solutionRevealedAssisted
        ))

        let viewModel = GamificationViewModel(service: mockService)
        await viewModel.recordVerifiedActivity(GamificationActivityRecordRequest(
            idempotencyKey: "ios-reveal-key-03",
            evidenceType: .solutionRevealedAssisted,
            evidenceReference: "attempt:reveal:03",
            learnerTimezone: "Europe/Istanbul"
        ))

        XCTAssertEqual(viewModel.summary?.currentStreakDays, 2)
        XCTAssertEqual(viewModel.summary?.lastActivityEvidenceType, .solutionRevealedAssisted)
        XCTAssertEqual(viewModel.summary?.streakIncrementedOnEvaluation, false)
    }

    @MainActor
    func testFreezeProtectedSummaryReflectsRemainingMonthlyFreezes() async throws {
        let mockService = MockGamificationService()
        mockService.nextSummaryResult = .success(makeSummary(
            currentStreakDays: 5,
            longestStreakDays: 5,
            freezesRemaining: 1,
            freezesUsedThisMonth: 1,
            streakStatus: .freezeProtected,
            verifiedToday: false,
            continuityPreservedToday: false,
            freezeAppliedOnEvaluation: true
        ))

        let viewModel = GamificationViewModel(service: mockService)
        await viewModel.loadSummary(timezone: "Europe/Istanbul")

        XCTAssertEqual(viewModel.summary?.streakStatus, .freezeProtected)
        XCTAssertEqual(viewModel.summary?.streakFreezesRemaining, 1)
        XCTAssertEqual(viewModel.summary?.maxMonthlyStreakFreezes, 2)
        XCTAssertEqual(viewModel.summary?.freezesUsedThisMonth, 1)
        XCTAssertEqual(viewModel.summary?.freezeAppliedOnEvaluation, true)
    }

    func testMilestoneAchievementsUnlockAndExposeEvidenceReference() throws {
        let data = try loadSummaryFixtureData()
        let summary = try GamificationJSONCoding.makeDecoder().decode(GamificationSummaryResponse.self, from: data)
        let unlocked = summary.achievements.filter { $0.unlocked }

        XCTAssertEqual(unlocked.count, 3)
        for item in unlocked {
            XCTAssertNotNil(item.achievementId)
            XCTAssertNotNil(item.evidenceReference)
            XCTAssertFalse((item.evidenceReference ?? "").isEmpty)
            XCTAssertNotNil(item.unlockedLocalDate)
            XCTAssertNotNil(item.unlockedAtUtc)
        }
        XCTAssertTrue(summary.newlyUnlockedBadgeCodes.contains(.threeDayIndependentStreak))
        XCTAssertTrue(summary.newlyUnlockedBadgeCodes.contains(.misconceptionOvercome))
    }

    @MainActor
    func testFeatureDisabled503TransitionsViewModelToCalmDegradedState() async throws {
        let mockService = MockGamificationService()
        let problem = ProblemDetails(
            type: "https://verifiedai.com/errors/gamification_feature_disabled",
            title: "Habit streak milestones are resting right now",
            status: 503,
            code: "GAMIFICATION_FEATURE_DISABLED",
            traceId: "trace-88-disabled",
            details: ProblemDetails.RecoveryDetails(recoverable: true, userAction: nil)
        )
        mockService.nextSummaryResult = .failure(NetworkError.server(problem: problem))

        let viewModel = GamificationViewModel(service: mockService)
        await viewModel.loadSummary(timezone: "Europe/Istanbul")

        if case .degraded(let message) = viewModel.state {
            XCTAssertFalse(message.isEmpty)
        } else {
            XCTFail("Expected .degraded state on 503 GAMIFICATION_FEATURE_DISABLED, got \(viewModel.state)")
        }
    }

    @MainActor
    func testPurgeLocalStateClearsAllCachedGamificationDataImmediately() async throws {
        let mockService = MockGamificationService()
        mockService.nextSummaryResult = .success(makeSummary(
            currentStreakDays: 3,
            longestStreakDays: 5,
            freezesRemaining: 2,
            streakStatus: .activeVerifiedToday,
            verifiedToday: true,
            continuityPreservedToday: true
        ))

        let viewModel = GamificationViewModel(service: mockService)
        await viewModel.loadSummary(timezone: "UTC")
        viewModel.isMilestoneSheetPresented = true
        XCTAssertNotNil(viewModel.summary)

        viewModel.purgeLocalState()
        XCTAssertNil(viewModel.summary)
        XCTAssertFalse(viewModel.isMilestoneSheetPresented)
        XCTAssertEqual(viewModel.state, .idle)
    }

    func testNonStigmatizingLocalizationKeysAndAccessibilityIdentifiers() throws {
        for status in GamificationStreakStatus.allCases {
            XCTAssertTrue(status.supportiveHeadlineKey.hasPrefix("gamification.streak.status."))
            XCTAssertTrue(status.supportiveBodyKey.hasPrefix("gamification.streak.status."))
        }
        for code in PedagogicalBadgeCode.allCases {
            XCTAssertTrue(code.titleKey.hasPrefix("gamification.badge."))
            XCTAssertTrue(code.descriptionKey.hasPrefix("gamification.badge."))
        }

        let repoRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let xcstringsURL = repoRoot.appendingPathComponent("apps/ios/VerifiedAI/Resources/Localizable.xcstrings")
        if FileManager.default.fileExists(atPath: xcstringsURL.path) {
            let xcData = try Data(contentsOf: xcstringsURL)
            let xcJson = try XCTUnwrap(try JSONSerialization.jsonObject(with: xcData) as? [String: Any])
            let strings = try XCTUnwrap(xcJson["strings"] as? [String: Any])
            let requiredKeys = [
                "gamification.streak.badge.days_format",
                "gamification.streak.badge.accessibility_label",
                "gamification.streak.badge.accessibility_hint",
                "gamification.sheet.title",
                "gamification.sheet.freezes_remaining_format",
                "gamification.sheet.section.ledger",
                "gamification.sheet.section.achievements"
            ] + GamificationStreakStatus.allCases.flatMap { [$0.supportiveHeadlineKey, $0.supportiveBodyKey] }
              + PedagogicalBadgeCode.allCases.flatMap { [$0.titleKey, $0.descriptionKey] }

            let bannedTerms = ["overdue", "punish", "penalty", "shame", "guilt", "slacker", "lazy", "quitter", "ceza", "utanç", "tembel"]
            for key in requiredKeys {
                let entry = try XCTUnwrap(strings[key] as? [String: Any], "Missing key: \(key)")
                let locs = try XCTUnwrap(entry["localizations"] as? [String: Any])
                for lang in ["en", "tr"] {
                    let langDict = try XCTUnwrap(locs[lang] as? [String: Any])
                    let unit = try XCTUnwrap(langDict["stringUnit"] as? [String: Any])
                    let value = try XCTUnwrap(unit["value"] as? String).lowercased()
                    XCTAssertFalse(value.isEmpty)
                    for banned in bannedTerms {
                        XCTAssertFalse(value.contains(banned), "Banned term '\(banned)' found in \(key)[\(lang)]")
                    }
                }
            }
        }
    }

    private func loadSummaryFixtureData() throws -> Data {
        if let bundleURL = Bundle(for: Self.self).url(
            forResource: "gamification-summary-response.valid",
            withExtension: "json"
        ) {
            return try Data(contentsOf: bundleURL)
        }
        let repoRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .deletingLastPathComponent()
        let fixtureURL = repoRoot.appendingPathComponent("packages/schemas/fixtures/gamification-summary-response.valid.json")
        return try Data(contentsOf: fixtureURL)
    }

    private func makeSummary(
        currentStreakDays: Int,
        longestStreakDays: Int,
        freezesRemaining: Int,
        freezesUsedThisMonth: Int = 0,
        streakStatus: GamificationStreakStatus,
        verifiedToday: Bool,
        continuityPreservedToday: Bool,
        freezeAppliedOnEvaluation: Bool = false,
        streakIncrementedOnEvaluation: Bool = false,
        lastActivityEvidenceType: GamificationEvidenceType? = .independent
    ) -> GamificationSummaryResponse {
        GamificationSummaryResponse(
            userId: UUID(uuidString: "00000000-0000-4000-8000-000000000880")!,
            learnerTimezone: "Europe/Istanbul",
            serverLocalDate: "2026-11-10",
            currentStreakDays: currentStreakDays,
            longestStreakDays: longestStreakDays,
            streakFreezesRemaining: freezesRemaining,
            maxMonthlyStreakFreezes: 2,
            freezesUsedThisMonth: freezesUsedThisMonth,
            freezeQuotaMonth: "2026-11",
            streakStatus: streakStatus,
            verifiedToday: verifiedToday,
            continuityPreservedToday: continuityPreservedToday,
            freezeAppliedOnEvaluation: freezeAppliedOnEvaluation,
            streakIncrementedOnEvaluation: streakIncrementedOnEvaluation,
            lastVerifiedIndependentDate: "2026-11-10",
            lastActivityLocalDate: "2026-11-10",
            lastActivityEvidenceType: lastActivityEvidenceType,
            totalIndependentDays: currentStreakDays,
            totalAssistedContinuityDays: 1,
            supportiveHeadlineKey: streakStatus.supportiveHeadlineKey,
            supportiveBodyKey: streakStatus.supportiveBodyKey,
            recentDailyLedger: [],
            achievements: PedagogicalBadgeCode.allCases.map {
                PedagogicalAchievementItem(
                    badgeCode: $0,
                    badgeCategory: $0.category,
                    titleKey: $0.titleKey,
                    descriptionKey: $0.descriptionKey,
                    unlocked: $0 == .firstIndependentTwin
                )
            },
            newlyUnlockedBadgeCodes: [],
            trainingEligible: false,
            evaluatedAtUtc: Date()
        )
    }
}

private final class MockGamificationService: GamificationServicing, @unchecked Sendable {
    var nextSummaryResult: Result<GamificationSummaryResponse, Error> = .success(.defaultCatalogue())
    var nextRecordResult: Result<GamificationSummaryResponse, Error> = .success(.defaultCatalogue())

    func fetchSummary(timezone: String) async throws -> GamificationSummaryResponse {
        try nextSummaryResult.get()
    }

    func recordActivity(_ request: GamificationActivityRecordRequest) async throws -> GamificationSummaryResponse {
        try nextRecordResult.get()
    }
}
