import Foundation
import Observation

@MainActor
@Observable
final class EntitlementViewModel {
    private let entitlementAPI: EntitlementServicing
    private let displayCache: EntitlementDisplayCache
    private let networkMonitor: NetworkMonitoring
    private let logger: AppLogger

    private(set) var state: EntitlementState = .idle
    private(set) var entitlement: Entitlement?
    private(set) var message: String?

    init(
        entitlementAPI: EntitlementServicing,
        displayCache: EntitlementDisplayCache,
        networkMonitor: NetworkMonitoring,
        logger: AppLogger
    ) {
        self.entitlementAPI = entitlementAPI
        self.displayCache = displayCache
        self.networkMonitor = networkMonitor
        self.logger = logger
    }

    private(set) var scope: EntitlementDisplayCache.Scope?
    private(set) var learning: LearningOsEntitlementV2?
    private var generation = UUID()

    func bind(_ newScope: EntitlementDisplayCache.Scope) {
        guard scope != newScope else { return }
        reset()
        scope = newScope
    }

    func refresh(force: Bool = true) async {
        await bootstrap(force: force)
    }

    func bootstrap(force: Bool = false) async {
        guard let scope else { return }
        if !force {
            if case .ready = state { return }
            if state == .loading { return }
        } else {
            self.generation = UUID()
        }
        let generation = self.generation

        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--ui-testing-authenticated") {
            let current = Entitlement(
                id: UUID(uuidString: "00000000-0000-0000-0000-0000000000C1")!,
                userId: UUID(uuidString: "00000000-0000-0000-0000-0000000000A1")!,
                tier: .pro,
                source: .promotional,
                status: .active,
                effectiveAt: Date(),
                expiresAt: nil,
                capabilities: [.basicSolve, .verifiedSolve, .advancedTutor, .mistakeHistory, .adaptivePlan],
                version: 1
            )
            entitlement = current
            state = .ready(current)
            message = nil
            return
        }
        #endif

        learning = nil
        guard networkMonitor.isReachable else {
            await loadCached(scope, generation: generation, failed: false)
            return
        }

        state = .loading
        message = nil

        do {
            let current = try await entitlementAPI.currentEntitlement()
            guard generation == self.generation, current.userId == scope.user else {
                if generation == self.generation { state = .failed }
                return
            }
            let projection = try? await entitlementAPI.learningAccess()
            guard generation == self.generation else { return }
            learning = projection
            message = statusBannerKey.map { String(localized: String.LocalizationValue($0)) }
            entitlement = current
            try? await displayCache.save(current, scope: scope)
            guard generation == self.generation else { return }
            state = .ready(current)
            logger.info("entitlement.bootstrap.ready")
        } catch {
            await loadCached(scope, generation: generation, failed: true)
            logger.warning("entitlement.bootstrap.failed")
        }
    }

    func reset() {
        generation = UUID()
        let oldScope = scope
        scope = nil
        learning = nil
        state = .idle
        entitlement = nil
        message = nil
        Task {
            if let oldScope { try? await displayCache.clear(scope: oldScope) }
        }
    }

    var quotaSummary: QuotaSummary? {
        learning?.quotaSummary
    }

    var isDegradedToDeterministicOnly: Bool {
        quotaSummary?.degradedToDeterministicOnly ?? false
    }

    var remainingAiChecksText: String {
        guard let quotaSummary else { return "" }
        return "\(quotaSummary.remainingAiChecks) / \(quotaSummary.aiVerificationsLimit)"
    }

    var statusBannerKey: String? {
        guard let learning else { return nil }
        switch learning.status {
        case .gracePeriod:
            return learning.paidAccessActive == true ? "billing.grace" : "billing.historyRetained"
        case .billingRetry:
            return learning.paidAccessActive == true ? "billing.retry" : "billing.historyRetained"
        case .expired, .revoked:
            return "billing.historyRetained"
        case .active:
            if learning.legacyTier != .free && learning.effectiveTier == .free {
                return "billing.historyRetained"
            }
            return nil
        }
    }

    func allows(_ capability: PremiumCapability) -> Bool {
        guard case .ready = state, networkMonitor.isReachable else { return false }
        return entitlement?.allows(capability) ?? false
    }

    func decision(_ capability: LearningOsCapability) -> LearningOsDecision {
        guard networkMonitor.isReachable, case .ready = state, let learning,
              let enabled = learning.featureEnabled else { return .pendingServerVerification }
        if !enabled { return .allowed }
        guard let capabilities = learning.learningOsCapabilities else { return .pendingServerVerification }
        if capabilities.contains(capability) { return .allowed }
        return capability == .mistakePassportFull ? .readOnlySummary : .upgrade
    }

    private func loadCached(_ scope: EntitlementDisplayCache.Scope, generation: UUID, failed: Bool) async {
        let cached = try? await displayCache.load(scope: scope)
        guard generation == self.generation else { return }
        entitlement = cached
        state = failed && cached == nil ? .failed : .offline(cached)
        message = cached != nil ? "Offline. Showing last known access." : "Entitlement could not be loaded."
    }

}

enum EntitlementState: Equatable {
    case idle
    case loading
    case ready(Entitlement)
    case offline(Entitlement?)
    case failed
}
