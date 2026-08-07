import Foundation

protocol AuthTokenProvider {
    func accessToken() async throws -> String?
}

