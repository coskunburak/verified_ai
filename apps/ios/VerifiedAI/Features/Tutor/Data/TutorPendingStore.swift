import Foundation

struct TutorPending: Codable, Equatable {
    let key: String
    let session: UUID?
    let content: String?
    // Missing kind identifies a Sprint 6.4 append; never replay it through the new turn endpoint.
    enum Kind: String, Codable { case turn }
    var kind: Kind? = nil
    var intent: TutorIntent? = nil
}
/// One bounded uncertain operation per owner/problem. Never an authoritative transcript.
/// Atomic, data protected, excluded from backup; removed after acknowledgement or logout.
@MainActor
final class TutorPendingStore {
    private let directory: URL
    init(directory: URL? = nil) {
        self.directory = directory ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("TutorPending")
    }
    private func file(_ owner: UUID, _ problem: UUID) -> URL { directory.appendingPathComponent("\(owner)_\(problem).json") }
    func load(owner: UUID, problem: UUID) throws -> TutorPending? {
        let url = file(owner, problem)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        let data = try Data(contentsOf: url)
        guard data.count <= 16384 else { throw NetworkError.invalidResponse }
        let pending = try JSONDecoder().decode(TutorPending.self, from: data)
        guard pending.key.range(of: #"^[A-Za-z0-9_-]{8,128}$"#, options: .regularExpression) != nil,
              (pending.session == nil) == (pending.content == nil),
              (pending.content?.utf8.count ?? 0) <= 8000,
              pending.kind == nil || (pending.session != nil && pending.intent != nil) else { throw NetworkError.invalidResponse }
        return pending
    }
    func save(_ pending: TutorPending, owner: UUID, problem: UUID) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var url = directory; var values = URLResourceValues(); values.isExcludedFromBackup = true
        try url.setResourceValues(values)
        if !FileManager.default.fileExists(atPath: file(owner, problem).path),
           try FileManager.default.contentsOfDirectory(atPath: directory.path).count >= 30 { throw NetworkError.invalidResponse }
        try JSONEncoder().encode(pending).write(to: file(owner, problem), options: [.atomic, .completeFileProtection])
    }
    func remove(owner: UUID, problem: UUID) throws {
        let url = file(owner, problem)
        if FileManager.default.fileExists(atPath: url.path) { try FileManager.default.removeItem(at: url) }
    }
    func clearAll() throws {
        if FileManager.default.fileExists(atPath: directory.path) { try FileManager.default.removeItem(at: directory) }
    }
}
