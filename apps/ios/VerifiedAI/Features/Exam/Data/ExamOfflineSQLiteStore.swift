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
        CREATE TABLE IF NOT EXISTS cached_exam_blueprints (
            exam_definition_id TEXT PRIMARY KEY,
            payload_json TEXT NOT NULL,
            updated_at TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS cached_assembled_assessments (
            assessment_id TEXT NOT NULL,
            user_id TEXT NOT NULL,
            blueprint_id TEXT NOT NULL,
            payload_json TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            PRIMARY KEY (assessment_id, user_id)
        );
        CREATE TABLE IF NOT EXISTS exam_sessions_local (
            session_id TEXT NOT NULL,
            user_id TEXT NOT NULL,
            assembly_id TEXT NOT NULL,
            payload_json TEXT NOT NULL,
            updated_at TEXT NOT NULL,
            PRIMARY KEY (session_id, user_id)
        );
        CREATE TABLE IF NOT EXISTS exam_autosave_outbox (
            id TEXT NOT NULL,
            session_id TEXT NOT NULL,
            user_id TEXT NOT NULL,
            item_position INTEGER NOT NULL,
            idempotency_key TEXT NOT NULL,
            payload_json TEXT NOT NULL,
            created_at TEXT NOT NULL,
            PRIMARY KEY (id, user_id)
        );
        CREATE TABLE IF NOT EXISTS exam_scratchpad_attachments_local (
            id TEXT NOT NULL,
            session_id TEXT NOT NULL,
            user_id TEXT NOT NULL,
            item_position INTEGER NOT NULL,
            payload_json TEXT NOT NULL,
            created_at TEXT NOT NULL,
            PRIMARY KEY (id, user_id)
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

    public func saveBlueprints(_ blueprints: [ExamBlueprintDTO], examDefinitionId: UUID) async {
        guard let db,
              let data = try? encoder.encode(blueprints),
              let jsonString = String(data: data, encoding: .utf8) else {
            return
        }
        let sql = """
        INSERT INTO cached_exam_blueprints (exam_definition_id, payload_json, updated_at)
        VALUES (?1, ?2, ?3)
        ON CONFLICT(exam_definition_id) DO UPDATE SET
            payload_json = excluded.payload_json,
            updated_at = excluded.updated_at;
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            let nowString = ISO8601DateFormatter().string(from: Date())
            sqlite3_bind_text(stmt, 1, examDefinitionId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 2, jsonString, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 3, nowString, -1, SQLITE_TRANSIENT)
            sqlite3_step(stmt)
        }
    }

    public func loadBlueprints(examDefinitionId: UUID) async -> [ExamBlueprintDTO] {
        guard let db else { return [] }
        let sql = "SELECT payload_json FROM cached_exam_blueprints WHERE lower(exam_definition_id) = lower(?1) LIMIT 1;"
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_text(stmt, 1, examDefinitionId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            if sqlite3_step(stmt) == SQLITE_ROW, let cStr = sqlite3_column_text(stmt, 0) {
                let jsonString = String(cString: cStr)
                if let data = jsonString.data(using: .utf8),
                   let decoded = try? decoder.decode([ExamBlueprintDTO].self, from: data) {
                    return decoded
                }
            }
        }
        return []
    }

    public func saveAssembledAssessment(_ assessment: AssembledAssessmentDTO) async {
        guard let db,
              let data = try? encoder.encode(assessment),
              let jsonString = String(data: data, encoding: .utf8) else {
            return
        }
        let sql = """
        INSERT INTO cached_assembled_assessments (assessment_id, user_id, blueprint_id, payload_json, updated_at)
        VALUES (?1, ?2, ?3, ?4, ?5)
        ON CONFLICT(assessment_id, user_id) DO UPDATE SET
            blueprint_id = excluded.blueprint_id,
            payload_json = excluded.payload_json,
            updated_at = excluded.updated_at;
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            let nowString = ISO8601DateFormatter().string(from: Date())
            sqlite3_bind_text(stmt, 1, assessment.id.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 2, assessment.userId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 3, assessment.blueprintId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 4, jsonString, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 5, nowString, -1, SQLITE_TRANSIENT)
            sqlite3_step(stmt)
        }
    }

    public func loadAssembledAssessment(userId: UUID, assessmentId: UUID) async -> AssembledAssessmentDTO? {
        guard let db else { return nil }
        let sql = """
        SELECT payload_json FROM cached_assembled_assessments
        WHERE lower(user_id) = lower(?1) AND lower(assessment_id) = lower(?2)
        LIMIT 1;
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_text(stmt, 1, userId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 2, assessmentId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            if sqlite3_step(stmt) == SQLITE_ROW, let cStr = sqlite3_column_text(stmt, 0) {
                let jsonString = String(cString: cStr)
                if let data = jsonString.data(using: .utf8),
                   let decoded = try? decoder.decode(AssembledAssessmentDTO.self, from: data) {
                    return decoded
                }
            }
        }
        return nil
    }

    public func loadLatestAssembledAssessment(userId: UUID, blueprintId: UUID) async -> AssembledAssessmentDTO? {
        guard let db else { return nil }
        let sql = """
        SELECT payload_json FROM cached_assembled_assessments
        WHERE lower(user_id) = lower(?1) AND lower(blueprint_id) = lower(?2)
        ORDER BY updated_at DESC
        LIMIT 1;
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_text(stmt, 1, userId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 2, blueprintId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            if sqlite3_step(stmt) == SQLITE_ROW, let cStr = sqlite3_column_text(stmt, 0) {
                let jsonString = String(cString: cStr)
                if let data = jsonString.data(using: .utf8),
                   let decoded = try? decoder.decode(AssembledAssessmentDTO.self, from: data) {
                    return decoded
                }
            }
        }
        return nil
    }

    // MARK: - Sprint 9.4 Timed Exam Session & Outbox Persistence

    public func saveExamSession(_ session: ExamSessionDTO) async {
        guard let db,
              let data = try? encoder.encode(session),
              let jsonString = String(data: data, encoding: .utf8) else {
            return
        }
        let sql = """
        INSERT INTO exam_sessions_local (session_id, user_id, assembly_id, payload_json, updated_at)
        VALUES (?1, ?2, ?3, ?4, ?5)
        ON CONFLICT(session_id, user_id) DO UPDATE SET
            assembly_id = excluded.assembly_id,
            payload_json = excluded.payload_json,
            updated_at = excluded.updated_at;
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            let nowString = ISO8601DateFormatter().string(from: Date())
            sqlite3_bind_text(stmt, 1, session.sessionId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 2, session.userId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 3, session.assemblyId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 4, jsonString, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 5, nowString, -1, SQLITE_TRANSIENT)
            sqlite3_step(stmt)
        }
    }

    public func loadExamSession(userId: UUID, sessionId: UUID) async -> ExamSessionDTO? {
        guard let db else { return nil }
        let sql = """
        SELECT payload_json FROM exam_sessions_local
        WHERE lower(user_id) = lower(?1) AND lower(session_id) = lower(?2)
        LIMIT 1;
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_text(stmt, 1, userId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 2, sessionId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            if sqlite3_step(stmt) == SQLITE_ROW, let cStr = sqlite3_column_text(stmt, 0) {
                let jsonString = String(cString: cStr)
                if let data = jsonString.data(using: .utf8),
                   let decoded = try? decoder.decode(ExamSessionDTO.self, from: data) {
                    return decoded
                }
            }
        }
        return nil
    }

    public func loadActiveExamSession(userId: UUID, assemblyId: UUID) async -> ExamSessionDTO? {
        guard let db else { return nil }
        let sql = """
        SELECT payload_json FROM exam_sessions_local
        WHERE lower(user_id) = lower(?1) AND lower(assembly_id) = lower(?2)
        ORDER BY updated_at DESC
        LIMIT 5;
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_text(stmt, 1, userId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 2, assemblyId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            while sqlite3_step(stmt) == SQLITE_ROW {
                if let cStr = sqlite3_column_text(stmt, 0),
                   let data = String(cString: cStr).data(using: .utf8),
                   let decoded = try? decoder.decode(ExamSessionDTO.self, from: data),
                   decoded.status.isActive {
                    return decoded
                }
            }
        }
        return nil
    }

    public func enqueueAutosaveOutbox(_ entry: ExamAutosaveOutboxEntry) async {
        guard let db,
              let data = try? encoder.encode(entry),
              let jsonString = String(data: data, encoding: .utf8) else {
            return
        }
        let sql = """
        INSERT INTO exam_autosave_outbox (id, session_id, user_id, item_position, idempotency_key, payload_json, created_at)
        VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7)
        ON CONFLICT(id, user_id) DO UPDATE SET
            payload_json = excluded.payload_json;
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_text(stmt, 1, entry.id.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 2, entry.sessionId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 3, entry.userId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_int(stmt, 4, Int32(entry.itemPosition))
            sqlite3_bind_text(stmt, 5, entry.idempotencyKey, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 6, jsonString, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 7, entry.enqueuedAt, -1, SQLITE_TRANSIENT)
            sqlite3_step(stmt)
        }
    }

    public func loadAutosaveOutbox(userId: UUID, sessionId: UUID) async -> [ExamAutosaveOutboxEntry] {
        guard let db else { return [] }
        let sql = """
        SELECT payload_json FROM exam_autosave_outbox
        WHERE lower(user_id) = lower(?1) AND lower(session_id) = lower(?2)
        ORDER BY created_at ASC;
        """
        var result: [ExamAutosaveOutboxEntry] = []
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_text(stmt, 1, userId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 2, sessionId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            while sqlite3_step(stmt) == SQLITE_ROW {
                if let cStr = sqlite3_column_text(stmt, 0),
                   let data = String(cString: cStr).data(using: .utf8),
                   let decoded = try? decoder.decode(ExamAutosaveOutboxEntry.self, from: data) {
                    result.append(decoded)
                }
            }
        }
        return result
    }

    public func removeAutosaveOutboxEntry(userId: UUID, id: UUID) async {
        guard let db else { return }
        let sql = "DELETE FROM exam_autosave_outbox WHERE lower(user_id) = lower(?1) AND lower(id) = lower(?2);"
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_text(stmt, 1, userId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 2, id.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_step(stmt)
        }
    }

    public func saveLocalScratchpadDraft(_ draft: ExamLocalScratchpadDraft) async {
        guard let db,
              let data = try? encoder.encode(draft),
              let jsonString = String(data: data, encoding: .utf8) else {
            return
        }
        let sql = """
        INSERT INTO exam_scratchpad_attachments_local (id, session_id, user_id, item_position, payload_json, created_at)
        VALUES (?1, ?2, ?3, ?4, ?5, ?6)
        ON CONFLICT(id, user_id) DO UPDATE SET
            payload_json = excluded.payload_json;
        """
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_text(stmt, 1, draft.id.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 2, draft.sessionId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 3, draft.userId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_int(stmt, 4, Int32(draft.itemPosition))
            sqlite3_bind_text(stmt, 5, jsonString, -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 6, draft.createdAt, -1, SQLITE_TRANSIENT)
            sqlite3_step(stmt)
        }
    }

    public func loadLocalScratchpadDrafts(userId: UUID, sessionId: UUID) async -> [ExamLocalScratchpadDraft] {
        guard let db else { return [] }
        let sql = """
        SELECT payload_json FROM exam_scratchpad_attachments_local
        WHERE lower(user_id) = lower(?1) AND lower(session_id) = lower(?2)
        ORDER BY created_at ASC;
        """
        var result: [ExamLocalScratchpadDraft] = []
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_text(stmt, 1, userId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 2, sessionId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            while sqlite3_step(stmt) == SQLITE_ROW {
                if let cStr = sqlite3_column_text(stmt, 0),
                   let data = String(cString: cStr).data(using: .utf8),
                   let decoded = try? decoder.decode(ExamLocalScratchpadDraft.self, from: data) {
                    result.append(decoded)
                }
            }
        }
        return result
    }

    public func removeLocalScratchpadDraft(userId: UUID, id: UUID) async {
        guard let db else { return }
        let sql = "DELETE FROM exam_scratchpad_attachments_local WHERE lower(user_id) = lower(?1) AND lower(id) = lower(?2);"
        var stmt: OpaquePointer?
        if sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK {
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_text(stmt, 1, userId.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_bind_text(stmt, 2, id.uuidString.lowercased(), -1, SQLITE_TRANSIENT)
            sqlite3_step(stmt)
        }
    }

    public func clearUserData(userId: UUID) async {
        await purgeUserData(userId: userId)
    }

    public func purgeUserData(userId: UUID) async {
        await purgeUserData(userId: userId.uuidString)
    }

    public func purgeUserData(userId: String) async {
        guard let db else { return }
        for table in [
            "student_exam_targets_cache",
            "cached_candidate_profiles",
            "cached_syllabus_uploads",
            "cached_assembled_assessments",
            "exam_sessions_local",
            "exam_autosave_outbox",
            "exam_scratchpad_attachments_local"
        ] {
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
            DELETE FROM cached_exam_blueprints;
            DELETE FROM cached_assembled_assessments;
            DELETE FROM exam_sessions_local;
            DELETE FROM exam_autosave_outbox;
            DELETE FROM exam_scratchpad_attachments_local;
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
