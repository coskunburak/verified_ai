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

    func bootstrap(force: Bool = false) async {
        if !force, case .ready = state {
            return
        }
        if state == .loading {
            return
        }

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

        guard networkMonitor.isReachable else {
            await loadCachedOfflineState()
            return
        }

        state = .loading
        message = nil

        do {
            let current = try await entitlementAPI.currentEntitlement()
            entitlement = current
            try? await displayCache.save(current)
            state = .ready(current)
            logger.info("entitlement.bootstrap.ready")
        } catch {
            await loadCachedFailureState()
            logger.warning("entitlement.bootstrap.failed")
        }
    }

    func reset() {
        state = .idle
        entitlement = nil
        message = nil
        Task {
            try? await displayCache.clear()
        }
    }

    func allows(_ capability: PremiumCapability) -> Bool {
        entitlement?.allows(capability) ?? false
    }

    private func loadCachedOfflineState() async {
        if let cached = try? await displayCache.load() {
            entitlement = cached
            state = .offline(cached)
            message = "Offline. Showing last known access."
        } else {
            entitlement = nil
            state = .offline(nil)
            message = "Entitlement could not be loaded while offline."
        }
        logger.warning("entitlement.bootstrap.offline")
    }

    private func loadCachedFailureState() async {
        if let cached = try? await displayCache.load() {
            entitlement = cached
            state = .offline(cached)
            message = "Showing last known access."
        } else {
            entitlement = nil
            state = .failed
            message = "Entitlement could not be loaded."
        }
    }
}

enum EntitlementState: Equatable {
    case idle
    case loading
    case ready(Entitlement)
    case offline(Entitlement?)
    case failed
}
