// apps/ios/VerifiedAI/Features/MistakePassport/ViewModels/MistakePassportViewModel.swift
import Foundation
import Observation

@MainActor
@Observable
public final class MistakePassportViewModel {

    public enum TrapCategoryTab: String, CaseIterable, Identifiable {
        case conceptualTraps // Primary: Structural & Conceptual Exam Traps
        case arithmeticSlips // Secondary: ARITH_BASIC_COMPUTATION

        public var id: String { rawValue }

        public var localizedKey: String {
            switch self {
            case .conceptualTraps: return "passport.tab.conceptual_traps"
            case .arithmeticSlips: return "passport.tab.arithmetic_slips"
            }
        }
    }

    public enum ViewState: Equatable {
        case idle
        case loading
        case loaded(MistakePassportResponseDto, isOfflineStale: Bool)
        case empty(TrapLifecycleStatus?)
        case error(String)
    }

    public private(set) var state: ViewState = .idle
    public var selectedCategoryTab: TrapCategoryTab = .conceptualTraps
    public var selectedStatusFilter: TrapLifecycleStatus? = nil

    private let apiClient: MistakePassportAPIClientProtocol
    private let cacheStore: MistakePassportCacheStoreProtocol
    private let analytics: AnalyticsTracking
    private var activeFetchTask: Task<Void, Never>?
    private var requestGeneration: UInt64 = 0

    public init(
        apiClient: MistakePassportAPIClientProtocol,
        cacheStore: MistakePassportCacheStoreProtocol,
        analytics: AnalyticsTracking = NoOpAnalyticsTracker()
    ) {
        self.apiClient = apiClient
        self.cacheStore = cacheStore
        self.analytics = analytics
    }

    public func loadPassport(statusFilter: TrapLifecycleStatus? = nil) {
        selectedStatusFilter = statusFilter
        requestGeneration &+= 1
        let currentGeneration = requestGeneration

        activeFetchTask?.cancel()
        activeFetchTask = Task { [weak self] in
            guard let self else { return }
            await self.executeFetch(generation: currentGeneration, statusFilter: statusFilter)
        }
    }

    private func executeFetch(generation: UInt64, statusFilter: TrapLifecycleStatus?) async {
        if case .idle = state {
            state = .loading
        }

        do {
            let response = try await apiClient.fetchMistakePassport(status: statusFilter)
            guard !Task.isCancelled, generation == requestGeneration else { return }

            await cacheStore.savePassportSnapshot(response, statusFilter: statusFilter)
            if hasVisibleContent(in: response) {
                state = .loaded(response, isOfflineStale: false)
                let firstItem = activeItems(from: response).first
                recordMistakeReviewed(canonicalSkillCode: firstItem?.skillNodeId, mistakeCode: firstItem?.mistakeCode)
            } else {
                state = .empty(statusFilter)
            }
        } catch {
            guard !Task.isCancelled, generation == requestGeneration else { return }

            if let cached = await cacheStore.loadCachedPassportSnapshot(statusFilter: statusFilter) {
                state = .loaded(cached, isOfflineStale: true)
            } else {
                state = .error("passport.error.load_failed")
            }
        }
    }

    public func switchCategoryTab(_ tab: TrapCategoryTab) {
        selectedCategoryTab = tab
        if case let .loaded(response, isOfflineStale) = state {
            state = hasVisibleContent(in: response) ? .loaded(response, isOfflineStale: isOfflineStale) : .empty(selectedStatusFilter)
        }
    }

    private func hasVisibleContent(in response: MistakePassportResponseDto) -> Bool {
        !activeItems(from: response).isEmpty
            || !(response.summaryTrapTitleKeys ?? []).isEmpty
            || !(response.earnedProofs ?? []).isEmpty
    }

    public func handleUserLogoutPurge() async {
        activeFetchTask?.cancel()
        requestGeneration &+= 1
        await cacheStore.purgeAllCachedPassports()
        state = .idle
    }

    public func activeItems(from response: MistakePassportResponseDto) -> [ExamTrapItemDto] {
        switch selectedCategoryTab {
        case .conceptualTraps:
            return response.conceptualTraps
        case .arithmeticSlips:
            return response.arithmeticSlips
        }
    }

    public func recordMistakeReviewed(canonicalSkillCode: String?, mistakeCode: String?) {
        Task { [analytics] in
            await analytics.track(.mistakeReviewed(canonicalSkillCode: canonicalSkillCode, mistakeCode: mistakeCode))
        }
    }

    public func cancel() {
        activeFetchTask?.cancel()
    }
}
