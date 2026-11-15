import CryptoKit
import Foundation

public enum ExamServiceError: Error, Equatable, Sendable {
    case featureDisabled
    case notFound
    case versionConflict
    case scoreOutOfRange
    case forbidden
    case offline
    case dateInPast
    case studyBudgetInvalid
    case accommodationInvalid
    case quotaExceeded
    case mediaUnsupported
    case disambiguationRequired
    case poolInsufficient
    case noHintModeActive
    case deadlineExpired
    case sessionAlreadySubmitted
    case autosaveVersionConflict
    case invalidScratchpadAsset
    case unexpected(String)
}

public typealias ExamAPIServiceProtocol = ExamAPIServicing

public protocol ExamAPIServicing: Sendable {
    func fetchDefinitions(
        jurisdiction: ExamJurisdiction?,
        examFamily: ExamFamily?,
        includeDeprecated: Bool
    ) async throws -> ExamDefinitionCatalogResponse

    func fetchDefinitionDetail(examCode: String) async throws -> ExamDefinitionModel

    func fetchDefinitionVersion(examCode: String, examVersion: String) async throws -> ExamDefinitionModel

    func upsertTarget(request: UpsertStudentExamTargetRequestDTO) async throws -> StudentExamTargetModel

    func listTargets() async throws -> StudentExamTargetListResponse
    func upsertCandidateProfile(_ request: UpsertCandidateProfileRequest, idempotencyKey: String) async throws -> CandidateProfileResponse
    func fetchCandidateProfile() async throws -> CandidateProfileResponse
    func fetchCandidateProfileHistory() async throws -> CandidateProfileHistoryResponse
    func ingestSyllabus(
        fileData: Data,
        mimeType: String,
        filename: String,
        examCode: String,
        examVersion: String?,
        idempotencyKey: String
    ) async throws -> SyllabusIngestionResponse
    func confirmSyllabusDisambiguation(
        uploadId: String,
        mappings: [SyllabusMappingConfirmation]
    ) async throws -> SyllabusIngestionResponse
    func fetchBlueprints(examDefinitionId: UUID, examVersionNumber: Int?) async throws -> [ExamBlueprintDTO]
    func assembleAssessment(examDefinitionId: UUID, request: AssessmentAssemblyRequestDTO) async throws -> AssembledAssessmentDTO
    func fetchAssembledAssessment(userId: UUID, assessmentId: UUID) async throws -> AssembledAssessmentDTO
    func fetchAssembledAssessments(userId: UUID, examDefinitionId: UUID?) async throws -> [AssembledAssessmentDTO]
    func startOrResumeSession(request: ExamSessionStartRequestDTO) async throws -> ExamSessionDTO
    func fetchSessionState(sessionId: UUID, clientTimestamp: String?) async throws -> ExamSessionDTO
    func autosaveSessionItem(
        sessionId: UUID,
        itemPosition: Int,
        idempotencyKey: String,
        request: ExamSessionItemAutosaveRequestDTO
    ) async throws -> ExamSessionItemAutosaveResponseDTO
    func attachScratchpadPhoto(
        sessionId: UUID,
        itemPosition: Int,
        idempotencyKey: String,
        request: ExamScratchpadUploadRequestDTO
    ) async throws -> ExamScratchpadUploadResponseDTO
    func submitSession(
        sessionId: UUID,
        idempotencyKey: String,
        request: ExamSessionSubmitRequestDTO
    ) async throws -> ExamSessionSubmitResponseDTO
    func checkNoHintGuard(sessionId: UUID, requestedSurface: String) async throws -> ExamNoHintGuardResponseDTO
}

public extension ExamAPIServicing {
    func fetchBlueprints(examDefinitionId: UUID) async throws -> [ExamBlueprintDTO] {
        try await fetchBlueprints(examDefinitionId: examDefinitionId, examVersionNumber: nil)
    }

    func assembleAssessment(userId: UUID, request: AssessmentAssemblyRequestDTO) async throws -> AssembledAssessmentDTO {
        let defaultDefId = UUID(uuidString: "91000000-0000-4000-8000-000000000101")!
        return try await assembleAssessment(examDefinitionId: defaultDefId, request: request)
    }

    func fetchAssembledAssessment(userId: UUID, assembledAssessmentId: UUID) async throws -> AssembledAssessmentDTO {
        try await fetchAssembledAssessment(userId: userId, assessmentId: assembledAssessmentId)
    }
}

final class HTTPExamAPIService: ExamAPIServicing, @unchecked Sendable {
    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func fetchDefinitions(
        jurisdiction: ExamJurisdiction? = nil,
        examFamily: ExamFamily? = nil,
        includeDeprecated: Bool = false
    ) async throws -> ExamDefinitionCatalogResponse {
        var queryItems: [URLQueryItem] = []
        if let jurisdiction {
            queryItems.append(URLQueryItem(name: "jurisdiction", value: jurisdiction.rawValue))
        }
        if let examFamily {
            queryItems.append(URLQueryItem(name: "examFamily", value: examFamily.rawValue))
        }
        if includeDeprecated {
            queryItems.append(URLQueryItem(name: "includeDeprecated", value: "true"))
        }
        let endpoint = Endpoint(path: "/v1/exams/definitions", method: .get, queryItems: queryItems)
        let httpRequest = HTTPRequest<ExamDefinitionCatalogResponse>(
            endpoint: endpoint,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func fetchDefinitionDetail(examCode: String) async throws -> ExamDefinitionModel {
        let endpoint = Endpoint(path: "/v1/exams/definitions/\(examCode)", method: .get)
        let httpRequest = HTTPRequest<ExamDefinitionModel>(
            endpoint: endpoint,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func fetchDefinitionVersion(examCode: String, examVersion: String) async throws -> ExamDefinitionModel {
        let endpoint = Endpoint(path: "/v1/exams/definitions/\(examCode)/versions/\(examVersion)", method: .get)
        let httpRequest = HTTPRequest<ExamDefinitionModel>(
            endpoint: endpoint,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func upsertTarget(request payload: UpsertStudentExamTargetRequestDTO) async throws -> StudentExamTargetModel {
        let bodyData = try encoder.encode(payload)
        let endpoint = Endpoint(path: "/v1/exams/targets", method: .post)
        let httpRequest = HTTPRequest<StudentExamTargetModel>(
            endpoint: endpoint,
            body: bodyData,
            idempotencyKey: payload.idempotencyKey,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func listTargets() async throws -> StudentExamTargetListResponse {
        let endpoint = Endpoint(path: "/v1/exams/targets", method: .get)
        let httpRequest = HTTPRequest<StudentExamTargetListResponse>(
            endpoint: endpoint,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func upsertCandidateProfile(_ request: UpsertCandidateProfileRequest, idempotencyKey: String) async throws -> CandidateProfileResponse {
        let bodyData = try encoder.encode(request)
        let endpoint = Endpoint(path: "/v1/candidate/profile", method: .put)
        let httpRequest = HTTPRequest<CandidateProfileResponse>(
            endpoint: endpoint,
            body: bodyData,
            idempotencyKey: idempotencyKey,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func fetchCandidateProfile() async throws -> CandidateProfileResponse {
        let endpoint = Endpoint(path: "/v1/candidate/profile", method: .get)
        let httpRequest = HTTPRequest<CandidateProfileResponse>(
            endpoint: endpoint,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func fetchCandidateProfileHistory() async throws -> CandidateProfileHistoryResponse {
        let endpoint = Endpoint(path: "/v1/candidate/profile/history", method: .get)
        let httpRequest = HTTPRequest<CandidateProfileHistoryResponse>(
            endpoint: endpoint,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func ingestSyllabus(
        fileData: Data,
        mimeType: String,
        filename: String,
        examCode: String,
        examVersion: String?,
        idempotencyKey: String
    ) async throws -> SyllabusIngestionResponse {
        let boundary = "Boundary-\(UUID().uuidString)"
        var body = Data()

        func appendField(name: String, value: String) {
            body.append(Data("--\(boundary)\r\n".utf8))
            body.append(Data("Content-Disposition: form-data; name=\"\(name)\"\r\n\r\n".utf8))
            body.append(Data("\(value)\r\n".utf8))
        }

        appendField(name: "examCode", value: examCode)
        if let examVersion, !examVersion.isEmpty {
            appendField(name: "examVersion", value: examVersion)
        }
        appendField(name: "idempotencyKey", value: idempotencyKey)

        let sanitizedFilename = filename.replacingOccurrences(of: "\"", with: "")
        body.append(Data("--\(boundary)\r\n".utf8))
        body.append(Data("Content-Disposition: form-data; name=\"file\"; filename=\"\(sanitizedFilename)\"\r\n".utf8))
        body.append(Data("Content-Type: \(mimeType)\r\n\r\n".utf8))
        body.append(fileData)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))

        let endpoint = Endpoint(path: "/v1/candidate/syllabus-ingest", method: .post)
        let httpRequest = HTTPRequest<SyllabusIngestionResponse>(
            endpoint: endpoint,
            body: body,
            contentType: "multipart/form-data; boundary=\(boundary)",
            idempotencyKey: idempotencyKey,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func confirmSyllabusDisambiguation(
        uploadId: String,
        mappings: [SyllabusMappingConfirmation]
    ) async throws -> SyllabusIngestionResponse {
        let payload = SyllabusConfirmRequest(mappings: mappings)
        let bodyData = try encoder.encode(payload)
        let endpoint = Endpoint(path: "/v1/candidate/syllabus-uploads/\(uploadId)/confirm", method: .post)
        let httpRequest = HTTPRequest<SyllabusIngestionResponse>(
            endpoint: endpoint,
            body: bodyData,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func fetchBlueprints(examDefinitionId: UUID, examVersionNumber: Int? = nil) async throws -> [ExamBlueprintDTO] {
        var queryItems: [URLQueryItem] = [URLQueryItem(name: "status", value: "ACTIVE")]
        if let examVersionNumber {
            queryItems.append(URLQueryItem(name: "examVersionNumber", value: String(examVersionNumber)))
        }
        let endpoint = Endpoint(
            path: "/v1/exams/\(examDefinitionId.uuidString.lowercased())/blueprints",
            method: .get,
            queryItems: queryItems
        )
        let httpRequest = HTTPRequest<[ExamBlueprintDTO]>(
            endpoint: endpoint,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func assembleAssessment(examDefinitionId: UUID, request: AssessmentAssemblyRequestDTO) async throws -> AssembledAssessmentDTO {
        let bodyData = try encoder.encode(request)
        let endpoint = Endpoint(
            path: "/v1/exams/\(examDefinitionId.uuidString.lowercased())/assessments/assemble",
            method: .post
        )
        let httpRequest = HTTPRequest<AssembledAssessmentDTO>(
            endpoint: endpoint,
            body: bodyData,
            idempotencyKey: "asm-\(request.blueprintId.uuidString.lowercased())-\(request.assemblySeed)",
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func fetchAssembledAssessment(userId: UUID, assessmentId: UUID) async throws -> AssembledAssessmentDTO {
        let endpoint = Endpoint(
            path: "/v1/exams/candidates/\(userId.uuidString.lowercased())/assessments/\(assessmentId.uuidString.lowercased())",
            method: .get
        )
        let httpRequest = HTTPRequest<AssembledAssessmentDTO>(
            endpoint: endpoint,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func fetchAssembledAssessments(userId: UUID, examDefinitionId: UUID? = nil) async throws -> [AssembledAssessmentDTO] {
        var queryItems: [URLQueryItem] = []
        if let examDefinitionId {
            queryItems.append(URLQueryItem(name: "examDefinitionId", value: examDefinitionId.uuidString.lowercased()))
        }
        let endpoint = Endpoint(
            path: "/v1/exams/candidates/\(userId.uuidString.lowercased())/assessments",
            method: .get,
            queryItems: queryItems
        )
        let httpRequest = HTTPRequest<[AssembledAssessmentDTO]>(
            endpoint: endpoint,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    // MARK: - Sprint 9.4 Timed Mock Exam Runtime Endpoints

    func startOrResumeSession(request: ExamSessionStartRequestDTO) async throws -> ExamSessionDTO {
        let bodyData = try encoder.encode(request)
        let endpoint = Endpoint(path: "/v1/exams/sessions", method: .post)
        let httpRequest = HTTPRequest<ExamSessionDTO>(
            endpoint: endpoint,
            body: bodyData,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func fetchSessionState(sessionId: UUID, clientTimestamp: String? = nil) async throws -> ExamSessionDTO {
        var queryItems: [URLQueryItem] = []
        if let clientTimestamp {
            queryItems.append(URLQueryItem(name: "clientTimestamp", value: clientTimestamp))
        }
        let endpoint = Endpoint(
            path: "/v1/exams/sessions/\(sessionId.uuidString.lowercased())",
            method: .get,
            queryItems: queryItems
        )
        let httpRequest = HTTPRequest<ExamSessionDTO>(
            endpoint: endpoint,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func autosaveSessionItem(
        sessionId: UUID,
        itemPosition: Int,
        idempotencyKey: String,
        request: ExamSessionItemAutosaveRequestDTO
    ) async throws -> ExamSessionItemAutosaveResponseDTO {
        let bodyData = try encoder.encode(request)
        let endpoint = Endpoint(
            path: "/v1/exams/sessions/\(sessionId.uuidString.lowercased())/items/\(itemPosition)",
            method: .patch
        )
        let httpRequest = HTTPRequest<ExamSessionItemAutosaveResponseDTO>(
            endpoint: endpoint,
            body: bodyData,
            idempotencyKey: idempotencyKey,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func attachScratchpadPhoto(
        sessionId: UUID,
        itemPosition: Int,
        idempotencyKey: String,
        request: ExamScratchpadUploadRequestDTO
    ) async throws -> ExamScratchpadUploadResponseDTO {
        let bodyData = try encoder.encode(request)
        let endpoint = Endpoint(
            path: "/v1/exams/sessions/\(sessionId.uuidString.lowercased())/items/\(itemPosition)/scratchpad",
            method: .post
        )
        let httpRequest = HTTPRequest<ExamScratchpadUploadResponseDTO>(
            endpoint: endpoint,
            body: bodyData,
            idempotencyKey: idempotencyKey,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func submitSession(
        sessionId: UUID,
        idempotencyKey: String,
        request: ExamSessionSubmitRequestDTO
    ) async throws -> ExamSessionSubmitResponseDTO {
        let bodyData = try encoder.encode(request)
        let endpoint = Endpoint(
            path: "/v1/exams/sessions/\(sessionId.uuidString.lowercased())/submit",
            method: .post
        )
        let httpRequest = HTTPRequest<ExamSessionSubmitResponseDTO>(
            endpoint: endpoint,
            body: bodyData,
            idempotencyKey: idempotencyKey,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    func checkNoHintGuard(sessionId: UUID, requestedSurface: String) async throws -> ExamNoHintGuardResponseDTO {
        let payload = ["requestedSurface": requestedSurface]
        let bodyData = try encoder.encode(payload)
        let endpoint = Endpoint(
            path: "/v1/exams/sessions/\(sessionId.uuidString.lowercased())/hint-guard-check",
            method: .post
        )
        let httpRequest = HTTPRequest<ExamNoHintGuardResponseDTO>(
            endpoint: endpoint,
            body: bodyData,
            allowsAuthRefreshRetry: true
        )
        do {
            return try await apiClient.send(httpRequest).body
        } catch let error as NetworkError {
            throw mapNetworkError(error)
        }
    }

    private func mapNetworkError(_ error: NetworkError) -> ExamServiceError {
        switch error {
        case .server(let problem):
            if problem.code == "EXAM_NO_HINT_MODE_ACTIVE" {
                return .noHintModeActive
            }
            if problem.code == "EXAM_SESSION_EXPIRED"
                || problem.code == "EXAM_SESSION_DEADLINE_EXPIRED"
                || problem.code == "EXAM_SESSION_OFFLINE_GRACE_EXPIRED" {
                return .deadlineExpired
            }
            if problem.code == "EXAM_SESSION_ALREADY_TERMINATED"
                || problem.code == "EXAM_SESSION_INVALID_STATE_TRANSITION"
                || problem.code == "EXAM_SESSION_ALREADY_SUBMITTED"
                || problem.code == "EXAM_SESSION_INVALID_STATE" {
                return .sessionAlreadySubmitted
            }
            if problem.code == "EXAM_ITEM_STALE_VERSION"
                || problem.code == "EXAM_SESSION_IDEMPOTENCY_CONFLICT"
                || problem.code == "EXAM_IDEMPOTENCY_KEY_REQUIRED"
                || problem.code == "EXAM_AUTOSAVE_VERSION_CONFLICT"
                || problem.code == "EXAM_IDEMPOTENCY_KEY_REUSED"
                || problem.code == "EXAM_SECTION_LOCKED" {
                return .autosaveVersionConflict
            }
            if problem.code == "EXAM_SCRATCHPAD_STORAGE_KEY_FORBIDDEN"
                || problem.code == "EXAM_SCRATCHPAD_INVALID_ASSET"
                || problem.code == "EXAM_ITEM_INVALID_ANSWER_PAYLOAD"
                || problem.code == "EXAM_SCRATCHPAD_LIMIT_EXCEEDED" {
                return .invalidScratchpadAsset
            }
            if problem.status == 503
                || problem.code == "EXAM_FEATURE_DISABLED"
                || problem.code == "EXAM_QUESTION_BANK_DISABLED"
                || problem.code == "EXAM_BLUEPRINT_DISABLED"
                || problem.code == "EXAM_ASSESSMENT_ASSEMBLY_DISABLED"
                || problem.code == "EXAM_SESSION_RUNTIME_FEATURE_DISABLED" {
                return .featureDisabled
            }
            if problem.status == 403
                || problem.code == "EXAM_ACCESS_DENIED"
                || problem.code == "CANDIDATE_PROFILE_ACCESS_DENIED"
                || problem.code == "EXAM_CANDIDATE_ACCESS_DENIED"
                || problem.code == "EXAM_SESSION_ACCESS_DENIED" {
                return .forbidden
            }
            if problem.status == 404
                || problem.code == "EXAM_DEFINITION_NOT_FOUND"
                || problem.code == "EXAM_VERSION_NOT_FOUND"
                || problem.code == "EXAM_TARGET_NOT_FOUND"
                || problem.code == "CANDIDATE_PROFILE_NOT_FOUND"
                || problem.code == "SYLLABUS_UPLOAD_NOT_FOUND"
                || problem.code == "EXAM_QUESTION_ITEM_NOT_FOUND"
                || problem.code == "EXAM_BLUEPRINT_NOT_FOUND"
                || problem.code == "EXAM_ASSEMBLY_NOT_FOUND"
                || problem.code == "EXAM_ASSEMBLED_ASSESSMENT_NOT_FOUND"
                || problem.code == "EXAM_SESSION_NOT_FOUND"
                || problem.code == "EXAM_ITEM_NOT_FOUND"
                || problem.code == "EXAM_SESSION_ITEM_NOT_FOUND" {
                return .notFound
            }
            if problem.status == 409
                || problem.code == "EXAM_VERSION_CONFLICT"
                || problem.code == "EXAM_BLUEPRINT_VERSION_MISMATCH"
                || problem.code == "EXAM_QUESTION_CODE_CONFLICT"
                || problem.code == "EXAM_BLUEPRINT_CODE_CONFLICT" {
                return .versionConflict
            }
            if problem.code == "EXAM_QUESTION_POOL_INSUFFICIENT"
                || problem.code == "EXAM_ASSEMBLY_DIFFICULTY_TOLERANCE_EXCEEDED"
                || problem.code == "EXAM_BLUEPRINT_DISTRIBUTION_INVALID"
                || problem.code == "EXAM_QUESTION_PROVENANCE_UNVERIFIED" {
                return .poolInsufficient
            }
            if problem.code == "EXAM_TARGET_SCORE_OUT_OF_RANGE" {
                return .scoreOutOfRange
            }
            if problem.code == "CANDIDATE_EXAM_DATE_IN_PAST" {
                return .dateInPast
            }
            if problem.code == "CANDIDATE_STUDY_BUDGET_INVALID" {
                return .studyBudgetInvalid
            }
            if problem.code == "CANDIDATE_ACCOMMODATION_INVALID" {
                return .accommodationInvalid
            }
            if problem.status == 429 || problem.code == "SYLLABUS_QUOTA_EXCEEDED" {
                return .quotaExceeded
            }
            if problem.status == 413 || problem.status == 415 || problem.code == "SYLLABUS_MEDIA_UNSUPPORTED" {
                return .mediaUnsupported
            }
            if problem.code == "SYLLABUS_DISAMBIGUATION_REQUIRED" {
                return .disambiguationRequired
            }
            return .unexpected(problem.title)
        case .httpStatus(503):
            return .featureDisabled
        case .httpStatus(403):
            return .forbidden
        case .httpStatus(404):
            return .notFound
        case .httpStatus(409):
            return .versionConflict
        case .httpStatus(413), .httpStatus(415):
            return .mediaUnsupported
        case .httpStatus(429):
            return .quotaExceeded
        case .transport:
            return .offline
        case .invalidURL, .invalidResponse, .httpStatus, .decoding:
            return .unexpected(String(describing: error))
        }
    }
}

#if DEBUG
final class FixtureExamAPIService: ExamAPIServicing, @unchecked Sendable {
    var simulateOffline: Bool
    private let userId: UUID
    private var storedTargets: [StudentExamTargetModel] = []
    private var storedProfile: CandidateProfileResponse?
    private var storedHistory: [CandidateHorizonHistoryEntry] = []
    private var storedUploads: [SyllabusIngestionResponse] = []
    private var storedAssembledAssessments: [AssembledAssessmentDTO] = []
    private var storedSessions: [UUID: ExamSessionDTO] = [:]
    private var autosaveIdempotencyCache: [String: ExamSessionItemAutosaveResponseDTO] = [:]
    private var scratchpadIdempotencyCache: [String: ExamScratchpadUploadResponseDTO] = [:]
    private var submitIdempotencyCache: [String: ExamSessionSubmitResponseDTO] = [:]

    init(simulateOffline: Bool = false, userId: UUID = UUID(uuidString: "91000000-0000-4000-8000-000000000a01")!) {
        self.simulateOffline = simulateOffline
        self.userId = userId
    }

    func fetchDefinitions(
        jurisdiction: ExamJurisdiction?,
        examFamily: ExamFamily?,
        includeDeprecated: Bool
    ) async throws -> ExamDefinitionCatalogResponse {
        if simulateOffline { throw ExamServiceError.offline }
        let all = Self.seedDefinitions()
        let filtered = all.filter { def in
            (jurisdiction == nil || def.jurisdiction == jurisdiction) &&
            (examFamily == nil || def.examFamily == examFamily) &&
            (includeDeprecated || def.status == .active)
        }
        let summaries = filtered.map { def in
            ExamDefinitionSummaryModel(
                examDefinitionId: def.examDefinitionId,
                examCode: def.examCode,
                examVersion: def.examVersion,
                jurisdiction: def.jurisdiction,
                examFamily: def.examFamily,
                titleEn: def.titleEn,
                titleTr: def.titleTr,
                status: def.status,
                totalQuestions: def.totalQuestions,
                durationMinutes: def.durationMinutes,
                scoringFormulaType: def.scoringFormulaType,
                wrongPenaltyFactor: def.wrongPenaltyFactor,
                minScore: def.minScore,
                maxScore: def.maxScore,
                navigationPolicy: def.navigationPolicy,
                blueprintHash: def.blueprintHash,
                effectiveFrom: def.effectiveFrom,
                deprecatedAt: def.deprecatedAt,
                supersededByVersion: def.supersededByVersion,
                sectionCount: def.sections.count,
                topicWeightCount: def.topicWeights.count,
                trainingEligible: false
            )
        }
        return ExamDefinitionCatalogResponse(
            schemaVersion: "exam-definition-catalog-v1",
            definitions: summaries,
            totalCount: summaries.count,
            includeDeprecated: includeDeprecated,
            trainingEligible: false,
            llmTokensUsed: 0
        )
    }

    func fetchDefinitionDetail(examCode: String) async throws -> ExamDefinitionModel {
        if simulateOffline { throw ExamServiceError.offline }
        guard let match = Self.seedDefinitions().first(where: { $0.examCode == examCode && $0.status == .active }) else {
            throw ExamServiceError.notFound
        }
        return match
    }

    func fetchDefinitionVersion(examCode: String, examVersion: String) async throws -> ExamDefinitionModel {
        if simulateOffline { throw ExamServiceError.offline }
        guard let match = Self.seedDefinitions().first(where: { $0.examCode == examCode && $0.examVersion == examVersion }) else {
            throw ExamServiceError.notFound
        }
        return match
    }

    func upsertTarget(request: UpsertStudentExamTargetRequestDTO) async throws -> StudentExamTargetModel {
        if simulateOffline { throw ExamServiceError.offline }
        let def = try await fetchDefinitionDetail(examCode: request.examCode)
        guard request.targetScore >= def.minScore && request.targetScore <= def.maxScore else {
            throw ExamServiceError.scoreOutOfRange
        }
        let baselineFloor = max(0.0, def.minScore)
        let estimated = (baselineFloor + 0.5 * (def.maxScore - baselineFloor))
        let snapshot = PinnedBlueprintSnapshot(
            examDefinitionId: def.examDefinitionId,
            examCode: def.examCode,
            examVersion: def.examVersion,
            jurisdiction: def.jurisdiction,
            examFamily: def.examFamily,
            titleEn: def.titleEn,
            titleTr: def.titleTr,
            totalQuestions: def.totalQuestions,
            durationMinutes: def.durationMinutes,
            scoringFormulaType: def.scoringFormulaType,
            wrongPenaltyFactor: def.wrongPenaltyFactor,
            minScore: def.minScore,
            maxScore: def.maxScore,
            navigationPolicy: def.navigationPolicy,
            blueprintHash: def.blueprintHash,
            effectiveFrom: def.effectiveFrom,
            sections: def.sections,
            scoringParameters: def.scoringParameters,
            topicWeights: def.topicWeights,
            trainingEligible: false
        )
        let now = "2026-11-14T10:00:00Z"
        let created = StudentExamTargetModel(
            schemaVersion: "student-exam-target-v1",
            targetId: UUID(),
            userId: userId,
            examDefinitionId: def.examDefinitionId,
            examCode: def.examCode,
            examVersion: def.examVersion,
            blueprintHash: def.blueprintHash,
            targetScore: request.targetScore,
            targetDate: request.targetDate,
            priorityRank: request.priorityRank,
            targetStatus: .active,
            isPrimary: request.isPrimary,
            idempotencyKey: request.idempotencyKey,
            readinessGapPreview: ReadinessGapPreview(
                schemaVersion: "readiness-gap-preview-v1",
                currentEstimatedScore: estimated,
                targetScore: request.targetScore,
                gapPoints: request.targetScore - estimated,
                estimationBasis: "BLUEPRINT_MIDPOINT_PRELIMINARY",
                llmTokensUsed: 0,
                trainingEligible: false
            ),
            pinnedBlueprint: snapshot,
            supersededAt: nil,
            idempotentReplay: false,
            llmTokensUsed: 0,
            trainingEligible: false,
            createdAt: now,
            updatedAt: now
        )
        storedTargets = [created]
        return created
    }

    func listTargets() async throws -> StudentExamTargetListResponse {
        if simulateOffline { throw ExamServiceError.offline }
        return StudentExamTargetListResponse(
            schemaVersion: "student-exam-target-list-v1",
            userId: userId,
            targets: storedTargets,
            totalCount: storedTargets.count,
            llmTokensUsed: 0,
            trainingEligible: false
        )
    }

    func upsertCandidateProfile(_ request: UpsertCandidateProfileRequest, idempotencyKey: String) async throws -> CandidateProfileResponse {
        if simulateOffline { throw ExamServiceError.offline }
        let def = try await fetchDefinitionDetail(examCode: request.examCode)
        guard request.targetScore >= def.minScore && request.targetScore <= def.maxScore else {
            throw ExamServiceError.scoreOutOfRange
        }
        guard [1.0, 1.25, 1.5, 2.0].contains(request.accommodations.extraTimeMultiplier) else {
            throw ExamServiceError.accommodationInvalid
        }
        guard request.dailyStudyMinutes >= 15, request.dailyStudyMinutes <= 960,
              request.weeklyStudyMinutes >= request.dailyStudyMinutes, request.weeklyStudyMinutes <= 6720 else {
            throw ExamServiceError.studyBudgetInvalid
        }

        let df = DateFormatter()
        df.calendar = Calendar(identifier: .gregorian)
        df.locale = Locale(identifier: "en_US_POSIX")
        df.dateFormat = "yyyy-MM-dd"
        let today = Calendar.current.startOfDay(for: Date())
        let parsedDate = df.date(from: request.targetDate) ?? today
        guard parsedDate > today else {
            throw ExamServiceError.dateInPast
        }

        let days = max(1, Calendar.current.dateComponents([.day], from: today, to: parsedDate).day ?? 1)
        let mastery = request.baselineMastery ?? 0.25
        let requiredDaily = Int(ceil((Double(def.totalQuestions) * (1.0 - mastery) * 90.0) / Double(days)))
        let effectiveDaily = max(1, Int((min(Double(request.dailyStudyMinutes), Double(request.weeklyStudyMinutes) / 7.0)).rounded()))
        let ratio = Double(requiredDaily) / Double(effectiveDaily)
        let feasibility: CandidateFeasibilityStatus = if ratio <= 0.85 {
            .optimal
        } else if ratio <= 1.15 {
            .stretch
        } else if ratio <= 1.50 {
            .atRisk
        } else {
            .unrealistic
        }

        let adjusted = def.sections.map { s in
            AdjustedExamSection(
                sectionCode: s.sectionCode,
                titleEn: s.titleEn,
                titleTr: s.titleTr,
                questionCount: s.questionCount,
                baseDurationMinutes: s.durationMinutes,
                adjustedDurationMinutes: Int(ceil(Double(s.durationMinutes) * request.accommodations.extraTimeMultiplier)),
                order: s.order
            )
        }

        let profileId = storedProfile?.profileId ?? UUID().uuidString.lowercased()
        let now = "2026-11-15T10:00:00Z"
        let profile = CandidateProfileResponse(
            schemaVersion: "candidate-profile-v1",
            profileId: profileId,
            userId: userId.uuidString.lowercased(),
            activeExamDefinitionId: def.examDefinitionId.uuidString.lowercased(),
            activeExamCode: def.examCode,
            pinnedExamVersion: def.examVersion,
            blueprintHash: def.blueprintHash,
            activeTargetId: storedTargets.first?.targetId.uuidString.lowercased(),
            jurisdiction: def.jurisdiction,
            targetDate: request.targetDate,
            targetScore: request.targetScore,
            dailyStudyMinutes: request.dailyStudyMinutes,
            weeklyStudyMinutes: request.weeklyStudyMinutes,
            accommodations: request.accommodations,
            baselineMastery: mastery,
            daysUntilExam: days,
            effectiveDailyCapacityMinutes: effectiveDaily,
            requiredDailyMinutes: requiredDaily,
            feasibilityStatus: feasibility,
            adjustedSections: adjusted,
            idempotentReplay: false,
            llmTokensUsed: 0,
            trainingEligible: false,
            createdAt: storedProfile?.createdAt ?? now,
            updatedAt: now
        )
        let prev = storedProfile
        storedProfile = profile
        storedHistory.insert(
            CandidateHorizonHistoryEntry(
                historyId: UUID().uuidString.lowercased(),
                profileId: profileId,
                examCode: def.examCode,
                pinnedExamVersion: def.examVersion,
                previousTargetDate: prev?.targetDate,
                newTargetDate: profile.targetDate,
                previousDailyMinutes: prev?.dailyStudyMinutes,
                newDailyMinutes: profile.dailyStudyMinutes,
                previousWeeklyMinutes: prev?.weeklyStudyMinutes,
                newWeeklyMinutes: profile.weeklyStudyMinutes,
                previousTargetScore: prev?.targetScore,
                newTargetScore: profile.targetScore,
                extraTimeMultiplier: request.accommodations.extraTimeMultiplier,
                requiredDailyMinutes: requiredDaily,
                feasibilityStatus: feasibility,
                changeReason: request.changeReason ?? (prev == nil ? "INITIAL_SETUP" : "HORIZON_UPDATE"),
                changedAt: now
            ),
            at: 0
        )
        return profile
    }

    func fetchCandidateProfile() async throws -> CandidateProfileResponse {
        if simulateOffline { throw ExamServiceError.offline }
        if let storedProfile {
            return storedProfile
        }
        throw ExamServiceError.notFound
    }

    func fetchCandidateProfileHistory() async throws -> CandidateProfileHistoryResponse {
        if simulateOffline { throw ExamServiceError.offline }
        return CandidateProfileHistoryResponse(
            schemaVersion: "candidate-profile-history-v1",
            userId: userId.uuidString.lowercased(),
            items: storedHistory,
            llmTokensUsed: 0,
            trainingEligible: false
        )
    }

    func ingestSyllabus(
        fileData: Data,
        mimeType: String,
        filename: String,
        examCode: String,
        examVersion: String?,
        idempotencyKey: String
    ) async throws -> SyllabusIngestionResponse {
        if simulateOffline { throw ExamServiceError.offline }
        guard !fileData.isEmpty, fileData.count <= 10 * 1024 * 1024 else {
            throw ExamServiceError.mediaUnsupported
        }
        let def = try await fetchDefinitionDetail(examCode: examCode)
        let digest = SHA256.hash(data: fileData).map { String(format: "%02x", $0) }.joined()

        if let cached = storedUploads.first(where: { $0.imageHash == digest }) {
            let remaining = max(0, 3 - storedUploads.count)
            return SyllabusIngestionResponse(
                schemaVersion: "syllabus-ingestion-v1",
                uploadId: cached.uploadId,
                userId: cached.userId,
                examDefinitionId: cached.examDefinitionId,
                examCode: cached.examCode,
                examVersion: cached.examVersion,
                imageHash: cached.imageHash,
                mimeType: cached.mimeType,
                byteSize: cached.byteSize,
                status: cached.status,
                deduplicatedFromCache: true,
                idempotentReplay: false,
                remainingMonthlyQuota: remaining,
                parsedTopics: cached.parsedTopics,
                llmTokensUsed: 0,
                trainingEligible: false,
                expiresAt: cached.expiresAt,
                confirmedAt: cached.confirmedAt,
                createdAt: cached.createdAt,
                updatedAt: cached.updatedAt
            )
        }

        if storedUploads.count >= 3 {
            throw ExamServiceError.quotaExceeded
        }

        let isAmbiguous = filename.lowercased().contains("ambiguous")
            || String(decoding: fileData, as: UTF8.self).contains("AMBIGUOUS_SYLLABUS")

        let topics: [ParsedSyllabusTopicItem] = isAmbiguous
            ? [
                ParsedSyllabusTopicItem(
                    itemIndex: 0,
                    rawTopicLabel: "Tam Sayılarla İşlemler",
                    targetWeek: 1,
                    targetMonth: "October",
                    extractionConfidence: 0.94,
                    matchedSkillId: "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                    matchedTopicId: "MATH.ARITHMETIC",
                    similarityScore: 0.89,
                    autoConfirmed: true,
                    userConfirmed: false,
                    candidates: [DisambiguationCandidate(skillId: "MATH.ARITHMETIC.INTEGER_OPERATIONS", topicId: "MATH.ARITHMETIC", similarityScore: 0.89)]
                ),
                ParsedSyllabusTopicItem(
                    itemIndex: 1,
                    rawTopicLabel: "Genel Karma Tekrar ve Hızlandırma Programı",
                    targetWeek: 2,
                    targetMonth: "November",
                    extractionConfidence: 0.58,
                    matchedSkillId: nil,
                    matchedTopicId: nil,
                    similarityScore: 0.48,
                    autoConfirmed: false,
                    userConfirmed: false,
                    candidates: [
                        DisambiguationCandidate(skillId: "MATH.EQUATIONS.LINEAR_ONE_VARIABLE", topicId: "MATH.EQUATIONS", similarityScore: 0.48),
                        DisambiguationCandidate(skillId: "MATH.EQUATIONS.QUADRATIC_SOLVING", topicId: "MATH.EQUATIONS", similarityScore: 0.41)
                    ]
                )
            ]
            : [
                ParsedSyllabusTopicItem(
                    itemIndex: 0,
                    rawTopicLabel: "Tam Sayılarla İşlemler",
                    targetWeek: 1,
                    targetMonth: "October",
                    extractionConfidence: 0.96,
                    matchedSkillId: "MATH.ARITHMETIC.INTEGER_OPERATIONS",
                    matchedTopicId: "MATH.ARITHMETIC",
                    similarityScore: 0.92,
                    autoConfirmed: true,
                    userConfirmed: false,
                    candidates: [DisambiguationCandidate(skillId: "MATH.ARITHMETIC.INTEGER_OPERATIONS", topicId: "MATH.ARITHMETIC", similarityScore: 0.92)]
                )
            ]

        let now = "2026-11-15T10:00:00Z"
        let created = SyllabusIngestionResponse(
            schemaVersion: "syllabus-ingestion-v1",
            uploadId: UUID().uuidString.lowercased(),
            userId: userId.uuidString.lowercased(),
            examDefinitionId: def.examDefinitionId.uuidString.lowercased(),
            examCode: def.examCode,
            examVersion: def.examVersion,
            imageHash: digest,
            mimeType: mimeType,
            byteSize: fileData.count,
            status: isAmbiguous ? .needsDisambiguation : .parsedAutoConfirmed,
            deduplicatedFromCache: false,
            idempotentReplay: false,
            remainingMonthlyQuota: max(0, 3 - (storedUploads.count + 1)),
            parsedTopics: topics,
            llmTokensUsed: 0,
            trainingEligible: false,
            expiresAt: "2026-12-15T10:00:00Z",
            confirmedAt: nil,
            createdAt: now,
            updatedAt: now
        )
        storedUploads.insert(created, at: 0)
        return created
    }

    func confirmSyllabusDisambiguation(
        uploadId: String,
        mappings: [SyllabusMappingConfirmation]
    ) async throws -> SyllabusIngestionResponse {
        if simulateOffline { throw ExamServiceError.offline }
        guard let idx = storedUploads.firstIndex(where: { $0.uploadId == uploadId }) else {
            throw ExamServiceError.notFound
        }
        let existing = storedUploads[idx]
        let overrideMap = Dictionary(uniqueKeysWithValues: mappings.map { ($0.itemIndex, $0.confirmedSkillId) })
        let updatedTopics = existing.parsedTopics.map { item in
            let finalSkill = overrideMap[item.itemIndex] ?? item.matchedSkillId ?? "MATH.ARITHMETIC.INTEGER_OPERATIONS"
            return ParsedSyllabusTopicItem(
                itemIndex: item.itemIndex,
                rawTopicLabel: item.rawTopicLabel,
                targetWeek: item.targetWeek,
                targetMonth: item.targetMonth,
                extractionConfidence: item.extractionConfidence,
                matchedSkillId: finalSkill,
                matchedTopicId: "MATH.EQUATIONS",
                similarityScore: item.similarityScore,
                autoConfirmed: item.autoConfirmed,
                userConfirmed: true,
                candidates: item.candidates
            )
        }
        let now = "2026-11-15T10:05:00Z"
        let confirmed = SyllabusIngestionResponse(
            schemaVersion: existing.schemaVersion,
            uploadId: existing.uploadId,
            userId: existing.userId,
            examDefinitionId: existing.examDefinitionId,
            examCode: existing.examCode,
            examVersion: existing.examVersion,
            imageHash: existing.imageHash,
            mimeType: existing.mimeType,
            byteSize: existing.byteSize,
            status: .userConfirmed,
            deduplicatedFromCache: existing.deduplicatedFromCache,
            idempotentReplay: false,
            remainingMonthlyQuota: existing.remainingMonthlyQuota,
            parsedTopics: updatedTopics,
            llmTokensUsed: 0,
            trainingEligible: false,
            expiresAt: existing.expiresAt,
            confirmedAt: now,
            createdAt: existing.createdAt,
            updatedAt: now
        )
        storedUploads[idx] = confirmed
        return confirmed
    }

    func fetchBlueprints(examDefinitionId: UUID, examVersionNumber: Int? = nil) async throws -> [ExamBlueprintDTO] {
        if simulateOffline { throw ExamServiceError.offline }
        let all = Self.seedBlueprints()
        return all.filter { bp in
            bp.examDefinitionId == examDefinitionId &&
            (examVersionNumber == nil || bp.examVersionNumber == examVersionNumber) &&
            bp.status == .active
        }
    }

    func assembleAssessment(examDefinitionId: UUID, request: AssessmentAssemblyRequestDTO) async throws -> AssembledAssessmentDTO {
        if simulateOffline { throw ExamServiceError.offline }
        guard request.assemblySeed.trimmingCharacters(in: .whitespacesAndNewlines).count >= 4 else {
            throw ExamServiceError.unexpected("assemblySeed must be at least 4 characters")
        }
        guard let blueprint = Self.seedBlueprints().first(where: { $0.id == request.blueprintId }) else {
            throw ExamServiceError.notFound
        }
        if let existing = storedAssembledAssessments.first(where: {
            $0.userId == request.userId && $0.blueprintId == request.blueprintId && $0.assemblySeed == request.assemblySeed
        }) {
            return existing
        }

        let multiplier = request.overrideTimeMultiplier
            ?? storedProfile?.accommodations.extraTimeMultiplier
            ?? 1.00
        guard [1.00, 1.25, 1.50, 2.00].contains(multiplier) else {
            throw ExamServiceError.accommodationInvalid
        }
        let effectiveDurationSeconds = Int(ceil(Double(blueprint.baseDurationSeconds) * multiplier))
        let accommodations = AppliedAccommodationSnapshotDTO(
            extraTimeMultiplier: multiplier,
            highContrastMode: request.highContrastMode || (storedProfile?.accommodations.highContrastEnabled ?? false),
            screenReaderOptimized: request.screenReaderOptimized || (storedProfile?.accommodations.screenReaderOptimized ?? false),
            extendedBreakPrompts: request.extendedBreakPrompts || multiplier > 1.0
        )

        let baseItems = Self.buildFixtureItems(for: blueprint, seed: request.assemblySeed)
        let canonicalPayload = "\(blueprint.id.uuidString.lowercased())|\(blueprint.blueprintHash)|\(request.assemblySeed)|\(String(format: "%.2f", multiplier))|" +
            baseItems.map { "\($0.sequenceNumber):\($0.questionItemId.uuidString.lowercased()):\($0.optionOrder.joined(separator: ","))" }.joined(separator: ";")
        let reproHash = SHA256.hash(data: Data(canonicalPayload.utf8)).map { String(format: "%02x", $0) }.joined()

        var topicCounts: [String: Int] = [:]
        var diffCounts: [String: Int] = ["EASY": 0, "MEDIUM": 0, "HARD": 0, "EXPERT": 0]
        var cogCounts: [String: Int] = [:]
        for item in baseItems {
            topicCounts[item.topicCode, default: 0] += 1
            diffCounts[item.difficultyBand.rawValue, default: 0] += 1
            cogCounts[item.cognitiveLevel.rawValue, default: 0] += 1
        }
        let total = max(1, baseItems.count)
        let summary = AssemblyDistributionSummaryDTO(
            topicCounts: topicCounts,
            difficultyCounts: diffCounts,
            cognitiveCounts: cogCounts,
            easyActualBps: (diffCounts["EASY", default: 0] * 10000) / total,
            mediumActualBps: (diffCounts["MEDIUM", default: 0] * 10000) / total,
            hardActualBps: (diffCounts["HARD", default: 0] * 10000) / total,
            expertActualBps: (diffCounts["EXPERT", default: 0] * 10000) / total
        )

        let assembled = AssembledAssessmentDTO(
            id: UUID(),
            userId: request.userId,
            candidateProfileId: request.candidateProfileId,
            blueprintId: blueprint.id,
            blueprintCode: blueprint.blueprintCode,
            blueprintTitle: blueprint.title,
            blueprintMode: blueprint.blueprintMode,
            examDefinitionId: blueprint.examDefinitionId,
            examVersionNumber: blueprint.examVersionNumber,
            assemblySeed: request.assemblySeed,
            reproducibilityHash: reproHash,
            totalQuestionCount: baseItems.count,
            baseDurationSeconds: blueprint.baseDurationSeconds,
            timeMultiplier: multiplier,
            effectiveDurationSeconds: effectiveDurationSeconds,
            accommodationsSnapshot: accommodations,
            distributionSummary: summary,
            status: .ready,
            assembledAt: "2026-11-15T12:00:00Z",
            expiresAt: "2026-11-22T12:00:00Z",
            items: baseItems,
            trainingEligible: false
        )
        storedAssembledAssessments.insert(assembled, at: 0)
        return assembled
    }

    func fetchAssembledAssessment(userId: UUID, assessmentId: UUID) async throws -> AssembledAssessmentDTO {
        if simulateOffline { throw ExamServiceError.offline }
        guard let found = storedAssembledAssessments.first(where: { $0.id == assessmentId }) else {
            throw ExamServiceError.notFound
        }
        guard found.userId == userId else {
            throw ExamServiceError.forbidden
        }
        return found
    }

    func fetchAssembledAssessments(userId: UUID, examDefinitionId: UUID? = nil) async throws -> [AssembledAssessmentDTO] {
        if simulateOffline { throw ExamServiceError.offline }
        return storedAssembledAssessments.filter { a in
            a.userId == userId && (examDefinitionId == nil || a.examDefinitionId == examDefinitionId)
        }
    }

    // MARK: - Sprint 9.4 Timed Mock Exam Runtime Fixtures

    func startOrResumeSession(request: ExamSessionStartRequestDTO) async throws -> ExamSessionDTO {
        if simulateOffline { throw ExamServiceError.offline }
        let effectiveUserId = request.userId ?? userId
        if let existing = storedSessions.values.first(where: {
            $0.userId == effectiveUserId && $0.assemblyId == request.assemblyId && $0.status.isActive
        }) {
            return existing
        }

        let assembled = storedAssembledAssessments.first(where: { $0.id == request.assemblyId })
            ?? Self.fallbackAssembledAssessment(userId: effectiveUserId, assemblyId: request.assemblyId)

        let now = Date()
        let formatter = ISO8601DateFormatter()
        let graceSeconds = min(max(request.offlineGraceBufferSeconds ?? 120, 0), 120)
        let deadline = now.addingTimeInterval(TimeInterval(assembled.effectiveDurationSeconds))
        let graceDeadline = deadline.addingTimeInterval(TimeInterval(graceSeconds))
        let nowString = formatter.string(from: now)
        let sessionId = UUID()

        let sessionItems: [ExamSessionItemDTO] = assembled.items.map { item in
            ExamSessionItemDTO(
                id: item.id,
                itemPosition: item.sequenceNumber,
                questionItemId: item.questionItemId,
                questionCode: item.questionCode,
                sectionCode: "MAIN",
                sectionOrder: 1,
                topicCode: item.topicCode,
                itemFormat: item.itemFormat.rawValue,
                difficultyBand: item.difficultyBand,
                stemMarkdown: item.stemMarkdown,
                optionsSnapshot: item.options,
                figureAltText: item.altTextDescription,
                maxPoints: item.allocatedPoints,
                negativeMarkingWeight: 0.25,
                responseState: .unvisited,
                selectedOptionKey: nil,
                numericResponseValue: nil,
                flaggedForReview: false,
                eliminatedOptionKeys: [],
                timeSpentSeconds: 0,
                autosaveVersion: 1,
                clientAutosaveSeq: 0,
                lastAutosavedAt: nil,
                scratchpadRequired: true,
                scratchpadAssets: [],
                trainingEligible: false
            )
        }

        let clockSync = ExamServerClockSyncDTO(
            serverNow: nowString,
            remainingSeconds: assembled.effectiveDurationSeconds,
            graceRemainingSeconds: graceSeconds,
            clientClockSkewMs: 0,
            clockSkewAnomaly: false,
            deadlineExpired: false,
            offlineGraceActive: false
        )

        let sectionProgress = [
            ExamSectionProgressSummaryDTO(
                sectionCode: "MAIN",
                sectionOrder: 1,
                totalItems: sessionItems.count,
                answeredItems: 0,
                flaggedItems: 0,
                omittedItems: sessionItems.count,
                locked: false
            )
        ]

        let session = ExamSessionDTO(
            sessionId: sessionId,
            userId: effectiveUserId,
            assemblyId: assembled.id,
            blueprintId: assembled.blueprintId,
            examCode: "TR_YKS_TYT_MATH",
            examVersionNumber: assembled.examVersionNumber,
            status: .inProgress,
            navigationPolicy: "FREE_NAVIGATION",
            noHintPolicyEnforced: true,
            accommodationsSnapshot: assembled.accommodationsSnapshot,
            baseDurationSeconds: assembled.baseDurationSeconds,
            extraTimeMultiplier: assembled.timeMultiplier,
            effectiveDurationSeconds: assembled.effectiveDurationSeconds,
            offlineGraceBufferSeconds: graceSeconds,
            startedAt: nowString,
            serverDeadlineAt: formatter.string(from: deadline),
            graceDeadlineAt: formatter.string(from: graceDeadline),
            lastHeartbeatAt: nowString,
            submittedAt: nil,
            submissionReason: nil,
            totalItemCount: sessionItems.count,
            answeredItemCount: 0,
            flaggedItemCount: 0,
            omittedItemCount: sessionItems.count,
            scratchpadAttachmentCount: 0,
            clockSync: clockSync,
            sectionProgress: sectionProgress,
            items: sessionItems,
            f28VerificationBatchManifest: nil,
            llmTokensUsed: 0,
            trainingEligible: false
        )
        storedSessions[sessionId] = session
        return session
    }

    func fetchSessionState(sessionId: UUID, clientTimestamp: String? = nil) async throws -> ExamSessionDTO {
        if simulateOffline { throw ExamServiceError.offline }
        guard var session = storedSessions[sessionId] else {
            throw ExamServiceError.notFound
        }
        let now = Date()
        let formatter = ISO8601DateFormatter()
        let deadlineDate = formatter.date(from: session.serverDeadlineAt) ?? now.addingTimeInterval(60)
        let graceDate = formatter.date(from: session.graceDeadlineAt) ?? deadlineDate.addingTimeInterval(TimeInterval(session.offlineGraceBufferSeconds))
        let remaining = max(0, Int(deadlineDate.timeIntervalSince(now)))
        let graceRemaining = max(0, Int(graceDate.timeIntervalSince(now)))
        let nowString = formatter.string(from: now)
        session.lastHeartbeatAt = nowString
        session.clockSync = ExamServerClockSyncDTO(
            serverNow: nowString,
            remainingSeconds: remaining,
            graceRemainingSeconds: graceRemaining,
            clientClockSkewMs: 0,
            clockSkewAnomaly: false,
            deadlineExpired: remaining == 0,
            offlineGraceActive: remaining == 0 && graceRemaining > 0
        )
        storedSessions[sessionId] = session
        return session
    }

    func autosaveSessionItem(
        sessionId: UUID,
        itemPosition: Int,
        idempotencyKey: String,
        request: ExamSessionItemAutosaveRequestDTO
    ) async throws -> ExamSessionItemAutosaveResponseDTO {
        if simulateOffline { throw ExamServiceError.offline }
        let cacheKey = "\(sessionId.uuidString)::\(itemPosition)::\(idempotencyKey)"
        if let cached = autosaveIdempotencyCache[cacheKey] {
            return cached
        }
        guard var session = storedSessions[sessionId] else {
            throw ExamServiceError.notFound
        }
        guard session.status.isActive else {
            throw ExamServiceError.sessionAlreadySubmitted
        }
        guard let idx = session.items.firstIndex(where: { $0.itemPosition == itemPosition }) else {
            throw ExamServiceError.notFound
        }
        var item = session.items[idx]
        if item.autosaveVersion != request.expectedAutosaveVersion {
            throw ExamServiceError.autosaveVersionConflict
        }

        switch request.action {
        case .viewOnly:
            if item.responseState == .unvisited {
                item.responseState = .viewedOmitted
            }
        case .selectOption:
            item.selectedOptionKey = request.selectedOptionKey
            item.numericResponseValue = request.numericResponseValue
        case .clearAnswer:
            item.selectedOptionKey = nil
            item.numericResponseValue = nil
        case .toggleFlag:
            item.flaggedForReview = request.flaggedForReview ?? !item.flaggedForReview
        case .setEliminatedOptions:
            item.eliminatedOptionKeys = request.eliminatedOptionKeys ?? []
        case .batchPatch:
            if let key = request.selectedOptionKey { item.selectedOptionKey = key }
            if let num = request.numericResponseValue { item.numericResponseValue = num }
            if let flag = request.flaggedForReview { item.flaggedForReview = flag }
            if let elim = request.eliminatedOptionKeys { item.eliminatedOptionKeys = elim }
        }

        let hasAnswer = (item.selectedOptionKey != nil && !item.selectedOptionKey!.isEmpty) || item.numericResponseValue != nil
        if item.flaggedForReview {
            item.responseState = hasAnswer ? .flaggedAnswered : .flaggedOmitted
        } else if hasAnswer {
            item.responseState = .answered
        } else if item.responseState != .unvisited {
            item.responseState = .viewedOmitted
        }

        let nowString = ISO8601DateFormatter().string(from: Date())
        item.timeSpentSeconds += request.deltaTimeSpentSeconds
        item.autosaveVersion += 1
        item.clientAutosaveSeq = max(item.clientAutosaveSeq, request.clientAutosaveSeq)
        item.lastAutosavedAt = nowString
        session.items[idx] = item

        session.answeredItemCount = session.items.filter {
            $0.responseState == .answered || $0.responseState == .flaggedAnswered
        }.count
        session.flaggedItemCount = session.items.filter { $0.flaggedForReview }.count
        session.omittedItemCount = session.totalItemCount - session.answeredItemCount
        session.sectionProgress = [
            ExamSectionProgressSummaryDTO(
                sectionCode: "MAIN",
                sectionOrder: 1,
                totalItems: session.totalItemCount,
                answeredItems: session.answeredItemCount,
                flaggedItems: session.flaggedItemCount,
                omittedItems: session.omittedItemCount,
                locked: false
            )
        ]
        storedSessions[sessionId] = session

        let response = ExamSessionItemAutosaveResponseDTO(
            sessionId: session.sessionId,
            status: session.status,
            itemPosition: item.itemPosition,
            questionCode: item.questionCode,
            responseState: item.responseState,
            selectedOptionKey: item.selectedOptionKey,
            numericResponseValue: item.numericResponseValue,
            flaggedForReview: item.flaggedForReview,
            eliminatedOptionKeys: item.eliminatedOptionKeys,
            timeSpentSeconds: item.timeSpentSeconds,
            autosaveVersion: item.autosaveVersion,
            clientAutosaveSeq: item.clientAutosaveSeq,
            answeredItemCount: session.answeredItemCount,
            flaggedItemCount: session.flaggedItemCount,
            omittedItemCount: session.omittedItemCount,
            clockSync: session.clockSync,
            llmTokensUsed: 0,
            trainingEligible: false
        )
        autosaveIdempotencyCache[cacheKey] = response
        return response
    }

    func attachScratchpadPhoto(
        sessionId: UUID,
        itemPosition: Int,
        idempotencyKey: String,
        request: ExamScratchpadUploadRequestDTO
    ) async throws -> ExamScratchpadUploadResponseDTO {
        if simulateOffline { throw ExamServiceError.offline }
        let cacheKey = "\(sessionId.uuidString)::\(itemPosition)::scratch::\(idempotencyKey)"
        if let cached = scratchpadIdempotencyCache[cacheKey] {
            return cached
        }
        guard var session = storedSessions[sessionId] else {
            throw ExamServiceError.notFound
        }
        guard session.status.isActive else {
            throw ExamServiceError.sessionAlreadySubmitted
        }
        guard let idx = session.items.firstIndex(where: { $0.itemPosition == itemPosition }) else {
            throw ExamServiceError.notFound
        }
        var item = session.items[idx]
        guard item.scratchpadAssets.count < 4 else {
            throw ExamServiceError.invalidScratchpadAsset
        }

        let nowString = ISO8601DateFormatter().string(from: Date())
        let asset = ExamScratchpadAssetDTO(
            id: UUID(),
            sessionId: session.sessionId,
            sessionItemId: item.id,
            itemPosition: itemPosition,
            pageNumber: request.pageNumber,
            storageKey: request.storageKey,
            mimeType: request.mimeType,
            byteSize: request.byteSize,
            sha256Checksum: request.sha256Checksum,
            widthPx: request.widthPx,
            heightPx: request.heightPx,
            captureSource: request.captureSource,
            bookletPublisher: request.bookletPublisher,
            f28VerificationStatus: "QUEUED_FOR_POST_SUBMIT_BATCH",
            capturedAt: request.capturedAt ?? nowString,
            trainingEligible: false
        )
        item.scratchpadAssets.append(asset)
        session.items[idx] = item
        session.scratchpadAttachmentCount += 1
        storedSessions[sessionId] = session

        let response = ExamScratchpadUploadResponseDTO(
            sessionId: session.sessionId,
            itemPosition: itemPosition,
            scratchpadAttachmentCount: session.scratchpadAttachmentCount,
            itemScratchpadCount: item.scratchpadAssets.count,
            asset: asset,
            clockSync: session.clockSync,
            llmTokensUsed: 0,
            trainingEligible: false
        )
        scratchpadIdempotencyCache[cacheKey] = response
        return response
    }

    func submitSession(
        sessionId: UUID,
        idempotencyKey: String,
        request: ExamSessionSubmitRequestDTO
    ) async throws -> ExamSessionSubmitResponseDTO {
        if simulateOffline { throw ExamServiceError.offline }
        let cacheKey = "\(sessionId.uuidString)::submit::\(idempotencyKey)"
        if let cached = submitIdempotencyCache[cacheKey] {
            return cached
        }
        guard var session = storedSessions[sessionId] else {
            throw ExamServiceError.notFound
        }
        if let existingManifest = session.f28VerificationBatchManifest, !session.status.isActive {
            return ExamSessionSubmitResponseDTO(
                sessionId: session.sessionId,
                assemblyId: session.assemblyId,
                blueprintId: session.blueprintId,
                examCode: session.examCode,
                status: session.status,
                submissionReason: session.submissionReason ?? request.submissionReason.rawValue,
                submittedAt: session.submittedAt ?? ISO8601DateFormatter().string(from: Date()),
                submitIdempotencyKey: idempotencyKey,
                totalItemCount: session.totalItemCount,
                answeredItemCount: session.answeredItemCount,
                flaggedItemCount: session.flaggedItemCount,
                omittedItemCount: session.omittedItemCount,
                scratchpadAttachmentCount: session.scratchpadAttachmentCount,
                f28VerificationBatchManifest: existingManifest,
                clockSync: session.clockSync,
                llmTokensUsed: 0,
                trainingEligible: false
            )
        }

        let nowString = ISO8601DateFormatter().string(from: Date())
        let finalStatus: ExamSessionStatus
        switch request.submissionReason {
        case .userInitiated: finalStatus = .submitted
        case .serverDeadlineExpired, .offlineGraceExhausted: finalStatus = .expiredAutoSubmitted
        case .userAbandoned: finalStatus = .abandoned
        }

        var entries: [F28VerificationBatchEntryDTO] = []
        for item in session.items {
            for asset in item.scratchpadAssets {
                entries.append(F28VerificationBatchEntryDTO(
                    assetId: asset.id,
                    sessionItemId: item.id,
                    itemPosition: item.itemPosition,
                    pageNumber: asset.pageNumber,
                    questionCode: item.questionCode,
                    topicCode: item.topicCode,
                    storageKey: asset.storageKey,
                    sha256Checksum: asset.sha256Checksum,
                    captureSource: asset.captureSource,
                    bookletPublisher: asset.bookletPublisher,
                    f28VerificationStatus: asset.f28VerificationStatus
                ))
            }
        }
        let manifest = F28VerificationBatchManifestDTO(
            batchStatus: entries.isEmpty ? "EMPTY_NO_SCRATCHPADS" : "QUEUED_FOR_F28_BATCH",
            sessionId: session.sessionId,
            userId: session.userId,
            examCode: session.examCode,
            totalAssets: entries.count,
            llmTokensUsed: 0,
            trainingEligible: false,
            generatedAt: nowString,
            assets: entries
        )

        session.status = finalStatus
        session.submittedAt = nowString
        session.submissionReason = request.submissionReason.rawValue
        session.f28VerificationBatchManifest = manifest
        storedSessions[sessionId] = session

        let response = ExamSessionSubmitResponseDTO(
            sessionId: session.sessionId,
            assemblyId: session.assemblyId,
            blueprintId: session.blueprintId,
            examCode: session.examCode,
            status: finalStatus,
            submissionReason: request.submissionReason.rawValue,
            submittedAt: nowString,
            submitIdempotencyKey: idempotencyKey,
            totalItemCount: session.totalItemCount,
            answeredItemCount: session.answeredItemCount,
            flaggedItemCount: session.flaggedItemCount,
            omittedItemCount: session.omittedItemCount,
            scratchpadAttachmentCount: session.scratchpadAttachmentCount,
            f28VerificationBatchManifest: manifest,
            clockSync: session.clockSync,
            llmTokensUsed: 0,
            trainingEligible: false
        )
        submitIdempotencyCache[cacheKey] = response
        return response
    }

    func checkNoHintGuard(sessionId: UUID, requestedSurface: String) async throws -> ExamNoHintGuardResponseDTO {
        if simulateOffline { throw ExamServiceError.offline }
        guard let session = storedSessions[sessionId] else {
            throw ExamServiceError.notFound
        }
        if session.status.isActive && session.noHintPolicyEnforced {
            throw ExamServiceError.noHintModeActive
        }
        return ExamNoHintGuardResponseDTO(
            sessionId: session.sessionId,
            status: session.status,
            hintAllowed: true,
            policyCode: "POST_EXAM_REVIEW_ALLOWED",
            requestedSurface: requestedSurface,
            neutralExplanationKey: "exam.session.post_submit_review.allowed",
            serverNow: ISO8601DateFormatter().string(from: Date()),
            llmTokensUsed: 0,
            trainingEligible: false
        )
    }

    private static func fallbackAssembledAssessment(userId: UUID, assemblyId: UUID) -> AssembledAssessmentDTO {
        let blueprint = seedBlueprints().first!
        let items = Self.buildFixtureItems(for: blueprint, seed: "FALLBACK-SESSION")
        let accommodations = AppliedAccommodationSnapshotDTO(
            extraTimeMultiplier: 1.0,
            highContrastMode: false,
            screenReaderOptimized: false,
            extendedBreakPrompts: false
        )
        let summary = AssemblyDistributionSummaryDTO(
            topicCounts: ["MATH.ARITHMETIC": 4, "MATH.ALGEBRA": 4],
            difficultyCounts: ["EASY": 2, "MEDIUM": 4, "HARD": 2],
            cognitiveCounts: ["RECALL": 2, "APPLICATION": 4, "ANALYSIS": 2],
            easyActualBps: 2500,
            mediumActualBps: 5000,
            hardActualBps: 2500,
            expertActualBps: 0
        )
        return AssembledAssessmentDTO(
            id: assemblyId,
            userId: userId,
            candidateProfileId: nil,
            blueprintId: blueprint.id,
            blueprintCode: blueprint.blueprintCode,
            blueprintTitle: blueprint.title,
            blueprintMode: blueprint.blueprintMode,
            examDefinitionId: blueprint.examDefinitionId,
            examVersionNumber: blueprint.examVersionNumber,
            assemblySeed: "FALLBACK-SESSION",
            reproducibilityHash: String(repeating: "a", count: 64),
            totalQuestionCount: items.count,
            baseDurationSeconds: blueprint.baseDurationSeconds,
            timeMultiplier: 1.0,
            effectiveDurationSeconds: blueprint.baseDurationSeconds,
            accommodationsSnapshot: accommodations,
            distributionSummary: summary,
            status: .ready,
            assembledAt: "2026-11-15T12:00:00Z",
            expiresAt: "2026-11-22T12:00:00Z",
            items: items,
            trainingEligible: false
        )
    }

    static func seedBlueprints() -> [ExamBlueprintDTO] {
        let tytDefId = UUID(uuidString: "91000000-0000-4000-8000-000000000101")!
        let sectionId = UUID(uuidString: "92000000-0000-4000-8000-000000000101")!
        return [
            ExamBlueprintDTO(
                id: UUID(uuidString: "94000000-0000-4000-8000-000000000101")!,
                examDefinitionId: tytDefId,
                examVersionNumber: 1,
                examSectionId: sectionId,
                blueprintCode: "BP-TYT-MATH-DIAG-2026-V1",
                title: "TYT Mathematics Diagnostic Baseline (8 Items)",
                description: "Deterministic 8-question diagnostic baseline across core arithmetic and algebra foundations.",
                blueprintMode: .diagnostic,
                totalQuestionCount: 8,
                baseDurationSeconds: 900,
                easyTargetBps: 2500,
                mediumTargetBps: 5000,
                hardTargetBps: 2500,
                expertTargetBps: 0,
                difficultyToleranceBps: 500,
                recallTargetBps: 2500,
                comprehensionTargetBps: 2500,
                applicationTargetBps: 2500,
                analysisTargetBps: 2500,
                evaluationTargetBps: 0,
                allowRecentlySeenCooldownDays: 14,
                shuffleQuestions: true,
                shuffleOptions: true,
                status: .active,
                blueprintHash: "3030303030303030303030303030303030303030303030303030303030303001",
                topicRules: [
                    ExamBlueprintTopicRuleDTO(id: UUID(uuidString: "94100000-0000-4000-8000-000000000101")!, topicCode: "MATH.ARITHMETIC.INTEGER_OPERATIONS", targetWeightBps: 2500, minQuestionCount: 1, maxQuestionCount: 3, requiredItemFormats: [.singleChoiceMcq], ruleOrder: 1),
                    ExamBlueprintTopicRuleDTO(id: UUID(uuidString: "94100000-0000-4000-8000-000000000102")!, topicCode: "MATH.ARITHMETIC.FRACTIONS", targetWeightBps: 2500, minQuestionCount: 1, maxQuestionCount: 3, requiredItemFormats: [.singleChoiceMcq], ruleOrder: 2),
                    ExamBlueprintTopicRuleDTO(id: UUID(uuidString: "94100000-0000-4000-8000-000000000103")!, topicCode: "MATH.ARITHMETIC.PERCENTAGES", targetWeightBps: 2500, minQuestionCount: 1, maxQuestionCount: 3, requiredItemFormats: [.singleChoiceMcq], ruleOrder: 3),
                    ExamBlueprintTopicRuleDTO(id: UUID(uuidString: "94100000-0000-4000-8000-000000000104")!, topicCode: "MATH.ALGEBRA.FACTORING", targetWeightBps: 2500, minQuestionCount: 1, maxQuestionCount: 3, requiredItemFormats: [.singleChoiceMcq], ruleOrder: 4)
                ],
                trainingEligible: false,
                createdAt: "2026-01-15T09:00:00Z",
                updatedAt: "2026-01-15T09:00:00Z"
            ),
            ExamBlueprintDTO(
                id: UUID(uuidString: "94000000-0000-4000-8000-000000000102")!,
                examDefinitionId: tytDefId,
                examVersionNumber: 1,
                examSectionId: sectionId,
                blueprintCode: "BP-TYT-MATH-DRILL-2026-V1",
                title: "TYT Mathematics Section Drill (10 Items)",
                description: "Deterministic 10-question timed section drill covering integer operations, fractions, percentages, and factoring.",
                blueprintMode: .sectionDrill,
                totalQuestionCount: 10,
                baseDurationSeconds: 1200,
                easyTargetBps: 2000,
                mediumTargetBps: 4000,
                hardTargetBps: 3000,
                expertTargetBps: 1000,
                difficultyToleranceBps: 500,
                recallTargetBps: 2000,
                comprehensionTargetBps: 2000,
                applicationTargetBps: 3000,
                analysisTargetBps: 2000,
                evaluationTargetBps: 1000,
                allowRecentlySeenCooldownDays: 14,
                shuffleQuestions: true,
                shuffleOptions: true,
                status: .active,
                blueprintHash: "3030303030303030303030303030303030303030303030303030303030303002",
                topicRules: [
                    ExamBlueprintTopicRuleDTO(id: UUID(uuidString: "94100000-0000-4000-8000-000000000201")!, topicCode: "MATH.ARITHMETIC.INTEGER_OPERATIONS", targetWeightBps: 3000, minQuestionCount: 2, maxQuestionCount: 4, requiredItemFormats: [.singleChoiceMcq], ruleOrder: 1),
                    ExamBlueprintTopicRuleDTO(id: UUID(uuidString: "94100000-0000-4000-8000-000000000202")!, topicCode: "MATH.ARITHMETIC.FRACTIONS", targetWeightBps: 3000, minQuestionCount: 2, maxQuestionCount: 4, requiredItemFormats: [.singleChoiceMcq], ruleOrder: 2),
                    ExamBlueprintTopicRuleDTO(id: UUID(uuidString: "94100000-0000-4000-8000-000000000203")!, topicCode: "MATH.ARITHMETIC.PERCENTAGES", targetWeightBps: 2000, minQuestionCount: 1, maxQuestionCount: 3, requiredItemFormats: [.singleChoiceMcq], ruleOrder: 3),
                    ExamBlueprintTopicRuleDTO(id: UUID(uuidString: "94100000-0000-4000-8000-000000000204")!, topicCode: "MATH.ALGEBRA.FACTORING", targetWeightBps: 2000, minQuestionCount: 1, maxQuestionCount: 3, requiredItemFormats: [.singleChoiceMcq], ruleOrder: 4)
                ],
                trainingEligible: false,
                createdAt: "2026-01-15T09:00:00Z",
                updatedAt: "2026-01-15T09:00:00Z"
            )
        ]
    }

    private static func buildFixtureItems(for blueprint: ExamBlueprintDTO, seed: String) -> [AssembledAssessmentItemDTO] {
        let topics = [
            "MATH.ARITHMETIC.INTEGER_OPERATIONS",
            "MATH.ARITHMETIC.FRACTIONS",
            "MATH.ARITHMETIC.PERCENTAGES",
            "MATH.ALGEBRA.FACTORING"
        ]
        let bands: [ExamDifficultyBand] = [.easy, .medium, .hard, .easy, .medium, .hard, .easy, .medium, .easy, .medium, .hard, .expert]
        let cognitives: [ExamCognitiveLevel] = [.recall, .comprehension, .application, .analysis]

        var rawItems: [(uuid: UUID, code: String, topic: String, band: ExamDifficultyBand, cog: ExamCognitiveLevel, stem: String, weight: String)] = []
        for idx in 1...blueprint.totalQuestionCount {
            let qUuid = UUID(uuidString: String(format: "93000000-0000-4000-8000-%012d", 100 + idx))!
            let topic = topics[(idx - 1) % topics.count]
            let band = bands[(idx - 1) % bands.count]
            let cog = cognitives[(idx - 1) % cognitives.count]
            let rank = SHA256.hash(data: Data("\(seed):item:\(qUuid.uuidString.lowercased())".utf8)).map { String(format: "%02x", $0) }.joined()
            rawItems.append((
                uuid: qUuid,
                code: String(format: "TYT-MATH-2026-Q%03d", idx),
                topic: topic,
                band: band,
                cog: cog,
                stem: "Evaluate the algebraic expression for question \(idx) under topic \(topic).",
                weight: rank
            ))
        }
        let ordered = blueprint.shuffleQuestions ? rawItems.sorted(by: { $0.weight < $1.weight }) : rawItems

        return ordered.enumerated().map { offset, entry in
            let seq = offset + 1
            let baseOptions = ["A", "B", "C", "D", "E"]
            let orderedKeys = blueprint.shuffleOptions
                ? baseOptions.sorted { lhs, rhs in
                    let lh = SHA256.hash(data: Data("\(seed):opt:\(entry.uuid.uuidString.lowercased()):\(lhs)".utf8)).map { String(format: "%02x", $0) }.joined()
                    let rh = SHA256.hash(data: Data("\(seed):opt:\(entry.uuid.uuidString.lowercased()):\(rhs)".utf8)).map { String(format: "%02x", $0) }.joined()
                    return lh < rh
                }
                : baseOptions
            let opts = orderedKeys.enumerated().map { optIdx, key in
                AssembledQuestionOptionDTO(
                    optionKey: key,
                    displayOrder: optIdx + 1,
                    contentMarkdown: "Choice \(key) for \(entry.code)",
                    contentLatex: "\(optIdx + 1)x",
                    trainingEligible: false
                )
            }
            let itemDigest = SHA256.hash(data: Data("asm-item:\(blueprint.id.uuidString):\(seed):\(seq)".utf8))
            var uuidBytes = Array(itemDigest.prefix(16))
            uuidBytes[6] = (uuidBytes[6] & 0x0F) | 0x40
            uuidBytes[8] = (uuidBytes[8] & 0x3F) | 0x80
            let itemId = UUID(uuid: (
                uuidBytes[0], uuidBytes[1], uuidBytes[2], uuidBytes[3],
                uuidBytes[4], uuidBytes[5], uuidBytes[6], uuidBytes[7],
                uuidBytes[8], uuidBytes[9], uuidBytes[10], uuidBytes[11],
                uuidBytes[12], uuidBytes[13], uuidBytes[14], uuidBytes[15]
            ))
            return AssembledAssessmentItemDTO(
                id: itemId,
                questionItemId: entry.uuid,
                questionCode: entry.code,
                sequenceNumber: seq,
                topicCode: entry.topic,
                subtopicCode: nil,
                itemFormat: .singleChoiceMcq,
                difficultyBand: entry.band,
                cognitiveLevel: entry.cog,
                stemMarkdown: entry.stem,
                stemLatex: "f(x) = x^2 + \(seq)",
                altTextDescription: nil,
                hasVisualDiagram: false,
                optionOrder: orderedKeys,
                options: opts,
                allocatedPoints: 1.0,
                estimatedTimeSeconds: 90,
                trainingEligible: false
            )
        }
    }

    static func seedDefinitions() -> [ExamDefinitionModel] {
        [
            ExamDefinitionModel(
                schemaVersion: "exam-definition-detail-v1",
                examDefinitionId: UUID(uuidString: "91000000-0000-4000-8000-000000000101")!,
                examCode: "TR_YKS_TYT_MATH",
                examVersion: "2026.1",
                jurisdiction: .tr,
                examFamily: .yks,
                titleEn: "YKS Basic Proficiency Test (TYT) Mathematics",
                titleTr: "YKS Temel Yeterlilik Testi (TYT) Matematik",
                status: .active,
                totalQuestions: 40,
                durationMinutes: 65,
                scoringFormulaType: .correctMinusQuarterWrong,
                wrongPenaltyFactor: 0.25,
                minScore: -10.0,
                maxScore: 40.0,
                navigationPolicy: .freeJump,
                blueprintHash: "c1cb8c26245fdee1b53c164a94e35a6cecbde8bae6ece948e60f577df53e1391",
                effectiveFrom: "2026-01-01T00:00:00Z",
                deprecatedAt: nil,
                supersededByVersion: nil,
                sections: [
                    ExamSectionSpec(sectionCode: "TYT_MATH", titleEn: "TYT Mathematics", titleTr: "TYT Matematik", questionCount: 40, durationMinutes: 65, order: 1)
                ],
                scoringParameters: ScoringParameters(scaleMin: nil, scaleMax: nil, moduleCount: 1, rubricScaleMin: nil, rubricScaleMax: nil, sectionWeights: []),
                topicWeights: [
                    ExamTopicWeightModel(sectionCode: "TYT_MATH", skillId: "MATH.ARITHMETIC.INTEGER_OPERATIONS", domainCode: "MATH.ARITHMETIC", questionCountWeight: 5.0, minQuestions: 4, maxQuestions: 7, cognitiveLevelMix: CognitiveLevelMix(recall: 0.3, application: 0.5, analysis: 0.2), sortOrder: 1),
                    ExamTopicWeightModel(sectionCode: "TYT_MATH", skillId: "MATH.ARITHMETIC.FRACTIONS", domainCode: "MATH.ARITHMETIC", questionCountWeight: 4.0, minQuestions: 3, maxQuestions: 5, cognitiveLevelMix: CognitiveLevelMix(recall: 0.25, application: 0.55, analysis: 0.2), sortOrder: 2),
                    ExamTopicWeightModel(sectionCode: "TYT_MATH", skillId: "MATH.ALGEBRA.FACTORING", domainCode: "MATH.ALGEBRA", questionCountWeight: 3.0, minQuestions: 2, maxQuestions: 4, cognitiveLevelMix: CognitiveLevelMix(recall: 0.2, application: 0.55, analysis: 0.25), sortOrder: 3)
                ],
                trainingEligible: false,
                llmTokensUsed: 0
            ),
            ExamDefinitionModel(
                schemaVersion: "exam-definition-detail-v1",
                examDefinitionId: UUID(uuidString: "91000000-0000-4000-8000-000000000104")!,
                examCode: "INTL_SAT_MATH",
                examVersion: "2026.1",
                jurisdiction: .intl,
                examFamily: .sat,
                titleEn: "Digital SAT Mathematics (Adaptive Modules 1 & 2)",
                titleTr: "Dijital SAT Matematik (Uyarlanabilir Modül 1 ve 2)",
                status: .active,
                totalQuestions: 44,
                durationMinutes: 70,
                scoringFormulaType: .rawCorrectOnly,
                wrongPenaltyFactor: 0.0,
                minScore: 200.0,
                maxScore: 800.0,
                navigationPolicy: .sectionLocked,
                blueprintHash: "89063bfc449a4000bb05154fceff8c9ebbb81446ae2cafc41c46adfeeaff29dd",
                effectiveFrom: "2026-01-01T00:00:00Z",
                deprecatedAt: nil,
                supersededByVersion: nil,
                sections: [
                    ExamSectionSpec(sectionCode: "SAT_MOD_1", titleEn: "Math Module 1", titleTr: "Matematik Modül 1", questionCount: 22, durationMinutes: 35, order: 1),
                    ExamSectionSpec(sectionCode: "SAT_MOD_2", titleEn: "Math Module 2", titleTr: "Matematik Modül 2", questionCount: 22, durationMinutes: 35, order: 2)
                ],
                scoringParameters: ScoringParameters(scaleMin: 200.0, scaleMax: 800.0, moduleCount: 2, rubricScaleMin: nil, rubricScaleMax: nil, sectionWeights: []),
                topicWeights: [
                    ExamTopicWeightModel(sectionCode: "SAT_MOD_1", skillId: "MATH.EQUATIONS.LINEAR_ONE_VARIABLE", domainCode: "MATH.EQUATIONS", questionCountWeight: 4.0, minQuestions: 3, maxQuestions: 5, cognitiveLevelMix: CognitiveLevelMix(recall: 0.2, application: 0.6, analysis: 0.2), sortOrder: 1),
                    ExamTopicWeightModel(sectionCode: "SAT_MOD_2", skillId: "MATH.EQUATIONS.QUADRATIC_SOLVING", domainCode: "MATH.EQUATIONS", questionCountWeight: 5.0, minQuestions: 4, maxQuestions: 6, cognitiveLevelMix: CognitiveLevelMix(recall: 0.15, application: 0.5, analysis: 0.35), sortOrder: 2)
                ],
                trainingEligible: false,
                llmTokensUsed: 0
            )
        ]
    }
}
#endif
