import XCTest
@testable import VerifiedAI

private actor MockMicroLessonClient: MicroLessonClientProtocol {
    var nextFetchResult: Result<MicroLessonCard, Error>
    var nextCompleteResult: Result<MicroLessonCompleteResponse, Error>
    private(set) var submittedRequests: [MicroLessonCompleteRequest] = []

    init(
        nextFetchResult: Result<MicroLessonCard, Error>,
        nextCompleteResult: Result<MicroLessonCompleteResponse, Error>
    ) {
        self.nextFetchResult = nextFetchResult
        self.nextCompleteResult = nextCompleteResult
    }

    func fetchMicroLesson(
        mistakeCode: String,
        locale: String,
        triggerSource: MicroLessonTriggerSource,
        sourceAttemptId: UUID?
    ) async throws -> MicroLessonCard {
        try nextFetchResult.get()
    }

    func completeMicroLesson(
        mistakeCode: String,
        request: MicroLessonCompleteRequest
    ) async throws -> MicroLessonCompleteResponse {
        submittedRequests.append(request)
        return try nextCompleteResult.get()
    }

    func setCompleteResult(_ result: Result<MicroLessonCompleteResponse, Error>) {
        self.nextCompleteResult = result
    }
}

final class Sprint86MicroLessonTests: XCTestCase {
    private let userA = UUID(uuidString: "00000000-0000-4000-8650-000000000001")!
    private let userB = UUID(uuidString: "00000000-0000-4000-8650-000000000002")!

    private let sampleCardJSON = """
    {
      "schemaVersion": "1.0.0",
      "cardId": "00000000-0000-4000-8601-000000000001",
      "mistakeCode": "ALG_SIGN_FLIP_DISTRIBUTE",
      "locale": "tr-TR",
      "title": "Parantez Önündeki Eksi İşaretini Dağıtma",
      "focusBadgeLabel": "Odak Noktası · İşaret Dağılımı",
      "estimatedDurationSeconds": 90,
      "triggerSource": "SOCRATIC_RUNG_2",
      "triggeredByAttemptId": null,
      "coreRule": {
        "ruleTitle": "Eksi Çarpan Her Terime Dağılır",
        "conciseExplanation": "Parantez dışındaki negatif katsayı tüm terimlerin işaretini tersine çevirir.",
        "canonicalLatexFormula": "-a(b - c) = -ab + ac",
        "sympyCheckExpr": "-a*(b - c) == -a*b + a*c",
        "estimatedSeconds": 30
      },
      "brokenVsValidCounterexample": {
        "promptLatex": "-2(x - 3)",
        "commonTrapLatex": "-2x - 6",
        "whyTrapFailsExplanation": "(-2)·(-3) = +6 olmalıdır.",
        "verifiedStepLatex": "-2x + 6",
        "numericCounterexampleLatex": "x = 1 => -2(1 - 3) = 4 != -8",
        "sympyEquivalenceCheckExpr": "simplify(-2*(x - 3) - (-2*x + 6)) == 0",
        "sympyTargetExpr": "-2*(x - 3)",
        "sympyValidExpr": "-2*x + 6",
        "sympyTrapExpr": "-2*x - 6",
        "counterexampleBindings": {"x": 1},
        "estimatedSeconds": 30
      },
      "interactiveQuickCheck": {
        "questionPrompt": "-3(2x - 5) ifadesinin eşdeğer açılımı hangisidir?",
        "questionLatex": "-3(2x - 5)",
        "options": [
          {"optionId": "A", "latexOrText": "-6x - 15", "rationale": "İkinci terim +15 olur.", "isCorrect": false},
          {"optionId": "B", "latexOrText": "-6x + 15", "rationale": "Harika! Her iki terim doğru dağıtıldı.", "isCorrect": true},
          {"optionId": "C", "latexOrText": "6x - 15", "rationale": "İlk terim -6x olur.", "isCorrect": false}
        ],
        "correctOptionId": "B",
        "estimatedSeconds": 30
      },
      "verificationStatus": "VERIFIED",
      "verifierVersion": "sympy-1.12-v1",
      "verificationHash": "231efa2277b1cfb64c04e417d52bf31ef2e2c5b1dc99ad24c1f835b8da96b2c0"
    }
    """

    private func makeTempDBURL(_ suffix: String) -> URL {
        FileManager.default.temporaryDirectory
            .appendingPathComponent("sprint86_\(suffix)_\(UUID().uuidString).sqlite")
    }

    func testCardAndCompletionDecodingAndIntegrityGuards() throws {
        let cardData = Data(sampleCardJSON.utf8)
        let card = try JSONDecoder().decode(MicroLessonCard.self, from: cardData)

        XCTAssertEqual(card.mistakeCode, "ALG_SIGN_FLIP_DISTRIBUTE")
        XCTAssertTrue(card.isDurationBounded)
        XCTAssertTrue(card.hasValidHexHash)
        XCTAssertTrue(card.hasZeroExternalMediaUrls)
        XCTAssertTrue(card.passesLocalIntegrityChecks)

        let tamperedCard = MicroLessonCard(
            cardId: card.cardId,
            mistakeCode: card.mistakeCode,
            locale: card.locale,
            title: card.title,
            focusBadgeLabel: card.focusBadgeLabel,
            estimatedDurationSeconds: 90,
            coreRule: card.coreRule,
            brokenVsValidCounterexample: card.brokenVsValidCounterexample,
            interactiveQuickCheck: card.interactiveQuickCheck,
            verificationStatus: "REJECTED",
            verifierVersion: card.verifierVersion,
            verificationHash: "not-a-hex-hash"
        )
        XCTAssertFalse(tamperedCard.passesLocalIntegrityChecks)
    }

    func testSQLiteCacheIsolationPendingQueueAndFastPurge() async throws {
        let dbURL = makeTempDBURL("cache")
        defer { try? FileManager.default.removeItem(at: dbURL) }

        let cache = MicroLessonSQLiteCache(dbURL: dbURL)
        let card = try JSONDecoder().decode(MicroLessonCard.self, from: Data(sampleCardJSON.utf8))

        try await cache.saveCard(card, userId: userA)
        let loadedA = try await cache.loadCard(userId: userA, mistakeCode: "ALG_SIGN_FLIP_DISTRIBUTE", locale: "tr-TR")
        let loadedB = try await cache.loadCard(userId: userB, mistakeCode: "ALG_SIGN_FLIP_DISTRIBUTE", locale: "tr-TR")
        XCTAssertEqual(loadedA?.cardId, card.cardId)
        XCTAssertNil(loadedB)

        let pendingReq = MicroLessonCompleteRequest(
            locale: "tr-TR",
            triggerSource: .socraticRung2,
            sourceAttemptId: nil,
            selectedOptionId: "B",
            dwellTimeMs: 42_000,
            idempotencyKey: "ios-offline-key-001"
        )
        try await cache.enqueuePendingCompletion(PendingMicroLessonCompletion(userId: userA, mistakeCode: "ALG_SIGN_FLIP_DISTRIBUTE", request: pendingReq))
        let queued = try await cache.pendingCompletions(userId: userA)
        XCTAssertEqual(queued.count, 1)

        let start = CFAbsoluteTimeGetCurrent()
        try await cache.purgeUserData(userId: userA)
        let elapsedMs = (CFAbsoluteTimeGetCurrent() - start) * 1000.0

        XCTAssertLessThan(elapsedMs, 500.0)
        let afterPurgeCard = try await cache.loadCard(userId: userA, mistakeCode: "ALG_SIGN_FLIP_DISTRIBUTE", locale: "tr-TR")
        let afterPurgeQueue = try await cache.pendingCompletions(userId: userA)
        XCTAssertNil(afterPurgeCard)
        XCTAssertTrue(afterPurgeQueue.isEmpty)
    }

    @MainActor
    func testViewModelThreeBeatFlowOfflineFallbackAndFailClosedGuard() async throws {
        let dbURL = makeTempDBURL("vm")
        defer { try? FileManager.default.removeItem(at: dbURL) }
        let cache = MicroLessonSQLiteCache(dbURL: dbURL)
        let card = try JSONDecoder().decode(MicroLessonCard.self, from: Data(sampleCardJSON.utf8))
        let receipt = MicroLessonCompleteResponse(
            completionId: UUID(),
            cardId: card.cardId,
            mistakeCode: card.mistakeCode,
            locale: card.locale,
            quickCheckPassed: true,
            dwellTimeMs: 5000,
            completedAt: Date(),
            idempotentReplay: false,
            trainingEligible: false
        )

        let client = MockMicroLessonClient(nextFetchResult: .success(card), nextCompleteResult: .success(receipt))
        let vm = MicroLessonViewModel(userId: userA, client: client, cache: cache)

        await vm.load(mistakeCode: "ALG_SIGN_FLIP_DISTRIBUTE", locale: "tr-TR", triggerSource: .socraticRung2)
        XCTAssertEqual(vm.currentBeat, .coreRule)
        XCTAssertFalse(vm.shouldFallbackToHints)

        vm.nextBeat()
        XCTAssertEqual(vm.currentBeat, .counterexample)
        vm.nextBeat()
        XCTAssertEqual(vm.currentBeat, .quickCheck)

        vm.selectedOptionId = "B"
        await vm.submitQuickCheck()
        XCTAssertTrue(vm.hasSubmittedQuickCheck)
        XCTAssertTrue(vm.quickCheckPassed)
        XCTAssertEqual(vm.completionReceipt?.trainingEligible, false)

        // Fail closed to hints if server reports verification failure
        let failClient = MockMicroLessonClient(
            nextFetchResult: .failure(MicroLessonClientError.verificationFailed),
            nextCompleteResult: .failure(MicroLessonClientError.verificationFailed)
        )
        let failVM = MicroLessonViewModel(userId: userA, client: failClient, cache: cache)
        await failVM.load(mistakeCode: "ALG_SIGN_FLIP_DISTRIBUTE", locale: "tr-TR", triggerSource: .socraticRung2)
        XCTAssertNil(failVM.card)
        XCTAssertTrue(failVM.shouldFallbackToHints)
    }
}
