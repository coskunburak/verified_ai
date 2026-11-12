import XCTest
@testable import VerifiedAI

final class Sprint81RecommendationTests: XCTestCase {

    // MARK: - Test 1: JSON Decode / Encode against contract
    func testLearnerRecommendationsJsonDecoding() throws {
        let fixtureJSON = """
        {
          "userId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
          "generatedAt": "2026-11-12T09:00:00Z",
          "deterministicEngineVersion": "NBA-v1.0",
          "llmTokenCostUsd": 0.0,
          "recommendations": [
            {
              "id": "c1111111-1111-1111-1111-111111111111",
              "userId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
              "recommendationType": "PREREQUISITE_REPAIR",
              "targetSkillCode": "MATH.CALC.LIMITS.BASIC",
              "targetMistakeCode": null,
              "priorityScore": 78.5000,
              "rationaleCode": "PREREQUISITE_REPAIR",
              "rationaleParams": {
                "prerequisiteSkillCode": "MATH.CALC.LIMITS.BASIC",
                "blockedSkillCode": "MATH.CALC.DERIVATIVES.CHAIN_RULE",
                "currentMastery": 0.4200,
                "requiredMasteryThreshold": 0.5000
              },
              "status": "ACTIVE",
              "expiresAt": "2026-11-13T09:00:00Z",
              "createdAt": "2026-11-12T09:00:00Z"
            },
            {
              "id": "c2222222-2222-2222-2222-222222222222",
              "userId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
              "recommendationType": "MISTAKE_REPAIR",
              "targetSkillCode": "MATH.ALG.QUADRATICS.FACTORING",
              "targetMistakeCode": "SIGN_DISTRIBUTION_TRAP",
              "priorityScore": 64.2500,
              "rationaleCode": "ACTIVE_RECURRING_TRAP",
              "rationaleParams": {
                "skillCode": "MATH.ALG.QUADRATICS.FACTORING",
                "mistakeCode": "SIGN_DISTRIBUTION_TRAP",
                "mistakePropensity": 72.5000,
                "unresolvedTrapCount": 3
              },
              "status": "ACTIVE",
              "expiresAt": "2026-11-13T09:00:00Z",
              "createdAt": "2026-11-12T09:00:00Z"
            },
            {
              "id": "c3333333-3333-3333-3333-333333333333",
              "userId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
              "recommendationType": "SPACED_RETENTION",
              "targetSkillCode": "MATH.TRIG.IDENTITIES.PYTHAGOREAN",
              "targetMistakeCode": null,
              "priorityScore": 51.0000,
              "rationaleCode": "RETENTION_HALF_LIFE_DUE",
              "rationaleParams": {
                "skillCode": "MATH.TRIG.IDENTITIES.PYTHAGOREAN",
                "currentRetention": 0.4500,
                "retentionThreshold": 0.7500,
                "halfLifeDays": 14.5
              },
              "status": "ACTIVE",
              "expiresAt": "2026-11-13T09:00:00Z",
              "createdAt": "2026-11-12T09:00:00Z"
            },
            {
              "id": "c4444444-4444-4444-4444-444444444444",
              "userId": "a1b2c3d4-e5f6-7a8b-9c0d-1e2f3a4b5c6d",
              "recommendationType": "HIGH_ROI_TOPIC",
              "targetSkillCode": "MATH.STAT.PROBABILITY.BAYES",
              "targetMistakeCode": null,
              "priorityScore": 38.7500,
              "rationaleCode": "HIGH_EXAM_NET_ROI",
              "rationaleParams": {
                "skillCode": "MATH.STAT.PROBABILITY.BAYES",
                "examCode": "AYT_MAT_2026",
                "examWeight": 0.8500,
                "estimatedNetGain": 1.7500,
                "opportunityAreaScore": 65.0000
              },
              "status": "ACTIVE",
              "expiresAt": "2026-11-13T09:00:00Z",
              "createdAt": "2026-11-12T09:00:00Z"
            }
          ]
        }
        """

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601

        let data = fixtureJSON.data(using: .utf8)!
        let response = try decoder.decode(LearnerRecommendationsResponseDto.self, from: data)

        XCTAssertEqual(response.deterministicEngineVersion, "NBA-v1.0")
        XCTAssertEqual(response.llmTokenCostUsd, 0.0)
        XCTAssertEqual(response.recommendations.count, 4)

        let r1 = response.recommendations[0]
        XCTAssertEqual(r1.recommendationType, .prerequisiteRepair)
        XCTAssertEqual(r1.targetSkillCode, "MATH.CALC.LIMITS.BASIC")
        XCTAssertEqual(r1.priorityScore, 78.5000)
        XCTAssertEqual(r1.urgencyLevel, .high)
        XCTAssertTrue(r1.explainableRationaleText.contains("MATH.CALC.DERIVATIVES.CHAIN_RULE"))

        let r2 = response.recommendations[1]
        XCTAssertEqual(r2.recommendationType, .mistakeRepair)
        XCTAssertEqual(r2.targetMistakeCode, "SIGN_DISTRIBUTION_TRAP")
        XCTAssertEqual(r2.urgencyLevel, .medium)

        let r3 = response.recommendations[2]
        XCTAssertEqual(r3.recommendationType, .spacedRetention)
        XCTAssertEqual(r3.urgencyLevel, .medium)

        let r4 = response.recommendations[3]
        XCTAssertEqual(r4.recommendationType, .highRoiTopic)
        XCTAssertEqual(r4.urgencyLevel, .standard)
    }

    // MARK: - Test 2: Priority Score Ordering
    func testPriorityScoreDescendingOrder() throws {
        let rec1 = LearnerRecommendationDto(
            id: UUID(),
            userId: UUID(),
            recommendationType: .prerequisiteRepair,
            targetSkillCode: "SKILL_A",
            priorityScore: 85.0,
            rationaleCode: .prerequisiteRepair,
            rationaleParams: RecommendationRationaleParams(),
            expiresAt: Date().addingTimeInterval(86400),
            createdAt: Date()
        )
        let rec2 = LearnerRecommendationDto(
            id: UUID(),
            userId: UUID(),
            recommendationType: .mistakeRepair,
            targetSkillCode: "SKILL_B",
            priorityScore: 60.0,
            rationaleCode: .activeRecurringTrap,
            rationaleParams: RecommendationRationaleParams(),
            expiresAt: Date().addingTimeInterval(86400),
            createdAt: Date()
        )
        let rec3 = LearnerRecommendationDto(
            id: UUID(),
            userId: UUID(),
            recommendationType: .highRoiTopic,
            targetSkillCode: "SKILL_C",
            priorityScore: 40.0,
            rationaleCode: .highExamNetRoi,
            rationaleParams: RecommendationRationaleParams(),
            expiresAt: Date().addingTimeInterval(86400),
            createdAt: Date()
        )

        let list = [rec2, rec3, rec1]
        let sorted = list.sorted(by: { $0.priorityScore > $1.priorityScore })

        XCTAssertEqual(sorted[0].targetSkillCode, "SKILL_A")
        XCTAssertEqual(sorted[1].targetSkillCode, "SKILL_B")
        XCTAssertEqual(sorted[2].targetSkillCode, "SKILL_C")
    }

    // MARK: - Test 3: CoachHubViewModel Accept and Dismiss Transitions
    func testCoachHubViewModelStateTransitions() async throws {
        let userId = UUID()
        let recId = UUID()
        let item = LearnerRecommendationDto(
            id: recId,
            userId: userId,
            recommendationType: .mistakeRepair,
            targetSkillCode: "MATH.ALG.FACTORING",
            targetMistakeCode: "SIGN_TRAP",
            priorityScore: 70.0,
            rationaleCode: .activeRecurringTrap,
            rationaleParams: RecommendationRationaleParams(),
            expiresAt: Date().addingTimeInterval(86400),
            createdAt: Date()
        )

        let inMemoryCache = RecommendationSQLiteCache(inMemory: true)
        await inMemoryCache.saveRecommendations([item], for: userId)

        let viewModel = await CoachHubViewModel(
            userId: userId,
            cacheStore: inMemoryCache,
            fetchRemoteHandler: { _, _, _ in [item] }
        )

        await viewModel.loadRecommendations(forceRefresh: true)
        let countAfterLoad = await viewModel.recommendations.count
        XCTAssertEqual(countAfterLoad, 1)

        // Accept recommendation
        await viewModel.acceptRecommendation(recId)
        let countAfterAccept = await viewModel.recommendations.count
        XCTAssertEqual(countAfterAccept, 0)

        let activeRoute = await viewModel.activeSessionRoute
        XCTAssertNotNil(activeRoute)
        XCTAssertEqual(activeRoute?.recommendationId, recId)
        XCTAssertEqual(activeRoute?.nextSessionType, "TWIN_REMEDIATION")
    }

    // MARK: - Test 4: Offline SQLite Cache Fallback on Network Error
    func testOfflineCacheFallback() async throws {
        let userId = UUID()
        let rec1 = LearnerRecommendationDto(
            id: UUID(),
            userId: userId,
            recommendationType: .prerequisiteRepair,
            targetSkillCode: "MATH.CALC.LIMITS",
            priorityScore: 82.0,
            rationaleCode: .prerequisiteRepair,
            rationaleParams: RecommendationRationaleParams(),
            expiresAt: Date().addingTimeInterval(86400),
            createdAt: Date()
        )
        let rec2 = LearnerRecommendationDto(
            id: UUID(),
            userId: userId,
            recommendationType: .spacedRetention,
            targetSkillCode: "MATH.TRIG.IDENTITIES",
            priorityScore: 55.0,
            rationaleCode: .retentionHalfLifeDue,
            rationaleParams: RecommendationRationaleParams(),
            expiresAt: Date().addingTimeInterval(86400),
            createdAt: Date()
        )

        let inMemoryCache = RecommendationSQLiteCache(inMemory: true)
        await inMemoryCache.saveRecommendations([rec1, rec2], for: userId)

        // Simulate network failure by injecting a failing fetch handler
        let viewModel = await CoachHubViewModel(
            userId: userId,
            cacheStore: inMemoryCache,
            fetchRemoteHandler: { _, _, _ in
                throw URLError(.notConnectedToInternet)
            }
        )

        await viewModel.loadRecommendations()

        let isFallback = await viewModel.isOfflineFallback
        let recs = await viewModel.recommendations

        XCTAssertTrue(isFallback, "Expected offline fallback mode to be activated")
        XCTAssertEqual(recs.count, 2)
        XCTAssertEqual(recs[0].targetSkillCode, "MATH.CALC.LIMITS")
        XCTAssertEqual(recs[1].targetSkillCode, "MATH.TRIG.IDENTITIES")
    }
}
