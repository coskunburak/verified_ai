import Foundation
import SQLite3

public protocol ColdStartCacheStoreProtocol: Sendable {
    func saveLatestResult(_ result: ColdStartDiagnosticResult, for learnerId: UUID) async
    func loadLatestResult(for learnerId: UUID) async -> ColdStartDiagnosticResult?
    func enqueuePendingSubmission(_ request: ColdStartDiagnosticRequest, for learnerId: UUID) async
    func dequeuePendingSubmission(for learnerId: UUID) async -> ColdStartDiagnosticRequest?
    func clearPendingSubmission(for learnerId: UUID) async
    func purgeUserData(for learnerId: UUID) async
}

public actor ColdStartSQLiteCache: ColdStartCacheStoreProtocol {
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
        if inMemory {
            connection = Connection(path: ":memory:")
        } else {
            let paths = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)
            let cacheDir = paths[0].appendingPathComponent("VerifiedAI", isDirectory: true)
            try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)
            let fileUrl = cacheDir.appendingPathComponent("cold_start_cache.sqlite")
            connection = Connection(path: fileUrl.path)
        }
        Self.createTablesIfNotExists(on: connection.handle)
    }

    private static func createTablesIfNotExists(on db: OpaquePointer?) {
        guard let db else { return }
        let sql = """
        CREATE TABLE IF NOT EXISTS cold_start_latest_cache (
            learner_id TEXT PRIMARY KEY,
            payload_json TEXT NOT NULL,
            updated_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS cold_start_pending_queue (
            learner_id TEXT PRIMARY KEY,
            payload_json TEXT NOT NULL,
            enqueued_at TEXT NOT NULL
        );
        """
        sqlite3_exec(db, sql, nil, nil, nil)
    }

    public func saveLatestResult(_ result: ColdStartDiagnosticResult, for learnerId: UUID) async {
        guard let db,
              let data = try? encoder.encode(result),
              let jsonString = String(data: data, encoding: .utf8) else {
            return
        }
        let sql = """
        INSERT INTO cold_start_latest_cache (learner_id, payload_json, updated_at)
        VALUES (?1, ?2, ?3)
        ON CONFLICT(learner_id)
        DO UPDATE SET payload_json = excluded.payload_json,
                      updated_at = excluded.updated_at;
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_text(stmt, 1, learnerId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 2, jsonString, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 3, ISO8601DateFormatter().string(from: Date()), -1, SQLITE_TRANSIENT)
            sqlite3_step(stmt)
        }
    }

    public func loadLatestResult(for learnerId: UUID) async -> ColdStartDiagnosticResult? {
        guard let db else { return nil }
        let sql = "SELECT payload_json FROM cold_start_latest_cache WHERE learner_id = ?1 LIMIT 1;"
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else {
            return nil
        }
        defer { sqlite3_finalize(stmt) }
        sqlite3_bind_text(stmt, 1, learnerId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
        if sqlite3_step(stmt) == SQLITE_ROW, let ptr = sqlite3_column_text(stmt, 0) {
            let json = String(cString: ptr)
            if let data = json.data(using: .utf8) {
                return try? decoder.decode(ColdStartDiagnosticResult.self, from: data)
            }
        }
        return nil
    }

    public func enqueuePendingSubmission(_ request: ColdStartDiagnosticRequest, for learnerId: UUID) async {
        guard let db,
              let data = try? encoder.encode(request),
              let jsonString = String(data: data, encoding: .utf8) else {
            return
        }
        let sql = """
        INSERT INTO cold_start_pending_queue (learner_id, payload_json, enqueued_at)
        VALUES (?1, ?2, ?3)
        ON CONFLICT(learner_id)
        DO UPDATE SET payload_json = excluded.payload_json,
                      enqueued_at = excluded.enqueued_at;
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_text(stmt, 1, learnerId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 2, jsonString, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 3, ISO8601DateFormatter().string(from: Date()), -1, SQLITE_TRANSIENT)
            sqlite3_step(stmt)
        }
    }

    public func dequeuePendingSubmission(for learnerId: UUID) async -> ColdStartDiagnosticRequest? {
        guard let db else { return nil }
        let sql = "SELECT payload_json FROM cold_start_pending_queue WHERE learner_id = ?1 LIMIT 1;"
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else {
            return nil
        }
        defer { sqlite3_finalize(stmt) }
        sqlite3_bind_text(stmt, 1, learnerId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
        if sqlite3_step(stmt) == SQLITE_ROW, let ptr = sqlite3_column_text(stmt, 0) {
            let json = String(cString: ptr)
            if let data = json.data(using: .utf8) {
                return try? decoder.decode(ColdStartDiagnosticRequest.self, from: data)
            }
        }
        return nil
    }

    public func clearPendingSubmission(for learnerId: UUID) async {
        guard let db else { return }
        let sql = "DELETE FROM cold_start_pending_queue WHERE learner_id = ?1;"
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_text(stmt, 1, learnerId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_step(stmt)
        }
    }

    public func purgeUserData(for learnerId: UUID) async {
        guard let db else { return }
        let key = learnerId.uuidString.lowercased()
        for table in ["cold_start_latest_cache", "cold_start_pending_queue"] {
            let sql = "DELETE FROM \(table) WHERE learner_id = ?1;"
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                defer { sqlite3_finalize(stmt) }
                sqlite3_bind_text(stmt, 1, key, -1, SQLITE_TRANSIENT)
                sqlite3_step(stmt)
            }
        }
    }
}
