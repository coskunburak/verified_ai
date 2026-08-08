import Foundation

protocol AuthTokenProvider: Sendable {
    func accessToken() async throws -> String?
    func refreshAccessToken() async throws -> String?
}

extension AuthTokenProvider {
    func refreshAccessToken() async throws -> String? {
        nil
    }
}
