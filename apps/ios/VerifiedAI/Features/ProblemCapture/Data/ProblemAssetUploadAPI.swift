import Foundation

final class ProblemAssetUploadAPI: ProblemAssetUploadServicing, @unchecked Sendable {
    private let apiClient: APIClient
    private let encoder = JSONEncoder()

    init(apiClient: APIClient) {
        self.apiClient = apiClient
    }

    func reserveUpload(_ request: ProblemAssetUploadRequest, idempotencyKey: String) async throws -> ProblemAssetUploadReservation {
        let body = try encoder.encode(ProblemAssetUploadWireRequest(from: request))
        let response: HTTPResponse<ProblemAssetUploadReservationWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/uploads/presign", method: .post),
                body: body,
                idempotencyKey: idempotencyKey,
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.reservation()
    }

    func completeUpload(uploadId: UUID, idempotencyKey: String) async throws -> DurableProblemAssetReference {
        let response: HTTPResponse<ProblemAssetUploadCompletionWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/uploads/\(uploadId.uuidString)/complete", method: .post),
                idempotencyKey: idempotencyKey,
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.reference()
    }

    func preprocessAsset(problemAssetId: UUID) async throws -> ProblemAssetPreprocessingResult {
        let response: HTTPResponse<ProblemAssetPreprocessingWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/problem-assets/\(problemAssetId.uuidString)/preprocess", method: .post),
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.result()
    }

    func getPreprocessing(problemAssetId: UUID) async throws -> ProblemAssetPreprocessingResult {
        let response: HTTPResponse<ProblemAssetPreprocessingWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/problem-assets/\(problemAssetId.uuidString)/preprocessing", method: .get),
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.result()
    }

    func requestRecognition(problemSessionId: UUID) async throws -> ProblemRecognitionResult {
        let response: HTTPResponse<ProblemRecognitionWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/problem-sessions/\(problemSessionId.uuidString)/recognition", method: .post),
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.result()
    }

    func getRecognition(problemSessionId: UUID) async throws -> ProblemRecognitionResult {
        let response: HTTPResponse<ProblemRecognitionWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/problem-sessions/\(problemSessionId.uuidString)/recognition", method: .get),
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.result()
    }

    func requestParse(problemSessionId: UUID) async throws -> ProblemParseResult {
        let response: HTTPResponse<ProblemParseWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/problem-sessions/\(problemSessionId.uuidString)/parse", method: .post),
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.result()
    }

    func getParse(problemSessionId: UUID) async throws -> ProblemParseResult {
        let response: HTTPResponse<ProblemParseWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/problem-sessions/\(problemSessionId.uuidString)/parse", method: .get),
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.result()
    }

    func canonicalize(problemSessionId: UUID) async throws -> CanonicalProblemResult {
        let response: HTTPResponse<CanonicalProblemWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/problem-sessions/\(problemSessionId.uuidString)/canonicalize", method: .post),
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.result()
    }

    func getCanonicalProblem(problemSessionId: UUID) async throws -> CanonicalProblemResult {
        let response: HTTPResponse<CanonicalProblemWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/problem-sessions/\(problemSessionId.uuidString)/canonical-problem", method: .get),
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.result()
    }

    func requestClassification(problemSessionId: UUID) async throws -> ProblemClassificationResult {
        let response: HTTPResponse<ProblemClassificationWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/problem-sessions/\(problemSessionId.uuidString)/classification", method: .post),
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.result()
    }

    func getClassification(problemSessionId: UUID) async throws -> ProblemClassificationResult {
        let response: HTTPResponse<ProblemClassificationWireResponse> = try await apiClient.send(
            HTTPRequest(
                endpoint: Endpoint(path: "/api/v1/problem-sessions/\(problemSessionId.uuidString)/classification", method: .get),
                allowsAuthRefreshRetry: false
            )
        )
        return try response.body.result()
    }
}

private struct ProblemAssetUploadWireRequest: Encodable {
    let source: String
    let assetKind: String
    let contentType: String
    let sizeBytes: Int64
    let checksumSha256: String
    let imageWidth: Int?
    let imageHeight: Int?
    let pageCount: Int?
    let cropX: Double
    let cropY: Double
    let cropWidth: Double
    let cropHeight: Double

    init(from request: ProblemAssetUploadRequest) {
        source = request.source
        assetKind = request.assetKind
        contentType = request.contentType
        sizeBytes = request.sizeBytes
        checksumSha256 = request.checksumSha256
        imageWidth = request.imageWidth
        imageHeight = request.imageHeight
        pageCount = request.pageCount
        cropX = request.cropX
        cropY = request.cropY
        cropWidth = request.cropWidth
        cropHeight = request.cropHeight
    }
}

private struct ProblemAssetUploadReservationWireResponse: Decodable {
    let uploadId: UUID
    let problemSessionId: UUID
    let problemAssetId: UUID
    let assetStatus: String
    let uploadUrl: String
    let expiresAt: String
    let requiredHeaders: [String: String]

    func reservation() throws -> ProblemAssetUploadReservation {
        guard let uploadURL = URL(string: uploadUrl),
              let expiresAtDate = ISO8601WireDate.parse(expiresAt) else {
            throw NetworkError.decoding("unsupported_upload_reservation")
        }
        return ProblemAssetUploadReservation(
            uploadId: uploadId,
            problemSessionId: problemSessionId,
            problemAssetId: problemAssetId,
            assetStatus: assetStatus,
            uploadURL: uploadURL,
            expiresAt: expiresAtDate,
            requiredHeaders: requiredHeaders
        )
    }
}

private struct ProblemAssetUploadCompletionWireResponse: Decodable {
    let uploadId: UUID
    let problemSessionId: UUID
    let problemAssetId: UUID
    let problemSessionStatus: String
    let assetStatus: String
    let availableAt: String

    func reference() throws -> DurableProblemAssetReference {
        guard let availableAtDate = ISO8601WireDate.parse(availableAt) else {
            throw NetworkError.decoding("unsupported_upload_completion")
        }
        return DurableProblemAssetReference(
            uploadId: uploadId,
            problemSessionId: problemSessionId,
            problemAssetId: problemAssetId,
            problemSessionStatus: problemSessionStatus,
            assetStatus: assetStatus,
            availableAt: availableAtDate
        )
    }
}

private struct ProblemAssetPreprocessingWireResponse: Decodable {
    let sourceAssetId: UUID
    let problemSessionId: UUID
    let sourceAssetStatus: String
    let preprocessingStatus: String
    let qualityOutcome: String?
    let failureCode: String?
    let preferredRecognitionDerivativeId: UUID?
    let derivatives: [ProblemAssetDerivativeWireResponse]
    let qualitySignals: [ProblemAssetQualitySignalWireResponse]
    let userRecoveryActions: [String]
    let completedAt: String?

    func result() throws -> ProblemAssetPreprocessingResult {
        let completedAtDate = ISO8601WireDate.parse(completedAt)
        if completedAt != nil, completedAtDate == nil {
            throw NetworkError.decoding("unsupported_preprocessing_completion_date")
        }
        return ProblemAssetPreprocessingResult(
            sourceAssetId: sourceAssetId,
            problemSessionId: problemSessionId,
            sourceAssetStatus: sourceAssetStatus,
            preprocessingStatus: preprocessingStatus,
            qualityOutcome: qualityOutcome,
            failureCode: failureCode,
            preferredRecognitionDerivativeId: preferredRecognitionDerivativeId,
            derivatives: derivatives.map(\.derivative),
            qualitySignals: qualitySignals.map(\.signal),
            userRecoveryActions: userRecoveryActions,
            completedAt: completedAtDate
        )
    }
}

private struct ProblemAssetDerivativeWireResponse: Decodable {
    let derivativeId: UUID
    let derivativeKind: String
    let status: String
    let selectedForRecognition: Bool
    let contentType: String?
    let sizeBytes: Int64?
    let checksumSha256: String?
    let width: Int?
    let height: Int?
    let processorName: String
    let processorVersion: String
    let configurationVersion: String
    let orientationNormalized: Bool
    let perspectiveApplied: Bool
    let contrastNormalized: Bool
    let resized: Bool
    let qualityOutcome: String
    let failureCode: String?

    var derivative: ProblemAssetDerivative {
        ProblemAssetDerivative(
            derivativeId: derivativeId,
            derivativeKind: derivativeKind,
            status: status,
            selectedForRecognition: selectedForRecognition,
            contentType: contentType,
            sizeBytes: sizeBytes,
            checksumSha256: checksumSha256,
            width: width,
            height: height,
            processorName: processorName,
            processorVersion: processorVersion,
            configurationVersion: configurationVersion,
            orientationNormalized: orientationNormalized,
            perspectiveApplied: perspectiveApplied,
            contrastNormalized: contrastNormalized,
            resized: resized,
            qualityOutcome: qualityOutcome,
            failureCode: failureCode
        )
    }
}

private struct ProblemAssetQualitySignalWireResponse: Decodable {
    let signalType: String
    let severity: String
    let score: Double
    let threshold: Double
    let policyVersion: String
    let messageCode: String

    var signal: ProblemAssetQualitySignal {
        ProblemAssetQualitySignal(
            signalType: signalType,
            severity: severity,
            score: score,
            threshold: threshold,
            policyVersion: policyVersion,
            messageCode: messageCode
        )
    }
}

private struct ProblemRecognitionWireResponse: Decodable {
    let recognitionJobId: UUID?
    let problemSessionId: UUID
    let sourceAssetId: UUID?
    let inputDerivativeId: UUID?
    let status: String
    let capability: String
    let attemptCount: Int
    let maxAttempts: Int
    let lastErrorCode: String?
    let lastFailureClass: String?
    let reviewRequired: Bool
    let schemaVersion: String?
    let promptId: String?
    let promptVersion: String?
    let routePolicyVersion: String?
    let provider: String?
    let model: String?
    let blockCount: Int
    let blocks: [ProblemRecognitionBlockWireResponse]
    let completedAt: String?

    func result() throws -> ProblemRecognitionResult {
        let completedAtDate = ISO8601WireDate.parse(completedAt)
        if completedAt != nil, completedAtDate == nil {
            throw NetworkError.decoding("unsupported_recognition_completion_date")
        }
        return ProblemRecognitionResult(
            recognitionJobId: recognitionJobId,
            problemSessionId: problemSessionId,
            sourceAssetId: sourceAssetId,
            inputDerivativeId: inputDerivativeId,
            status: status,
            capability: capability,
            attemptCount: attemptCount,
            maxAttempts: maxAttempts,
            lastErrorCode: lastErrorCode,
            lastFailureClass: lastFailureClass,
            reviewRequired: reviewRequired,
            schemaVersion: schemaVersion,
            promptId: promptId,
            promptVersion: promptVersion,
            routePolicyVersion: routePolicyVersion,
            provider: provider,
            model: model,
            blockCount: blockCount,
            blocks: blocks.map(\.block),
            completedAt: completedAtDate
        )
    }
}

private struct ProblemRecognitionBlockWireResponse: Decodable {
    let id: String
    let kind: String
    let text: String
    let boundingBox: ProblemRecognitionBoundingBoxWireResponse
    let readingOrder: Int
    let confidenceStatus: String
    let normalizedConfidence: Double?
    let uncertainty: [String]
    let layoutHints: [String]

    var block: ProblemRecognitionBlock {
        ProblemRecognitionBlock(
            id: id,
            kind: kind,
            text: text,
            boundingBox: boundingBox.box,
            readingOrder: readingOrder,
            confidenceStatus: confidenceStatus,
            normalizedConfidence: normalizedConfidence,
            uncertainty: uncertainty,
            layoutHints: layoutHints
        )
    }
}

private struct ProblemRecognitionBoundingBoxWireResponse: Decodable {
    let x: Double
    let y: Double
    let width: Double
    let height: Double

    var box: ProblemRecognitionBoundingBox {
        ProblemRecognitionBoundingBox(x: x, y: y, width: width, height: height)
    }
}

private struct ProblemParseWireResponse: Decodable {
    let parseJobId: UUID?
    let problemSessionId: UUID
    let recognitionEvidenceId: UUID?
    let recognitionEvidenceRevision: Int?
    let jobStatus: String
    let capability: String
    let attemptCount: Int
    let maxAttempts: Int
    let lastErrorCode: String?
    let lastFailureClass: String?
    let problemParseId: UUID?
    let parseRevision: Int?
    let supportStatus: String?
    let unsupportedReason: String?
    let reviewRequired: Bool
    let schemaVersion: String?
    let promptId: String?
    let promptVersion: String?
    let routePolicyVersion: String?
    let provider: String?
    let model: String?
    let normalizedProblem: NormalizedProblemParseWireResponse?
    let createdAt: String?
    let updatedAt: String?
    let completedAt: String?

    func result() throws -> ProblemParseResult {
        let createdAtDate = ISO8601WireDate.parse(createdAt)
        if createdAt != nil, createdAtDate == nil {
            throw NetworkError.decoding("unsupported_problem_parse_created_date")
        }
        let updatedAtDate = ISO8601WireDate.parse(updatedAt)
        if updatedAt != nil, updatedAtDate == nil {
            throw NetworkError.decoding("unsupported_problem_parse_updated_date")
        }
        let completedAtDate = ISO8601WireDate.parse(completedAt)
        if completedAt != nil, completedAtDate == nil {
            throw NetworkError.decoding("unsupported_problem_parse_completion_date")
        }
        return ProblemParseResult(
            parseJobId: parseJobId,
            problemSessionId: problemSessionId,
            recognitionEvidenceId: recognitionEvidenceId,
            recognitionEvidenceRevision: recognitionEvidenceRevision,
            jobStatus: jobStatus,
            capability: capability,
            attemptCount: attemptCount,
            maxAttempts: maxAttempts,
            lastErrorCode: lastErrorCode,
            lastFailureClass: lastFailureClass,
            problemParseId: problemParseId,
            parseRevision: parseRevision,
            supportStatus: supportStatus,
            unsupportedReason: unsupportedReason,
            reviewRequired: reviewRequired,
            schemaVersion: schemaVersion,
            promptId: promptId,
            promptVersion: promptVersion,
            routePolicyVersion: routePolicyVersion,
            provider: provider,
            model: model,
            normalizedProblem: normalizedProblem?.parse,
            createdAt: createdAtDate,
            updatedAt: updatedAtDate,
            completedAt: completedAtDate
        )
    }
}

private struct NormalizedProblemParseWireResponse: Decodable {
    let schemaVersion: String
    let supportStatus: String
    let unsupportedReason: String?
    let subjectId: String?
    let topicId: String?
    let taskType: String?
    let problemType: String?
    let expressions: [ProblemParseExpressionWireResponse]
    let variables: [ProblemParseVariableWireResponse]
    let constraints: [ProblemParseConstraintWireResponse]
    let assumptions: [ProblemParseAssumptionWireResponse]
    let uncertainty: ProblemParseUncertaintyWireResponse
    let sourceEvidenceRefs: [ProblemParseSourceEvidenceRefWireResponse]
    let visualQualityRisks: [ProblemParseVisualQualityRiskWireResponse]
    let reviewRequired: Bool

    var parse: NormalizedProblemParse {
        NormalizedProblemParse(
            schemaVersion: schemaVersion,
            supportStatus: supportStatus,
            unsupportedReason: unsupportedReason,
            subjectId: subjectId,
            topicId: topicId,
            taskType: taskType,
            problemType: problemType,
            expressions: expressions.map(\.expression),
            variables: variables.map(\.variable),
            constraints: constraints.map(\.constraint),
            assumptions: assumptions.map(\.assumption),
            uncertainty: uncertainty.uncertainty,
            sourceEvidenceRefs: sourceEvidenceRefs.map(\.ref),
            visualQualityRisks: visualQualityRisks.map(\.risk),
            reviewRequired: reviewRequired
        )
    }
}

private struct ProblemParseExpressionWireResponse: Decodable {
    let id: String
    let role: String
    let sourceText: String
    let normalizedText: String
    let displayLatex: String?
    let relation: String?
    let sourceBlockIds: [String]

    var expression: ProblemParseExpression {
        ProblemParseExpression(
            id: id,
            role: role,
            sourceText: sourceText,
            normalizedText: normalizedText,
            displayLatex: displayLatex,
            relation: relation,
            sourceBlockIds: sourceBlockIds
        )
    }
}

private struct ProblemParseVariableWireResponse: Decodable {
    let symbol: String
    let role: String
    let sourceBlockIds: [String]

    var variable: ProblemParseVariable {
        ProblemParseVariable(symbol: symbol, role: role, sourceBlockIds: sourceBlockIds)
    }
}

private struct ProblemParseConstraintWireResponse: Decodable {
    let id: String
    let sourceText: String
    let normalizedText: String
    let variables: [String]
    let explicit: Bool
    let sourceBlockIds: [String]

    var constraint: ProblemParseConstraint {
        ProblemParseConstraint(
            id: id,
            sourceText: sourceText,
            normalizedText: normalizedText,
            variables: variables,
            explicit: explicit,
            sourceBlockIds: sourceBlockIds
        )
    }
}

private struct ProblemParseAssumptionWireResponse: Decodable {
    let id: String
    let text: String
    let explicit: Bool
    let sourceBlockIds: [String]

    var assumption: ProblemParseAssumption {
        ProblemParseAssumption(id: id, text: text, explicit: explicit, sourceBlockIds: sourceBlockIds)
    }
}

private struct ProblemParseUncertaintyWireResponse: Decodable {
    let recognition: [String]
    let parse: [String]
    let reviewRequired: Bool

    var uncertainty: ProblemParseUncertainty {
        ProblemParseUncertainty(recognition: recognition, parse: parse, reviewRequired: reviewRequired)
    }
}

private struct ProblemParseSourceEvidenceRefWireResponse: Decodable {
    let blockId: String
    let fieldPath: String

    var ref: ProblemParseSourceEvidenceRef {
        ProblemParseSourceEvidenceRef(blockId: blockId, fieldPath: fieldPath)
    }
}

private struct ProblemParseVisualQualityRiskWireResponse: Decodable {
    let signalType: String
    let severity: String
    let messageCode: String?

    var risk: ProblemParseVisualQualityRisk {
        ProblemParseVisualQualityRisk(signalType: signalType, severity: severity, messageCode: messageCode)
    }
}

private struct CanonicalProblemWireResponse: Decodable {
    let canonicalProblemId: UUID
    let problemSessionId: UUID
    let problemParseId: UUID
    let problemParseRevision: Int
    let canonicalRevision: Int
    let schemaVersion: String
    let verifierSchemaVersion: String
    let problemType: String
    let taskType: String
    let normalizedText: String?
    let displayLatex: String?
    let variables: [String]
    let sourceConstraintCount: Int
    let derivedRestrictionCount: Int
    let createdAt: String

    func result() throws -> CanonicalProblemResult {
        guard let createdAtDate = ISO8601WireDate.parse(createdAt) else {
            throw NetworkError.decoding("unsupported_canonical_problem_created_date")
        }
        return CanonicalProblemResult(
            canonicalProblemId: canonicalProblemId,
            problemSessionId: problemSessionId,
            problemParseId: problemParseId,
            problemParseRevision: problemParseRevision,
            canonicalRevision: canonicalRevision,
            schemaVersion: schemaVersion,
            verifierSchemaVersion: verifierSchemaVersion,
            problemType: problemType,
            taskType: taskType,
            normalizedText: normalizedText,
            displayLatex: displayLatex,
            variables: variables,
            sourceConstraintCount: sourceConstraintCount,
            derivedRestrictionCount: derivedRestrictionCount,
            createdAt: createdAtDate
        )
    }
}

private struct ProblemClassificationWireResponse: Decodable {
    let classificationJobId: UUID?
    let problemSessionId: UUID
    let canonicalProblemId: UUID
    let canonicalProblemRevision: Int
    let jobStatus: String
    let capability: String
    let attemptCount: Int
    let maxAttempts: Int
    let lastErrorCode: String?
    let lastFailureClass: String?
    let classificationId: UUID?
    let classificationRevision: Int?
    let classificationSource: String?
    let classificationStatus: String?
    let reviewReason: String?
    let subjectId: String?
    let topicId: String?
    let primarySkillId: String?
    let secondarySkillIds: [String]
    let difficulty: String?
    let confidenceBand: String?
    let confidenceCalibration: String?
    let provider: String?
    let model: String?
    let fallbackUsed: Bool?
    let ontologyVersion: String
    let projectionVersion: String
    let schemaVersion: String
    let difficultyPolicyVersion: String
    let confidencePolicyVersion: String
    let createdAt: String?
    let updatedAt: String?
    let completedAt: String?
    let classificationCreatedAt: String?

    func result() throws -> ProblemClassificationResult {
        let createdAtDate = ISO8601WireDate.parse(createdAt)
        if createdAt != nil, createdAtDate == nil {
            throw NetworkError.decoding("unsupported_problem_classification_created_date")
        }
        let updatedAtDate = ISO8601WireDate.parse(updatedAt)
        if updatedAt != nil, updatedAtDate == nil {
            throw NetworkError.decoding("unsupported_problem_classification_updated_date")
        }
        let completedAtDate = ISO8601WireDate.parse(completedAt)
        if completedAt != nil, completedAtDate == nil {
            throw NetworkError.decoding("unsupported_problem_classification_completion_date")
        }
        let classificationCreatedAtDate = ISO8601WireDate.parse(classificationCreatedAt)
        if classificationCreatedAt != nil, classificationCreatedAtDate == nil {
            throw NetworkError.decoding("unsupported_problem_classification_revision_created_date")
        }
        return ProblemClassificationResult(
            classificationJobId: classificationJobId,
            problemSessionId: problemSessionId,
            canonicalProblemId: canonicalProblemId,
            canonicalProblemRevision: canonicalProblemRevision,
            jobStatus: jobStatus,
            capability: capability,
            attemptCount: attemptCount,
            maxAttempts: maxAttempts,
            lastErrorCode: lastErrorCode,
            lastFailureClass: lastFailureClass,
            classificationId: classificationId,
            classificationRevision: classificationRevision,
            classificationSource: classificationSource,
            classificationStatus: classificationStatus,
            reviewReason: reviewReason,
            subjectId: subjectId,
            topicId: topicId,
            primarySkillId: primarySkillId,
            secondarySkillIds: secondarySkillIds,
            difficulty: difficulty,
            confidenceBand: confidenceBand,
            confidenceCalibration: confidenceCalibration,
            provider: provider,
            model: model,
            fallbackUsed: fallbackUsed,
            ontologyVersion: ontologyVersion,
            projectionVersion: projectionVersion,
            schemaVersion: schemaVersion,
            difficultyPolicyVersion: difficultyPolicyVersion,
            confidencePolicyVersion: confidencePolicyVersion,
            createdAt: createdAtDate,
            updatedAt: updatedAtDate,
            completedAt: completedAtDate,
            classificationCreatedAt: classificationCreatedAtDate
        )
    }
}

private enum ISO8601WireDate {
    static func parse(_ value: String?) -> Date? {
        guard let value else {
            return nil
        }

        let fractionalFormatter = ISO8601DateFormatter()
        fractionalFormatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = fractionalFormatter.date(from: value) {
            return date
        }

        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value)
    }
}
