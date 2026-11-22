import Foundation

actor EntitlementDisplayCache {
    struct Scope: Equatable, Sendable {
        let user: UUID
        let session: UUID
        var key: String { "entitlement.display.\(user).\(session)" }
    }
    private struct Entry: Codable {
        let saved: Date
        let entitlement: Entitlement
    }

    private let secureStorage: SecureStorage
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    init(secureStorage: SecureStorage) {
        self.secureStorage = secureStorage
        self.encoder = JSONEncoder()
        self.encoder.dateEncodingStrategy = .iso8601
        self.decoder = JSONDecoder()
        self.decoder.dateDecodingStrategy = .iso8601
    }

    func save(_ entitlement: Entitlement, scope: Scope) throws {
        guard entitlement.userId == scope.user else { return }
        let data = try encoder.encode(Entry(saved: Date(), entitlement: entitlement))
        try secureStorage.setString(String(decoding: data, as: UTF8.self), forKey: scope.key)
    }

    func load(scope: Scope) throws -> Entitlement? {
        guard let value = try secureStorage.string(forKey: scope.key),
              let entry = try? decoder.decode(Entry.self, from: Data(value.utf8)),
              entry.entitlement.userId == scope.user,
              (0..<86400).contains(Date().timeIntervalSince(entry.saved)) else { return nil }
        return entry.entitlement
    }

    func clear(scope: Scope) throws {
        try secureStorage.removeValue(forKey: scope.key)
        try secureStorage.removeValue(forKey: "entitlement.display.current")
    }
}
