import Foundation
import Combine

@MainActor
public final class CoachHubViewModel: ObservableObject {
    @Published public private(set) var recommendations: [LearnerRecommendationDto] = []
    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var isOfflineFallback: Bool = false
    @Published public private(set) var activeSessionRoute: RecommendationActionResponseDto? = nil
    @Published public private(set) var errorMessage: String? = nil

    private let userId: UUID
    private let cacheStore: RecommendationCacheStoreProtocol
    private let fetchRemoteHandler: (@Sendable (UUID, Int, Bool) async throws -> [LearnerRecommendationDto])?
    private let acceptRemoteHandler: (@Sendable (UUID, UUID) async throws -> RecommendationActionResponseDto)?
    private let dismissRemoteHandler: (@Sendable (UUID, UUID, String) async throws -> RecommendationActionResponseDto)?

    public init(
        userId: UUID,
        cacheStore: RecommendationCacheStoreProtocol? = nil,
        fetchRemoteHandler: (@Sendable (UUID, Int, Bool) async throws -> [LearnerRecommendationDto])? = nil,
        acceptRemoteHandler: (@Sendable (UUID, UUID) async throws -> RecommendationActionResponseDto)? = nil,
        dismissRemoteHandler: (@Sendable (UUID, UUID, String) async throws -> RecommendationActionResponseDto)? = nil
    ) {
        self.userId = userId
        self.cacheStore = cacheStore ?? RecommendationSQLiteCache()
        self.fetchRemoteHandler = fetchRemoteHandler
        self.acceptRemoteHandler = acceptRemoteHandler
        self.dismissRemoteHandler = dismissRemoteHandler
    }

    public func loadRecommendations(forceRefresh: Bool = false) async {
        isLoading = true
        errorMessage = nil

        do {
            let fetched: [LearnerRecommendationDto]
            if let handler = fetchRemoteHandler {
                fetched = try await handler(userId, 5, forceRefresh)
            } else {
                fetched = try await defaultFetchRecommendations(forceRefresh: forceRefresh)
            }

            self.recommendations = fetched
            self.isOfflineFallback = false
            await cacheStore.saveRecommendations(fetched, for: userId)
        } catch {
            // Offline fallback from local SQLite cache
            let cached = await cacheStore.loadCachedRecommendations(for: userId)
            if !cached.isEmpty {
                self.recommendations = cached
                self.isOfflineFallback = true
                self.errorMessage = nil
            } else {
                self.errorMessage = error.localizedDescription
            }
        }

        isLoading = false
    }

    public func acceptRecommendation(_ id: UUID) async {
        guard let index = recommendations.firstIndex(where: { $0.id == id }) else { return }
        let target = recommendations[index]

        do {
            let actionResponse: RecommendationActionResponseDto
            if let handler = acceptRemoteHandler {
                actionResponse = try await handler(userId, id)
            } else {
                let sessionType: String
                switch target.recommendationType {
                case .prerequisiteRepair: sessionType = "PREREQUISITE_DRILL"
                case .mistakeRepair: sessionType = "TWIN_REMEDIATION"
                case .spacedRetention: sessionType = "SPACED_REVIEW"
                case .highRoiTopic: sessionType = "TOPIC_PRACTICE"
                }
                actionResponse = RecommendationActionResponseDto(
                    recommendationId: id,
                    status: "ACCEPTED",
                    nextSessionType: sessionType,
                    targetSkillCode: target.targetSkillCode,
                    targetMistakeCode: target.targetMistakeCode,
                    deepLinkUri: "verifiedai://coach/session?type=\(sessionType)&skill=\(target.targetSkillCode)"
                )
            }

            await cacheStore.updateRecommendationStatus(id: id, status: .accepted)
            recommendations.remove(at: index)
            self.activeSessionRoute = actionResponse
        } catch {
            self.errorMessage = error.localizedDescription
        }
    }

    public func dismissRecommendation(_ id: UUID, reason: String = "NOT_NOW") async {
        guard let index = recommendations.firstIndex(where: { $0.id == id }) else { return }

        do {
            if let handler = dismissRemoteHandler {
                _ = try await handler(userId, id, reason)
            }
            await cacheStore.updateRecommendationStatus(id: id, status: .dismissed)
            recommendations.remove(at: index)
        } catch {
            // Optimistic UI dismissal even if network failed
            await cacheStore.updateRecommendationStatus(id: id, status: .dismissed)
            recommendations.remove(at: index)
        }
    }

    public func clearActiveSessionRoute() {
        self.activeSessionRoute = nil
    }

    private func defaultFetchRecommendations(forceRefresh: Bool) async throws -> [LearnerRecommendationDto] {
        // Mock fallback if running in preview/fixture mode
        throw URLError(.notConnectedToInternet)
    }
}
