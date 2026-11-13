import XCTest
@testable import VerifiedAI

final class Sprint83SpacedRepetitionTests: XCTestCase {

    // MARK: - Test 1: JSON Decoding of spaced-queue-v1.valid.json Fixture
    func testSpacedQueueJsonDecoding() throws {
        let fixtureJSON = """
        {
          "schemaVersion": "1.0.0",
          "userId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
          "evaluatedAt": "2026-11-13T08:00:00Z",
          "dailyCap": 3,
          "surfacedCount": 3,
          "interleavedSkillCount": 3,
          "interleavingSatisfied": true,
          "items": [
            {
              "itemId": "11111111-aaaa-bbbb-cccc-111111111111",
              "canonicalSkillCode": "MATH.ALG.QUADRATIC_DISCRIMINANT",
              "skillDisplayName": "Quadratic Discriminant Analysis",
              "triggerType": "TRAP_IMMUNITY_7D",
              "targetMistakeCode": "TRAP_SIGN_DISTRIB_NEG",
              "estimatedRetention": 0.7100,
              "halfLifeDays": 7.0,
              "dueAt": "2026-11-13T08:00:00Z",
              "assignedTwinId": "44444444-aaaa-bbbb-cccc-444444444444",
              "promptPreviewLatex": "\\\\Delta = b^2 - 4ac \\\\text{ for } 2x^2 - 5x - 3 = 0",
              "status": "PENDING",
              "pedagogicalBadgeKey": "studyplan.spaced.badge.trap_immunity_7d"
            },
            {
              "itemId": "22222222-aaaa-bbbb-cccc-222222222222",
              "canonicalSkillCode": "MATH.CALC.CHAIN_RULE_COMPOSITE",
              "skillDisplayName": "Composite Function Chain Rule",
              "triggerType": "RETENTION_DECAY",
              "targetMistakeCode": null,
              "estimatedRetention": 0.6840,
              "halfLifeDays": 4.5,
              "dueAt": "2026-11-13T08:00:00Z",
              "assignedTwinId": null,
              "promptPreviewLatex": "\\\\frac{d}{dx}\\\\left[(3x^2 + 1)^4\\\\right]",
              "status": "PENDING",
              "pedagogicalBadgeKey": "studyplan.spaced.badge.retention_refresh"
            },
            {
              "itemId": "33333333-aaaa-bbbb-cccc-333333333333",
              "canonicalSkillCode": "MATH.TRIG.DOUBLE_ANGLE_IDENTITY",
              "skillDisplayName": "Double Angle Trigonometric Identity",
              "triggerType": "RETENTION_DECAY",
              "targetMistakeCode": null,
              "estimatedRetention": 0.7050,
              "halfLifeDays": 6.0,
              "dueAt": "2026-11-13T08:00:00Z",
              "assignedTwinId": null,
              "promptPreviewLatex": "\\\\sin(2\\\\theta) = 2\\\\sin(\\\\theta)\\\\cos(\\\\theta)",
              "status": "PENDING",
              "pedagogicalBadgeKey": "studyplan.spaced.badge.retention_refresh"
            }
          ]
        }
        """

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let data = fixtureJSON.data(using: .utf8)!
        let response = try decoder.decode(SpacedQueueResponse.self, from: data)

        XCTAssertEqual(response.schemaVersion, "1.0.0")
        XCTAssertEqual(response.dailyCap, 3)
        XCTAssertEqual(response.surfacedCount, 3)
        XCTAssertEqual(response.interleavedSkillCount, 3)
        XCTAssertTrue(response.interleavingSatisfied)
        XCTAssertEqual(response.items.count, 3)

        // Item 1: 7-Day Trap Immunity check
        let item1 = response.items[0]
        XCTAssertEqual(item1.triggerType, .trapImmunity7D)
        XCTAssertEqual(item1.canonicalSkillCode, "MATH.ALG.QUADRATIC_DISCRIMINANT")
        XCTAssertEqual(item1.targetMistakeCode, "TRAP_SIGN_DISTRIB_NEG")
        XCTAssertEqual(item1.retentionPercentageInt, 71)
        XCTAssertEqual(item1.pedagogicalBadgeKey, "studyplan.spaced.badge.trap_immunity_7d")

        // Item 2: Retention decay item
        let item2 = response.items[1]
        XCTAssertEqual(item2.triggerType, .retentionDecay)
        XCTAssertNil(item2.targetMistakeCode)
        XCTAssertEqual(item2.retentionPercentageInt, 68)
        XCTAssertEqual(item2.pedagogicalBadgeKey, "studyplan.spaced.badge.retention_refresh")
    }

    // MARK: - Test 2: SpacedReviewSQLiteCache Round-Trip, BOLA Isolation & Fast Purge
    func testSQLiteCachePersistenceAndFastPurge() async throws {
        let cache = SpacedReviewSQLiteCache(inMemory: true)
        let userA = UUID()
        let userB = UUID()

        let itemA = SpacedQueueItem(
            itemId: UUID(),
            canonicalSkillCode: "MATH.ALG.QUADRATIC_DISCRIMINANT",
            skillDisplayName: "Quadratic Discriminant",
            triggerType: .trapImmunity7D,
            targetMistakeCode: "TRAP_SIGN_DISTRIB_NEG",
            estimatedRetention: 0.7100,
            halfLifeDays: 7.0,
            dueAt: Date(),
            promptPreviewLatex: "\\Delta = b^2 - 4ac"
        )

        let responseA = SpacedQueueResponse(
            userId: userA,
            evaluatedAt: Date(),
            dailyCap: 3,
            surfacedCount: 1,
            interleavedSkillCount: 1,
            interleavingSatisfied: true,
            items: [itemA]
        )

        // Save for User A
        await cache.saveQueue(responseA, for: userA)

        // Read for User A
        let loadedA = await cache.loadQueue(for: userA)
        XCTAssertNotNil(loadedA)
        XCTAssertEqual(loadedA?.items.count, 1)
        XCTAssertEqual(loadedA?.items[0].itemId, itemA.itemId)

        // BOLA check: User B has no records
        let loadedB = await cache.loadQueue(for: userB)
        XCTAssertNil(loadedB, "User B must not access User A cached queue")

        // Fast Single-User Purge SLA (< 50ms)
        let startTime = CFAbsoluteTimeGetCurrent()
        await cache.purgeUserData(for: userA)
        let elapsedMs = (CFAbsoluteTimeGetCurrent() - startTime) * 1000.0
        XCTAssertLessThan(elapsedMs, 50.0, "Purge must complete in under 50ms")

        let purgedA = await cache.loadQueue(for: userA)
        XCTAssertNil(purgedA, "User A cache must be empty after purge")
    }

    // MARK: - Test 3: SpacedReviewViewModel 3-Item Cap & Completion Transitions
    func testViewModelCapAndStateTransitions() async throws {
        let userId = UUID()
        let itemId = UUID()

        // Create 5 items to test client-side defensive 3-item cap
        var fiveItems: [SpacedQueueItem] = []
        for i in 0..<5 {
            fiveItems.append(SpacedQueueItem(
                itemId: UUID(),
                canonicalSkillCode: "MATH.SKILL_\(i)",
                skillDisplayName: "Skill \(i)",
                triggerType: .retentionDecay,
                estimatedRetention: 0.65,
                halfLifeDays: 3.0,
                dueAt: Date(),
                promptPreviewLatex: "x + \(i)"
            ))
        }

        let fiveResponse = SpacedQueueResponse(
            userId: userId,
            evaluatedAt: Date(),
            dailyCap: 3,
            surfacedCount: 5,
            interleavedSkillCount: 5,
            interleavingSatisfied: true,
            items: fiveItems
        )

        let cache = SpacedReviewSQLiteCache(inMemory: true)
        let viewModel = await SpacedReviewViewModel(
            userId: userId,
            cacheStore: cache,
            fetchRemoteHandler: { _ in fiveResponse },
            completeRemoteHandler: { _, id, req in
                let status: MistakeResolutionStatus = req.passed && req.independentVerification ? .stableResolved : .relapsed
                return SpacedItemCompletionResponse(
                    itemId: id,
                    previousHalfLifeDays: 7.0,
                    updatedHalfLifeDays: req.passed ? 15.4 : 3.85,
                    updatedRetention: 0.85,
                    nextDueAt: Date().addingTimeInterval(604800),
                    mistakeResolutionStatus: status
                )
            }
        )

        await viewModel.loadQueue()

        // Strict client-side 3-item cap enforcement
        let loadedItems = await viewModel.items
        XCTAssertEqual(loadedItems.count, 3, "ViewModel must defensively cap active queue to at most 3 items")

        // Complete first item with independent pass -> STABLE_RESOLVED
        let firstItemId = loadedItems[0].itemId
        await viewModel.completeItem(
            itemId: firstItemId,
            passed: true,
            independentVerification: true,
            responseTimeMs: 9000,
            triggeredMistakeCode: nil
        )

        let completionResult = await viewModel.lastCompletionResult
        XCTAssertEqual(completionResult?.mistakeResolutionStatus, .stableResolved)
        XCTAssertEqual(completionResult?.updatedHalfLifeDays, 15.4)

        // Queue size should now be 2
        let remainingItems = await viewModel.items
        XCTAssertEqual(remainingItems.count, 2)
        XCTAssertFalse(remainingItems.contains(where: { $0.itemId == firstItemId }))
    }

    // MARK: - Test 4: NativeMathLabel & Non-Stigmatizing Badge Keys
    @MainActor
    func testNativeMathLabelAndBadgeKeys() throws {
        let itemImmunity = SpacedQueueItem(
            itemId: UUID(),
            canonicalSkillCode: "MATH.ALG.QUADRATIC_DISCRIMINANT",
            skillDisplayName: "Quadratic Discriminant Analysis",
            triggerType: .trapImmunity7D,
            targetMistakeCode: "TRAP_SIGN_DISTRIB_NEG",
            estimatedRetention: 0.7100,
            halfLifeDays: 7.0,
            dueAt: Date(),
            promptPreviewLatex: "\\Delta = b^2 - 4ac"
        )

        // Verify pedagogical badge key
        XCTAssertEqual(itemImmunity.pedagogicalBadgeKey, "studyplan.spaced.badge.trap_immunity_7d")

        // Verify NativeMathLabel instantiation
        let mathLabel = NativeMathLabel(
            latex: itemImmunity.promptPreviewLatex,
            fallback: itemImmunity.promptPreviewLatex,
            pointSize: 18
        )
        XCTAssertEqual(mathLabel.latex, "\\Delta = b^2 - 4ac")
        XCTAssertEqual(mathLabel.pointSize, 18)
    }
}
