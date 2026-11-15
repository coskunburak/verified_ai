import Foundation
import SQLite3

public protocol ExamCacheStoreProtocol: Sendable {
    func saveCatalog(_ catalog: ExamDefinitionCatalogResponse) async
    func loadCatalog() async -> ExamDefinitionCatalogResponse?
    func saveDefinitionDetail(_ detail: ExamDefinitionModel) async
    func loadDefinitionDetail(examCode: String) async -> ExamDefinitionModel?
    func saveTargets(_ targets: [StudentExamTargetModel], for userId: UUID) async
    func loadTargets(for userId: UUID) async -> [StudentExamTargetModel]
    func purgeUserData(userId: UUID) async
}

public actor ExamOfflineSQLiteStore: ExamCacheStoreProtocol {
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
            let fileUrl = cacheDir.appendingPathComponent("exam_platform_cache.sqlite")
            connection = Connection(path: fileUrl.path)
        }
        Self.createTablesIfNotExists(on: connection.handle)
    }

    private static func createTablesIfNotExists(on db: OpaquePointer?) {
        guard let db else { return }
        let sql = """
        CREATE TABLE IF NOT EXISTS exam_definitions_cache (
            cache_key TEXT PRIMARY KEY,
            payload_json TEXT NOT NULL,
            updated_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS student_exam_targets_cache (
            user_id TEXT PRIMARY KEY,
            payload_json TEXT NOT NULL,
            updated_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS cached_candidate_profiles (
            user_id TEXT PRIMARY KEY,
            payload_json TEXT NOT NULL,
            updated_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS cached_syllabus_uploads (
            user_id TEXT PRIMARY KEY,
            payload_json TEXT NOT NULL,
            updated_at TEXT NOT NULL
        );
        """
        sqlite3_exec(db, sql, nil, nil, nil)
    }

    public func saveCatalog(_ catalog: ExamDefinitionCatalogResponse) async {
        upsertDefinitionRow(cacheKey: "CATALOG::ACTIVE", payload: catalog)
    }

    public func loadCatalog() async -> ExamDefinitionCatalogResponse? {
        loadDefinitionRow(cacheKey: "CATALOG::ACTIVE", as: ExamDefinitionCatalogResponse.self)
    }

    public func saveDefinitionDetail(_ detail: ExamDefinitionModel) async {
        upsertDefinitionRow(cacheKey: "DETAIL::\(detail.examCode)", payload: detail)
    }

    public func loadDefinitionDetail(examCode: String) async -> ExamDefinitionModel? {
        loadDefinitionRow(cacheKey: "DETAIL::\(examCode)", as: ExamDefinitionModel.self)
    }

    public func saveTargets(_ targets: [StudentExamTargetModel], for userId: UUID) async {
        upsertUserScopedRow(table: "student_exam_targets_cache", userId: userId.uuidString, payload: targets)
    }

    public func loadTargets(for userId: UUID) async -> [StudentExamTargetModel] {
        loadUserScopedRow(table: "student_exam_targets_cache", userId: userId.uuidString, as: [StudentExamTargetModel].self) ?? []
    }

    public func saveCandidateProfile(_ profile: CandidateProfileResponse, userId: String) async {
        upsertUserScopedRow(table: "cached_candidate_profiles", userId: userId, payload: profile)
    }

    public func loadCandidateProfile(userId: String) async -> CandidateProfileResponse? {
        loadUserScopedRow(table: "cached_candidate_profiles", userId: userId, as: CandidateProfileResponse.self)
    }

    public func saveSyllabusUpload(_ upload: SyllabusIngestionResponse, userId: String) async {
        upsertUserScopedRow(table: "cached_syllabus_uploads", userId: userId, payload: upload)
    }

    public func loadLatestSyllabusUpload(userId: String) async -> SyllabusIngestionResponse? {
        loadUserScopedRow(table: "cached_syllabus_uploads", userId: userId, as: SyllabusIngestionResponse.self)
    }

    public func purgeUserData(userId: UUID) async {
        await purgeUserData(userId: userId.uuidString)
    }

    public func purgeUserData(userId: String) async {
        guard let db else { return }
        for table in ["student_exam_targets_cache", "cached_candidate_profiles", "cached_syllabus_uploads"] {
            let sql = "DELETE FROM \(table) WHERE lower(user_id) = lower(?1);"
            var stmt: OpaquePointer?
            if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
                defer { sqlite3_finalize(stmt) }
                sqlite3_bind_text(stmt, 1, userId, -1, SQLITE_TRANSIENT)
                sqlite3_step(stmt)
            }
        }
    }

    public func purgeAll() async {
        guard let db else { return }
        sqlite3_exec(db, """
            DELETE FROM exam_definitions_cache;
            DELETE FROM student_exam_targets_cache;
            DELETE FROM cached_candidate_profiles;
            DELETE FROM cached_syllabus_uploads;
            """, nil, nil, nil)
    }

    private func upsertUserScopedRow<T: Encodable>(table: String, userId: String, payload: T) {
        guard let db,
              let data = try? encoder.encode(payload),
              let jsonString = String(data: data, encoding: .utf8) else {
            return
        }
        let sql = """
        INSERT INTO \(table) (user_id, payload_json, updated_at)
        VALUES (?1, ?2, ?3)
        ON CONFLICT(user_id) DO UPDATE SET
            payload_json = excluded.payload_json,
            updated_at = excluded.updated_at;
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            let nowString = ISO8601DateFormatter().string(from: Date())
            sqlite3_bind_text(stmt, 1, userId, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 2, jsonString, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 3, nowString, -1, SQLITE_TRANSIENT)
            sqlite3_step(stmt)
        }
    }

    private func loadUserScopedRow<T: Decodable>(table: String, userId: String, as type: T.Type) -> T? {
        guard let db else { return nil }
        let sql = "SELECT payload_json FROM \(table) WHERE lower(user_id) = lower(?1) LIMIT 1;"
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_text(stmt, 1, userId, -1, SQLITE_TRANSIENT)
            if sqlite3_step(stmt) == SQLITE_ROW, let cStr = sqlite3_column_text(stmt, 0) {
                let jsonString = String(cString: cStr)
                if let data = jsonString.data(using: .utf8),
                   let decoded = try? decoder.decode(T.self, from: data) {
                    return decoded
                }
            }
        }
        return nil
    }

    private func upsertDefinitionRow<T: Encodable>(cacheKey: String, payload: T) {
        guard let db,
              let data = try? encoder.encode(payload),
              let jsonString = String(data: data, encoding: .utf8) else {
            return
        }
        let sql = """
        INSERT INTO exam_definitions_cache (cache_key, payload_json, updated_at)
        VALUES (?1, ?2, ?3)
        ON CONFLICT(cache_key) DO UPDATE SET
            payload_json = excluded.payload_json,
            updated_at = excluded.updated_at;
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            let nowString = ISO8601DateFormatter().string(from: Date())
            sqlite3_bind_text(stmt, 1, cacheKey, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 2, jsonString, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 3, nowString, -1, SQLITE_TRANSIENT)
            sqlite3_step(stmt)
        }
    }

    private func loadDefinitionRow<T: Decodable>(cacheKey: String, as type: T.Type) -> T? {
        guard let db else { return nil }
        let sql = "SELECT payload_json FROM exam_definitions_cache WHERE cache_key = ?1 LIMIT 1;"
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_text(stmt, 1, cacheKey, -1, SQLITE_TRANSIENT)
            if sqlite3_step(stmt) == SQLITE_ROW, let cStr = sqlite3_column_text(stmt, 0) {
                let jsonString = String(cString: cStr)
                if let data = jsonString.data(using: .utf8),
                   let decoded = try? decoder.decode(T.self, from: data) {
                    return decoded
                }
            }
        }
        return nil
    }
}
