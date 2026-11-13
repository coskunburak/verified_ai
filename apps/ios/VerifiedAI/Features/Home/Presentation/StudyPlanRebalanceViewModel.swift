import Foundation
import Combine

@MainActor
public final class StudyPlanRebalanceViewModel: ObservableObject {
    @Published public private(set) var recoveryStatus: StudyPlanRecoveryStatus? = nil
    @Published public private(set) var lastRebalanceResponse: StudyPlanRebalanceResponse? = nil
    @Published public var isPresentingRecoverySheet: Bool = false
    @Published public private(set) var isRebalancing: Bool = false
    @Published public private(set) var errorMessage: String? = nil

    public let userId: UUID
    private let cacheStore: RebalanceCacheStoreProtocol
    private let statusFetcher: (@Sendable (UUID) async throws -> StudyPlanRecoveryStatus)?
    private let rebalanceHandler: (@Sendable (UUID, RebalanceTriggerType, Bool, String) async throws -> StudyPlanRebalanceResponse)?

    public init(
        userId: UUID,
        cacheStore: RebalanceCacheStoreProtocol,
        statusFetcher: (@Sendable (UUID) async throws -> StudyPlanRecoveryStatus)? = nil,
        rebalanceHandler: (@Sendable (UUID, RebalanceTriggerType, Bool, String) async throws -> StudyPlanRebalanceResponse)? = nil
    ) {
        self.userId = userId
        self.cacheStore = cacheStore
        self.statusFetcher = statusFetcher
        self.rebalanceHandler = rebalanceHandler
    }

    public func checkRecoveryStatus(userId: UUID, status: StudyPlanRecoveryStatus? = nil) async {
        guard userId == self.userId else { return }
        errorMessage = nil

        var resolved: StudyPlanRecoveryStatus?
        if let status, status.userId == userId {
            resolved = status
        } else if let statusFetcher {
            do {
                let remote = try await statusFetcher(userId)
                if remote.userId == userId {
                    resolved = remote
                }
            } catch {
                errorMessage = error.localizedDescription
            }
        }

        if resolved == nil {
            resolved = await cacheStore.loadRecoveryStatus(for: userId)
        }

        if let resolved {
            self.recoveryStatus = resolved
            self.isPresentingRecoverySheet = resolved.recoveryNeeded && resolved.missedDaysCount >= 1
            await cacheStore.saveRecoveryStatus(resolved, for: userId)
        }
    }

    @discardableResult
    public func acceptRebalancedPlan(triggerType: RebalanceTriggerType = .missedDays) async -> StudyPlanRebalanceResponse? {
        guard !isRebalancing else { return lastRebalanceResponse }
        isRebalancing = true
        errorMessage = nil
        defer { isRebalancing = false }

        let currentStatus = recoveryStatus
        let idempotencyKey = makeIdempotencyKey(
            userId: userId,
            date: currentStatus?.asOfDate ?? Self.todayDateString(),
            trigger: triggerType
        )

        do {
            let response: StudyPlanRebalanceResponse
            if let rebalanceHandler {
                response = try await rebalanceHandler(userId, triggerType, false, idempotencyKey)
            } else {
                response = Self.makeLocalResponse(
                    userId: userId,
                    status: currentStatus,
                    triggerType: triggerType,
                    preferMiniMode: false
                )
            }
            guard response.userId == userId else { return nil }
            await persistAcceptedResponse(response, previousStatus: currentStatus)
            return response
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }

    @discardableResult
    public func activateMiniModeDownshift() async -> StudyPlanRebalanceResponse? {
        guard !isRebalancing else { return lastRebalanceResponse }
        isRebalancing = true
        errorMessage = nil
        defer { isRebalancing = false }

        let currentStatus = recoveryStatus
        let idempotencyKey = makeIdempotencyKey(
            userId: userId,
            date: currentStatus?.asOfDate ?? Self.todayDateString(),
            trigger: .miniModeDownshift
        )

        do {
            let response: StudyPlanRebalanceResponse
            if let rebalanceHandler {
                response = try await rebalanceHandler(userId, .miniModeDownshift, true, idempotencyKey)
            } else {
                response = Self.makeLocalResponse(
                    userId: userId,
                    status: currentStatus,
                    triggerType: .miniModeDownshift,
                    preferMiniMode: true
                )
            }
            guard response.userId == userId else { return nil }
            await persistAcceptedResponse(response, previousStatus: currentStatus)
            return response
        } catch {
            errorMessage = error.localizedDescription
            return nil
        }
    }

    public func purgeUserData(userId: UUID) async {
        await cacheStore.purgeUserData(userId: userId)
        if userId == self.userId {
            recoveryStatus = nil
            lastRebalanceResponse = nil
            isPresentingRecoverySheet = false
            errorMessage = nil
        }
    }

    private func persistAcceptedResponse(
        _ response: StudyPlanRebalanceResponse,
        previousStatus: StudyPlanRecoveryStatus?
    ) async {
        self.lastRebalanceResponse = response
        self.isPresentingRecoverySheet = false
        await cacheStore.saveRebalanceResponse(response, for: userId)

        if let previousStatus {
            let cleared = StudyPlanRecoveryStatus(
                schemaVersion: previousStatus.schemaVersion,
                userId: previousStatus.userId,
                asOfDate: response.recoveryDate,
                recoveryNeeded: false,
                missedDaysCount: previousStatus.missedDaysCount,
                daysUntilExam: previousStatus.daysUntilExam,
                previousDailyBudgetMinutes: previousStatus.previousDailyBudgetMinutes,
                recommendedDailyBudgetMinutes: response.rebalancedDailyBudgetMinutes,
                miniModeRecommended: response.miniModeActivated,
                activeTodayTasksCount: response.activeTodayTasksCount,
                prunedTasksCount: response.prunedTasksCount,
                rescheduledReviewsCount: response.rescheduledReviewsCount,
                pruneReasonCode: response.pruneReasonCode,
                welcomeMessageKey: response.welcomeMessageKey,
                lastRebalanceEventId: response.rebalanceEventId
            )
            self.recoveryStatus = cleared
            await cacheStore.saveRecoveryStatus(cleared, for: userId)
        }
    }

    private func makeIdempotencyKey(userId: UUID, date: String, trigger: RebalanceTriggerType) -> String {
        "\(userId.uuidString.lowercased()):\(date):\(trigger.rawValue)"
    }

    private static func makeLocalResponse(
        userId: UUID,
        status: StudyPlanRecoveryStatus?,
        triggerType: RebalanceTriggerType,
        preferMiniMode: Bool
    ) -> StudyPlanRebalanceResponse {
        let today = status?.asOfDate ?? todayDateString()
        let prevBudget = status?.previousDailyBudgetMinutes ?? 15
        let boundedBudget = preferMiniMode ? 5 : min(25, max(5, status?.recommendedDailyBudgetMinutes ?? prevBudget))
        let activeCount = preferMiniMode ? 1 : min(3, max(1, status?.activeTodayTasksCount ?? 3))
        let reason: PruneReasonCode = preferMiniMode
            ? .miniModeSingleFocus
            : (status?.pruneReasonCode ?? .missedDayWindowReset)
        let welcomeKey = preferMiniMode
            ? "studyplan.rebalance.welcome_mini_mode"
            : (status?.welcomeMessageKey ?? "studyplan.rebalance.welcome_recovered")

        return StudyPlanRebalanceResponse(
            schemaVersion: "1.0.0",
            rebalanceEventId: UUID(),
            userId: userId,
            recoveryDate: today,
            triggerType: triggerType,
            missedDaysCount: status?.missedDaysCount ?? 0,
            daysUntilExam: status?.daysUntilExam ?? 30,
            previousDailyBudgetMinutes: prevBudget,
            rebalancedDailyBudgetMinutes: boundedBudget,
            prunedTasksCount: status?.prunedTasksCount ?? 0,
            rescheduledReviewsCount: status?.rescheduledReviewsCount ?? 0,
            activeTodayTasksCount: activeCount,
            miniModeActivated: preferMiniMode,
            pruneReasonCode: reason,
            prunedConceptIds: [],
            welcomeMessageKey: welcomeKey,
            idempotentReplay: false,
            createdAt: ISO8601DateFormatter().string(from: Date())
        )
    }

    private static func todayDateString() -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter.string(from: Date())
    }
}
