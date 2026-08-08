import Foundation

actor EntitlementDisplayCache {
    private enum Key {
        static let current = "entitlement.display.current"
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

    func save(_ entitlement: Entitlement) throws {
        let data = try encoder.encode(entitlement)
        try secureStorage.setString(String(decoding: data, as: UTF8.self), forKey: Key.current)
    }

    func load() throws -> Entitlement? {
        guard let value = try secureStorage.string(forKey: Key.current),
              let data = value.data(using: .utf8) else {
            return nil
        }
        return try decoder.decode(Entitlement.self, from: data)
    }

    func clear() throws {
        try secureStorage.removeValue(forKey: Key.current)
    }
}
