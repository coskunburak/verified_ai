import Foundation
struct AttemptPending: Codable, Equatable {
    let key: String
    let submission: AttemptSubmission
}
/// Private local retry material; never a verified result or a training dataset.
@MainActor
final class AttemptAnalysisPendingStore {
    private let directory: URL
    init(directory: URL? = nil) {
        self.directory = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("AttemptPending")
    }
    private func file(_ owner: UUID, _ problem: UUID) -> URL { directory.appendingPathComponent("\(owner)_\(problem).json") }
    func load(owner: UUID, problem: UUID) throws -> AttemptPending? {
        let url = file(owner, problem)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        let handle = try FileHandle(forReadingFrom: url)
        defer { try? handle.close() }
        // Bound the allocation before decoding, including corrupt or externally enlarged files.
        let data = try handle.read(upToCount: 16385) ?? Data()
        guard data.count <= 16384 else { throw NetworkError.invalidResponse }
        let result = try JSONDecoder().decode(AttemptPending.self, from: data)
        try validate(result)
        return result
    }
    private func validate(_ value: AttemptPending) throws {
        guard value.key.range(of: #"^[A-Za-z0-9_-]{8,128}$"#, options: .regularExpression) != nil,
              value.submission.isValid else { throw NetworkError.invalidResponse }
    }
    func save(_ value: AttemptPending, owner: UUID, problem: UUID) throws {
        try validate(value)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var url = directory; var attributes = URLResourceValues(); attributes.isExcludedFromBackup = true
        try url.setResourceValues(attributes)
        if !FileManager.default.fileExists(atPath: file(owner, problem).path),
           try FileManager.default.contentsOfDirectory(atPath: directory.path).count >= 30 { throw NetworkError.invalidResponse }
        let data = try JSONEncoder().encode(value)
        guard data.count <= 16384 else { throw NetworkError.invalidResponse }
        try data.write(to: file(owner, problem), options: [.atomic, .completeFileProtection])
    }
    func remove(owner: UUID, problem: UUID) throws {
        let url = file(owner, problem)
        if FileManager.default.fileExists(atPath: url.path) { try FileManager.default.removeItem(at: url) }
    }
    struct Draft: Codable {
        let text: String
        let realDomainConfirmed: Bool
        var captureReview: AttemptCaptureReview? = nil
        var source: AttemptSourceIdentity? = nil
        var finalAnswer: String? = nil
        var structuredWork: AttemptWorkRequest? = nil
    }
    func saveDraft(_ draft: Draft, owner: UUID, problem: UUID) throws {
        guard draft.text.utf8.count <= 12288, (draft.finalAnswer?.utf8.count ?? 0) <= 1024 else { throw NetworkError.invalidResponse }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var root = directory; var attributes = URLResourceValues(); attributes.isExcludedFromBackup = true; try root.setResourceValues(attributes)
        let url = directory.appendingPathComponent("draft_\(owner)_\(problem).json")
        if !FileManager.default.fileExists(atPath: url.path), try FileManager.default.contentsOfDirectory(atPath: directory.path).count >= 60 { throw NetworkError.invalidResponse }
        let data = try JSONEncoder().encode(draft)
        guard data.count <= (draft.structuredWork == nil ? 16384 : 20000) else { throw NetworkError.invalidResponse }
        try data.write(to: url, options: [.atomic, .completeFileProtection])
    }
    func loadDraft(owner: UUID, problem: UUID) throws -> Draft? {
        let url = directory.appendingPathComponent("draft_\(owner)_\(problem).json")
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        let handle = try FileHandle(forReadingFrom: url); defer { try? handle.close() }
        let data = try handle.read(upToCount: 20001) ?? Data()
        guard data.count <= 20000 else { throw NetworkError.invalidResponse }
        let draft = try JSONDecoder().decode(Draft.self, from: data)
        guard data.count <= (draft.structuredWork == nil ? 16384 : 20000) else { throw NetworkError.invalidResponse }
        return draft
    }
    func removeDraft(owner: UUID, problem: UUID) throws {
        let url = directory.appendingPathComponent("draft_\(owner)_\(problem).json")
        if FileManager.default.fileExists(atPath: url.path) { try FileManager.default.removeItem(at: url) }
    }
    func clearAll() throws {
        if FileManager.default.fileExists(atPath: directory.path) { try FileManager.default.removeItem(at: directory) }
    }
}

struct AttemptWorkPending: Codable, Equatable {
    let key: String
    let input: AttemptWorkRequest
}
extension AttemptAnalysisPendingStore {
    private func workFile(_ owner: UUID, _ problem: UUID) -> URL { directory.appendingPathComponent("work_\(owner)_\(problem).json") }
    func loadWork(owner: UUID, problem: UUID) throws -> AttemptWorkPending? {
        let url = workFile(owner, problem)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        let handle = try FileHandle(forReadingFrom: url); defer { try? handle.close() }
        let data = try handle.read(upToCount: 20001) ?? Data()
        guard data.count <= 20000 else { throw NetworkError.invalidResponse }
        let result = try JSONDecoder().decode(AttemptWorkPending.self, from: data)
        guard result.input.isValid, result.key.range(of: #"^[A-Za-z0-9_-]{8,128}$"#, options: .regularExpression) != nil else { throw NetworkError.invalidResponse }
        return result
    }
    func saveWork(_ value: AttemptWorkPending, owner: UUID, problem: UUID) throws {
        guard value.input.isValid, try JSONEncoder().encode(value.input).count <= 16384,
              value.key.range(of: #"^[A-Za-z0-9_-]{8,128}$"#, options: .regularExpression) != nil else { throw NetworkError.invalidResponse }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var root = directory; var attributes = URLResourceValues(); attributes.isExcludedFromBackup = true; try root.setResourceValues(attributes)
        if !FileManager.default.fileExists(atPath: workFile(owner, problem).path), try FileManager.default.contentsOfDirectory(atPath: directory.path).count >= 60 { throw NetworkError.invalidResponse }
        try JSONEncoder().encode(value).write(to: workFile(owner, problem), options: [.atomic, .completeFileProtection])
    }
    func removeWork(owner: UUID, problem: UUID) throws {
        let url = workFile(owner, problem)
        if FileManager.default.fileExists(atPath: url.path) { try FileManager.default.removeItem(at: url) }
    }
}

extension AttemptAnalysisPendingStore {
    // Native crop previews stay on device, excluded from backup and erased by the existing account purge.
    // The immutable capture UUID binds this local evidence; it never claims server asset ownership.
    private func previewFile(_ owner: UUID, _ problem: UUID, _ capture: UUID) -> URL {
        directory.appendingPathComponent("capture_\(owner)_\(problem)_\(capture).jpg")
    }
    func saveWorkPreview(_ data: Data, owner: UUID, problem: UUID, capture: UUID) throws {
        guard data.count <= 2_000_000 else { throw NetworkError.invalidResponse }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var root = directory; var flags = URLResourceValues(); flags.isExcludedFromBackup = true; try root.setResourceValues(flags)
        let target = previewFile(owner, problem, capture)
        if FileManager.default.fileExists(atPath: target.path) {
            guard try loadWorkPreview(owner: owner, problem: problem, capture: capture) == data else { throw NetworkError.invalidResponse }
            return
        }
        guard try FileManager.default.contentsOfDirectory(atPath: directory.path).filter({ $0.hasPrefix("capture_") }).count < 30 else { throw NetworkError.invalidResponse }
        try data.write(to: target, options: [.atomic, .completeFileProtection])
    }
    func removeWorkPreview(owner: UUID, problem: UUID, capture: UUID) throws {
        let target = previewFile(owner, problem, capture)
        if FileManager.default.fileExists(atPath: target.path) { try FileManager.default.removeItem(at: target) }
    }
    func loadWorkPreview(owner: UUID, problem: UUID, capture: UUID) throws -> Data? {
        let url = previewFile(owner, problem, capture)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        let handle = try FileHandle(forReadingFrom: url); defer { try? handle.close() }
        let data = try handle.read(upToCount: 2_000_001) ?? Data()
        guard data.count <= 2_000_000 else { throw NetworkError.invalidResponse }
        return data
    }
}

struct AttemptEvaluationPending: Codable, Equatable {
    let attemptId: UUID; let key: String; let input: AttemptEvaluationRequest
}
extension AttemptAnalysisPendingStore {
    private func evaluationFile(_ owner: UUID, _ problem: UUID) -> URL { directory.appendingPathComponent("evaluation_\(owner)_\(problem).json") }
    func loadEvaluation(owner: UUID, problem: UUID) throws -> AttemptEvaluationPending? {
        let url = evaluationFile(owner, problem)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        let handle = try FileHandle(forReadingFrom: url); defer { try? handle.close() }
        let data = try handle.read(upToCount: 2049) ?? Data()
        guard data.count <= 2048 else { throw NetworkError.invalidResponse }
        let value = try JSONDecoder().decode(AttemptEvaluationPending.self, from: data)
        try validateEvaluation(value); return value
    }
    private func validateEvaluation(_ value: AttemptEvaluationPending) throws {
        guard value.key.range(of: #"^[A-Za-z0-9_-]{8,128}$"#, options: .regularExpression) != nil,
              value.input.schemaVersion == "attempt-evaluation-request-v1", (1...30).contains(value.input.workRevision),
              value.input.source.canonicalRevision > 0, value.input.source.parseRevision > 0 else { throw NetworkError.invalidResponse }
    }
    func saveEvaluation(_ value: AttemptEvaluationPending, owner: UUID, problem: UUID) throws {
        try validateEvaluation(value)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var root = directory; var flags = URLResourceValues(); flags.isExcludedFromBackup = true; try root.setResourceValues(flags)
        let url = evaluationFile(owner, problem)
        if !FileManager.default.fileExists(atPath: url.path), try FileManager.default.contentsOfDirectory(atPath: directory.path).count >= 90 { throw NetworkError.invalidResponse }
        let data = try JSONEncoder().encode(value)
        guard data.count <= 2048 else { throw NetworkError.invalidResponse }
        try data.write(to: url, options: [.atomic, .completeFileProtection])
    }
    func removeEvaluation(owner: UUID, problem: UUID) throws {
        let url = evaluationFile(owner, problem)
        if FileManager.default.fileExists(atPath: url.path) { try FileManager.default.removeItem(at: url) }
    }
}
