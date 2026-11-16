import XCTest
@testable import VerifiedAI

@MainActor
final class Sprint96ExamReadinessTests: XCTestCase {

    func testDecodesReadinessProjectionAndPreliminaryGuardInvariants() throws {
        let establishedJson = """
        {
          "projectionId": "96000000-0000-4000-8000-000000000011",
          "userId": "91000000-0000-4000-8000-000000000a01",
          "examCode": "TR_YKS_TYT_MATH",
          "targetId": "91000000-0000-4000-8000-000000000301",
          "latestScoredSessionId": "95000000-0000-4000-8000-000000000401",
          "totalQuestionCount": 40,
          "wrongAnswerPenaltyRatio": 0.25,
          "estimatedNetLow": 25.00,
          "estimatedNetMid": 28.25,
          "estimatedNetHigh": 31.50,
          "credibleIntervalLevel": 0.90,
          "targetNetScore": 33.50,
          "netGapToTarget": 5.25,
          "netLostToTraps": 2.75,
          "pacingFactor": 0.94,
          "meanRetentionProbability": 0.84,
          "epistemicConfidence": 0.64,
          "confidenceTier": "ESTABLISHED",
          "calibrationRequired": false,
          "recommendedDiagnosticQuestionCount": 0,
          "evaluatedSkillCount": 13,
          "totalEvidenceCount": 48,
          "completedMockCount": 2,
          "activeTrapBreakdown": [
            {
              "trapFamilyCode": "SIGN_ERROR",
              "trapTitleKey": "exam.readiness.trap.sign_error",
              "posteriorRate": 0.38,
              "estimatedNetLoss": 1.65,
              "affectedSkillCount": 2
            }
          ],
          "sectionReadiness": [
            {
              "sectionCode": "TYT_MATH",
              "sectionTitleKey": "TYT Mathematics",
              "questionCount": 40,
              "estimatedNetLow": 25.00,
              "estimatedNetMid": 28.25,
              "estimatedNetHigh": 31.50,
              "meanMastery": 0.72,
              "meanRetention": 0.84,
              "epistemicConfidence": 0.64
            }
          ],
          "topRoiAllocations": [
            {
              "allocationId": "96000000-0000-4000-8000-000000000501",
              "sectionCode": "TYT_MATH",
              "skillCode": "MATH.ALGEBRA.FACTORING",
              "topicTitleKey": "topic.math.algebra.factoring",
              "blueprintQuestionWeight": 8.0,
              "currentMastery": 0.56,
              "retentionProbability": 0.78,
              "expectedNetGainPerHour": 1.45,
              "recommendedStudyMinutes": 45,
              "priorityRank": 1,
              "rationaleCode": "ACTIVE_TRAP_MITIGATION",
              "trainingEligible": false
            }
          ],
          "methodologyVersion": "EXAM_READINESS_V1",
          "trainingEligible": false,
          "llmTokensUsed": 0,
          "inferenceCostUsd": 0.0,
          "computedAt": "2026-11-16T12:00:00Z"
        }
        """

        let decoder = JSONDecoder()
        let decoded = try decoder.decode(ExamReadinessProjection.self, from: Data(establishedJson.utf8))
        XCTAssertEqual(decoded.examCode, "TR_YKS_TYT_MATH")
        XCTAssertEqual(decoded.confidenceTier, .established)
        XCTAssertFalse(decoded.calibrationRequired)
        XCTAssertTrue(decoded.satisfiesIntervalOrderInvariant)
        XCTAssertEqual(decoded.credibleIntervalWidth, 6.50, accuracy: 0.01)
        XCTAssertFalse(decoded.trainingEligible)
        XCTAssertEqual(decoded.llmTokensUsed, 0)
        XCTAssertEqual(decoded.inferenceCostUsd, 0.0)
        XCTAssertEqual(decoded.topRoiAllocations.first?.rationaleCode, .activeTrapMitigation)

        let preliminary = FixtureExamAPIService.buildFixtureReadinessProjection(
            examCode: "YKS_TYT",
            userId: decoded.userId,
            preliminaryMode: true
        )
        XCTAssertTrue(preliminary.isPreliminaryGuardActive)
        XCTAssertEqual(preliminary.confidenceTier, .preliminary)
        XCTAssertGreaterThanOrEqual(preliminary.recommendedDiagnosticQuestionCount, 10)
        XCTAssertGreaterThanOrEqual(preliminary.credibleIntervalWidth, 12.0)
    }

    func testOfflineSQLiteStoreUserIsolationAndPurge() async {
        let store = ExamOfflineSQLiteStore(inMemory: true)
        let userA = UUID(uuidString: "96000000-0000-4000-8000-000000000a01")!
        let userB = UUID(uuidString: "96000000-0000-4000-8000-000000000b02")!

        let projA = FixtureExamAPIService.buildFixtureReadinessProjection(
            examCode: "TR_YKS_TYT_MATH",
            userId: userA,
            preliminaryMode: false
        )
        let projB = FixtureExamAPIService.buildFixtureReadinessProjection(
            examCode: "TR_YKS_AYT_MATH",
            userId: userB,
            preliminaryMode: true
        )

        await store.saveReadinessProjection(projA, for: userA)
        await store.saveReadinessProjection(projB, for: userB)

        let loadedA = await store.loadReadinessProjection(userId: userA, examCode: "TR_YKS_TYT_MATH")
        let crossA = await store.loadReadinessProjection(userId: userA, examCode: "TR_YKS_AYT_MATH")
        let loadedB = await store.loadReadinessProjection(userId: userB, examCode: "TR_YKS_AYT_MATH")

        XCTAssertEqual(loadedA?.projectionId, projA.projectionId)
        XCTAssertNil(crossA)
        XCTAssertEqual(loadedB?.projectionId, projB.projectionId)

        await store.purgeUserData(userId: userA)
        let purgedA = await store.loadReadinessProjection(userId: userA, examCode: "TR_YKS_TYT_MATH")
        let intactB = await store.loadReadinessProjection(userId: userB, examCode: "TR_YKS_AYT_MATH")
        XCTAssertNil(purgedA)
        XCTAssertEqual(intactB?.projectionId, projB.projectionId)
    }

    func testViewModelOnlineOfflineFallbackPreliminaryAndSimulatorMonotonicity() async {
        let userId = UUID(uuidString: "96000000-0000-4000-8000-000000000a09")!
        let offlineStore = ExamOfflineSQLiteStore(inMemory: true)
        let apiService = FixtureExamAPIService(simulateOffline: false, userId: userId)
        let vm = ExamReadinessViewModel(
            userId: userId,
            selectedExamCode: "YKS_TYT",
            highContrastMode: true,
            apiService: apiService,
            offlineStore: offlineStore
        )

        XCTAssertEqual(vm.selectedExamCode, "TR_YKS_TYT_MATH")
        await vm.loadReadiness()
        XCTAssertNotNil(vm.projection)
        XCTAssertFalse(vm.isOfflineFallback)
        XCTAssertFalse(vm.isFeatureDisabled)
        XCTAssertEqual(vm.projection?.confidenceTier, .established)

        // Simulator monotonicity
        vm.simulatedTrapReductionRatio = 0.0
        vm.simulatedAdditionalStudyHours = 0.0
        let baseMid = vm.simulatedProjectedNetMid

        vm.simulatedTrapReductionRatio = 0.5
        let trapHalfMid = vm.simulatedProjectedNetMid

        vm.simulatedTrapReductionRatio = 1.0
        vm.simulatedAdditionalStudyHours = 8.0
        let boostMid = vm.simulatedProjectedNetMid

        XCTAssertLessThanOrEqual(baseMid, trapHalfMid)
        XCTAssertLessThan(trapHalfMid, boostMid)
        XCTAssertLessThanOrEqual(vm.simulatedProjectedNetLow, vm.simulatedProjectedNetMid)
        XCTAssertLessThanOrEqual(vm.simulatedProjectedNetMid, vm.simulatedProjectedNetHigh)
        XCTAssertLessThanOrEqual(vm.simulatedProjectedNetHigh, 40.0)

        // Switch exam to AYT
        await vm.switchExam(to: "TR_YKS_AYT_MATH")
        XCTAssertEqual(vm.projection?.examCode, "TR_YKS_AYT_MATH")

        // Switch back and go offline -> should serve from SQLite cache cleanly
        apiService.simulateOffline = true
        await vm.switchExam(to: "YKS_TYT")
        XCTAssertTrue(vm.isOfflineFallback)
        XCTAssertEqual(vm.projection?.examCode, "TR_YKS_TYT_MATH")
        XCTAssertNil(vm.errorLocalizationKey)

        // Preliminary guard mode
        let prelimService = FixtureExamAPIService(
            simulateOffline: false,
            userId: userId,
            simulatePreliminaryReadiness: true
        )
        let prelimVM = ExamReadinessViewModel(
            userId: userId,
            selectedExamCode: "TR_YKS_TYT_MATH",
            apiService: prelimService,
            offlineStore: nil
        )
        await prelimVM.recompute()
        XCTAssertEqual(prelimVM.projection?.confidenceTier, .preliminary)
        XCTAssertEqual(prelimVM.projection?.calibrationRequired, true)
        XCTAssertGreaterThanOrEqual(prelimVM.projection?.recommendedDiagnosticQuestionCount ?? 0, 10)

        // Feature disabled handling
        let disabledVM = ExamReadinessViewModel(
            userId: userId,
            selectedExamCode: "TR_YKS_TYT_MATH",
            apiService: DisabledReadinessExamService()
        )
        await disabledVM.loadReadiness()
        XCTAssertTrue(disabledVM.isFeatureDisabled)
        XCTAssertEqual(disabledVM.errorLocalizationKey, "exam.readiness.error.feature_disabled")
    }
}

private final class DisabledReadinessExamService: ExamAPIServicing, @unchecked Sendable {
    func fetchDefinitions(jurisdiction: ExamJurisdiction?, examFamily: ExamFamily?, includeDeprecated: Bool) async throws -> ExamDefinitionCatalogResponse {
        throw ExamServiceError.featureDisabled
    }

    func fetchDefinitionDetail(examCode: String) async throws -> ExamDefinitionModel {
        throw ExamServiceError.featureDisabled
    }

    func fetchDefinitionVersion(examCode: String, examVersion: String) async throws -> ExamDefinitionModel {
        throw ExamServiceError.featureDisabled
    }

    func upsertTarget(request: UpsertStudentExamTargetRequestDTO) async throws -> StudentExamTargetModel {
        throw ExamServiceError.featureDisabled
    }

    func listTargets() async throws -> StudentExamTargetListResponse {
        throw ExamServiceError.featureDisabled
    }

    func upsertCandidateProfile(_ request: UpsertCandidateProfileRequest, idempotencyKey: String) async throws -> CandidateProfileResponse {
        throw ExamServiceError.featureDisabled
    }

    func fetchCandidateProfile() async throws -> CandidateProfileResponse {
        throw ExamServiceError.featureDisabled
    }

    func fetchCandidateProfileHistory() async throws -> CandidateProfileHistoryResponse {
        throw ExamServiceError.featureDisabled
    }

    func ingestSyllabus(fileData: Data, mimeType: String, filename: String, examCode: String, examVersion: String?, idempotencyKey: String) async throws -> SyllabusIngestionResponse {
        throw ExamServiceError.featureDisabled
    }

    func confirmSyllabusDisambiguation(uploadId: String, mappings: [SyllabusMappingConfirmation]) async throws -> SyllabusIngestionResponse {
        throw ExamServiceError.featureDisabled
    }

    func fetchBlueprints(examDefinitionId: UUID, examVersionNumber: Int?) async throws -> [ExamBlueprintDTO] {
        throw ExamServiceError.featureDisabled
    }

    func assembleAssessment(examDefinitionId: UUID, request: AssessmentAssemblyRequestDTO) async throws -> AssembledAssessmentDTO {
        throw ExamServiceError.featureDisabled
    }

    func fetchAssembledAssessment(userId: UUID, assessmentId: UUID) async throws -> AssembledAssessmentDTO {
        throw ExamServiceError.featureDisabled
    }

    func fetchAssembledAssessments(userId: UUID, examDefinitionId: UUID?) async throws -> [AssembledAssessmentDTO] {
        throw ExamServiceError.featureDisabled
    }

    func startOrResumeSession(request: ExamSessionStartRequestDTO) async throws -> ExamSessionDTO {
        throw ExamServiceError.featureDisabled
    }

    func fetchSessionState(sessionId: UUID, clientTimestamp: String?) async throws -> ExamSessionDTO {
        throw ExamServiceError.featureDisabled
    }

    func autosaveSessionItem(sessionId: UUID, itemPosition: Int, idempotencyKey: String, request: ExamSessionItemAutosaveRequestDTO) async throws -> ExamSessionItemAutosaveResponseDTO {
        throw ExamServiceError.featureDisabled
    }

    func attachScratchpadPhoto(sessionId: UUID, itemPosition: Int, idempotencyKey: String, request: ExamScratchpadUploadRequestDTO) async throws -> ExamScratchpadUploadResponseDTO {
        throw ExamServiceError.featureDisabled
    }

    func submitSession(sessionId: UUID, idempotencyKey: String, request: ExamSessionSubmitRequestDTO) async throws -> ExamSessionSubmitResponseDTO {
        throw ExamServiceError.featureDisabled
    }

    func checkNoHintGuard(sessionId: UUID, requestedSurface: String) async throws -> ExamNoHintGuardResponseDTO {
        throw ExamServiceError.featureDisabled
    }

    func computeOrGetScoreSummary(sessionId: UUID) async throws -> ExamSessionScoreSummaryResponse {
        throw ExamServiceError.featureDisabled
    }

    func fetchScoreSummary(sessionId: UUID) async throws -> ExamSessionScoreSummaryResponse {
        throw ExamServiceError.featureDisabled
    }

    func regradeSession(sessionId: UUID, request: ExamScoreRegradeRequest) async throws -> ExamScoreRegradeResponse {
        throw ExamServiceError.featureDisabled
    }

    func fetchRegradeAudits(sessionId: UUID) async throws -> ExamScoreRegradeAuditListResponse {
        throw ExamServiceError.featureDisabled
    }

    func fetchExamReadiness(examCode: String, recompute: Bool) async throws -> ExamReadinessProjection {
        throw ExamServiceError.featureDisabled
    }

    func recomputeExamReadiness(examCode: String) async throws -> ExamReadinessProjection {
        throw ExamServiceError.featureDisabled
    }
}
