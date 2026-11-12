import Foundation
import Combine

@MainActor
public final class SpacedReviewViewModel: ObservableObject {
    @Published public private(set) var items: [SpacedQueueItem] = []
    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var lastCompletionResult: SpacedItemCompletionResponse? = nil
    @Published public private(set) var errorMessageKey: String? = nil
    @Published public private(set) var isOfflineFallback: Bool = false

    private let userId: UUID
    private let cacheStore: SpacedReviewCacheStoreProtocol
    private let fetchRemoteHandler: (@Sendable (UUID) async throws -> SpacedQueueResponse)?
    private let completeRemoteHandler: (@Sendable (UUID, UUID, SpacedItemCompleteRequest) async throws -> SpacedItemCompletionResponse)?
    private let dismissRemoteHandler: (@Sendable (UUID, UUID, SpacedItemDismissRequest) async throws -> SpacedQueueItem)?

    public init(
        userId: UUID,
        cacheStore: SpacedReviewCacheStoreProtocol? = nil,
        fetchRemoteHandler: (@Sendable (UUID) async throws -> SpacedQueueResponse)? = nil,
        completeRemoteHandler: (@Sendable (UUID, UUID, SpacedItemCompleteRequest) async throws -> SpacedItemCompletionResponse)? = nil,
        dismissRemoteHandler: (@Sendable (UUID, UUID, SpacedItemDismissRequest) async throws -> SpacedQueueItem)? = nil
    ) {
        self.userId = userId
        self.cacheStore = cacheStore ?? SpacedReviewSQLiteCache()
        self.fetchRemoteHandler = fetchRemoteHandler
        self.completeRemoteHandler = completeRemoteHandler
        self.dismissRemoteHandler = dismissRemoteHandler
    }

    public func loadQueue(forceRefresh: Bool = false) async {
        isLoading = true
        errorMessageKey = nil

        do {
            let response: SpacedQueueResponse
            if let handler = fetchRemoteHandler {
                response = try await handler(userId)
            } else {
                response = try await defaultFetchQueue()
            }

            // Client-side defensive guard: strictly cap at <= 3 items
            let boundedItems = Array(response.items.prefix(3))
            self.items = boundedItems
            self.isOfflineFallback = false

            let boundedResponse = SpacedQueueResponse(
                schemaVersion: response.schemaVersion,
                userId: response.userId,
                evaluatedAt: response.evaluatedAt,
                dailyCap: 3,
                surfacedCount: boundedItems.count,
                interleavedSkillCount: Set(boundedItems.map(\.canonicalSkillCode)).count,
                interleavingSatisfied: response.interleavingSatisfied,
                items: boundedItems
            )
            await cacheStore.saveQueue(boundedResponse, for: userId)
        } catch {
            // Offline fallback from local SQLite cache
            if let cached = await cacheStore.loadQueue(for: userId) {
                self.items = Array(cached.items.prefix(3))
                self.isOfflineFallback = true
                self.errorMessageKey = nil
            } else {
                self.errorMessageKey = "studyplan.spaced.empty.title"
            }
        }

        isLoading = false
    }

    public func completeItem(
        itemId: UUID,
        passed: Bool,
        independentVerification: Bool,
        responseTimeMs: Int,
        triggeredMistakeCode: String? = nil
    ) async {
        guard !isLoading else { return }
        isLoading = true
        errorMessageKey = nil

        let request = SpacedItemCompleteRequest(
            passed: passed,
            independentVerification: independentVerification,
            responseTimeMs: responseTimeMs,
            triggeredMistakeCode: triggeredMistakeCode
        )

        do {
            let result: SpacedItemCompletionResponse
            if let handler = completeRemoteHandler {
                result = try await handler(userId, itemId, request)
            } else {
                result = try await defaultCompleteItem(itemId: itemId, request: request)
            }

            self.lastCompletionResult = result

            // Remove completed item from active surfaced queue
            self.items.removeAll(where: { $0.itemId == itemId })

            // Update offline cache
            let updatedResponse = SpacedQueueResponse(
                userId: userId,
                evaluatedAt: Date(),
                surfacedCount: items.count,
                interleavedSkillCount: Set(items.map(\.canonicalSkillCode)).count,
                interleavingSatisfied: true,
                items: items
            )
            await cacheStore.saveQueue(updatedResponse, for: userId)
        } catch {
            self.errorMessageKey = "studyplan.spaced.empty.title"
        }

        isLoading = false
    }

    public func dismissItem(itemId: UUID, reasonCode: String = "SCHEDULE_LATER") async {
        guard !isLoading else { return }
        isLoading = true
        errorMessageKey = nil

        let request = SpacedItemDismissRequest(reasonCode: reasonCode)

        do {
            if let handler = dismissRemoteHandler {
                _ = try await handler(userId, itemId, request)
            } else {
                _ = try await defaultDismissItem(itemId: itemId, request: request)
            }

            self.items.removeAll(where: { $0.itemId == itemId })

            let updatedResponse = SpacedQueueResponse(
                userId: userId,
                evaluatedAt: Date(),
                surfacedCount: items.count,
                interleavedSkillCount: Set(items.map(\.canonicalSkillCode)).count,
                interleavingSatisfied: true,
                items: items
            )
            await cacheStore.saveQueue(updatedResponse, for: userId)
        } catch {
            self.errorMessageKey = "studyplan.spaced.empty.title"
        }

        isLoading = false
    }

    private func defaultFetchQueue() async throws -> SpacedQueueResponse {
        // Fallback placeholder when remote network client is not wired in local sandbox
        throw URLError(.cannotConnectToHost)
    }

    private func defaultCompleteItem(itemId: UUID, request: SpacedItemCompleteRequest) async throws -> SpacedItemCompletionResponse {
        throw URLError(.cannotConnectToHost)
    }

    private func defaultDismissItem(itemId: UUID, request: SpacedItemDismissRequest) async throws -> SpacedQueueItem {
        throw URLError(.cannotConnectToHost)
    }
}
