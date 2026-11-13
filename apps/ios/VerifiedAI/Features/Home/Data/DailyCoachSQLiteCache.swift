import Foundation
import SQLite3

public enum DailyCoachMode: String, Codable, Sendable, CaseIterable {
    case solverMode = "SOLVER_MODE"
    case socraticCoachMode = "SOCRATIC_COACH_MODE"
}

public enum DailyCoachSessionStatus: String, Codable, Sendable {
    case inProgress = "IN_PROGRESS"
    case completed = "COMPLETED"
}

public enum DailyCoachTaskType: String, Codable, Sendable {
    case trapRepair = "TRAP_REPAIR"
    case spacedRetention = "SPACED_RETENTION"
    case examSynthesis = "EXAM_SYNTHESIS"
}

public enum DailyCoachTaskStatus: String, Codable, Sendable {
    case pending = "PENDING"
    case completed = "COMPLETED"
    case skipped = "SKIPPED"
}

public struct DailyCoachTaskModel: Codable, Identifiable, Sendable, Equatable {
    public let taskId: UUID
    public let taskOrder: Int
    public let taskType: DailyCoachTaskType
    public let targetSkillCode: String
    public let targetMistakeCode: String?
    public let assignedTwinId: String
    public let estimatedMinutes: Int
    public let whyThisTaskRationale: String
    public let promptPreviewLatex: String
    public var status: DailyCoachTaskStatus
    public var completedAt: Date?

    public var id: UUID { taskId }

    public init(
        taskId: UUID,
        taskOrder: Int,
        taskType: DailyCoachTaskType,
        targetSkillCode: String,
        targetMistakeCode: String?,
        assignedTwinId: String,
        estimatedMinutes: Int,
        whyThisTaskRationale: String,
        promptPreviewLatex: String,
        status: DailyCoachTaskStatus = .pending,
        completedAt: Date? = nil
    ) {
        self.taskId = taskId
        self.taskOrder = taskOrder
        self.taskType = taskType
        self.targetSkillCode = targetSkillCode
        self.targetMistakeCode = targetMistakeCode
        self.assignedTwinId = assignedTwinId
        self.estimatedMinutes = estimatedMinutes
        self.whyThisTaskRationale = whyThisTaskRationale
        self.promptPreviewLatex = promptPreviewLatex
        self.status = status
        self.completedAt = completedAt
    }
}

public struct TrapRadarPreviewModel: Codable, Identifiable, Sendable, Equatable {
    public let skillCode: String
    public let mistakeCode: String
    public let title: String
    public let badgeKey: String
    public let statusLabel: String

    public var id: String { "\(skillCode):\(mistakeCode)" }

    public init(
        skillCode: String,
        mistakeCode: String,
        title: String,
        badgeKey: String,
        statusLabel: String
    ) {
        self.skillCode = skillCode
        self.mistakeCode = mistakeCode
        self.title = title
        self.badgeKey = badgeKey
        self.statusLabel = statusLabel
    }
}

public struct ColdStartDemoCardModel: Codable, Sendable, Equatable {
    public let title: String
    public let samplePromptLatex: String
    public let correctStepLatex: String
    public let focusStepLatex: String
    public let hintText: String

    public init(
        title: String,
        samplePromptLatex: String,
        correctStepLatex: String,
        focusStepLatex: String,
        hintText: String
    ) {
        self.title = title
        self.samplePromptLatex = samplePromptLatex
        self.correctStepLatex = correctStepLatex
        self.focusStepLatex = focusStepLatex
        self.hintText = hintText
    }
}

public struct DailyCoachSummaryModel: Codable, Sendable, Equatable {
    public let sessionId: UUID
    public let userId: UUID
    public let localDate: String
    public var budgetMinutes: Int
    public var status: DailyCoachSessionStatus
    public var isDownshifted: Bool
    public let isColdStart: Bool
    public var completedTasksCount: Int
    public var totalTasksCount: Int
    public var briefingText: String
    public var preferredMode: DailyCoachMode
    public var streakDays: Int
    public var tasks: [DailyCoachTaskModel]
    public let activeTrapRadar: [TrapRadarPreviewModel]
    public let coldStartDemo: ColdStartDemoCardModel?

    public init(
        sessionId: UUID,
        userId: UUID,
        localDate: String,
        budgetMinutes: Int,
        status: DailyCoachSessionStatus,
        isDownshifted: Bool,
        isColdStart: Bool,
        completedTasksCount: Int,
        totalTasksCount: Int,
        briefingText: String,
        preferredMode: DailyCoachMode,
        streakDays: Int,
        tasks: [DailyCoachTaskModel],
        activeTrapRadar: [TrapRadarPreviewModel],
        coldStartDemo: ColdStartDemoCardModel? = nil
    ) {
        self.sessionId = sessionId
        self.userId = userId
        self.localDate = localDate
        self.budgetMinutes = budgetMinutes
        self.status = status
        self.isDownshifted = isDownshifted
        self.isColdStart = isColdStart
        self.completedTasksCount = completedTasksCount
        self.totalTasksCount = totalTasksCount
        self.briefingText = briefingText
        self.preferredMode = preferredMode
        self.streakDays = streakDays
        self.tasks = tasks
        self.activeTrapRadar = activeTrapRadar
        self.coldStartDemo = coldStartDemo
    }
}

public protocol DailyCoachCacheStoreProtocol: Sendable {
    func saveSummary(_ summary: DailyCoachSummaryModel, for userId: UUID) async
    func loadSummary(for userId: UUID, localDate: String) async -> DailyCoachSummaryModel?
    func loadLatestSummary(for userId: UUID) async -> DailyCoachSummaryModel?
    func purgeUserData(for userId: UUID) async
}

public actor DailyCoachSQLiteCache: DailyCoachCacheStoreProtocol {
    private let SQLITE_TRANSIENT = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

    private final class Connection {
        let handle: OpaquePointer?

        init(path: String) {
            var handle: OpaquePointer?
            sqlite3_open(path, &handle)
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
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    public init(inMemory: Bool = false) {
        encoder.dateEncodingStrategy = .iso8601
        decoder.dateDecodingStrategy = .iso8601

        if inMemory {
            connection = Connection(path: ":memory:")
        } else {
            let paths = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)
            let cacheDir = paths[0].appendingPathComponent("VerifiedAI", isDirectory: true)
            try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)
            let fileUrl = cacheDir.appendingPathComponent("daily_coach_cache.sqlite")
            connection = Connection(path: fileUrl.path)
        }
        Self.createTableIfNotExists(on: connection.handle)
    }

    private static func createTableIfNotExists(on db: OpaquePointer?) {
        guard let db else { return }
        let createSql = """
        CREATE TABLE IF NOT EXISTS daily_coach_cache (
            user_id TEXT NOT NULL,
            local_date TEXT NOT NULL,
            payload_json TEXT NOT NULL,
            preferred_mode TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            PRIMARY KEY (user_id, local_date)
        );
        CREATE INDEX IF NOT EXISTS idx_daily_coach_cache_user
            ON daily_coach_cache (user_id, updated_at DESC);
        """
        sqlite3_exec(db, createSql, nil, nil, nil)
    }

    public func saveSummary(_ summary: DailyCoachSummaryModel, for userId: UUID) async {
        guard let db else { return }

        let insertSql = """
        INSERT OR REPLACE INTO daily_coach_cache (
            user_id, local_date, payload_json, preferred_mode, updated_at
        ) VALUES (?, ?, ?, ?, ?);
        """

        var statement: OpaquePointer?
        if sqlite3_prepare_v2(db, insertSql, -1, &statement, nil) == SQLITE_OK {
            let isoFormatter = ISO8601DateFormatter()
            let userIdStr = userId.uuidString
            let payloadData = (try? encoder.encode(summary)) ?? Data()
            let payloadJson = String(data: payloadData, encoding: .utf8) ?? "{}"
            let updatedStr = isoFormatter.string(from: Date())

            sqlite3_bind_text(statement, 1, userIdStr, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(statement, 2, summary.localDate, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(statement, 3, payloadJson, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(statement, 4, summary.preferredMode.rawValue, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(statement, 5, updatedStr, -1, SQLITE_TRANSIENT)

            sqlite3_step(statement)
        }
        sqlite3_finalize(statement)
    }

    public func loadSummary(for userId: UUID, localDate: String) async -> DailyCoachSummaryModel? {
        guard let db else { return nil }

        let querySql = """
        SELECT payload_json FROM daily_coach_cache
        WHERE user_id = ? AND local_date = ?
        LIMIT 1;
        """

        var statement: OpaquePointer?
        var result: DailyCoachSummaryModel? = nil

        if sqlite3_prepare_v2(db, querySql, -1, &statement, nil) == SQLITE_OK {
            let userIdStr = userId.uuidString
            sqlite3_bind_text(statement, 1, userIdStr, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(statement, 2, localDate, -1, SQLITE_TRANSIENT)

            if sqlite3_step(statement) == SQLITE_ROW {
                if let jsonCString = sqlite3_column_text(statement, 0) {
                    let jsonString = String(cString: jsonCString)
                    if let data = jsonString.data(using: .utf8) {
                        result = try? decoder.decode(DailyCoachSummaryModel.self, from: data)
                    }
                }
            }
        }
        sqlite3_finalize(statement)
        return result
    }

    public func loadLatestSummary(for userId: UUID) async -> DailyCoachSummaryModel? {
        guard let db else { return nil }

        let querySql = """
        SELECT payload_json FROM daily_coach_cache
        WHERE user_id = ?
        ORDER BY updated_at DESC
        LIMIT 1;
        """

        var statement: OpaquePointer?
        var result: DailyCoachSummaryModel? = nil

        if sqlite3_prepare_v2(db, querySql, -1, &statement, nil) == SQLITE_OK {
            let userIdStr = userId.uuidString
            sqlite3_bind_text(statement, 1, userIdStr, -1, SQLITE_TRANSIENT)

            if sqlite3_step(statement) == SQLITE_ROW {
                if let jsonCString = sqlite3_column_text(statement, 0) {
                    let jsonString = String(cString: jsonCString)
                    if let data = jsonString.data(using: .utf8) {
                        result = try? decoder.decode(DailyCoachSummaryModel.self, from: data)
                    }
                }
            }
        }
        sqlite3_finalize(statement)
        return result
    }

    public func purgeUserData(for userId: UUID) async {
        guard let db else { return }
        let deleteSql = "DELETE FROM daily_coach_cache WHERE user_id = ?;"
        var statement: OpaquePointer?
        if sqlite3_prepare_v2(db, deleteSql, -1, &statement, nil) == SQLITE_OK {
            let userIdStr = userId.uuidString
            sqlite3_bind_text(statement, 1, userIdStr, -1, SQLITE_TRANSIENT)
            sqlite3_step(statement)
        }
        sqlite3_finalize(statement)
    }
}
