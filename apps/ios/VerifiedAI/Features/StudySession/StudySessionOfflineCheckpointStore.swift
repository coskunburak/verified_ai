import Foundation
import SQLite3

private let sqliteTransient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

enum StudySessionStoreError: Error, Equatable {
    case databaseOpenFailed
    case statementFailed(String)
    case serializationFailed
    case staleSequenceRejected
}

/// Actor-isolated SQLite WAL store guaranteeing offline checkpoints are persisted
/// durably before any network attempt and scoped strictly by user_id (INV-SESS-002, INV-SESS-004).
actor StudySessionOfflineCheckpointStore {

    private var db: OpaquePointer?
    private let encoder = StudySessionJSONCoding.makeEncoder()
    private let decoder = StudySessionJSONCoding.makeDecoder()

    init(databasePath: String? = nil) throws {
        let path: String
        if let databasePath {
            path = databasePath
        } else {
            let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? URL(fileURLWithPath: NSTemporaryDirectory(), isDirectory: true)
            try? FileManager.default.createDirectory(at: appSupport, withIntermediateDirectories: true)
            path = appSupport.appendingPathComponent("verifiedai_study_sessions.sqlite").path
        }

        var handle: OpaquePointer?
        if sqlite3_open_v2(path, &handle, SQLITE_OPEN_CREATE | SQLITE_OPEN_READWRITE | SQLITE_OPEN_FULLMUTEX, nil) != SQLITE_OK {
            if let handle { sqlite3_close(handle) }
            throw StudySessionStoreError.databaseOpenFailed
        }
        self.db = handle
        try Self.configureSchema(on: handle)
    }

    deinit {
        if let db {
            sqlite3_close(db)
        }
    }

    private static func configureSchema(on db: OpaquePointer?) throws {
        try executeStatic("PRAGMA journal_mode=WAL;", on: db)
        try executeStatic("PRAGMA synchronous=NORMAL;", on: db)
        try executeStatic("""
            CREATE TABLE IF NOT EXISTS offline_session_checkpoints (
                idempotency_key TEXT NOT NULL,
                session_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                sequence_number INTEGER NOT NULL,
                step_index INTEGER NOT NULL,
                payload_json TEXT NOT NULL,
                synced INTEGER NOT NULL DEFAULT 0,
                created_at_epoch REAL NOT NULL,
                PRIMARY KEY (session_id, idempotency_key),
                UNIQUE (session_id, sequence_number)
            );
            """, on: db)
        try executeStatic("""
            CREATE TABLE IF NOT EXISTS offline_session_snapshots (
                session_id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                last_sequence_number INTEGER NOT NULL,
                state_json TEXT NOT NULL,
                updated_at_epoch REAL NOT NULL
            );
            """, on: db)
        try executeStatic("""
            CREATE INDEX IF NOT EXISTS idx_offline_ckpt_pending
                ON offline_session_checkpoints (user_id, session_id, synced, sequence_number);
            """, on: db)
        try executeStatic("""
            CREATE INDEX IF NOT EXISTS idx_offline_snap_user_updated
                ON offline_session_snapshots (user_id, updated_at_epoch DESC);
            """, on: db)
    }

    private static func executeStatic(_ sql: String, on db: OpaquePointer?) throws {
        var errMsg: UnsafeMutablePointer<CChar>?
        if sqlite3_exec(db, sql, nil, nil, &errMsg) != SQLITE_OK {
            let message = errMsg.map { String(cString: $0) } ?? "SQLite execution failed"
            if let errMsg { sqlite3_free(errMsg) }
            throw StudySessionStoreError.statementFailed(message)
        }
    }

    func saveSessionSnapshot(_ session: StudySessionStateModel) throws {
        guard let jsonData = try? encoder.encode(session),
              let jsonString = String(data: jsonData, encoding: .utf8) else {
            throw StudySessionStoreError.serializationFailed
        }
        let sql = """
            INSERT INTO offline_session_snapshots (session_id, user_id, last_sequence_number, state_json, updated_at_epoch)
            VALUES (?1, ?2, ?3, ?4, ?5)
            ON CONFLICT(session_id) DO UPDATE SET
                user_id = excluded.user_id,
                last_sequence_number = MAX(offline_session_snapshots.last_sequence_number, excluded.last_sequence_number),
                state_json = excluded.state_json,
                updated_at_epoch = excluded.updated_at_epoch;
            """
        let stmt = try prepare(sql)
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, index: 1, value: session.sessionId.uuidString.lowercased())
        bindText(stmt, index: 2, value: session.userId.uuidString.lowercased())
        sqlite3_bind_int64(stmt, 3, Int64(session.lastSequenceNumber))
        bindText(stmt, index: 4, value: jsonString)
        sqlite3_bind_double(stmt, 5, Date().timeIntervalSince1970)
        guard sqlite3_step(stmt) == SQLITE_DONE else {
            throw lastError()
        }
    }

    func loadSession(sessionId: UUID, userId: UUID) throws -> StudySessionStateModel? {
        let sql = """
            SELECT state_json FROM offline_session_snapshots
            WHERE session_id = ?1 AND user_id = ?2
            LIMIT 1;
            """
        let stmt = try prepare(sql)
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, index: 1, value: sessionId.uuidString.lowercased())
        bindText(stmt, index: 2, value: userId.uuidString.lowercased())
        guard sqlite3_step(stmt) == SQLITE_ROW,
              let cStr = sqlite3_column_text(stmt, 0) else {
            return nil
        }
        let json = String(cString: cStr)
        return try decoder.decode(StudySessionStateModel.self, from: Data(json.utf8))
    }

    func loadLatestResumableSession(userId: UUID, now: Date = Date()) throws -> StudySessionStateModel? {
        let sql = """
            SELECT state_json FROM offline_session_snapshots
            WHERE user_id = ?1
            ORDER BY updated_at_epoch DESC;
            """
        let stmt = try prepare(sql)
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, index: 1, value: userId.uuidString.lowercased())
        while sqlite3_step(stmt) == SQLITE_ROW {
            if let cStr = sqlite3_column_text(stmt, 0) {
                let json = String(cString: cStr)
                if let decoded = try? decoder.decode(StudySessionStateModel.self, from: Data(json.utf8)),
                   decoded.state.isResumable,
                   decoded.expiresAt > now {
                    return decoded
                }
            }
        }
        return nil
    }

    func nextSequenceNumber(sessionId: UUID, userId: UUID) throws -> Int {
        var maxSeq = 0
        let snapStmt = try prepare("SELECT last_sequence_number FROM offline_session_snapshots WHERE session_id = ?1 AND user_id = ?2;")
        defer { sqlite3_finalize(snapStmt) }
        bindText(snapStmt, index: 1, value: sessionId.uuidString.lowercased())
        bindText(snapStmt, index: 2, value: userId.uuidString.lowercased())
        if sqlite3_step(snapStmt) == SQLITE_ROW {
            maxSeq = max(maxSeq, Int(sqlite3_column_int64(snapStmt, 0)))
        }

        let ckptStmt = try prepare("SELECT COALESCE(MAX(sequence_number), 0) FROM offline_session_checkpoints WHERE session_id = ?1 AND user_id = ?2;")
        defer { sqlite3_finalize(ckptStmt) }
        bindText(ckptStmt, index: 1, value: sessionId.uuidString.lowercased())
        bindText(ckptStmt, index: 2, value: userId.uuidString.lowercased())
        if sqlite3_step(ckptStmt) == SQLITE_ROW {
            maxSeq = max(maxSeq, Int(sqlite3_column_int64(ckptStmt, 0)))
        }
        return maxSeq + 1
    }

    func enqueueCheckpoint(
        userId: UUID,
        sessionId: UUID,
        request: StudySessionCheckpointRequest
    ) throws {
        // Check idempotency key first so duplicate enqueue returns cleanly without error
        let existsStmt = try prepare(
            "SELECT 1 FROM offline_session_checkpoints WHERE session_id = ?1 AND user_id = ?2 AND idempotency_key = ?3 LIMIT 1;"
        )
        defer { sqlite3_finalize(existsStmt) }
        bindText(existsStmt, index: 1, value: sessionId.uuidString.lowercased())
        bindText(existsStmt, index: 2, value: userId.uuidString.lowercased())
        bindText(existsStmt, index: 3, value: request.idempotencyKey)
        if sqlite3_step(existsStmt) == SQLITE_ROW {
            return
        }

        // Guard against monotonic sequence regression in local store
        let currentMaxStmt = try prepare(
            "SELECT COALESCE(MAX(sequence_number), 0) FROM offline_session_checkpoints WHERE session_id = ?1 AND user_id = ?2;"
        )
        defer { sqlite3_finalize(currentMaxStmt) }
        bindText(currentMaxStmt, index: 1, value: sessionId.uuidString.lowercased())
        bindText(currentMaxStmt, index: 2, value: userId.uuidString.lowercased())
        if sqlite3_step(currentMaxStmt) == SQLITE_ROW {
            let currentMax = Int(sqlite3_column_int64(currentMaxStmt, 0))
            if request.sequenceNumber <= currentMax {
                throw StudySessionStoreError.staleSequenceRejected
            }
        }

        guard let payloadData = try? encoder.encode(request),
              let payloadJson = String(data: payloadData, encoding: .utf8) else {
            throw StudySessionStoreError.serializationFailed
        }

        let insertSql = """
            INSERT INTO offline_session_checkpoints (
                idempotency_key, session_id, user_id, sequence_number, step_index, payload_json, synced, created_at_epoch
            ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, 0, ?7);
            """
        let stmt = try prepare(insertSql)
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, index: 1, value: request.idempotencyKey)
        bindText(stmt, index: 2, value: sessionId.uuidString.lowercased())
        bindText(stmt, index: 3, value: userId.uuidString.lowercased())
        sqlite3_bind_int64(stmt, 4, Int64(request.sequenceNumber))
        sqlite3_bind_int(stmt, 5, Int32(request.stepIndex))
        bindText(stmt, index: 6, value: payloadJson)
        sqlite3_bind_double(stmt, 7, Date().timeIntervalSince1970)
        guard sqlite3_step(stmt) == SQLITE_DONE else {
            throw lastError()
        }
    }

    func pendingCheckpoints(sessionId: UUID, userId: UUID) throws -> [QueuedOfflineCheckpoint] {
        let sql = """
            SELECT idempotency_key, session_id, user_id, sequence_number, step_index, payload_json, synced
            FROM offline_session_checkpoints
            WHERE session_id = ?1 AND user_id = ?2 AND synced = 0
            ORDER BY sequence_number ASC;
            """
        let stmt = try prepare(sql)
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, index: 1, value: sessionId.uuidString.lowercased())
        bindText(stmt, index: 2, value: userId.uuidString.lowercased())
        return try readCheckpointRows(stmt)
    }

    func allPendingCheckpoints(userId: UUID) throws -> [QueuedOfflineCheckpoint] {
        let sql = """
            SELECT idempotency_key, session_id, user_id, sequence_number, step_index, payload_json, synced
            FROM offline_session_checkpoints
            WHERE user_id = ?1 AND synced = 0
            ORDER BY created_at_epoch ASC, sequence_number ASC;
            """
        let stmt = try prepare(sql)
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, index: 1, value: userId.uuidString.lowercased())
        return try readCheckpointRows(stmt)
    }

    func markCheckpointSynced(sessionId: UUID, userId: UUID, idempotencyKey: String) throws {
        let sql = """
            UPDATE offline_session_checkpoints
            SET synced = 1
            WHERE session_id = ?1 AND user_id = ?2 AND idempotency_key = ?3;
            """
        let stmt = try prepare(sql)
        defer { sqlite3_finalize(stmt) }
        bindText(stmt, index: 1, value: sessionId.uuidString.lowercased())
        bindText(stmt, index: 2, value: userId.uuidString.lowercased())
        bindText(stmt, index: 3, value: idempotencyKey)
        guard sqlite3_step(stmt) == SQLITE_DONE else {
            throw lastError()
        }
    }

    @discardableResult
    func purgeUserData(userId: UUID) throws -> Int {
        let normalized = userId.uuidString.lowercased()
        let ckptStmt = try prepare("DELETE FROM offline_session_checkpoints WHERE user_id = ?1;")
        defer { sqlite3_finalize(ckptStmt) }
        bindText(ckptStmt, index: 1, value: normalized)
        guard sqlite3_step(ckptStmt) == SQLITE_DONE else {
            throw lastError()
        }
        let deletedCheckpoints = Int(sqlite3_changes(db))

        let snapStmt = try prepare("DELETE FROM offline_session_snapshots WHERE user_id = ?1;")
        defer { sqlite3_finalize(snapStmt) }
        bindText(snapStmt, index: 1, value: normalized)
        guard sqlite3_step(snapStmt) == SQLITE_DONE else {
            throw lastError()
        }
        let deletedSnapshots = Int(sqlite3_changes(db))
        return deletedCheckpoints + deletedSnapshots
    }

    private func readCheckpointRows(_ stmt: OpaquePointer) throws -> [QueuedOfflineCheckpoint] {
        var rows: [QueuedOfflineCheckpoint] = []
        while sqlite3_step(stmt) == SQLITE_ROW {
            guard let idemPtr = sqlite3_column_text(stmt, 0),
                  let sessPtr = sqlite3_column_text(stmt, 1),
                  let userPtr = sqlite3_column_text(stmt, 2),
                  let payloadPtr = sqlite3_column_text(stmt, 5),
                  let sessId = UUID(uuidString: String(cString: sessPtr)),
                  let userId = UUID(uuidString: String(cString: userPtr)) else {
                continue
            }
            let payloadStr = String(cString: payloadPtr)
            let req = try decoder.decode(StudySessionCheckpointRequest.self, from: Data(payloadStr.utf8))
            rows.append(QueuedOfflineCheckpoint(
                idempotencyKey: String(cString: idemPtr),
                sessionId: sessId,
                userId: userId,
                sequenceNumber: Int(sqlite3_column_int64(stmt, 3)),
                stepIndex: Int(sqlite3_column_int(stmt, 4)),
                request: req,
                synced: sqlite3_column_int(stmt, 6) == 1
            ))
        }
        return rows
    }

    private func prepare(_ sql: String) throws -> OpaquePointer {
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) != SQLITE_OK {
            throw lastError()
        }
        guard let stmt else { throw lastError() }
        return stmt
    }

    private func bindText(_ stmt: OpaquePointer, index: Int32, value: String) {
        value.withCString { cStr in
            _ = sqlite3_bind_text(stmt, index, cStr, -1, sqliteTransient)
        }
    }

    private func lastError() -> StudySessionStoreError {
        let msg = db.flatMap { sqlite3_errmsg($0) }.map { String(cString: $0) } ?? "Unknown SQLite error"
        return .statementFailed(msg)
    }
}
