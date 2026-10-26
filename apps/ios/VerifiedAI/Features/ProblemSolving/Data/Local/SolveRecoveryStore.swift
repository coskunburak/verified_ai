import Foundation

struct SolveRecoveryMarker: Codable, Equatable {
    let key: String
    let retryOf: UUID?
    let jobId: UUID?
}

@MainActor
protocol SolveRecoveryStoring {
    func load(userId: UUID, sessionId: UUID) throws -> SolveRecoveryMarker?
    func save(_ marker: SolveRecoveryMarker, userId: UUID, sessionId: UUID) throws
    func remove(userId: UUID, sessionId: UUID) throws
    func clearAll() throws
}

/// Identifiers only. Atomic, protected, excluded from backup. Server lookup remains authoritative.
@MainActor
final class SolveRecoveryStore: SolveRecoveryStoring {
    private let root: URL
    private let directory: URL
    init(root: URL? = nil, scope: String) {
        self.root = root ?? FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("SolveRecovery", isDirectory: true)
        self.directory = self.root.appendingPathComponent(scope, isDirectory: true)
    }
    private func file(_ user: UUID, _ session: UUID) -> URL { directory.appendingPathComponent("\(user.uuidString)_\(session.uuidString).json") }
    func load(userId: UUID, sessionId: UUID) throws -> SolveRecoveryMarker? {
        let path = file(userId, sessionId)
        guard FileManager.default.fileExists(atPath: path.path) else { return nil }
        let data = try Data(contentsOf: path)
        guard data.count <= 1024 else { throw SolveContractError.recoveryStorage }
        let marker = try JSONDecoder().decode(SolveRecoveryMarker.self, from: data)
        guard marker.key.range(of: #"^[A-Za-z0-9_-]{8,128}$"#, options: .regularExpression) != nil else { throw SolveContractError.recoveryStorage }
        return marker
    }
    func save(_ marker: SolveRecoveryMarker, userId: UUID, sessionId: UUID) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        var folder = root
        var values = URLResourceValues(); values.isExcludedFromBackup = true
        try folder.setResourceValues(values)
        let target = file(userId, sessionId)
        // Never evict an uncertain request to make room for a new billable operation.
        if !FileManager.default.fileExists(atPath: target.path),
           try FileManager.default.contentsOfDirectory(atPath: directory.path).count >= 128 { throw SolveContractError.recoveryStorage }
        try JSONEncoder().encode(marker).write(to: target, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
    }
    func remove(userId: UUID, sessionId: UUID) throws {
        let target = file(userId, sessionId)
        if FileManager.default.fileExists(atPath: target.path) { try FileManager.default.removeItem(at: target) }
    }
    func clearAll() throws {
        if FileManager.default.fileExists(atPath: root.path) { try FileManager.default.removeItem(at: root) }
    }
}
