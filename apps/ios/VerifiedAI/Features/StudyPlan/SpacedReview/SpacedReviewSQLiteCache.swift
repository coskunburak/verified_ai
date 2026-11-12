import Foundation
import SQLite3

public protocol SpacedReviewCacheStoreProtocol: Sendable {
    func saveQueue(_ response: SpacedQueueResponse, for userId: UUID) async
    func loadQueue(for userId: UUID) async -> SpacedQueueResponse?
    func purgeUserData(for userId: UUID) async
    func purgeCache() async
}

public actor SpacedReviewSQLiteCache: SpacedReviewCacheStoreProtocol {
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
            let fileUrl = cacheDir.appendingPathComponent("spaced_review_cache.sqlite")
            connection = Connection(path: fileUrl.path)
        }
        createTableIfNotExists()
    }

    private func createTableIfNotExists() {
        guard let db else { return }
        let createSql = """
        CREATE TABLE IF NOT EXISTS spaced_review_cache (
            user_id TEXT PRIMARY KEY,
            payload_json TEXT NOT NULL,
            evaluated_at TEXT NOT NULL,
            item_count INTEGER NOT NULL,
            updated_at TEXT NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_spaced_cache_user
            ON spaced_review_cache (user_id);
        """
        sqlite3_exec(db, createSql, nil, nil, nil)
    }

    public func saveQueue(_ response: SpacedQueueResponse, for userId: UUID) async {
        guard let db else { return }

        let insertSql = """
        INSERT OR REPLACE INTO spaced_review_cache (
            user_id, payload_json, evaluated_at, item_count, updated_at
        ) VALUES (?, ?, ?, ?, ?);
        """

        var statement: OpaquePointer?
        if sqlite3_prepare_v2(db, insertSql, -1, &statement, nil) == SQLITE_OK {
            let isoFormatter = ISO8601DateFormatter()
            let userIdStr = userId.uuidString
            let payloadData = (try? encoder.encode(response)) ?? Data()
            let payloadJson = String(data: payloadData, encoding: .utf8) ?? "{}"
            let evaluatedStr = isoFormatter.string(from: response.evaluatedAt)
            let count = response.items.count
            let updatedStr = isoFormatter.string(from: Date())

            sqlite3_bind_text(statement, 1, userIdStr, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(statement, 2, payloadJson, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(statement, 3, evaluatedStr, -1, SQLITE_TRANSIENT)
            sqlite3_bind_int(statement, 4, Int32(count))
            sqlite3_bind_text(statement, 5, updatedStr, -1, SQLITE_TRANSIENT)

            sqlite3_step(statement)
        }
        sqlite3_finalize(statement)
    }

    public func loadQueue(for userId: UUID) async -> SpacedQueueResponse? {
        guard let db else { return nil }

        let querySql = """
        SELECT payload_json FROM spaced_review_cache
        WHERE user_id = ?
        LIMIT 1;
        """

        var statement: OpaquePointer?
        var result: SpacedQueueResponse? = nil

        if sqlite3_prepare_v2(db, querySql, -1, &statement, nil) == SQLITE_OK {
            let userIdStr = userId.uuidString
            sqlite3_bind_text(statement, 1, userIdStr, -1, SQLITE_TRANSIENT)

            if sqlite3_step(statement) == SQLITE_ROW {
                if let jsonCString = sqlite3_column_text(statement, 0) {
                    let jsonString = String(cString: jsonCString)
                    if let data = jsonString.data(using: .utf8) {
                        result = try? decoder.decode(SpacedQueueResponse.self, from: data)
                    }
                }
            }
        }
        sqlite3_finalize(statement)
        return result
    }

    public func purgeUserData(for userId: UUID) async {
        guard let db else { return }
        let deleteSql = "DELETE FROM spaced_review_cache WHERE user_id = ?;"
        var statement: OpaquePointer?
        if sqlite3_prepare_v2(db, deleteSql, -1, &statement, nil) == SQLITE_OK {
            let userIdStr = userId.uuidString
            sqlite3_bind_text(statement, 1, userIdStr, -1, SQLITE_TRANSIENT)
            sqlite3_step(statement)
        }
        sqlite3_finalize(statement)
    }

    public func purgeCache() async {
        guard let db else { return }
        let purgeSql = "DELETE FROM spaced_review_cache;"
        sqlite3_exec(db, purgeSql, nil, nil, nil)
    }
}
