import Foundation

public enum ExamServiceError: Error, Equatable, Sendable {
    case featureDisabled
    case notFound
    case versionConflict
    case scoreOutOfRange
    case forbidden
    case offline
    case unexpected(String)
}

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

    private func mapNetworkError(_ error: NetworkError) -> ExamServiceError {
        switch error {
        case .server(let problem):
            if problem.status == 503 || problem.code == "EXAM_FEATURE_DISABLED" {
                return .featureDisabled
            }
            if problem.status == 403 || problem.code == "EXAM_ACCESS_DENIED" {
                return .forbidden
            }
            if problem.status == 404
                || problem.code == "EXAM_DEFINITION_NOT_FOUND"
                || problem.code == "EXAM_VERSION_NOT_FOUND"
                || problem.code == "EXAM_TARGET_NOT_FOUND" {
                return .notFound
            }
            if problem.status == 409 || problem.code == "EXAM_VERSION_CONFLICT" {
                return .versionConflict
            }
            if problem.code == "EXAM_TARGET_SCORE_OUT_OF_RANGE" {
                return .scoreOutOfRange
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
        case .transport:
            return .offline
        case .invalidURL, .invalidResponse, .httpStatus, .decoding:
            return .unexpected(String(describing: error))
        }
    }
}

#if DEBUG
final class FixtureExamAPIService: ExamAPIServicing, @unchecked Sendable {
    private let simulateOffline: Bool
    private let userId: UUID
    private var storedTargets: [StudentExamTargetModel] = []

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
