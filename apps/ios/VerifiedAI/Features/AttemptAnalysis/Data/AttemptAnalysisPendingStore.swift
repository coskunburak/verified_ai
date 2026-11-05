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
    }
    func saveDraft(_ draft: Draft, owner: UUID, problem: UUID) throws {
        guard draft.text.utf8.count <= 12288, (draft.finalAnswer?.utf8.count ?? 0) <= 1024 else { throw NetworkError.invalidResponse }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var root = directory; var attributes = URLResourceValues(); attributes.isExcludedFromBackup = true; try root.setResourceValues(attributes)
        let url = directory.appendingPathComponent("draft_\(owner)_\(problem).json")
        if !FileManager.default.fileExists(atPath: url.path), try FileManager.default.contentsOfDirectory(atPath: directory.path).count >= 60 { throw NetworkError.invalidResponse }
        let data = try JSONEncoder().encode(draft)
        guard data.count <= 16384 else { throw NetworkError.invalidResponse }
        try data.write(to: url, options: [.atomic, .completeFileProtection])
    }
    func loadDraft(owner: UUID, problem: UUID) throws -> Draft? {
        let url = directory.appendingPathComponent("draft_\(owner)_\(problem).json")
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        let handle = try FileHandle(forReadingFrom: url); defer { try? handle.close() }
        let data = try handle.read(upToCount: 16385) ?? Data()
        guard data.count <= 16384 else { throw NetworkError.invalidResponse }
        return try JSONDecoder().decode(Draft.self, from: data)
    }
    func removeDraft(owner: UUID, problem: UUID) throws {
        let url = directory.appendingPathComponent("draft_\(owner)_\(problem).json")
        if FileManager.default.fileExists(atPath: url.path) { try FileManager.default.removeItem(at: url) }
    }
    func clearAll() throws {
        if FileManager.default.fileExists(atPath: directory.path) { try FileManager.default.removeItem(at: directory) }
    }
}
