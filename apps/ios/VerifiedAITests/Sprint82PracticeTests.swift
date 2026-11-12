import XCTest
@testable import VerifiedAI

final class Sprint82PracticeTests: XCTestCase {

    // MARK: - Test 1: JSON Decoding of practice-session-v1.valid.json
    func testPracticeSessionJsonDecoding() throws {
        let fixtureJSON = """
        {
          "id": "11111111-2222-3333-4444-555555555555",
          "userId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
          "sourceRecommendationId": "bbbbbbbb-cccc-dddd-eeee-ffffffffffff",
          "sessionMode": "DAILY_1_2_1",
          "status": "IN_PROGRESS",
          "targetSkillId": "MATH.ALG.RATIONAL_EQ",
          "activeTrapFamily": "ALG_DOMAIN_EXCLUSION_MISSED",
          "learnerMasterySnapshot": 0.5500,
          "totalItems": 4,
          "completedItems": 0,
          "verifiedCorrectItems": 0,
          "trapClosureAchieved": false,
          "startedAt": "2026-11-12T10:00:00Z",
          "completedAt": null,
          "abandonedAt": null,
          "updatedAt": "2026-11-12T10:00:00Z",
          "items": [
            {
              "id": "22222222-3333-4444-5555-666666666666",
              "sessionId": "11111111-2222-3333-4444-555555555555",
              "userId": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
              "ordinalIndex": 0,
              "slotRole": "TRAP_CLOSURE",
              "questionSource": "DETERMINISTIC_TWIN_TIER1",
              "sourceQuestionId": "TWIN-ALG-RATIONAL-001",
              "skillId": "MATH.ALG.RATIONAL_EQ",
              "trapFamily": "ALG_DOMAIN_EXCLUSION_MISSED",
              "targetDifficulty": 0.5000,
              "itemDifficulty": 0.4900,
              "zpdWindow": {
                "lowerBound": 0.4000,
                "upperBound": 0.6000,
                "targetDifficulty": 0.5000
              },
              "promptLatex": "\\\\frac{2x}{x-3} + \\\\frac{4}{x-5} = \\\\frac{6}{x-3}",
              "promptInstructionTr": "Denklemi çözünüz.",
              "promptInstructionEn": "Solve the equation.",
              "canonicalSolutionExpr": "3",
              "trapDistractorExpr": "{3, 5}",
              "sympyVerificationHash": "a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0",
              "isTwinVerification": true,
              "lockState": {
                "isLocked": true,
                "lockDurationSeconds": 60,
                "remainingLockSeconds": 60,
                "unlockedAt": null
              },
              "hintsUnlockedCount": 0,
              "attemptCount": 0,
              "itemStatus": "PENDING",
              "submittedAnswerLatex": null,
              "detectedTrapTriggered": false,
              "verificationDurationMs": null,
              "submittedAt": null,
              "createdAt": "2026-11-12T10:00:00Z"
            }
          ]
        }
        """

        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        let data = fixtureJSON.data(using: .utf8)!
        let session = try decoder.decode(PracticeSession.self, from: data)

        XCTAssertEqual(session.id, UUID(uuidString: "11111111-2222-3333-4444-555555555555"))
        XCTAssertEqual(session.sessionMode, .daily121)
        XCTAssertEqual(session.status, .inProgress)
        XCTAssertEqual(session.items.count, 1)
        XCTAssertEqual(session.items[0].slotRole, .trapClosure)
        XCTAssertEqual(session.items[0].questionSource, .deterministicTwinTier1)
        XCTAssertTrue(session.items[0].isTwinVerification)
        XCTAssertEqual(session.items[0].lockState.remainingLockSeconds, 60)
    }

    // MARK: - Test 2: 60-Second Countdown State Transitions
    @MainActor
    func testCountdownStateTransitions() async {
        let userId = UUID()
        let vm = PracticeSessionViewModel(userId: userId)
        await vm.loadOrCreateSession(targetSkillId: "MATH.ALG.RATIONAL_EQ", sessionMode: .daily121)

        XCTAssertTrue(vm.isLockActive)
        XCTAssertEqual(vm.remainingLockSeconds, 60)
        XCTAssertEqual(vm.urgencyPhase, .calm)

        // Tick down to 30s (focused phase transition)
        for _ in 0..<30 {
            vm.tickVerificationTimer()
        }
        XCTAssertEqual(vm.remainingLockSeconds, 30)
        XCTAssertEqual(vm.urgencyPhase, .focused)

        // Tick down to 10s (finalCountdown phase transition)
        for _ in 0..<20 {
            vm.tickVerificationTimer()
        }
        XCTAssertEqual(vm.remainingLockSeconds, 10)
        XCTAssertEqual(vm.urgencyPhase, .finalCountdown)

        // Tick down to 0s (unlocked phase transition)
        for _ in 0..<10 {
            vm.tickVerificationTimer()
        }
        XCTAssertEqual(vm.remainingLockSeconds, 0)
        XCTAssertFalse(vm.isLockActive)
        XCTAssertEqual(vm.urgencyPhase, .unlocked)
    }

    // MARK: - Test 3: Hint Lock Gating During Verification Window
    @MainActor
    func testHintLockGating() async {
        let userId = UUID()
        var hintRequestedRemotely = false

        let vm = PracticeSessionViewModel(
            userId: userId,
            submitRemoteHandler: { sessionId, itemId, answer, elapsed, hintRequested in
                if hintRequested {
                    hintRequestedRemotely = true
                }
                throw NSError(domain: "test", code: 1, userInfo: nil)
            }
        )
        await vm.loadOrCreateSession(targetSkillId: "MATH.ALG.RATIONAL_EQ")

        XCTAssertTrue(vm.isLockActive)

        // While lock is active, requesting a hint should be ignored locally
        await vm.requestHint()
        XCTAssertFalse(hintRequestedRemotely)
    }

    // MARK: - Test 4: PracticeSQLiteCache Persistence and User Purge
    func testSQLiteCachePersistenceAndUserPurge() async {
        let cache = PracticeSQLiteCache(inMemory: true)
        let userId = UUID()
        let sessionId = UUID()

        let item = PracticeSessionItem(
            id: UUID(),
            sessionId: sessionId,
            userId: userId,
            ordinalIndex: 0,
            slotRole: .trapClosure,
            questionSource: .deterministicTwinTier1,
            sourceQuestionId: "Q1",
            skillId: "MATH.ALG.RATIONAL_EQ",
            trapFamily: "ALG_DOMAIN_EXCLUSION_MISSED",
            targetDifficulty: 0.5000,
            itemDifficulty: 0.4900,
            zpdWindow: ZpdWindow(lowerBound: 0.4000, upperBound: 0.6000, targetDifficulty: 0.5000),
            promptLatex: "\\frac{x}{x-2} = 3",
            promptInstructionTr: "Çöz",
            promptInstructionEn: "Solve",
            canonicalSolutionExpr: "3",
            trapDistractorExpr: "{2, 3}",
            sympyVerificationHash: "a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0",
            isTwinVerification: true,
            lockState: VerificationLockState(isLocked: true, lockDurationSeconds: 60, remainingLockSeconds: 60),
            hintsUnlockedCount: 0,
            attemptCount: 0,
            itemStatus: .pending,
            submittedAnswerLatex: nil,
            detectedTrapTriggered: false,
            verificationDurationMs: nil,
            submittedAt: nil,
            createdAt: Date()
        )

        let session = PracticeSession(
            id: sessionId,
            userId: userId,
            sourceRecommendationId: nil,
            sessionMode: .daily121,
            status: .inProgress,
            targetSkillId: "MATH.ALG.RATIONAL_EQ",
            activeTrapFamily: "ALG_DOMAIN_EXCLUSION_MISSED",
            learnerMasterySnapshot: 0.5500,
            totalItems: 4,
            completedItems: 0,
            verifiedCorrectItems: 0,
            trapClosureAchieved: false,
            startedAt: Date(),
            completedAt: nil,
            abandonedAt: nil,
            updatedAt: Date(),
            items: [item]
        )

        await cache.saveSession(session, for: userId)

        let loaded = await cache.loadLatestActiveSession(for: userId)
        XCTAssertNotNil(loaded)
        XCTAssertEqual(loaded?.id, sessionId)
        XCTAssertEqual(loaded?.items.count, 1)

        // Test user purge completing under 50ms SLA
        let start = Date()
        await cache.purgeAllPracticeData(for: userId)
        let durationMs = Date().timeIntervalSince(start) * 1000.0
        XCTAssertLessThan(durationMs, 50.0, "Purge duration exceeded 50ms SLA: \(durationMs)ms")

        let afterPurge = await cache.loadLatestActiveSession(for: userId)
        XCTAssertNil(afterPurge)
    }

    // MARK: - Test 5: ADR-010 Zero-WebView Verification
    func testZeroWebViewVerification() {
        let practiceFiles = [
            "apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSessionModels.swift",
            "apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSQLiteCache.swift",
            "apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSessionViewModel.swift",
            "apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeQuestionCardView.swift",
            "apps/ios/VerifiedAI/Features/StudyPlan/Practice/PracticeSessionView.swift"
        ]

        let forbiddenTerms = ["WebKit", "WKWebView", "UIWebView", "SFSafariViewController"]

        for file in practiceFiles {
            let url = URL(fileURLWithPath: file)
            if let content = try? String(contentsOf: url, encoding: .utf8) {
                for forbidden in forbiddenTerms {
                    XCTAssertFalse(content.contains(forbidden), "Forbidden symbol \(forbidden) found in \(file)")
                }
            }
        }
    }
}
