import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class SolveRecoveryStoreTests: XCTestCase {
    func testRelaunchAccountIsolationScopeIsolationAndDeletion() throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let user = UUID(), session = UUID()
        let marker = SolveRecoveryMarker(key: "stable_request_key", retryOf: UUID(), jobId: nil)
        let first = SolveRecoveryStore(root: root, scope: "server-development")
        try first.save(marker, userId: user, sessionId: session)
        let restored = SolveRecoveryStore(root: root, scope: "server-development")
        XCTAssertEqual(try restored.load(userId: user, sessionId: session), marker)
        XCTAssertNil(try restored.load(userId: UUID(), sessionId: session))
        XCTAssertNil(try SolveRecoveryStore(root: root, scope: "synthetic-fixture").load(userId: user, sessionId: session))
        try restored.clearAll()
        XCTAssertNil(try first.load(userId: user, sessionId: session))
    }
}
