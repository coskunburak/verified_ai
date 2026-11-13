import Foundation
import SQLite3

public protocol PracticeCacheStoreProtocol: Sendable {
    func saveSession(_ session: PracticeSession, for userId: UUID) async
    func loadLatestActiveSession(for userId: UUID) async -> PracticeSession?
    func purgeAllPracticeData(for userId: UUID) async
    func purgeCache() async
}

public actor PracticeSQLiteCache: PracticeCacheStoreProtocol {
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
            let fileUrl = cacheDir.appendingPathComponent("practice_cache.sqlite")
            connection = Connection(path: fileUrl.path)
        }
        Self.createTablesIfNotExists(on: connection.handle)
    }

    private static func createTablesIfNotExists(on db: OpaquePointer?) {
        guard let db = db else { return }
        let createSql = """
        CREATE TABLE IF NOT EXISTS practice_sessions_cache (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            session_mode TEXT NOT NULL,
            status TEXT NOT NULL,
            target_skill_id TEXT NOT NULL,
            active_trap_family TEXT,
            payload_json TEXT NOT NULL,
            updated_at TEXT NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_practice_user_status
            ON practice_sessions_cache (user_id, status, updated_at DESC);
        """
        sqlite3_exec(db, createSql, nil, nil, nil)
    }

    public func saveSession(_ session: PracticeSession, for userId: UUID) async {
        guard let db = db else { return }

        let insertSql = """
        INSERT OR REPLACE INTO practice_sessions_cache (
            id, user_id, session_mode, status, target_skill_id, active_trap_family, payload_json, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?);
        """

        var statement: OpaquePointer?
        if sqlite3_prepare_v2(db, insertSql, -1, &statement, nil) == SQLITE_OK {
            let isoFormatter = ISO8601DateFormatter()
            let idStr = session.id.uuidString
            let userIdStr = userId.uuidString
            let modeStr = session.sessionMode.rawValue
            let statusStr = session.status.rawValue
            let skillStr = session.targetSkillId
            let trapStr = session.activeTrapFamily
            let payloadData = (try? encoder.encode(session)) ?? Data()
            let payloadJson = String(data: payloadData, encoding: .utf8) ?? "{}"
            let updatedStr = isoFormatter.string(from: session.updatedAt)

            sqlite3_bind_text(statement, 1, (idStr as NSString).utf8String, -1, nil)
            sqlite3_bind_text(statement, 2, (userIdStr as NSString).utf8String, -1, nil)
            sqlite3_bind_text(statement, 3, (modeStr as NSString).utf8String, -1, nil)
            sqlite3_bind_text(statement, 4, (statusStr as NSString).utf8String, -1, nil)
            sqlite3_bind_text(statement, 5, (skillStr as NSString).utf8String, -1, nil)
            if let trapStr = trapStr {
                sqlite3_bind_text(statement, 6, (trapStr as NSString).utf8String, -1, nil)
            } else {
                sqlite3_bind_null(statement, 6)
            }
            sqlite3_bind_text(statement, 7, (payloadJson as NSString).utf8String, -1, nil)
            sqlite3_bind_text(statement, 8, (updatedStr as NSString).utf8String, -1, nil)

            sqlite3_step(statement)
        }
        sqlite3_finalize(statement)
    }

    public func loadLatestActiveSession(for userId: UUID) async -> PracticeSession? {
        guard let db = db else { return nil }

        let querySql = """
        SELECT payload_json FROM practice_sessions_cache
        WHERE user_id = ? AND status = 'IN_PROGRESS'
        ORDER BY updated_at DESC
        LIMIT 1;
        """

        var statement: OpaquePointer?
        var result: PracticeSession? = nil

        if sqlite3_prepare_v2(db, querySql, -1, &statement, nil) == SQLITE_OK {
            sqlite3_bind_text(statement, 1, (userId.uuidString as NSString).utf8String, -1, nil)

            if sqlite3_step(statement) == SQLITE_ROW {
                if let jsonCString = sqlite3_column_text(statement, 0) {
                    let jsonString = String(cString: jsonCString)
                    if let data = jsonString.data(using: .utf8) {
                        result = try? decoder.decode(PracticeSession.self, from: data)
                    }
                }
            }
        }
        sqlite3_finalize(statement)
        return result
    }

    public func purgeAllPracticeData(for userId: UUID) async {
        guard let db = db else { return }
        let deleteSql = "DELETE FROM practice_sessions_cache WHERE user_id = ?;"
        var statement: OpaquePointer?
        if sqlite3_prepare_v2(db, deleteSql, -1, &statement, nil) == SQLITE_OK {
            sqlite3_bind_text(statement, 1, (userId.uuidString as NSString).utf8String, -1, nil)
            sqlite3_step(statement)
        }
        sqlite3_finalize(statement)
    }

    public func purgeCache() async {
        guard let db = db else { return }
        let purgeSql = "DELETE FROM practice_sessions_cache;"
        sqlite3_exec(db, purgeSql, nil, nil, nil)
    }
}
