import Foundation
import SQLite3

private let microLessonSqliteTransient = unsafeBitCast(-1, to: sqlite3_destructor_type.self)

public enum MicroLessonCacheError: Error, Equatable, Sendable {
    case databaseOpenFailed
    case statementFailed(String)
    case verificationIntegrityRejected
}

public struct PendingMicroLessonCompletion: Codable, Equatable, Sendable {
    public let userId: UUID
    public let mistakeCode: String
    public let request: MicroLessonCompleteRequest
    public let queuedAt: Date

    public init(
        userId: UUID,
        mistakeCode: String,
        request: MicroLessonCompleteRequest,
        queuedAt: Date = Date()
    ) {
        self.userId = userId
        self.mistakeCode = mistakeCode
        self.request = request
        self.queuedAt = queuedAt
    }
}

public actor MicroLessonSQLiteCache {
    private let dbURL: URL
    private let encoder: JSONEncoder
    private let decoder: JSONDecoder

    public init(dbURL: URL? = nil) {
        if let dbURL {
            self.dbURL = dbURL
        } else {
            let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
                ?? FileManager.default.temporaryDirectory
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
            self.dbURL = dir.appendingPathComponent("verified_ai_micro_lessons.sqlite")
        }
        let enc = JSONEncoder()
        enc.dateEncodingStrategy = .iso8601
        self.encoder = enc
        let dec = JSONDecoder()
        dec.dateDecodingStrategy = .iso8601
        self.decoder = dec
    }

    public func saveCard(_ card: MicroLessonCard, userId: UUID) throws {
        guard card.passesLocalIntegrityChecks else {
            throw MicroLessonCacheError.verificationIntegrityRejected
        }
        let db = try openAndEnsureSchema()
        defer { sqlite3_close(db) }

        let payloadData = try encoder.encode(card)
        guard let payloadString = String(data: payloadData, encoding: .utf8) else {
            throw MicroLessonCacheError.statementFailed("Failed to encode card JSON")
        }

        let sql = """
        INSERT INTO cached_micro_lessons (user_id, mistake_code, locale, verification_hash, payload_json, updated_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(user_id, mistake_code, locale)
        DO UPDATE SET verification_hash = excluded.verification_hash,
                      payload_json = excluded.payload_json,
                      updated_at = excluded.updated_at;
        """
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else {
            throw MicroLessonCacheError.statementFailed(lastError(db))
        }
        defer { sqlite3_finalize(stmt) }

        let nowIso = ISO8601DateFormatter().string(from: Date())
        sqlite3_bind_text(stmt, 1, userId.uuidString, -1, microLessonSqliteTransient)
        sqlite3_bind_text(stmt, 2, card.mistakeCode, -1, microLessonSqliteTransient)
        sqlite3_bind_text(stmt, 3, card.locale, -1, microLessonSqliteTransient)
        sqlite3_bind_text(stmt, 4, card.verificationHash, -1, microLessonSqliteTransient)
        sqlite3_bind_text(stmt, 5, payloadString, -1, microLessonSqliteTransient)
        sqlite3_bind_text(stmt, 6, nowIso, -1, microLessonSqliteTransient)

        guard sqlite3_step(stmt) == SQLITE_DONE else {
            throw MicroLessonCacheError.statementFailed(lastError(db))
        }
    }

    public func loadCard(userId: UUID, mistakeCode: String, locale: String) throws -> MicroLessonCard? {
        let db = try openAndEnsureSchema()
        defer { sqlite3_close(db) }

        let sql = """
        SELECT payload_json, verification_hash
        FROM cached_micro_lessons
        WHERE user_id = ? AND mistake_code = ? AND locale = ?
        LIMIT 1;
        """
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else {
            throw MicroLessonCacheError.statementFailed(lastError(db))
        }
        defer { sqlite3_finalize(stmt) }

        sqlite3_bind_text(stmt, 1, userId.uuidString, -1, microLessonSqliteTransient)
        sqlite3_bind_text(stmt, 2, mistakeCode, -1, microLessonSqliteTransient)
        sqlite3_bind_text(stmt, 3, locale, -1, microLessonSqliteTransient)

        if sqlite3_step(stmt) == SQLITE_ROW {
            guard let jsonPtr = sqlite3_column_text(stmt, 0),
                  let hashPtr = sqlite3_column_text(stmt, 1) else {
                return nil
            }
            let jsonString = String(cString: jsonPtr)
            let storedHash = String(cString: hashPtr)
            guard let data = jsonString.data(using: .utf8) else { return nil }
            let card = try decoder.decode(MicroLessonCard.self, from: data)
            guard card.passesLocalIntegrityChecks, card.verificationHash == storedHash else {
                return nil
            }
            return card
        }
        return nil
    }

    public func enqueuePendingCompletion(_ item: PendingMicroLessonCompletion) throws {
        let db = try openAndEnsureSchema()
        defer { sqlite3_close(db) }

        let data = try encoder.encode(item.request)
        guard let requestJson = String(data: data, encoding: .utf8) else {
            throw MicroLessonCacheError.statementFailed("Failed to encode completion request")
        }

        let sql = """
        INSERT INTO pending_micro_lesson_completions (user_id, idempotency_key, mistake_code, request_json, queued_at)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(user_id, idempotency_key) DO NOTHING;
        """
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else {
            throw MicroLessonCacheError.statementFailed(lastError(db))
        }
        defer { sqlite3_finalize(stmt) }

        let queuedIso = ISO8601DateFormatter().string(from: item.queuedAt)
        sqlite3_bind_text(stmt, 1, item.userId.uuidString, -1, microLessonSqliteTransient)
        sqlite3_bind_text(stmt, 2, item.request.idempotencyKey, -1, microLessonSqliteTransient)
        sqlite3_bind_text(stmt, 3, item.mistakeCode, -1, microLessonSqliteTransient)
        sqlite3_bind_text(stmt, 4, requestJson, -1, microLessonSqliteTransient)
        sqlite3_bind_text(stmt, 5, queuedIso, -1, microLessonSqliteTransient)

        guard sqlite3_step(stmt) == SQLITE_DONE else {
            throw MicroLessonCacheError.statementFailed(lastError(db))
        }
    }

    public func pendingCompletions(userId: UUID) throws -> [PendingMicroLessonCompletion] {
        let db = try openAndEnsureSchema()
        defer { sqlite3_close(db) }

        let sql = """
        SELECT mistake_code, request_json, queued_at
        FROM pending_micro_lesson_completions
        WHERE user_id = ?
        ORDER BY queued_at ASC;
        """
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else {
            throw MicroLessonCacheError.statementFailed(lastError(db))
        }
        defer { sqlite3_finalize(stmt) }

        sqlite3_bind_text(stmt, 1, userId.uuidString, -1, microLessonSqliteTransient)

        var items: [PendingMicroLessonCompletion] = []
        let formatter = ISO8601DateFormatter()
        while sqlite3_step(stmt) == SQLITE_ROW {
            guard let codePtr = sqlite3_column_text(stmt, 0),
                  let reqPtr = sqlite3_column_text(stmt, 1),
                  let tsPtr = sqlite3_column_text(stmt, 2),
                  let reqData = String(cString: reqPtr).data(using: .utf8) else {
                continue
            }
            let req = try decoder.decode(MicroLessonCompleteRequest.self, from: reqData)
            let queuedDate = formatter.date(from: String(cString: tsPtr)) ?? Date()
            items.append(
                PendingMicroLessonCompletion(
                    userId: userId,
                    mistakeCode: String(cString: codePtr),
                    request: req,
                    queuedAt: queuedDate
                )
            )
        }
        return items
    }

    public func removePendingCompletion(userId: UUID, idempotencyKey: String) throws {
        let db = try openAndEnsureSchema()
        defer { sqlite3_close(db) }

        let sql = "DELETE FROM pending_micro_lesson_completions WHERE user_id = ? AND idempotency_key = ?;"
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else {
            throw MicroLessonCacheError.statementFailed(lastError(db))
        }
        defer { sqlite3_finalize(stmt) }

        sqlite3_bind_text(stmt, 1, userId.uuidString, -1, microLessonSqliteTransient)
        sqlite3_bind_text(stmt, 2, idempotencyKey, -1, microLessonSqliteTransient)
        guard sqlite3_step(stmt) == SQLITE_DONE else {
            throw MicroLessonCacheError.statementFailed(lastError(db))
        }
    }

    public func purgeUserData(userId: UUID) throws {
        let db = try openAndEnsureSchema()
        defer { sqlite3_close(db) }

        for table in ["cached_micro_lessons", "pending_micro_lesson_completions"] {
            let sql = "DELETE FROM \(table) WHERE user_id = ?;"
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else {
                throw MicroLessonCacheError.statementFailed(lastError(db))
            }
            sqlite3_bind_text(stmt, 1, userId.uuidString, -1, microLessonSqliteTransient)
            let rc = sqlite3_step(stmt)
            sqlite3_finalize(stmt)
            guard rc == SQLITE_DONE else {
                throw MicroLessonCacheError.statementFailed(lastError(db))
            }
        }
    }

    private func openAndEnsureSchema() throws -> OpaquePointer {
        var db: OpaquePointer?
        guard sqlite3_open(dbURL.path, &db) == SQLITE_OK, let opened = db else {
            if let db { sqlite3_close(db) }
            throw MicroLessonCacheError.databaseOpenFailed
        }

        let ddl = """
        PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS cached_micro_lessons (
            user_id TEXT NOT NULL,
            mistake_code TEXT NOT NULL,
            locale TEXT NOT NULL,
            verification_hash TEXT NOT NULL,
            payload_json TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            PRIMARY KEY (user_id, mistake_code, locale)
        );
        CREATE TABLE IF NOT EXISTS pending_micro_lesson_completions (
            user_id TEXT NOT NULL,
            idempotency_key TEXT NOT NULL,
            mistake_code TEXT NOT NULL,
            request_json TEXT NOT NULL,
            queued_at TEXT NOT NULL,
            PRIMARY KEY (user_id, idempotency_key)
        );
        """
        var errMsg: UnsafeMutablePointer<CChar>?
        if sqlite3_exec(opened, ddl, nil, nil, &errMsg) != SQLITE_OK {
            let message = errMsg.map { String(cString: $0) } ?? "Schema creation error"
            sqlite3_free(errMsg)
            sqlite3_close(opened)
            throw MicroLessonCacheError.statementFailed(message)
        }
        return opened
    }

    private func lastError(_ db: OpaquePointer?) -> String {
        guard let db, let cStr = sqlite3_errmsg(db) else { return "Unknown SQLite error" }
        return String(cString: cStr)
    }
}
