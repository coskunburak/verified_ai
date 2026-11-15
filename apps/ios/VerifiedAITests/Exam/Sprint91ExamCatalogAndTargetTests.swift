import XCTest
@testable import VerifiedAI

@MainActor
final class Sprint91ExamCatalogAndTargetTests: XCTestCase {

    func testDecodesServerExamCatalogDetailAndTargetPayloads() throws {
        let catalogJson = """
        {
          "schemaVersion": "exam-definition-catalog-v1",
          "definitions": [
            {
              "examDefinitionId": "91000000-0000-4000-8000-000000000101",
              "examCode": "TR_YKS_TYT_MATH",
              "examVersion": "2026.1",
              "jurisdiction": "TR",
              "examFamily": "YKS",
              "titleEn": "YKS TYT Mathematics",
              "titleTr": "YKS TYT Matematik",
              "status": "ACTIVE",
              "totalQuestions": 40,
              "durationMinutes": 65,
              "scoringFormulaType": "CORRECT_MINUS_QUARTER_WRONG",
              "wrongPenaltyFactor": 0.25,
              "minScore": -10.0,
              "maxScore": 40.0,
              "navigationPolicy": "FREE_JUMP",
              "blueprintHash": "c1cb8c26245fdee1b53c164a94e35a6cecbde8bae6ece948e60f577df53e1391",
              "effectiveFrom": "2026-01-01T00:00:00Z",
              "deprecatedAt": null,
              "supersededByVersion": null,
              "sectionCount": 1,
              "topicWeightCount": 13,
              "trainingEligible": false
            }
          ],
          "totalCount": 1,
          "includeDeprecated": false,
          "trainingEligible": false,
          "llmTokensUsed": 0
        }
        """

        let catalog = try JSONDecoder().decode(
            ExamDefinitionCatalogResponse.self,
            from: Data(catalogJson.utf8)
        )
        XCTAssertEqual(catalog.schemaVersion, "exam-definition-catalog-v1")
        XCTAssertEqual(catalog.definitions.count, 1)
        XCTAssertEqual(catalog.definitions[0].examCode, "TR_YKS_TYT_MATH")
        XCTAssertEqual(catalog.definitions[0].scoringFormulaType, .correctMinusQuarterWrong)
        XCTAssertFalse(catalog.trainingEligible)
        XCTAssertEqual(catalog.llmTokensUsed, 0)
    }

    func testOfflineSQLiteStoreCachesAndIsolatesPurgeByUser() async throws {
        let store = ExamOfflineSQLiteStore(inMemory: true)
        let userA = UUID(uuidString: "91000000-0000-4000-8000-000000000a01")!
        let userB = UUID(uuidString: "91000000-0000-4000-8000-000000000b02")!

        let fixtureServiceA = FixtureExamAPIService(simulateOffline: false, userId: userA)
        let catalog = try await fixtureServiceA.fetchDefinitions(jurisdiction: nil, examFamily: nil, includeDeprecated: false)
        let detail = try await fixtureServiceA.fetchDefinitionDetail(examCode: "TR_YKS_TYT_MATH")
        let targetA = try await fixtureServiceA.upsertTarget(request: UpsertStudentExamTargetRequestDTO(
            examCode: "TR_YKS_TYT_MATH",
            examVersion: "2026.1",
            expectedBlueprintHash: detail.blueprintHash,
            targetScore: 34.0,
            targetDate: "2027-06-20",
            idempotencyKey: "unit-test-a-1"
        ))

        let fixtureServiceB = FixtureExamAPIService(simulateOffline: false, userId: userB)
        let targetB = try await fixtureServiceB.upsertTarget(request: UpsertStudentExamTargetRequestDTO(
            examCode: "INTL_SAT_MATH",
            examVersion: "2026.1",
            expectedBlueprintHash: nil,
            targetScore: 760.0,
            targetDate: "2027-05-10",
            idempotencyKey: "unit-test-b-1"
        ))

        await store.saveCatalog(catalog)
        await store.saveDefinitionDetail(detail)
        await store.saveTargets([targetA], for: userA)
        await store.saveTargets([targetB], for: userB)

        let loadedCatalog = await store.loadCatalog()
        XCTAssertEqual(loadedCatalog?.definitions.count, 2)
        let loadedDetail = await store.loadDefinitionDetail(examCode: "TR_YKS_TYT_MATH")
        XCTAssertEqual(loadedDetail?.blueprintHash, detail.blueprintHash)

        // Purge User A -> User A targets cleared, User B untouched
        await store.purgeUserData(userId: userA)
        let afterPurgeA = await store.loadTargets(for: userA)
        let afterPurgeB = await store.loadTargets(for: userB)
        XCTAssertTrue(afterPurgeA.isEmpty)
        XCTAssertEqual(afterPurgeB.count, 1)
        XCTAssertEqual(afterPurgeB[0].examCode, "INTL_SAT_MATH")
    }

    func testViewModelLoadBindValidationAndOfflineFallback() async throws {
        let userId = UUID(uuidString: "91000000-0000-4000-8000-000000000c01")!
        let store = ExamOfflineSQLiteStore(inMemory: true)
        let liveService = FixtureExamAPIService(simulateOffline: false, userId: userId)
        let viewModel = ExamCatalogAndTargetViewModel(service: liveService, cacheStore: store)
        viewModel.updateLearnerId(userId)

        await viewModel.loadCatalogAndTargets()
        XCTAssertEqual(viewModel.definitions.count, 2)
        XCTAssertEqual(viewModel.selectedDetail?.examCode, "TR_YKS_TYT_MATH")
        XCTAssertFalse(viewModel.isOfflineBannerVisible)

        // Out-of-bounds target score rejected client-side using server min/max
        viewModel.targetScoreInput = "999"
        await viewModel.savePrimaryTarget()
        XCTAssertEqual(viewModel.errorMessageKey, "exam.target.score_out_of_range")

        // Valid target score saved cleanly
        viewModel.targetScoreInput = "36.5"
        await viewModel.savePrimaryTarget()
        XCTAssertEqual(viewModel.statusMessageKey, "exam.target.saved_banner")
        XCTAssertEqual(viewModel.primaryActiveTarget?.examCode, "TR_YKS_TYT_MATH")
        XCTAssertEqual(viewModel.primaryActiveTarget?.readinessGapPreview.llmTokensUsed, 0)

        // Offline recovery from SQLite store displays offline banner cleanly
        let offlineService = FixtureExamAPIService(simulateOffline: true, userId: userId)
        let offlineViewModel = ExamCatalogAndTargetViewModel(service: offlineService, cacheStore: store)
        offlineViewModel.updateLearnerId(userId)
        await offlineViewModel.loadCatalogAndTargets()
        XCTAssertTrue(offlineViewModel.isOfflineBannerVisible)
        XCTAssertEqual(offlineViewModel.definitions.count, 2)
        XCTAssertEqual(offlineViewModel.selectedDetail?.examCode, "TR_YKS_TYT_MATH")
        XCTAssertEqual(offlineViewModel.primaryActiveTarget?.examCode, "TR_YKS_TYT_MATH")
    }

    func testViewModelHandlesFeatureDisabledGracefully() async {
        struct DisabledExamService: ExamAPIServicing {
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
        }

        let store = ExamOfflineSQLiteStore(inMemory: true)
        let viewModel = ExamCatalogAndTargetViewModel(service: DisabledExamService(), cacheStore: store)
        await viewModel.loadCatalogAndTargets()
        XCTAssertTrue(viewModel.isFeatureDisabled)
        XCTAssertEqual(viewModel.errorMessageKey, "exam.catalog.feature_disabled")
    }
}
