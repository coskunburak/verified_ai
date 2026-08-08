import Foundation

enum AccountStatus: String, Equatable, Sendable {
    case active = "ACTIVE"
    case disabled = "DISABLED"
    case locked = "LOCKED"
    case deletionRequested = "DELETION_REQUESTED"
    case deletionInProgress = "DELETION_IN_PROGRESS"
    case deleted = "DELETED"

    var title: String {
        switch self {
        case .active:
            return "Active"
        case .disabled:
            return "Disabled"
        case .locked:
            return "Locked"
        case .deletionRequested:
            return "Deletion requested"
        case .deletionInProgress:
            return "Deletion in progress"
        case .deleted:
            return "Deleted"
        }
    }
}

struct AccountState: Equatable, Sendable {
    let userId: UUID
    let status: AccountStatus
    let createdAt: Date
    let deletionRequestedAt: Date?
    let deletedAt: Date?
}

enum DataExportStatus: String, Equatable, Sendable {
    case ready = "READY"
    case expired = "EXPIRED"
    case failed = "FAILED"
}

struct DataExportRecord: Equatable, Sendable {
    let exportId: UUID
    let status: DataExportStatus
    let schemaVersion: String
    let requestedAt: Date
    let completedAt: Date?
    let downloadedAt: Date?
    let expiresAt: Date
}

struct DataExportDocument: Equatable, Sendable {
    let schemaVersion: String
    let generatedAt: Date?
    let categories: [String]
}

struct DeletionRequest: Equatable, Sendable {
    let userId: UUID
    let status: AccountStatus
    let deletionRequestedAt: Date?
    let deletedAt: Date?
}
