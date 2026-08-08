import Foundation
import XCTest
@testable import VerifiedAI

final class AuthenticationSessionStoreTests: XCTestCase {
    func testPersistsAndLoadsSession() async throws {
        let store = AuthenticationSessionStore(secureStorage: InMemorySecureStorage())
        let session = Self.session(accessToken: "access-1", refreshToken: "refresh-1")

        try await store.save(session)
        let loaded = try await store.loadSession()

        XCTAssertEqual(loaded, session)
    }

    func testRefreshUsesSingleFlightTask() async throws {
        let store = AuthenticationSessionStore(secureStorage: InMemorySecureStorage())
        let counter = RefreshCounter()
        try await store.save(Self.session(accessToken: "expired", refreshToken: "refresh-1"))
        await store.setRefreshHandler { refreshToken in
            try await counter.refresh(refreshToken: refreshToken)
        }

        async let first = store.refreshAccessToken()
        async let second = store.refreshAccessToken()

        let tokens = try await [first, second]
        let refreshCount = await counter.count

        XCTAssertEqual(tokens, ["access-2", "access-2"])
        XCTAssertEqual(refreshCount, 1)
    }

    private static func session(accessToken: String, refreshToken: String) -> AuthSession {
        AuthSession(
            userId: UUID(uuidString: "00000000-0000-0000-0000-000000000001")!,
            sessionId: UUID(uuidString: "00000000-0000-0000-0000-000000000002")!,
            accessToken: accessToken,
            accessTokenExpiresAt: Date(timeIntervalSince1970: 1_800_000_000),
            refreshToken: refreshToken,
            refreshTokenExpiresAt: Date(timeIntervalSince1970: 1_900_000_000)
        )
    }
}

private actor RefreshCounter {
    private(set) var count = 0

    func refresh(refreshToken: String) async throws -> AuthSession {
        count += 1
        try await Task.sleep(nanoseconds: 50_000_000)
        return AuthSession(
            userId: UUID(uuidString: "00000000-0000-0000-0000-000000000001")!,
            sessionId: UUID(uuidString: "00000000-0000-0000-0000-000000000002")!,
            accessToken: "access-2",
            accessTokenExpiresAt: Date(timeIntervalSince1970: 1_800_000_000),
            refreshToken: "refresh-2",
            refreshTokenExpiresAt: Date(timeIntervalSince1970: 1_900_000_000)
        )
    }
}
