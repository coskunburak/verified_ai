import Foundation
import Observation
import OSLog

struct RemoteConfigRefreshState: Sendable, Equatable {
    let snapshot: RemoteConfigSnapshot?
    let degradedReason: RemoteConfigDegradedReason?
    let lastUpdatedAt: Date?
}

actor RemoteFeatureFlagCoordinator {
    private let service: any RemoteConfigServicing
    private let cacheStore: RemoteFeatureFlagCacheStore
    private let isOnline: @Sendable () async -> Bool
    private let nowProvider: @Sendable () -> Date
    private let logger = Logger(subsystem: "com.verifiedai.learning", category: "featureflags")

    private var activeUserId: UUID?
    private var activeSessionId: UUID?
    private var currentSnapshot: RemoteConfigSnapshot?

    init(
        service: any RemoteConfigServicing,
        cacheStore: RemoteFeatureFlagCacheStore = RemoteFeatureFlagCacheStore(),
        isOnline: @escaping @Sendable () async -> Bool = { true },
        nowProvider: @escaping @Sendable () -> Date = Date.init
    ) {
        self.service = service
        self.cacheStore = cacheStore
        self.isOnline = isOnline
        self.nowProvider = nowProvider
    }

    func bind(userId: UUID, sessionId: UUID) async -> RemoteConfigRefreshState {
        if activeUserId != userId || activeSessionId != sessionId {
            activeUserId = userId
            activeSessionId = sessionId
            currentSnapshot = await cacheStore.load(userId: userId)
        }
        return RemoteConfigRefreshState(
            snapshot: currentSnapshot,
            degradedReason: nil,
            lastUpdatedAt: currentSnapshot?.cachedAt
        )
    }

    func refresh(force: Bool = false) async -> RemoteConfigRefreshState {
        guard let userId = activeUserId else {
            return RemoteConfigRefreshState(snapshot: nil, degradedReason: nil, lastUpdatedAt: nil)
        }
        let sessionSnapshot = activeSessionId

        if currentSnapshot == nil {
            currentSnapshot = await cacheStore.load(userId: userId)
        }

        guard await isOnline() else {
            return RemoteConfigRefreshState(
                snapshot: currentSnapshot,
                degradedReason: .offline,
                lastUpdatedAt: currentSnapshot?.cachedAt
            )
        }

        let ifNoneMatch = force ? nil : currentSnapshot?.etag
        do {
            let outcome = try await service.fetchRemoteConfig(ifNoneMatch: ifNoneMatch)
            guard activeUserId == userId, activeSessionId == sessionSnapshot else {
                return RemoteConfigRefreshState(snapshot: currentSnapshot, degradedReason: nil, lastUpdatedAt: currentSnapshot?.cachedAt)
            }
            let now = nowProvider()
            switch outcome {
            case .updated(let dto, let headerEtag):
                guard !dto.trainingEligible, dto.llmTokensUsed == 0 else {
                    logger.warning("featureflags.remote_config_invariant_mismatch")
                    return RemoteConfigRefreshState(
                        snapshot: currentSnapshot,
                        degradedReason: .decodingFailure,
                        lastUpdatedAt: currentSnapshot?.cachedAt
                    )
                }
                let updated = RemoteConfigSnapshot.from(
                    response: dto,
                    etag: headerEtag ?? dto.etag,
                    userId: userId,
                    cachedAt: now
                )
                currentSnapshot = updated
                await cacheStore.save(userId: userId, snapshot: updated)
                return RemoteConfigRefreshState(
                    snapshot: updated,
                    degradedReason: nil,
                    lastUpdatedAt: now
                )
            case .notModified(let headerEtag):
                if let existing = currentSnapshot {
                    let preserved = RemoteConfigSnapshot(
                        userId: existing.userId,
                        etag: headerEtag ?? existing.etag,
                        flagsByKey: existing.flagsByKey,
                        experimentsByKey: existing.experimentsByKey,
                        killSwitches: existing.killSwitches,
                        evaluatedAt: existing.evaluatedAt,
                        cachedAt: now
                    )
                    currentSnapshot = preserved
                    await cacheStore.save(userId: userId, snapshot: preserved)
                }
                return RemoteConfigRefreshState(
                    snapshot: currentSnapshot,
                    degradedReason: nil,
                    lastUpdatedAt: now
                )
            }
        } catch let networkError as NetworkError {
            guard activeUserId == userId, activeSessionId == sessionSnapshot else {
                return RemoteConfigRefreshState(snapshot: currentSnapshot, degradedReason: nil, lastUpdatedAt: currentSnapshot?.cachedAt)
            }
            let reason = mapDegradedReason(networkError)
            logger.info("featureflags.remote_config_degraded")
            return RemoteConfigRefreshState(
                snapshot: currentSnapshot,
                degradedReason: reason,
                lastUpdatedAt: currentSnapshot?.cachedAt
            )
        } catch {
            guard activeUserId == userId, activeSessionId == sessionSnapshot else {
                return RemoteConfigRefreshState(snapshot: currentSnapshot, degradedReason: nil, lastUpdatedAt: currentSnapshot?.cachedAt)
            }
            logger.info("featureflags.remote_config_unexpected_error")
            return RemoteConfigRefreshState(
                snapshot: currentSnapshot,
                degradedReason: .transportFailure,
                lastUpdatedAt: currentSnapshot?.cachedAt
            )
        }
    }

    func reset(purgeAll: Bool = true) async -> RemoteConfigRefreshState {
        let priorUserId = activeUserId
        activeUserId = nil
        activeSessionId = nil
        currentSnapshot = nil
        if purgeAll {
            await cacheStore.purgeAll()
        } else if let priorUserId {
            await cacheStore.purge(userId: priorUserId)
        }
        return RemoteConfigRefreshState(snapshot: nil, degradedReason: nil, lastUpdatedAt: nil)
    }

    private func mapDegradedReason(_ error: NetworkError) -> RemoteConfigDegradedReason {
        switch error {
        case .httpStatus(503):
            return .serviceUnavailable
        case .httpStatus(401), .httpStatus(403):
            return .unauthorized
        case .server(let problem):
            if problem.status == 503 {
                return .serviceUnavailable
            }
            if problem.status == 401 || problem.status == 403 {
                return .unauthorized
            }
            return .transportFailure
        case .decoding:
            return .decodingFailure
        case .transport, .invalidURL, .invalidResponse, .httpStatus:
            return .transportFailure
        }
    }
}

private final class LockedSnapshotBox: @unchecked Sendable {
    private let lock = NSLock()
    private var snapshot: RemoteConfigSnapshot?

    func get() -> RemoteConfigSnapshot? {
        lock.lock()
        defer { lock.unlock() }
        return snapshot
    }

    func set(_ newValue: RemoteConfigSnapshot?) {
        lock.lock()
        defer { lock.unlock() }
        snapshot = newValue
    }
}

@MainActor
@Observable
final class RemoteFeatureFlagStore: FeatureFlagClient {
    private let coordinator: RemoteFeatureFlagCoordinator
    private let stateBox = LockedSnapshotBox()

    private(set) var currentSnapshot: RemoteConfigSnapshot?
    private(set) var degradedReason: RemoteConfigDegradedReason?
    private(set) var lastUpdatedAt: Date?

    init(coordinator: RemoteFeatureFlagCoordinator) {
        self.coordinator = coordinator
    }

    convenience init(
        service: any RemoteConfigServicing,
        cacheStore: RemoteFeatureFlagCacheStore = RemoteFeatureFlagCacheStore(),
        isOnline: @escaping @Sendable () async -> Bool = { true },
        nowProvider: @escaping @Sendable () -> Date = Date.init
    ) {
        self.init(
            coordinator: RemoteFeatureFlagCoordinator(
                service: service,
                cacheStore: cacheStore,
                isOnline: isOnline,
                nowProvider: nowProvider
            )
        )
    }

    func bind(userId: UUID, sessionId: UUID) async {
        let state = await coordinator.bind(userId: userId, sessionId: sessionId)
        apply(state)
    }

    func refresh(force: Bool = false) async {
        let state = await coordinator.refresh(force: force)
        apply(state)
    }

    func reset(purgeAll: Bool = true) async {
        let state = await coordinator.reset(purgeAll: purgeAll)
        apply(state)
    }

    nonisolated func isEnabled(_ flag: FeatureFlag) -> Bool {
        stateBox.get()?.isEnabled(flag) ?? flag.defaultEnabled
    }

    nonisolated func isKillSwitchActive(_ flag: FeatureFlag) -> Bool {
        stateBox.get()?.isKillSwitchActive(flag) ?? false
    }

    nonisolated func experimentVariant(for experimentKey: String) -> String? {
        stateBox.get()?.experimentVariant(for: experimentKey)
    }

    private func apply(_ state: RemoteConfigRefreshState) {
        stateBox.set(state.snapshot)
        currentSnapshot = state.snapshot
        degradedReason = state.degradedReason
        lastUpdatedAt = state.lastUpdatedAt
    }
}

#if DEBUG
struct FixtureFeatureFlagClient: FeatureFlagClient, Sendable {
    private let base: StaticFeatureFlagClient

    init(arguments: [String] = ProcessInfo.processInfo.arguments) {
        let killPdf = arguments.contains("--kill-switch-pdf")
        let enableHealth = arguments.contains("--feature-flag-platform-health")
        let enablePaywall = arguments.contains("--feature-flag-paywall-ui")

        var enabled: Set<FeatureFlag> = []
        var killed: Set<FeatureFlag> = []
        if killPdf {
            killed.insert(.pdfUploadEnabled)
        } else {
            enabled.insert(.pdfUploadEnabled)
        }
        if enableHealth {
            enabled.insert(.platformHealthScreen)
        }
        if enablePaywall {
            enabled.insert(.mistakeMomentPaywallUI)
        }
        self.base = StaticFeatureFlagClient(
            enabledFlags: enabled,
            activeKillSwitches: killed
        )
    }

    func isEnabled(_ flag: FeatureFlag) -> Bool {
        base.isEnabled(flag)
    }

    func isKillSwitchActive(_ flag: FeatureFlag) -> Bool {
        base.isKillSwitchActive(flag)
    }

    func experimentVariant(for experimentKey: String) -> String? {
        base.experimentVariant(for: experimentKey)
    }
}
#endif
