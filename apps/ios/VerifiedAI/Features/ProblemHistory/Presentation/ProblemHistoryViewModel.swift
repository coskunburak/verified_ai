import Foundation
import Observation

enum ProblemHistoryState: Equatable {
    case idle
    case loading
    case ready
    case offline
    case failed(String)
}

@MainActor
@Observable
final class ProblemHistoryViewModel {
    private let historyAPI: ProblemSessionHistoryServicing
    private let workflowAPI: ProblemAssetUploadServicing
    private let cache: ProblemSessionCaching
    private let networkMonitor: NetworkMonitoring
    private let logger: AppLogger
    private let pageLimit: Int

    private(set) var state: ProblemHistoryState = .idle
    private(set) var items: [ProblemSessionHistoryItem] = []
    private(set) var selectedDetail: ProblemSessionDetail?
    private(set) var message: String?
    private(set) var nextCursor: String?

    init(
        historyAPI: ProblemSessionHistoryServicing,
        workflowAPI: ProblemAssetUploadServicing,
        cache: ProblemSessionCaching,
        networkMonitor: NetworkMonitoring,
        logger: AppLogger,
        pageLimit: Int = 20
    ) {
        self.historyAPI = historyAPI
        self.workflowAPI = workflowAPI
        self.cache = cache
        self.networkMonitor = networkMonitor
        self.logger = logger
        self.pageLimit = pageLimit
    }

    var canLoadMore: Bool {
        nextCursor != nil && state != .loading
    }

    func bootstrap() async {
        if case .idle = state {
            loadCached()
            await refresh()
        }
    }

    func refresh() async {
        guard networkMonitor.isReachable else {
            loadCached()
            state = .offline
            message = items.isEmpty ? "Problem history needs a network connection." : "Showing saved problem history."
            return
        }

        state = .loading
        message = nil
        do {
            let page = try await historyAPI.history(limit: pageLimit, cursor: nil)
            try cache.save(page: page)
            items = page.items
            nextCursor = page.nextCursor
            state = .ready
        } catch {
            logger.warning("problem_history.refresh_failed")
            loadCached()
            let text = failureMessage(for: error)
            state = items.isEmpty ? .failed(text) : .ready
            message = text
        }
    }

    func loadMore() async {
        guard let nextCursor, networkMonitor.isReachable else {
            return
        }
        state = .loading
        do {
            let page = try await historyAPI.history(limit: pageLimit, cursor: nextCursor)
            try cache.save(page: page)
            items.append(contentsOf: page.items)
            self.nextCursor = page.nextCursor
            state = .ready
        } catch {
            logger.warning("problem_history.load_more_failed")
            state = .ready
            message = failureMessage(for: error)
        }
    }

    func reconnect(to item: ProblemSessionHistoryItem) async {
        await reconnect(problemSessionId: item.problemSessionId)
    }

    func reconnect(problemSessionId: UUID) async {
        guard networkMonitor.isReachable else {
            state = .offline
            message = "Reconnecting needs a network connection."
            return
        }
        do {
            let detail = try await historyAPI.detail(problemSessionId: problemSessionId)
            try cache.save(detail: detail)
            selectedDetail = detail
            merge(detail)
            message = resumeMessage(for: detail)
        } catch {
            logger.warning("problem_history.reconnect_failed")
            message = failureMessage(for: error)
        }
    }

    func performNextAction(for detail: ProblemSessionDetail) async {
        guard networkMonitor.isReachable else {
            state = .offline
            message = "This action needs a network connection."
            return
        }

        do {
            switch detail.nextAction {
            case .startRecognition, .retryRecognition:
                _ = try await workflowAPI.requestRecognition(problemSessionId: detail.problemSessionId)
            case .startParse, .retryParse:
                _ = try await workflowAPI.requestParse(problemSessionId: detail.problemSessionId)
            case .waitRecognition, .waitParse, .waitClassification, .reviewParse, .canonicalize, .startClassification,
                 .retryClassification, .readyForSolve, .none, .resumeUpload, .startPreprocessing, .retryPreprocessing,
                 .recaptureOrReimport, .unsupported:
                break
            }
            await reconnect(problemSessionId: detail.problemSessionId)
        } catch {
            logger.warning("problem_history.next_action_failed")
            message = failureMessage(for: error)
        }
    }

    func clearSelection() {
        selectedDetail = nil
    }

    func reset() {
        state = .idle
        items = []
        selectedDetail = nil
        message = nil
        nextCursor = nil
    }

    private func loadCached() {
        do {
            items = try cache.loadSummaries(limit: pageLimit)
            nextCursor = nil
            if !items.isEmpty {
                state = .ready
            }
        } catch {
            logger.warning("problem_history.cache_load_failed")
        }
    }

    private func merge(_ detail: ProblemSessionDetail) {
        let updated = detail.historyItem
        if let index = items.firstIndex(where: { $0.problemSessionId == detail.problemSessionId }) {
            items[index] = updated
        } else {
            items.insert(updated, at: 0)
        }
        items.sort { lhs, rhs in
            if lhs.updatedAt == rhs.updatedAt {
                return lhs.problemSessionId.uuidString > rhs.problemSessionId.uuidString
            }
            return lhs.updatedAt > rhs.updatedAt
        }
    }

    private func resumeMessage(for detail: ProblemSessionDetail) -> String {
        switch detail.nextAction {
        case .startRecognition, .retryRecognition:
            "Ready to read this problem."
        case .startParse, .retryParse:
            "Ready to understand this problem."
        case .reviewParse:
            "Parse review is required."
        case .canonicalize:
            "Verification preparation is required."
        case .startClassification, .retryClassification:
            "Classification is required."
        case .readyForSolve:
            "Problem is ready."
        case .waitRecognition, .waitParse, .waitClassification:
            "Work is still in progress."
        case .resumeUpload, .startPreprocessing, .retryPreprocessing, .recaptureOrReimport:
            "Capture should be restarted."
        case .unsupported:
            "This problem is not supported yet."
        case .none:
            detail.failureCode ?? "No recovery action is available."
        }
    }

    private func failureMessage(for error: Error) -> String {
        if case NetworkError.server(let problem) = error {
            switch problem.code {
            case "PROBLEM_SESSION_CURSOR_INVALID":
                return "Problem history cursor expired. Pull to refresh."
            case "PROBLEM_SESSION_LINEAGE_AMBIGUOUS":
                return "This problem session needs support review."
            default:
                return problem.title
            }
        }
        if case NetworkError.decoding = error {
            return "Problem history response could not be read."
        }
        return "Problem history could not be loaded."
    }
}
