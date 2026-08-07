import Foundation
import Security

final class KeychainStore: SecureStorage {
    private let service: String

    init(service: String) {
        self.service = service
    }

    func string(forKey key: String) throws -> String? {
        var query = baseQuery(forKey: key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        if status == errSecItemNotFound {
            return nil
        }

        guard status == errSecSuccess else {
            throw SecureStorageError.operationFailed("keychain read failed: \(status)")
        }

        guard let data = result as? Data, let value = String(data: data, encoding: .utf8) else {
            throw SecureStorageError.invalidData
        }

        return value
    }

    func setString(_ value: String, forKey key: String) throws {
        try removeValue(forKey: key)
        var query = baseQuery(forKey: key)
        query[kSecValueData as String] = Data(value.utf8)

        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw SecureStorageError.operationFailed("keychain write failed: \(status)")
        }
    }

    func removeValue(forKey key: String) throws {
        let status = SecItemDelete(baseQuery(forKey: key) as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw SecureStorageError.operationFailed("keychain delete failed: \(status)")
        }
    }

    private func baseQuery(forKey key: String) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: key,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
    }
}

final class InMemorySecureStorage: SecureStorage {
    private var values: [String: String] = [:]

    func string(forKey key: String) throws -> String? {
        values[key]
    }

    func setString(_ value: String, forKey key: String) throws {
        values[key] = value
    }

    func removeValue(forKey key: String) throws {
        values.removeValue(forKey: key)
    }
}

