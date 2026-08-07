import Foundation

protocol SecureStorage: AuthTokenProvider {
    func string(forKey key: String) throws -> String?
    func setString(_ value: String, forKey key: String) throws
    func removeValue(forKey key: String) throws
}

extension SecureStorage {
    func accessToken() async throws -> String? {
        try string(forKey: "accessToken")
    }
}

enum SecureStorageError: Error, Equatable {
    case invalidData
    case operationFailed(String)
}

