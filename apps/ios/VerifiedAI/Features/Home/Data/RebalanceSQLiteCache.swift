import Foundation
import SQLite3

private let sqliteTransient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

public enum RebalanceTriggerType: String, Codable, Sendable, CaseIterable {
    case missedDays = "MISSED_DAYS"
    case paceChange = "PACE_CHANGE"
    case examDateShift = "EXAM_DATE_SHIFT"
    case manualReset = "MANUAL_RESET"
    case miniModeDownshift = "MINI_MODE_DOWNSHIFT"
}

public enum PruneReasonCode: String, Codable, Sendable, CaseIterable {
    case none = "NONE"
    case missedDayWindowReset = "MISSED_DAY_WINDOW_RESET"
    case budgetCeilingLowRoiPruned = "BUDGET_CEILING_LOW_ROI_PRUNED"
    case dailyMissionCapEnforced = "DAILY_MISSION_CAP_ENFORCED"
    case miniModeSingleFocus = "MINI_MODE_SINGLE_FOCUS"
}

public struct StudyPlanRecoveryStatus: Codable, Equatable, Sendable {
    public let schemaVersion: String
    public let userId: UUID
    public let asOfDate: String
    public let recoveryNeeded: Bool
    public let missedDaysCount: Int
    public let daysUntilExam: Int
    public let previousDailyBudgetMinutes: Int
    public let recommendedDailyBudgetMinutes: Int
    public let miniModeRecommended: Bool
    public let activeTodayTasksCount: Int
    public let prunedTasksCount: Int
    public let rescheduledReviewsCount: Int
    public let pruneReasonCode: PruneReasonCode
    public let welcomeMessageKey: String
    public let lastRebalanceEventId: UUID?

    enum CodingKeys: String, CodingKey {
        case schemaVersion = "schema_version"
        case userId = "user_id"
        case asOfDate = "as_of_date"
        case recoveryNeeded = "recovery_needed"
        case missedDaysCount = "missed_days_count"
        case daysUntilExam = "days_until_exam"
        case previousDailyBudgetMinutes = "previous_daily_budget_minutes"
        case recommendedDailyBudgetMinutes = "recommended_daily_budget_minutes"
        case miniModeRecommended = "mini_mode_recommended"
        case activeTodayTasksCount = "active_today_tasks_count"
        case prunedTasksCount = "pruned_tasks_count"
        case rescheduledReviewsCount = "rescheduled_reviews_count"
        case pruneReasonCode = "prune_reason_code"
        case welcomeMessageKey = "welcome_message_key"
        case lastRebalanceEventId = "last_rebalance_event_id"
    }

    public init(
        schemaVersion: String = "1.0.0",
        userId: UUID,
        asOfDate: String,
        recoveryNeeded: Bool,
        missedDaysCount: Int,
        daysUntilExam: Int,
        previousDailyBudgetMinutes: Int,
        recommendedDailyBudgetMinutes: Int,
        miniModeRecommended: Bool,
        activeTodayTasksCount: Int,
        prunedTasksCount: Int,
        rescheduledReviewsCount: Int,
        pruneReasonCode: PruneReasonCode,
        welcomeMessageKey: String,
        lastRebalanceEventId: UUID? = nil
    ) {
        self.schemaVersion = schemaVersion
        self.userId = userId
        self.asOfDate = asOfDate
        self.recoveryNeeded = recoveryNeeded
        self.missedDaysCount = missedDaysCount
        self.daysUntilExam = daysUntilExam
        self.previousDailyBudgetMinutes = previousDailyBudgetMinutes
        self.recommendedDailyBudgetMinutes = recommendedDailyBudgetMinutes
        self.miniModeRecommended = miniModeRecommended
        self.activeTodayTasksCount = activeTodayTasksCount
        self.prunedTasksCount = prunedTasksCount
        self.rescheduledReviewsCount = rescheduledReviewsCount
        self.pruneReasonCode = pruneReasonCode
        self.welcomeMessageKey = welcomeMessageKey
        self.lastRebalanceEventId = lastRebalanceEventId
    }
}

public struct StudyPlanRebalanceResponse: Codable, Equatable, Sendable {
    public let schemaVersion: String
    public let rebalanceEventId: UUID
    public let userId: UUID
    public let recoveryDate: String
    public let triggerType: RebalanceTriggerType
    public let missedDaysCount: Int
    public let daysUntilExam: Int
    public let previousDailyBudgetMinutes: Int
    public let rebalancedDailyBudgetMinutes: Int
    public let prunedTasksCount: Int
    public let rescheduledReviewsCount: Int
    public let activeTodayTasksCount: Int
    public let miniModeActivated: Bool
    public let pruneReasonCode: PruneReasonCode
    public let prunedConceptIds: [String]
    public let welcomeMessageKey: String
    public let idempotentReplay: Bool
    public let createdAt: String

    enum CodingKeys: String, CodingKey {
        case schemaVersion = "schema_version"
        case rebalanceEventId = "rebalance_event_id"
        case userId = "user_id"
        case recoveryDate = "recovery_date"
        case triggerType = "trigger_type"
        case missedDaysCount = "missed_days_count"
        case daysUntilExam = "days_until_exam"
        case previousDailyBudgetMinutes = "previous_daily_budget_minutes"
        case rebalancedDailyBudgetMinutes = "rebalanced_daily_budget_minutes"
        case prunedTasksCount = "pruned_tasks_count"
        case rescheduledReviewsCount = "rescheduled_reviews_count"
        case activeTodayTasksCount = "active_today_tasks_count"
        case miniModeActivated = "mini_mode_activated"
        case pruneReasonCode = "prune_reason_code"
        case prunedConceptIds = "pruned_concept_ids"
        case welcomeMessageKey = "welcome_message_key"
        case idempotentReplay = "idempotent_replay"
        case createdAt = "created_at"
    }

    public init(
        schemaVersion: String = "1.0.0",
        rebalanceEventId: UUID,
        userId: UUID,
        recoveryDate: String,
        triggerType: RebalanceTriggerType,
        missedDaysCount: Int,
        daysUntilExam: Int,
        previousDailyBudgetMinutes: Int,
        rebalancedDailyBudgetMinutes: Int,
        prunedTasksCount: Int,
        rescheduledReviewsCount: Int,
        activeTodayTasksCount: Int,
        miniModeActivated: Bool,
        pruneReasonCode: PruneReasonCode,
        prunedConceptIds: [String],
        welcomeMessageKey: String,
        idempotentReplay: Bool,
        createdAt: String
    ) {
        self.schemaVersion = schemaVersion
        self.rebalanceEventId = rebalanceEventId
        self.userId = userId
        self.recoveryDate = recoveryDate
        self.triggerType = triggerType
        self.missedDaysCount = missedDaysCount
        self.daysUntilExam = daysUntilExam
        self.previousDailyBudgetMinutes = previousDailyBudgetMinutes
        self.rebalancedDailyBudgetMinutes = rebalancedDailyBudgetMinutes
        self.prunedTasksCount = prunedTasksCount
        self.rescheduledReviewsCount = rescheduledReviewsCount
        self.activeTodayTasksCount = activeTodayTasksCount
        self.miniModeActivated = miniModeActivated
        self.pruneReasonCode = pruneReasonCode
        self.prunedConceptIds = prunedConceptIds
        self.welcomeMessageKey = welcomeMessageKey
        self.idempotentReplay = idempotentReplay
        self.createdAt = createdAt
    }
}

public protocol RebalanceCacheStoreProtocol: Sendable {
    func saveRecoveryStatus(_ status: StudyPlanRecoveryStatus, for userId: UUID) async
    func loadRecoveryStatus(for userId: UUID) async -> StudyPlanRecoveryStatus?
    func saveRebalanceResponse(_ response: StudyPlanRebalanceResponse, for userId: UUID) async
    func loadLastRebalanceResponse(for userId: UUID) async -> StudyPlanRebalanceResponse?
    func purgeUserData(userId: UUID) async
}

public enum RebalanceSQLiteCacheError: Error, Sendable {
    case openError(String)
    case prepareError(String)
    case executionError(String)
}

public actor RebalanceSQLiteCache: RebalanceCacheStoreProtocol {
    private final class Connection {
        let handle: OpaquePointer?

        init(handle: OpaquePointer?) {
            self.handle = handle
        }

        deinit {
            if let handle {
                sqlite3_close(handle)
            }
        }
    }

    private let connection: Connection
    private var db: OpaquePointer? { connection.handle }
    private let databaseURL: URL
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    public init(databaseURL: URL? = nil) throws {
        let resolvedURL: URL
        if let databaseURL {
            resolvedURL = databaseURL
        } else {
            let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
            let dir = appSupport.appendingPathComponent("VerifiedAI", isDirectory: true)
            try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            resolvedURL = dir.appendingPathComponent("studyplan_rebalance_cache.sqlite", isDirectory: false)
        }
        self.databaseURL = resolvedURL
        self.encoder = JSONEncoder()
        self.decoder = JSONDecoder()

        var handle: OpaquePointer?
        let openResult = sqlite3_open_v2(
            resolvedURL.path,
            &handle,
            SQLITE_OPEN_READWRITE | SQLITE_OPEN_CREATE | SQLITE_OPEN_FULLMUTEX,
            nil
        )
        guard openResult == SQLITE_OK, let openedDB = handle else {
            let msg = handle.map { String(cString: sqlite3_errmsg($0)) } ?? "Unable to open rebalance SQLite DB"
            if let handle { sqlite3_close(handle) }
            throw RebalanceSQLiteCacheError.openError(msg)
        }
        self.connection = Connection(handle: openedDB)
        try Self.executeSQL(on: openedDB, sql: "PRAGMA journal_mode = WAL;")
        try Self.executeSQL(on: openedDB, sql: """
            CREATE TABLE IF NOT EXISTS rebalance_recovery_status (
                user_id TEXT PRIMARY KEY NOT NULL,
                as_of_date TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                updated_at REAL NOT NULL
            );
            """)
        try Self.executeSQL(on: openedDB, sql: """
            CREATE TABLE IF NOT EXISTS rebalance_responses (
                user_id TEXT PRIMARY KEY NOT NULL,
                rebalance_event_id TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                updated_at REAL NOT NULL
            );
            """)
    }

    public func saveRecoveryStatus(_ status: StudyPlanRecoveryStatus, for userId: UUID) async {
        guard let db, status.userId == userId else { return }
        guard let encoded = try? encoder.encode(status),
              let jsonString = String(data: encoded, encoding: .utf8) else {
            return
        }

        let sql = """
            INSERT INTO rebalance_recovery_status (user_id, as_of_date, payload_json, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                as_of_date = excluded.as_of_date,
                payload_json = excluded.payload_json,
                updated_at = excluded.updated_at;
            """
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK, let stmt else { return }
        defer { sqlite3_finalize(stmt) }

        sqlite3_bind_text(stmt, 1, userId.uuidString.lowercased(), -1, sqliteTransient)
        sqlite3_bind_text(stmt, 2, status.asOfDate, -1, sqliteTransient)
        sqlite3_bind_text(stmt, 3, jsonString, -1, sqliteTransient)
        sqlite3_bind_double(stmt, 4, Date().timeIntervalSince1970)
        sqlite3_step(stmt)
    }

    public func loadRecoveryStatus(for userId: UUID) async -> StudyPlanRecoveryStatus? {
        guard let db else { return nil }
        let sql = "SELECT payload_json FROM rebalance_recovery_status WHERE user_id = ? LIMIT 1;"
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK, let stmt else { return nil }
        defer { sqlite3_finalize(stmt) }

        sqlite3_bind_text(stmt, 1, userId.uuidString.lowercased(), -1, sqliteTransient)
        guard sqlite3_step(stmt) == SQLITE_ROW,
              let textPtr = sqlite3_column_text(stmt, 0) else {
            return nil
        }
        let jsonString = String(cString: textPtr)
        guard let data = jsonString.data(using: .utf8) else { return nil }
        return try? decoder.decode(StudyPlanRecoveryStatus.self, from: data)
    }

    public func saveRebalanceResponse(_ response: StudyPlanRebalanceResponse, for userId: UUID) async {
        guard let db, response.userId == userId else { return }
        guard let encoded = try? encoder.encode(response),
              let jsonString = String(data: encoded, encoding: .utf8) else {
            return
        }

        let sql = """
            INSERT INTO rebalance_responses (user_id, rebalance_event_id, payload_json, updated_at)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                rebalance_event_id = excluded.rebalance_event_id,
                payload_json = excluded.payload_json,
                updated_at = excluded.updated_at;
            """
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK, let stmt else { return }
        defer { sqlite3_finalize(stmt) }

        sqlite3_bind_text(stmt, 1, userId.uuidString.lowercased(), -1, sqliteTransient)
        sqlite3_bind_text(stmt, 2, response.rebalanceEventId.uuidString.lowercased(), -1, sqliteTransient)
        sqlite3_bind_text(stmt, 3, jsonString, -1, sqliteTransient)
        sqlite3_bind_double(stmt, 4, Date().timeIntervalSince1970)
        sqlite3_step(stmt)
    }

    public func loadLastRebalanceResponse(for userId: UUID) async -> StudyPlanRebalanceResponse? {
        guard let db else { return nil }
        let sql = "SELECT payload_json FROM rebalance_responses WHERE user_id = ? LIMIT 1;"
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK, let stmt else { return nil }
        defer { sqlite3_finalize(stmt) }

        sqlite3_bind_text(stmt, 1, userId.uuidString.lowercased(), -1, sqliteTransient)
        guard sqlite3_step(stmt) == SQLITE_ROW,
              let textPtr = sqlite3_column_text(stmt, 0) else {
            return nil
        }
        let jsonString = String(cString: textPtr)
        guard let data = jsonString.data(using: .utf8) else { return nil }
        return try? decoder.decode(StudyPlanRebalanceResponse.self, from: data)
    }

    public func purgeUserData(userId: UUID) async {
        guard let db else { return }
        let normalizedId = userId.uuidString.lowercased()
        for table in ["rebalance_recovery_status", "rebalance_responses"] {
            let sql = "DELETE FROM \(table) WHERE user_id = ?;"
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK, let stmt {
                sqlite3_bind_text(stmt, 1, normalizedId, -1, sqliteTransient)
                sqlite3_step(stmt)
                sqlite3_finalize(stmt)
            }
        }
    }

    private static func executeSQL(on db: OpaquePointer, sql: String) throws {
        var errorMessage: UnsafeMutablePointer<CChar>?
        let rc = sqlite3_exec(db, sql, nil, nil, &errorMessage)
        if rc != SQLITE_OK {
            let msg = errorMessage.map { String(cString: $0) } ?? "SQLite error \(rc)"
            sqlite3_free(errorMessage)
            throw RebalanceSQLiteCacheError.executionError(msg)
        }
    }
}
