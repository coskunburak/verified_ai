import CryptoKit
import Foundation
import OSLog

/// Actor-isolated, non-blocking analytics client supporting privacy validation, bounded offline queueing,
/// batch flushing, backoff on transient/503 outages, permanent discard on 422 rejections, and session purge.
public actor AnalyticsClient: AnalyticsTracking {

    public static let autoFlushBatchThreshold = 10
    public static let maxBatchSize = 50
    private static let baseBackoffSeconds: TimeInterval = 5.0
    private static let maxBackoffSeconds: TimeInterval = 300.0

    private let transport: AnalyticsBatchTransport
    private let store: AnalyticsOfflineQueueStore
    private let isOnline: @Sendable () async -> Bool
    private let nowProvider: @Sendable () -> Date
    private let logger = Logger(subsystem: "com.verifiedai.learning", category: "analytics")

    private var activeUserId: UUID?
    private var activeSessionRefHash: String?
    private var consecutiveFailures = 0
    private var nextAllowedFlushAt: Date?
    private var activeFlushToken: UUID?

    public init(
        transport: AnalyticsBatchTransport,
        store: AnalyticsOfflineQueueStore = AnalyticsOfflineQueueStore(),
        isOnline: @escaping @Sendable () async -> Bool = { true },
        nowProvider: @escaping @Sendable () -> Date = Date.init
    ) {
        self.transport = transport
        self.store = store
        self.isOnline = isOnline
        self.nowProvider = nowProvider
    }

    public func bind(userId: UUID, sessionId: UUID) {
        if activeUserId != userId {
            consecutiveFailures = 0
            nextAllowedFlushAt = nil
            activeFlushToken = nil
        }
        activeUserId = userId
        activeSessionRefHash = Self.sha256Hex(sessionId.uuidString.lowercased())
    }

    public func reset(purgeAll: Bool = true) async {
        let priorUser = activeUserId
        activeUserId = nil
        activeSessionRefHash = nil
        consecutiveFailures = 0
        nextAllowedFlushAt = nil
        activeFlushToken = nil
        if purgeAll {
            await store.clearAll()
        } else if let priorUser {
            await store.clear(userId: priorUser)
        }
    }

    public func track(_ event: AnalyticsClientEvent) async {
        guard let userId = activeUserId else {
            return
        }
        do {
            let dto = try AnalyticsPrivacySanitizer.buildValidatedDTO(
                from: event,
                sessionRefHash: activeSessionRefHash,
                occurredAt: nowProvider()
            )
            let queuedCount = await store.enqueue(dto, for: userId)
            if queuedCount >= Self.autoFlushBatchThreshold {
                await flush(ignoreBackoff: false)
            }
        } catch {
            logger.warning("analytics.client_event_sanitization_rejected")
        }
    }

    public func flush(ignoreBackoff: Bool = false) async {
        guard activeFlushToken == nil, let userId = activeUserId else {
            return
        }
        let now = nowProvider()
        if !ignoreBackoff, let nextAllowedFlushAt, now < nextAllowedFlushAt {
            return
        }

        let token = UUID()
        activeFlushToken = token
        defer {
            if activeFlushToken == token {
                activeFlushToken = nil
            }
        }

        guard await isOnline() else {
            return
        }
        guard activeFlushToken == token, activeUserId == userId else {
            return
        }

        let batch = await store.peekBatch(for: userId, limit: Self.maxBatchSize)
        guard activeFlushToken == token, activeUserId == userId, !batch.isEmpty else {
            return
        }

        do {
            let request = AnalyticsEventBatchRequestDTO(events: batch, trainingEligible: false)
            let response = try await transport.sendBatch(request)
            guard activeFlushToken == token, activeUserId == userId else {
                return
            }
            let resolvedIds = Set(response.results.compactMap(\.clientEventId))
            let batchIds = Set(batch.map(\.clientEventId))
            let toRemove = resolvedIds.isEmpty ? batchIds : resolvedIds
            await store.remove(clientEventIds: toRemove, for: userId)
            guard activeFlushToken == token, activeUserId == userId else {
                return
            }
            consecutiveFailures = 0
            nextAllowedFlushAt = nil
        } catch let networkError as NetworkError {
            guard activeFlushToken == token, activeUserId == userId else {
                return
            }
            await handleNetworkFailure(networkError, batch: batch, userId: userId, now: nowProvider())
        } catch {
            guard activeFlushToken == token, activeUserId == userId else {
                return
            }
            applyBackoff(now: nowProvider())
        }
    }

    private func handleNetworkFailure(
        _ error: NetworkError,
        batch: [AnalyticsEventIngestRequestDTO],
        userId: UUID,
        now: Date
    ) async {
        switch error {
        case .server(let problem):
            if [400, 413, 422].contains(problem.status) {
                await store.remove(clientEventIds: Set(batch.map(\.clientEventId)), for: userId)
                consecutiveFailures = 0
                nextAllowedFlushAt = nil
                logger.warning("analytics.batch_permanently_discarded_client_error")
            } else {
                applyBackoff(now: now)
            }
        case .httpStatus(let status) where [400, 413, 422].contains(status):
            await store.remove(clientEventIds: Set(batch.map(\.clientEventId)), for: userId)
            consecutiveFailures = 0
            nextAllowedFlushAt = nil
            logger.warning("analytics.batch_permanently_discarded_http_status")
        case .invalidURL, .invalidResponse, .decoding:
            await store.remove(clientEventIds: Set(batch.map(\.clientEventId)), for: userId)
            consecutiveFailures = 0
            nextAllowedFlushAt = nil
        case .transport, .httpStatus:
            applyBackoff(now: now)
        }
    }

    private func applyBackoff(now: Date) {
        consecutiveFailures += 1
        let multiplier = pow(2.0, Double(min(consecutiveFailures - 1, 6)))
        let delay = min(Self.maxBackoffSeconds, Self.baseBackoffSeconds * multiplier)
        nextAllowedFlushAt = now.addingTimeInterval(delay)
    }

    private static func sha256Hex(_ input: String) -> String {
        let digest = SHA256.hash(data: Data(input.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }
}
