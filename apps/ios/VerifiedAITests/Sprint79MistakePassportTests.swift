// apps/ios/VerifiedAITests/Sprint79MistakePassportTests.swift
import XCTest
@testable import VerifiedAI

final class Sprint79MistakePassportTests: XCTestCase {

    // MARK: - Mocks & Fixtures

    final class MockAPIClient: MistakePassportAPIClientProtocol, @unchecked Sendable {
        var stubbedResponse: MistakePassportResponseDto?
        var stubbedError: Error?
        var fetchCallCount = 0
        var lastStatusFilter: TrapLifecycleStatus?
        var delayNanoseconds: UInt64 = 0

        func fetchMistakePassport(status: TrapLifecycleStatus?) async throws -> MistakePassportResponseDto {
            fetchCallCount += 1
            lastStatusFilter = status
            if delayNanoseconds > 0 {
                try? await Task.sleep(nanoseconds: delayNanoseconds)
            }
            if let error = stubbedError {
                throw error
            }
            guard let response = stubbedResponse else {
                throw NSError(domain: "MockAPIClient", code: -1, userInfo: nil)
            }
            return response
        }
    }

    private func makeSampleResponse(
        conqueredCount: Int = 2,
        activeCount: Int = 1,
        conceptualCount: Int = 1,
        arithmeticCount: Int = 1
    ) -> MistakePassportResponseDto {
        let metrics = PassportSummaryMetrics(
            conqueredExamTrapsCount: conqueredCount,
            activeExamTrapsCount: activeCount,
            pendingRetentionVerificationCount: 0,
            independentTransferRate: 0.85,
            meanPosteriorMastery: 0.75,
            meanRetentionProbability: 0.70
        )

        let fiveMetrics = SkillFiveMetricsSnapshot(
            observedOutcomeScore: 1.0,
            independentCompetenceScore: 0.85,
            posteriorMasteryProbability: 0.75,
            retentionProbability: 0.70,
            scaffoldDependencyIndex: 0.15
        )

        let conceptualItems = (0..<conceptualCount).map { i in
            ExamTrapItemDto(
                mistakeDnaId: UUID(),
                mistakeCode: "ALG_SIGN_DISTRIBUTION_\(i)",
                mistakeFamily: "CONCEPTUAL_STRUCTURAL",
                skillNodeId: "MATH.ALGEBRA.SIGNS",
                localizedTrapTitleKey: "trap.alg_sign_distribution.title",
                localizedCoachingFramingKey: "trap.alg_sign_distribution.coaching_framing",
                lifecycleStatus: .recurringPattern,
                occurrenceCount: 2,
                consecutiveSoloTwinPasses: 1,
                lastDetectedAt: Date(),
                skillMetrics: fiveMetrics
            )
        }

        let arithmeticItems = (0..<arithmeticCount).map { i in
            ExamTrapItemDto(
                mistakeDnaId: UUID(),
                mistakeCode: "ARITH_BASIC_COMPUTATION",
                mistakeFamily: "ARITHMETIC_SLIP",
                skillNodeId: "MATH.ARITHMETIC.INTEGERS",
                localizedTrapTitleKey: "trap.arith_basic_computation.title",
                localizedCoachingFramingKey: "trap.arith_basic_computation.coaching_framing",
                lifecycleStatus: .recurringPattern,
                occurrenceCount: 3,
                consecutiveSoloTwinPasses: 0,
                lastDetectedAt: Date(),
                skillMetrics: fiveMetrics
            )
        }

        return MistakePassportResponseDto(
            schemaVersion: "mistake-passport.v1",
            learnerId: UUID(),
            generatedAt: Date(),
            summaryMetrics: metrics,
            conceptualTraps: conceptualItems,
            arithmeticSlips: arithmeticItems
        )
    }

    // MARK: - State Machine Tests

    @MainActor
    func testViewModelInitialStateIsIdle() {
        let client = MockAPIClient()
        let store = MistakePassportCacheStore()
        let vm = MistakePassportViewModel(apiClient: client, cacheStore: store)

        XCTAssertEqual(vm.state, .idle)
        XCTAssertEqual(vm.selectedCategoryTab, .conceptualTraps)
        XCTAssertNil(vm.selectedStatusFilter)
    }

    @MainActor
    func testSuccessfulFetchTransitionsToLoaded() async {
        let client = MockAPIClient()
        let sample = makeSampleResponse()
        client.stubbedResponse = sample
        let store = MistakePassportCacheStore()
        let vm = MistakePassportViewModel(apiClient: client, cacheStore: store)

        vm.loadPassport()
        // Wait a short moment for async Task execution
        try? await Task.sleep(nanoseconds: 50_000_000)

        if case let .loaded(response, isOfflineStale) = vm.state {
            XCTAssertEqual(response.schemaVersion, "mistake-passport.v1")
            XCTAssertFalse(isOfflineStale)
            XCTAssertEqual(vm.activeItems(from: response).count, 1)
        } else {
            XCTFail("Expected .loaded state, got \(vm.state)")
        }
    }

    @MainActor
    func testEmptyResponseTransitionsToEmptyState() async {
        let client = MockAPIClient()
        let emptySample = makeSampleResponse(conceptualCount: 0, arithmeticCount: 0)
        client.stubbedResponse = emptySample
        let store = MistakePassportCacheStore()
        let vm = MistakePassportViewModel(apiClient: client, cacheStore: store)

        vm.loadPassport()
        try? await Task.sleep(nanoseconds: 50_000_000)

        if case let .empty(filter) = vm.state {
            XCTAssertNil(filter)
        } else {
            XCTFail("Expected .empty state, got \(vm.state)")
        }
    }

    @MainActor
    func testErrorWithNoCacheTransitionsToErrorState() async {
        let client = MockAPIClient()
        client.stubbedError = NSError(domain: "Network", code: -1009)
        let store = MistakePassportCacheStore()
        let vm = MistakePassportViewModel(apiClient: client, cacheStore: store)

        vm.loadPassport()
        try? await Task.sleep(nanoseconds: 50_000_000)

        if case let .error(key) = vm.state {
            XCTAssertEqual(key, "passport.error.load_failed")
        } else {
            XCTFail("Expected .error state, got \(vm.state)")
        }
    }

    @MainActor
    func testErrorWithCachedSnapshotFallsBackToOfflineStale() async {
        let client = MockAPIClient()
        let cached = makeSampleResponse(conceptualCount: 2)
        let store = MistakePassportCacheStore()
        await store.savePassportSnapshot(cached, statusFilter: nil)

        // API fails
        client.stubbedError = NSError(domain: "Network", code: -1009)
        let vm = MistakePassportViewModel(apiClient: client, cacheStore: store)

        vm.loadPassport()
        try? await Task.sleep(nanoseconds: 50_000_000)

        if case let .loaded(response, isOfflineStale) = vm.state {
            XCTAssertTrue(isOfflineStale)
            XCTAssertEqual(response.conceptualTraps.count, 2)
        } else {
            XCTFail("Expected .loaded(isOfflineStale: true), got \(vm.state)")
        }
    }

    // MARK: - Generation Fencing & Race Condition Protection

    @MainActor
    func testGenerationFencingDiscardsDelayedOutOfOrderResponses() async {
        let client = MockAPIClient()
        let store = MistakePassportCacheStore()
        let vm = MistakePassportViewModel(apiClient: client, cacheStore: store)

        let fastResponse = makeSampleResponse(conceptualCount: 1)
        client.stubbedResponse = fastResponse

        // First request is fired with delay
        client.delayNanoseconds = 100_000_000 // 100ms
        vm.loadPassport(statusFilter: .recurringPattern)

        // Immediately fire second request with no delay
        client.delayNanoseconds = 0
        let secondResponse = makeSampleResponse(conceptualCount: 3)
        client.stubbedResponse = secondResponse
        vm.loadPassport(statusFilter: .stableResolved)

        try? await Task.sleep(nanoseconds: 150_000_000)

        // The state MUST reflect the second request (.stableResolved with 3 items), not the first
        XCTAssertEqual(vm.selectedStatusFilter, .stableResolved)
        if case let .loaded(response, _) = vm.state {
            XCTAssertEqual(response.conceptualTraps.count, 3)
        } else {
            XCTFail("Expected .loaded with second request data, got \(vm.state)")
        }
    }

    // MARK: - Logout Cache Purge

    @MainActor
    func testLogoutPurgeCancelsTasksAndWipesCache() async {
        let client = MockAPIClient()
        let sample = makeSampleResponse()
        let store = MistakePassportCacheStore()
        await store.savePassportSnapshot(sample, statusFilter: nil)

        let vm = MistakePassportViewModel(apiClient: client, cacheStore: store)
        await vm.handleUserLogoutPurge()

        XCTAssertEqual(vm.state, .idle)
        let cachedAfterLogout = await store.loadCachedPassportSnapshot(statusFilter: nil)
        XCTAssertNil(cachedAfterLogout)
    }

    // MARK: - Category Switcher

    @MainActor
    func testCategoryTabSwitchingPartitionsView() async {
        let client = MockAPIClient()
        let sample = makeSampleResponse(conceptualCount: 2, arithmeticCount: 1)
        client.stubbedResponse = sample
        let store = MistakePassportCacheStore()
        let vm = MistakePassportViewModel(apiClient: client, cacheStore: store)

        vm.loadPassport()
        try? await Task.sleep(nanoseconds: 50_000_000)

        // Default tab is conceptualTraps
        XCTAssertEqual(vm.selectedCategoryTab, .conceptualTraps)
        XCTAssertEqual(vm.activeItems(from: sample).count, 2)

        // Switch to arithmeticSlips
        vm.switchCategoryTab(.arithmeticSlips)
        XCTAssertEqual(vm.selectedCategoryTab, .arithmeticSlips)
        XCTAssertEqual(vm.activeItems(from: sample).count, 1)
        XCTAssertEqual(vm.activeItems(from: sample).first?.mistakeCode, "ARITH_BASIC_COMPUTATION")
    }

    // MARK: - Counterexample Proof Card Snapshot Verification

    func testCounterexampleSnapshotBilingualSpeech() {
        let snapshot = CounterexampleProofSnapshot(
            cardId: "H-01",
            faultyStepLatex: "(a+b)^2 = a^2 + b^2",
            validStepLatex: "(a+b)^2 = a^2 + 2ab + b^2",
            witnessSubstitutionLatex: "a = 2, b = 3",
            lhsEvaluatedLatex: "(2+3)^2 = 25",
            rhsFaultyEvaluatedLatex: "2^2 + 3^2 = 13",
            contradictionStatementLatex: "25 \\neq 13",
            accessibilitySpeechTr: "Sol taraf 25, sağ taraf 13 olur. 25 eşit değildir 13.",
            accessibilitySpeechEn: "Left side is 25, right side is 13. 25 does not equal 13."
        )

        XCTAssertEqual(snapshot.cardId, "H-01")
        XCTAssertTrue(snapshot.accessibilitySpeechTr.contains("25 eşit değildir 13"))
        XCTAssertTrue(snapshot.accessibilitySpeechEn.contains("25 does not equal 13"))
        XCTAssertEqual(snapshot.witnessSubstitutionLatex, "a = 2, b = 3")
    }

    // MARK: - Dual Ring Mastery View Model Verification

    func testDualRingFiveMetricsSeparation() {
        let metrics = SkillFiveMetricsSnapshot(
            observedOutcomeScore: 0.80,
            independentCompetenceScore: 0.85,
            posteriorMasteryProbability: 0.90,
            retentionProbability: 0.75,
            scaffoldDependencyIndex: 0.10
        )

        XCTAssertEqual(metrics.observedOutcomeScore, 0.80)
        XCTAssertEqual(metrics.independentCompetenceScore, 0.85)
        XCTAssertEqual(metrics.posteriorMasteryProbability, 0.90)
        XCTAssertEqual(metrics.retentionProbability, 0.75)
        XCTAssertEqual(metrics.scaffoldDependencyIndex, 0.10)
    }
}
