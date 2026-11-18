import Foundation
import Combine

@MainActor
final class PreExamCramViewModel: ObservableObject {
    let user: UUID
    let exam: String
    private let api: any PreExamServicing
    private let cache: ExamOfflineSQLiteStore?
    @Published private(set) var bundle: PreExamBundle?
    @Published private(set) var loading = false
    @Published private(set) var offline = false
    @Published private(set) var message: String?
    init(user: UUID, exam: String, api: any PreExamServicing, cache: ExamOfflineSQLiteStore?) {
        self.user = user; self.exam = exam; self.api = api; self.cache = cache
    }
    func load() async {
        guard !loading else { return }
        loading = true; message = nil; offline = false; bundle = nil
        defer { loading = false }
        let epoch = await cache?.preExamEpoch() ?? 0
        do {
            let cached = try? await cache?.loadPreExam(user: user, exam: exam)
            var review = try await fetch(sheet: false, cached: cached?.review)
            var sheet = try await fetch(sheet: true, cached: cached?.sheet)
            if review.sessionId != sheet.sessionId {
                review = try await fetch(sheet: false, cached: nil)
                sheet = try await fetch(sheet: true, cached: nil)
            }
            let result = PreExamBundle(review: review, sheet: sheet)
            try result.validate(user: user, exam: exam)
            try Task.checkCancellation()
            guard (await cache?.preExamEpoch() ?? 0) == epoch else { throw PreExamError.integrity }
            if let cache { try await cache.savePreExam(result, user: user, exam: exam, epoch: epoch) }
            bundle = result
        } catch is CancellationError { return }
        catch {
            guard !Task.isCancelled, (await cache?.preExamEpoch() ?? 0) == epoch else { return }
            if Self.allowsOffline(error) {
                do {
                    if let cached = try await cache?.loadPreExam(user: user, exam: exam) {
                        try cached.validate(user: user, exam: exam)
                        bundle = cached; offline = true; return
                    }
                } catch { message = "exam.preExam.cacheError"; return }
            }
            if case NetworkError.httpStatus(let status) = error, status == 401 || status == 403 {
                await cache?.purgeUserData(userId: user)
                message = status == 403 ? "exam.preExam.disabled" : "exam.preExam.signIn"
            } else { message = "exam.preExam.error" }
        }
    }
    private func fetch(sheet: Bool, cached: PreExamSnapshot?) async throws -> PreExamSnapshot {
        let schema = sheet ? "exam-cheat-sheet-v1" : "pre-exam-review-v1"
        let tag = cached.map { "\"\($0.etag)\"" }
        let result = try await api.preExam(exam: exam, sheet: sheet, etag: tag)
        switch result {
        case .modified(let body, let responseTag):
            try body.validate(user: user, exam: exam, schema: schema)
            guard responseTag == "\"\(body.etag)\"" else { throw PreExamError.integrity }
            return body
        case .notModified(let responseTag):
            if let cached, cached.expiresAt > Date(), responseTag == tag {
                try cached.validate(user: user, exam: exam, schema: schema)
                return cached
            }
            guard case .modified(let body, let retryTag) = try await api.preExam(exam: exam, sheet: sheet, etag: nil)
            else { throw PreExamError.integrity }
            try body.validate(user: user, exam: exam, schema: schema)
            guard retryTag == "\"\(body.etag)\"" else { throw PreExamError.integrity }
            return body
        }
    }
    static func allowsOffline(_ error: Error) -> Bool {
        guard let error = error as? NetworkError else { return false }
        switch error {
        case .transport: return true
        case .httpStatus(let status): return (500...599).contains(status)
        default: return false
        }
    }
    func recordExport(key: String) async {
        guard let bundle else { return }
        do { try await api.exported(session: bundle.sheet.sessionId, key: key) }
        catch { message = "exam.preExam.exportEventError" }
    }
}
