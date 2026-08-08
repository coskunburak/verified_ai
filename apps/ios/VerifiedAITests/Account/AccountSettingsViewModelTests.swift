import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class AccountSettingsViewModelTests: XCTestCase {
    func testLoadFetchesAccountAndDeletionState() async {
        let service = StubAccountPrivacyService()
        let viewModel = makeViewModel(service: service)

        await viewModel.load()

        XCTAssertEqual(viewModel.state, .ready)
        XCTAssertEqual(viewModel.account?.status, .active)
        XCTAssertEqual(viewModel.deletionRequest?.status, .active)
    }

    func testExportRequestsAndDownloadsCurrentData() async {
        let service = StubAccountPrivacyService()
        let viewModel = makeViewModel(service: service)

        await viewModel.requestDataExport()

        XCTAssertEqual(viewModel.state, .ready)
        XCTAssertEqual(viewModel.exportRecord?.status, .ready)
        XCTAssertEqual(viewModel.exportDocument?.categories, ["account", "billing", "learningProfile", "sessions"])
        let requestCount = await service.exportRequestCount
        let downloadCount = await service.downloadCount
        XCTAssertEqual(requestCount, 1)
        XCTAssertEqual(downloadCount, 1)
    }

    func testConfirmDeletionClearsLocalSessionAndMarksDeleted() async throws {
        let storage = InMemorySecureStorage()
        let sessionStore = AuthenticationSessionStore(secureStorage: storage)
        try await sessionStore.save(AuthSession.fixture())
        let service = StubAccountPrivacyService()
        let viewModel = makeViewModel(service: service, sessionStore: sessionStore)

        await viewModel.confirmDeletion(confirmationText: "DELETE")

        XCTAssertEqual(viewModel.state, .deleted)
        XCTAssertEqual(viewModel.deletionRequest?.status, .deleted)
        let loaded = try await sessionStore.loadSession()
        XCTAssertNil(loaded)
    }

    func testOfflinePreventsDataExportRequest() async {
        let service = StubAccountPrivacyService()
        let viewModel = makeViewModel(service: service, networkMonitor: StubNetworkMonitor(isReachable: false))

        await viewModel.requestDataExport()

        XCTAssertEqual(viewModel.state, .offline)
        let requestCount = await service.exportRequestCount
        XCTAssertEqual(requestCount, 0)
    }

    private func makeViewModel(
        service: StubAccountPrivacyService,
        sessionStore: AuthenticationSessionStore = AuthenticationSessionStore(secureStorage: InMemorySecureStorage()),
        networkMonitor: StubNetworkMonitor = StubNetworkMonitor()
    ) -> AccountSettingsViewModel {
        AccountSettingsViewModel(
            accountPrivacyAPI: service,
            sessionStore: sessionStore,
            networkMonitor: networkMonitor,
            logger: AppLogger(subsystem: "com.verifiedai.learning.tests", category: "account")
        )
    }
}

private actor StubAccountPrivacyService: AccountPrivacyServicing {
    private(set) var exportRequestCount = 0
    private(set) var downloadCount = 0

    func currentAccount() async throws -> AccountState {
        AccountState(
            userId: Self.userId,
            status: .active,
            createdAt: Self.createdAt,
            deletionRequestedAt: nil,
            deletedAt: nil
        )
    }

    func requestDataExport() async throws -> DataExportRecord {
        exportRequestCount += 1
        return Self.exportRecord
    }

    func dataExportStatus(exportId: UUID) async throws -> DataExportRecord {
        Self.exportRecord
    }

    func downloadDataExport(exportId: UUID) async throws -> DataExportDocument {
        downloadCount += 1
        return DataExportDocument(
            schemaVersion: "phase3-account-v1",
            generatedAt: Self.createdAt,
            categories: ["account", "billing", "learningProfile", "sessions"]
        )
    }

    func requestDeletion() async throws -> DeletionRequest {
        DeletionRequest(
            userId: Self.userId,
            status: .deletionRequested,
            deletionRequestedAt: Self.createdAt,
            deletedAt: nil
        )
    }

    func deletionRequest() async throws -> DeletionRequest {
        DeletionRequest(
            userId: Self.userId,
            status: .active,
            deletionRequestedAt: nil,
            deletedAt: nil
        )
    }

    func confirmDeletion(confirmationText: String) async throws -> DeletionRequest {
        DeletionRequest(
            userId: Self.userId,
            status: .deleted,
            deletionRequestedAt: Self.createdAt,
            deletedAt: Self.deletedAt
        )
    }

    private static let userId = UUID(uuidString: "00000000-0000-0000-0000-000000000101")!
    private static let exportId = UUID(uuidString: "00000000-0000-0000-0000-000000000707")!
    private static let createdAt = Date(timeIntervalSince1970: 1_800_000_000)
    private static let deletedAt = Date(timeIntervalSince1970: 1_800_000_100)

    private static var exportRecord: DataExportRecord {
        DataExportRecord(
            exportId: exportId,
            status: .ready,
            schemaVersion: "phase3-account-v1",
            requestedAt: createdAt,
            completedAt: createdAt,
            downloadedAt: nil,
            expiresAt: Date(timeIntervalSince1970: 1_800_604_800)
        )
    }
}

@MainActor
private final class StubNetworkMonitor: NetworkMonitoring {
    var isReachable: Bool

    init(isReachable: Bool = true) {
        self.isReachable = isReachable
    }
}

private extension AuthSession {
    static func fixture() -> AuthSession {
        AuthSession(
            userId: UUID(uuidString: "00000000-0000-0000-0000-000000000101")!,
            sessionId: UUID(uuidString: "00000000-0000-0000-0000-000000000202")!,
            accessToken: "access",
            accessTokenExpiresAt: Date(timeIntervalSince1970: 1_800_000_000),
            refreshToken: "refresh",
            refreshTokenExpiresAt: Date(timeIntervalSince1970: 1_900_000_000)
        )
    }
}
