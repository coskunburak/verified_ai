import XCTest
@testable import VerifiedAI

final class SecureStorageTests: XCTestCase {
    func testInMemorySecureStorageSupportsTokenLifecycle() async throws {
        let storage = InMemorySecureStorage()

        try storage.setString("token", forKey: "accessToken")
        let storedToken = try await storage.accessToken()
        XCTAssertEqual(storedToken, "token")

        try storage.removeValue(forKey: "accessToken")
        let removedToken = try await storage.accessToken()
        XCTAssertNil(removedToken)
    }
}
