import Foundation
import SQLite3

public protocol RecommendationCacheStoreProtocol: Sendable {
    func saveRecommendations(_ recommendations: [LearnerRecommendationDto], for userId: UUID) async
    func loadCachedRecommendations(for userId: UUID) async -> [LearnerRecommendationDto]
    func updateRecommendationStatus(id: UUID, status: RecommendationStatus) async
    func purgeCache() async
}

public actor RecommendationSQLiteCache: RecommendationCacheStoreProtocol {
    private var db: OpaquePointer?
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()

    public init(inMemory: Bool = false) {
        encoder.dateEncodingStrategy = .iso8601
        decoder.dateDecodingStrategy = .iso8601

        if inMemory {
            sqlite3_open(":memory:", &db)
        } else {
            let paths = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)
            let cacheDir = paths[0].appendingPathComponent("VerifiedAI", isDirectory: true)
            try? FileManager.default.createDirectory(at: cacheDir, withIntermediateDirectories: true)
            let fileUrl = cacheDir.appendingPathComponent("recommendations_cache.sqlite")
            sqlite3_open(fileUrl.path, &db)
        }
        createTableIfNotExists()
    }

    deinit {
        if let db = db {
            sqlite3_close(db)
        }
    }

    private func createTableIfNotExists() {
        guard let db = db else { return }
        let createSql = """
        CREATE TABLE IF NOT EXISTS recommendations_cache (
            id TEXT PRIMARY KEY,
            user_id TEXT NOT NULL,
            recommendation_type TEXT NOT NULL,
            target_skill_code TEXT NOT NULL,
            target_mistake_code TEXT,
            priority_score REAL NOT NULL,
            rationale_code TEXT NOT NULL,
            payload_json TEXT NOT NULL,
            status TEXT NOT NULL,
            expires_at TEXT NOT NULL,
            created_at TEXT NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_cache_user_status_priority
            ON recommendations_cache (user_id, status, priority_score DESC);
        """
        sqlite3_exec(db, createSql, nil, nil, nil)
    }

    public func saveRecommendations(_ recommendations: [LearnerRecommendationDto], for userId: UUID) async {
        guard let db = db else { return }

        let insertSql = """
        INSERT OR REPLACE INTO recommendations_cache (
            id, user_id, recommendation_type, target_skill_code, target_mistake_code,
            priority_score, rationale_code, payload_json, status, expires_at, created_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """

        var statement: OpaquePointer?
        if sqlite3_prepare_v2(db, insertSql, -1, &statement, nil) == SQLITE_OK {
            let isoFormatter = ISO8601DateFormatter()
            for rec in recommendations {
                let idStr = rec.id.uuidString
                let userIdStr = userId.uuidString
                let typeStr = rec.recommendationType.rawValue
                let skillStr = rec.targetSkillCode
                let mistakeStr = rec.targetMistakeCode
                let score = rec.priorityScore
                let rationaleStr = rec.rationaleCode.rawValue
                let payloadData = (try? encoder.encode(rec)) ?? Data()
                let payloadJson = String(data: payloadData, encoding: .utf8) ?? "{}"
                let statusStr = rec.status.rawValue
                let expiresStr = isoFormatter.string(from: rec.expiresAt)
                let createdStr = isoFormatter.string(from: rec.createdAt)

                sqlite3_bind_text(statement, 1, (idStr as NSString).utf8String, -1, nil)
                sqlite3_bind_text(statement, 2, (userIdStr as NSString).utf8String, -1, nil)
                sqlite3_bind_text(statement, 3, (typeStr as NSString).utf8String, -1, nil)
                sqlite3_bind_text(statement, 4, (skillStr as NSString).utf8String, -1, nil)
                if let mistakeStr = mistakeStr {
                    sqlite3_bind_text(statement, 5, (mistakeStr as NSString).utf8String, -1, nil)
                } else {
                    sqlite3_bind_null(statement, 5)
                }
                sqlite3_bind_double(statement, 6, score)
                sqlite3_bind_text(statement, 7, (rationaleStr as NSString).utf8String, -1, nil)
                sqlite3_bind_text(statement, 8, (payloadJson as NSString).utf8String, -1, nil)
                sqlite3_bind_text(statement, 9, (statusStr as NSString).utf8String, -1, nil)
                sqlite3_bind_text(statement, 10, (expiresStr as NSString).utf8String, -1, nil)
                sqlite3_bind_text(statement, 11, (createdStr as NSString).utf8String, -1, nil)

                sqlite3_step(statement)
                sqlite3_reset(statement)
            }
        }
        sqlite3_finalize(statement)
    }

    public func loadCachedRecommendations(for userId: UUID) async -> [LearnerRecommendationDto] {
        guard let db = db else { return [] }

        let querySql = """
        SELECT payload_json FROM recommendations_cache
        WHERE user_id = ? AND status = 'ACTIVE' AND expires_at > ?
        ORDER BY priority_score DESC, target_skill_code ASC;
        """

        let isoFormatter = ISO8601DateFormatter()
        let nowStr = isoFormatter.string(from: Date())
        var statement: OpaquePointer?
        var results: [LearnerRecommendationDto] = []

        if sqlite3_prepare_v2(db, querySql, -1, &statement, nil) == SQLITE_OK {
            sqlite3_bind_text(statement, 1, (userId.uuidString as NSString).utf8String, -1, nil)
            sqlite3_bind_text(statement, 2, (nowStr as NSString).utf8String, -1, nil)

            while sqlite3_step(statement) == SQLITE_ROW {
                if let jsonCString = sqlite3_column_text(statement, 0) {
                    let jsonString = String(cString: jsonCString)
                    if let data = jsonString.data(using: .utf8),
                       let item = try? decoder.decode(LearnerRecommendationDto.self, from: data) {
                        results.append(item)
                    }
                }
            }
        }
        sqlite3_finalize(statement)
        return results
    }

    public func updateRecommendationStatus(id: UUID, status: RecommendationStatus) async {
        guard let db = db else { return }
        let updateSql = "UPDATE recommendations_cache SET status = ? WHERE id = ?;"
        var statement: OpaquePointer?
        if sqlite3_prepare_v2(db, updateSql, -1, &statement, nil) == SQLITE_OK {
            sqlite3_bind_text(statement, 1, (status.rawValue as NSString).utf8String, -1, nil)
            sqlite3_bind_text(statement, 2, (id.uuidString as NSString).utf8String, -1, nil)
            sqlite3_step(statement)
        }
        sqlite3_finalize(statement)
    }

    public func purgeCache() async {
        guard let db = db else { return }
        let purgeSql = "DELETE FROM recommendations_cache;"
        sqlite3_exec(db, purgeSql, nil, nil, nil)
    }
}
