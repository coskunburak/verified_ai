import Foundation
import SwiftUI
import XCTest
@testable import VerifiedAI

@MainActor
final class LearningOsDowngradePresentationTests: XCTestCase {
    private let userId = UUID(uuidString: "00000000-0000-4000-8000-000000000601")!

    func testDowngradeBannersActionGatesAndMistakePassportFit320ptAndAccessibility3() async {
        let vm = makeAccessViewModel(
            tier: .free,
            status: .expired,
            caps: [.attemptVerify, .deterministicTwin]
        )
        await vm.bootstrap(force: true)

        var downgradedPassport = MistakePassportResponseDto(
            learnerId: userId,
            summaryMetrics: PassportSummaryMetrics(
                conqueredExamTrapsCount: 1,
                activeExamTrapsCount: 2,
                pendingRetentionVerificationCount: 0,
                independentTransferRate: 0.75,
                meanPosteriorMastery: 0.68,
                meanRetentionProbability: 0.64
            ),
            conceptualTraps: [],
            arithmeticSlips: []
        )
        downgradedPassport.summaryTrapTitleKeys = ["trap.ineq_negative_mult_no_flip.title"]
        downgradedPassport.earnedProofs = [
            CounterexampleProofSnapshot(
                faultyStepLatex: "(a+b)^2 = a^2 + b^2",
                validStepLatex: "(a+b)^2 = a^2 + 2ab + b^2",
                witnessSubstitutionLatex: "a = 2, b = 3",
                lhsEvaluatedLatex: "25",
                rhsFaultyEvaluatedLatex: "13",
                contradictionStatementLatex: "25 \\neq 13",
                accessibilitySpeechTr: "Yirmi beş eşit değildir on üç",
                accessibilitySpeechEn: "Twenty-five is not equal to thirteen"
            )
        ]

        let passportVM = MistakePassportViewModel(
            apiClient: StubPassportAPI(response: downgradedPassport),
            cacheStore: MistakePassportCacheStore()
        )
        passportVM.loadPassport()
        for _ in 0..<50 {
            if case .loaded = passportVM.state { break }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        guard case .loaded = passportVM.state else {
            return XCTFail("Expected MistakePassportViewModel to enter .loaded state for downgraded summary")
        }

        for size in [DynamicTypeSize.xSmall, .accessibility3] {
            for locale in ["en-US", "tr-TR"] {
                for scheme in [ColorScheme.light, .dark] {
                    assertFits320(
                        LearningOsStatusBannerView(key: "billing.historyRetained"),
                        size: size, locale: locale, scheme: scheme, label: "statusBanner"
                    )
                    assertFits320(
                        Button("Start") {}
                            .modifier(LearningOsActionGate(capability: .dailyCoachMission))
                            .environment(\.learningOsAccess, vm),
                        size: size, locale: locale, scheme: scheme, label: "actionGate"
                    )
                    assertFits320(
                        MistakePassportView(viewModel: passportVM)
                            .environment(\.learningOsAccess, vm),
                        size: size, locale: locale, scheme: scheme, label: "mistakePassport"
                    )
                }
            }
        }
    }

    func testForeignMistakePassportResponseIsSuppressedAndTimersBlockPaywall() async {
        let vm = makeAccessViewModel(
            tier: .free,
            status: .active,
            caps: [.attemptVerify, .deterministicTwin]
        )
        await vm.bootstrap(force: true)

        var foreignDto = MistakePassportResponseDto(
            learnerId: UUID(uuidString: "00000000-0000-4000-8000-000000000999")!,
            summaryMetrics: PassportSummaryMetrics(
                conqueredExamTrapsCount: 3, activeExamTrapsCount: 1, pendingRetentionVerificationCount: 0,
                independentTransferRate: 0.9, meanPosteriorMastery: 0.85, meanRetentionProbability: 0.8
            ),
            conceptualTraps: [],
            arithmeticSlips: []
        )
        foreignDto.summaryTrapTitleKeys = ["trap.foreign.secret"]
        let foreignPassportVM = MistakePassportViewModel(
            apiClient: StubPassportAPI(response: foreignDto),
            cacheStore: MistakePassportCacheStore()
        )
        foreignPassportVM.loadPassport()
        for _ in 0..<50 {
            if case .loaded = foreignPassportVM.state { break }
            try? await Task.sleep(nanoseconds: 10_000_000)
        }
        assertFits320(
            MistakePassportView(viewModel: foreignPassportVM).environment(\.learningOsAccess, vm),
            size: .large, locale: "en-US", scheme: .light, label: "foreignGuard"
        )

        let coordinator = PaywallTriggerCoordinator()
        let now = Date(timeIntervalSince1970: 2_000)
        for flow in [PaywallTriggerCoordinator.Flow.exam, .diagnostic, .twin] {
            coordinator.update(flow, .active)
            XCTAssertFalse(coordinator.safeToPresent)
            XCTAssertFalse(coordinator.permits(eligible: true, enabled: true, acceptedFreeOutput: false, explicit: true, now: now))
            coordinator.update(flow, .unknown)
            XCTAssertFalse(coordinator.permits(eligible: true, enabled: true, acceptedFreeOutput: false, explicit: false, now: now))
            coordinator.update(flow, .inactive)
        }
        XCTAssertTrue(coordinator.safeToPresent)
    }

    private func assertFits320<V: View>(
        _ view: V,
        size: DynamicTypeSize,
        locale: String,
        scheme: ColorScheme,
        label: String
    ) {
        let host = UIHostingController(
            rootView: view
                .environment(\.locale, Locale(identifier: locale))
                .environment(\.colorScheme, scheme)
                .dynamicTypeSize(size)
        )
        let measured = host.sizeThatFits(in: CGSize(width: 320, height: 640))
        XCTAssertGreaterThan(measured.height, 0, label)
        XCTAssertLessThanOrEqual(measured.width, 320, label)
    }

    private func makeAccessViewModel(
        tier: LearningOsEffectiveTier,
        status: EntitlementStatus,
        caps: Set<LearningOsCapability>
    ) -> EntitlementViewModel {
        let legacy = Entitlement(
            id: UUID(), userId: userId, tier: .free, source: .defaultFree, status: .active,
            effectiveAt: Date(timeIntervalSince1970: 1_800_000_000), expiresAt: nil,
            capabilities: [.basicSolve], version: 1
        )
        let learning = LearningOsEntitlementV2(
            effectiveTier: tier, legacyTier: .pro, status: status, source: .appStoreSubscription,
            expiresAt: "2026-11-19T00:00:00Z", capabilities: [.basicSolve],
            packageCode: .free, featureEnabled: true, learningOsCapabilities: caps,
            historyAccess: LearningOsEntitlementV2.History(mistakePassport: .summary, skillMastery: .summary, priorExams: .summary),
            accessEvaluatedAt: "2026-11-20T12:00:00Z", gracePeriodExpiresAt: nil, paidAccessActive: false
        )
        let vm = EntitlementViewModel(
            entitlementAPI: PresentationStubEntitlementService(legacy: legacy, learning: learning),
            displayCache: EntitlementDisplayCache(secureStorage: InMemorySecureStorage()),
            networkMonitor: PresentationStubNetworkMonitor(),
            logger: AppLogger(subsystem: "com.verifiedai.learning.tests", category: "presentation")
        )
        vm.bind(EntitlementDisplayCache.Scope(user: userId, session: UUID()))
        return vm
    }
}

private struct StubPassportAPI: MistakePassportAPIClientProtocol {
    let response: MistakePassportResponseDto
    func fetchMistakePassport(status: TrapLifecycleStatus?) async throws -> MistakePassportResponseDto { response }
}

private actor PresentationStubEntitlementService: EntitlementServicing {
    let legacy: Entitlement
    let learning: LearningOsEntitlementV2
    init(legacy: Entitlement, learning: LearningOsEntitlementV2) {
        self.legacy = legacy
        self.learning = learning
    }
    func currentEntitlement() async throws -> Entitlement { legacy }
    func learningAccess() async throws -> LearningOsEntitlementV2 { learning }
}

@MainActor
private final class PresentationStubNetworkMonitor: NetworkMonitoring {
    var isReachable = true
}
