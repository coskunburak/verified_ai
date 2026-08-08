import Foundation
import Observation

@MainActor
@Observable
final class AccountSettingsViewModel {
    private let accountPrivacyAPI: AccountPrivacyServicing
    private let sessionStore: AuthenticationSessionStore
    private let networkMonitor: NetworkMonitoring
    private let logger: AppLogger

    private(set) var state: AccountSettingsState = .idle
    private(set) var account: AccountState?
    private(set) var exportRecord: DataExportRecord?
    private(set) var exportDocument: DataExportDocument?
    private(set) var deletionRequest: DeletionRequest?
    private(set) var message: String?

    init(
        accountPrivacyAPI: AccountPrivacyServicing,
        sessionStore: AuthenticationSessionStore,
        networkMonitor: NetworkMonitoring,
        logger: AppLogger
    ) {
        self.accountPrivacyAPI = accountPrivacyAPI
        self.sessionStore = sessionStore
        self.networkMonitor = networkMonitor
        self.logger = logger
    }

    func load() async {
        guard networkMonitor.isReachable else {
            state = .offline
            message = "Account settings are unavailable offline."
            return
        }

        state = .loading
        message = nil
        do {
            account = try await accountPrivacyAPI.currentAccount()
            deletionRequest = try await accountPrivacyAPI.deletionRequest()
            state = .ready
            logger.info("account.settings.ready")
        } catch {
            state = .failed
            message = "Account settings could not be loaded."
            logger.warning("account.settings.failed")
        }
    }

    func reset() {
        state = .idle
        account = nil
        exportRecord = nil
        exportDocument = nil
        deletionRequest = nil
        message = nil
    }

    func requestDataExport() async {
        guard networkMonitor.isReachable else {
            state = .offline
            message = "Data export is unavailable offline."
            return
        }

        state = .exporting
        message = nil
        do {
            let record = try await accountPrivacyAPI.requestDataExport()
            exportRecord = record
            exportDocument = try await accountPrivacyAPI.downloadDataExport(exportId: record.exportId)
            state = .ready
            message = "Data export ready."
            logger.info("account.export.ready")
        } catch {
            state = .failed
            message = "Data export could not be prepared."
            logger.warning("account.export.failed")
        }
    }

    func requestDeletion() async {
        guard networkMonitor.isReachable else {
            state = .offline
            message = "Account deletion is unavailable offline."
            return
        }

        state = .requestingDeletion
        message = nil
        do {
            let request = try await accountPrivacyAPI.requestDeletion()
            deletionRequest = request
            account = account.map {
                AccountState(
                    userId: $0.userId,
                    status: request.status,
                    createdAt: $0.createdAt,
                    deletionRequestedAt: request.deletionRequestedAt,
                    deletedAt: request.deletedAt
                )
            }
            state = .deletionRequested
            message = "Deletion requested."
            logger.info("account.deletion.requested")
        } catch {
            state = .failed
            message = "Deletion request could not be submitted."
            logger.warning("account.deletion.request_failed")
        }
    }

    func confirmDeletion(confirmationText: String) async {
        guard networkMonitor.isReachable else {
            state = .offline
            message = "Account deletion is unavailable offline."
            return
        }

        state = .confirmingDeletion
        message = nil
        do {
            let request = try await accountPrivacyAPI.confirmDeletion(confirmationText: confirmationText)
            deletionRequest = request
            account = account.map {
                AccountState(
                    userId: $0.userId,
                    status: request.status,
                    createdAt: $0.createdAt,
                    deletionRequestedAt: request.deletionRequestedAt,
                    deletedAt: request.deletedAt
                )
            }
            try await sessionStore.clear()
            state = .deleted
            message = "Account deleted."
            logger.info("account.deletion.confirmed")
        } catch NetworkError.server(let problem) where problem.code == "DELETION_CONFIRMATION_INVALID" {
            state = .deletionRequested
            message = "Confirmation did not match."
            logger.warning("account.deletion.confirmation_invalid")
        } catch {
            state = .failed
            message = "Account deletion could not be completed."
            logger.warning("account.deletion.confirm_failed")
        }
    }
}

enum AccountSettingsState: Equatable {
    case idle
    case loading
    case ready
    case exporting
    case requestingDeletion
    case deletionRequested
    case confirmingDeletion
    case deleted
    case offline
    case failed

    var isBusy: Bool {
        switch self {
        case .loading, .exporting, .requestingDeletion, .confirmingDeletion:
            return true
        case .idle, .ready, .deletionRequested, .deleted, .offline, .failed:
            return false
        }
    }
}
