import Foundation
import Combine

@MainActor
final class ExamProgressHistoryViewModel: ObservableObject {
    enum State: Equatable {
        case idle
        case loading
        case loaded(ExamProgressReport)
        case empty(ExamProgressReport)
        case insufficient(ExamProgressReport)
        case offline(ExamProgressReport)
        case disabled
        case failed(String)
    }

    let user: UUID
    @Published private(set) var exam: String
    private let api: any ExamProgressServicing
    private let cache: ExamOfflineSQLiteStore?

    @Published private(set) var state: State = .idle
    @Published private(set) var report: ExamProgressReport?
    @Published private(set) var isLoading = false
    @Published private(set) var isOffline = false
    @Published private(set) var messageKey: String?

    private var requestToken: UInt64 = 0

    init(
        user: UUID,
        exam: String,
        api: any ExamProgressServicing,
        cache: ExamOfflineSQLiteStore?
    ) {
        self.user = user
        self.exam = ExamProgressReport.canonicalExamCode(exam)
        self.api = api
        self.cache = cache
    }

    func switchExam(to newExamCode: String) async {
        let canonical = ExamProgressReport.canonicalExamCode(newExamCode)
        requestToken &+= 1
        exam = canonical
        report = nil
        isOffline = false
        messageKey = nil
        state = .idle
        await load()
    }

    func load() async {
        guard ExamProgressReport.supportedExams.contains(exam) else {
            report = nil
            isOffline = false
            isLoading = false
            messageKey = "exam.progress.unsupported"
            state = .failed("exam.progress.unsupported")
            return
        }

        requestToken &+= 1
        let activeToken = requestToken
        isLoading = true
        isOffline = false
        messageKey = nil
        state = .loading
        let epoch = await cache?.progressEpoch() ?? 0

        defer {
            if requestToken == activeToken {
                isLoading = false
            }
        }

        do {
            let fetched = try await api.progress(exam: exam)
            try Task.checkCancellation()
            guard requestToken == activeToken else { return }
            try fetched.validate(user: user, exam: exam)
            guard (await cache?.progressEpoch() ?? 0) == epoch else {
                throw ExamProgressError.integrity
            }
            if let cache {
                try await cache.saveProgressReport(fetched, user: user, exam: exam, epoch: epoch)
            }
            report = fetched
            isOffline = false
            switch fetched.evidenceStatus {
            case .empty:
                state = .empty(fetched)
            case .insufficientForTrend, .partialTrend:
                state = .insufficient(fetched)
            case .sufficientEvidence:
                state = .loaded(fetched)
            }
        } catch is CancellationError {
            return
        } catch {
            guard !Task.isCancelled, requestToken == activeToken else { return }
            let currentEpoch = await cache?.progressEpoch() ?? 0
            guard currentEpoch == epoch else {
                report = nil
                isOffline = false
                messageKey = "exam.progress.error"
                state = .failed("exam.progress.error")
                return
            }

            if let authStatus = Self.authStatus(for: error) {
                await cache?.purgeUserData(userId: user)
                report = nil
                isOffline = false
                if authStatus == 403 {
                    messageKey = "exam.progress.disabled"
                    state = .disabled
                } else {
                    messageKey = "exam.progress.signIn"
                    state = .failed("exam.progress.signIn")
                }
                return
            }

            if Self.allowsOffline(error) {
                do {
                    if let cached = try await cache?.loadProgressReport(user: user, exam: exam) {
                        report = cached
                        isOffline = true
                        state = .offline(cached)
                        return
                    }
                } catch {
                    report = nil
                    isOffline = false
                    messageKey = "exam.progress.cacheError"
                    state = .failed("exam.progress.cacheError")
                    return
                }
            }

            report = nil
            isOffline = false
            let key: String
            if let progressError = error as? ExamProgressError {
                switch progressError {
                case .unsupportedExam:
                    key = "exam.progress.unsupported"
                case .integrity:
                    key = "exam.progress.integrityError"
                case .cache:
                    key = "exam.progress.cacheError"
                }
            } else {
                key = "exam.progress.error"
            }
            messageKey = key
            state = .failed(key)
        }
    }

    static func allowsOffline(_ error: Error) -> Bool {
        guard let networkError = error as? NetworkError else { return false }
        switch networkError {
        case .transport:
            return true
        case .httpStatus(let status):
            return (500...599).contains(status)
        case .server(let problem):
            return (500...599).contains(problem.status)
        default:
            return false
        }
    }

    private static func authStatus(for error: Error) -> Int? {
        guard let networkError = error as? NetworkError else { return nil }
        switch networkError {
        case .httpStatus(let status) where status == 401 || status == 403:
            return status
        case .server(let problem) where problem.status == 401 || problem.status == 403:
            return problem.status
        default:
            return nil
        }
    }
}
