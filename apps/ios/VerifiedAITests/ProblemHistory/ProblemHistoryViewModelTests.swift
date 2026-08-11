import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class ProblemHistoryViewModelTests: XCTestCase {
    func testBootstrapUsesCachedHistoryWhenOffline() async throws {
        let cache = FakeProblemSessionCache(items: [.fixture(nextAction: .reviewParse)])
        let historyAPI = FakeProblemSessionHistoryAPI()
        let viewModel = makeViewModel(
            historyAPI: historyAPI,
            cache: cache,
            networkMonitor: StubNetworkMonitor(isReachable: false)
        )

        await viewModel.bootstrap()

        XCTAssertEqual(viewModel.state, .offline)
        XCTAssertEqual(viewModel.items.map(\.problemSessionId), [ProblemSessionHistoryItem.fixture().problemSessionId])
        let historyCalls = await historyAPI.historyCalls()
        XCTAssertTrue(historyCalls.isEmpty)
    }

    func testRefreshLoadsFirstPageAndCachesIt() async throws {
        let page = ProblemSessionHistoryPage(items: [.fixture(nextAction: .startRecognition)], nextCursor: "cursor-1")
        let cache = FakeProblemSessionCache()
        let historyAPI = FakeProblemSessionHistoryAPI(historyPages: [page])
        let viewModel = makeViewModel(historyAPI: historyAPI, cache: cache)

        await viewModel.refresh()

        XCTAssertEqual(viewModel.state, .ready)
        XCTAssertEqual(viewModel.items, page.items)
        XCTAssertEqual(viewModel.nextCursor, "cursor-1")
        XCTAssertEqual(cache.savedPages, [page])
    }

    func testPerformNextActionRetriesParseOnlyForParseRecoveryAction() async throws {
        let detail = ProblemSessionDetail.fixture(nextAction: .retryParse)
        let refreshedDetail = ProblemSessionDetail.fixture(nextAction: .waitParse)
        let historyAPI = FakeProblemSessionHistoryAPI(details: [refreshedDetail])
        let workflowAPI = FakeProblemAssetUploadAPI()
        let viewModel = makeViewModel(historyAPI: historyAPI, workflowAPI: workflowAPI)

        await viewModel.performNextAction(for: detail)

        let parseRequests = await workflowAPI.parseSessionIds()
        let recognitionRequests = await workflowAPI.recognitionSessionIds()
        XCTAssertEqual(parseRequests, [detail.problemSessionId])
        XCTAssertEqual(recognitionRequests, [])
        XCTAssertEqual(viewModel.selectedDetail, refreshedDetail)
    }

    func testPerformNextActionCanonicalizesForCanonicalizeRecoveryAction() async throws {
        let detail = ProblemSessionDetail.fixture(nextAction: .canonicalize)
        let refreshedDetail = ProblemSessionDetail.fixture(nextAction: .startClassification)
        let historyAPI = FakeProblemSessionHistoryAPI(details: [refreshedDetail])
        let workflowAPI = FakeProblemAssetUploadAPI()
        let viewModel = makeViewModel(historyAPI: historyAPI, workflowAPI: workflowAPI)

        await viewModel.performNextAction(for: detail)

        let canonicalizeRequests = await workflowAPI.canonicalizeSessionIds()
        let classificationRequests = await workflowAPI.classificationSessionIds()
        XCTAssertEqual(canonicalizeRequests, [detail.problemSessionId])
        XCTAssertEqual(classificationRequests, [])
        XCTAssertEqual(viewModel.selectedDetail, refreshedDetail)
    }

    func testPerformNextActionStartsClassificationForClassificationRecoveryActions() async throws {
        for action in [ProblemSessionNextAction.startClassification, .retryClassification] {
            let detail = ProblemSessionDetail.fixture(nextAction: action)
            let refreshedDetail = ProblemSessionDetail.fixture(nextAction: .waitClassification)
            let historyAPI = FakeProblemSessionHistoryAPI(details: [refreshedDetail])
            let workflowAPI = FakeProblemAssetUploadAPI()
            let viewModel = makeViewModel(historyAPI: historyAPI, workflowAPI: workflowAPI)

            await viewModel.performNextAction(for: detail)

            let canonicalizeRequests = await workflowAPI.canonicalizeSessionIds()
            let classificationRequests = await workflowAPI.classificationSessionIds()
            XCTAssertEqual(canonicalizeRequests, [])
            XCTAssertEqual(classificationRequests, [detail.problemSessionId])
            XCTAssertEqual(viewModel.selectedDetail, refreshedDetail)
        }
    }

    private func makeViewModel(
        historyAPI: FakeProblemSessionHistoryAPI = FakeProblemSessionHistoryAPI(),
        workflowAPI: FakeProblemAssetUploadAPI = FakeProblemAssetUploadAPI(),
        cache: FakeProblemSessionCache = FakeProblemSessionCache(),
        networkMonitor: StubNetworkMonitor = StubNetworkMonitor(isReachable: true)
    ) -> ProblemHistoryViewModel {
        ProblemHistoryViewModel(
            historyAPI: historyAPI,
            workflowAPI: workflowAPI,
            cache: cache,
            networkMonitor: networkMonitor,
            logger: AppLogger(subsystem: "com.verifiedai.learning.tests", category: "problem-history")
        )
    }
}

@MainActor
private final class FakeProblemSessionCache: ProblemSessionCaching {
    private var cachedItems: [ProblemSessionHistoryItem]
    private(set) var savedPages: [ProblemSessionHistoryPage] = []
    private(set) var savedDetails: [ProblemSessionDetail] = []

    init(items: [ProblemSessionHistoryItem] = []) {
        cachedItems = items
    }

    func loadSummaries(limit: Int) throws -> [ProblemSessionHistoryItem] {
        Array(cachedItems.prefix(limit))
    }

    func save(page: ProblemSessionHistoryPage) throws {
        savedPages.append(page)
        cachedItems = page.items
    }

    func save(detail: ProblemSessionDetail) throws {
        savedDetails.append(detail)
        let item = detail.historyItem
        if let index = cachedItems.firstIndex(where: { $0.problemSessionId == item.problemSessionId }) {
            cachedItems[index] = item
        } else {
            cachedItems.insert(item, at: 0)
        }
    }

    func clear() throws {
        cachedItems = []
    }
}

private actor FakeProblemSessionHistoryAPI: ProblemSessionHistoryServicing {
    private var historyPages: [ProblemSessionHistoryPage]
    private var details: [ProblemSessionDetail]
    private var receivedHistoryCalls: [(limit: Int, cursor: String?)] = []

    init(historyPages: [ProblemSessionHistoryPage] = [], details: [ProblemSessionDetail] = []) {
        self.historyPages = historyPages
        self.details = details
    }

    func history(limit: Int, cursor: String?) async throws -> ProblemSessionHistoryPage {
        receivedHistoryCalls.append((limit: limit, cursor: cursor))
        if historyPages.isEmpty {
            return ProblemSessionHistoryPage(items: [], nextCursor: nil)
        }
        return historyPages.removeFirst()
    }

    func detail(problemSessionId: UUID) async throws -> ProblemSessionDetail {
        if details.isEmpty {
            return .fixture(problemSessionId: problemSessionId)
        }
        return details.removeFirst()
    }

    func historyCalls() -> [(limit: Int, cursor: String?)] {
        receivedHistoryCalls
    }
}

private actor FakeProblemAssetUploadAPI: ProblemAssetUploadServicing {
    private var recognitionRequests: [UUID] = []
    private var parseRequests: [UUID] = []
    private var canonicalizeRequests: [UUID] = []
    private var classificationRequests: [UUID] = []

    func reserveUpload(_ request: ProblemAssetUploadRequest, idempotencyKey: String) async throws -> ProblemAssetUploadReservation {
        throw NetworkError.invalidResponse
    }

    func completeUpload(uploadId: UUID, idempotencyKey: String) async throws -> DurableProblemAssetReference {
        throw NetworkError.invalidResponse
    }

    func preprocessAsset(problemAssetId: UUID) async throws -> ProblemAssetPreprocessingResult {
        throw NetworkError.invalidResponse
    }

    func getPreprocessing(problemAssetId: UUID) async throws -> ProblemAssetPreprocessingResult {
        throw NetworkError.invalidResponse
    }

    func requestRecognition(problemSessionId: UUID) async throws -> ProblemRecognitionResult {
        recognitionRequests.append(problemSessionId)
        return .fixture(problemSessionId: problemSessionId, status: "QUEUED")
    }

    func getRecognition(problemSessionId: UUID) async throws -> ProblemRecognitionResult {
        .fixture(problemSessionId: problemSessionId, status: "QUEUED")
    }

    func requestParse(problemSessionId: UUID) async throws -> ProblemParseResult {
        parseRequests.append(problemSessionId)
        return .fixture(problemSessionId: problemSessionId, status: "QUEUED")
    }

    func getParse(problemSessionId: UUID) async throws -> ProblemParseResult {
        .fixture(problemSessionId: problemSessionId, status: "QUEUED")
    }

    func canonicalize(problemSessionId: UUID) async throws -> CanonicalProblemResult {
        canonicalizeRequests.append(problemSessionId)
        return .fixture(problemSessionId: problemSessionId)
    }

    func getCanonicalProblem(problemSessionId: UUID) async throws -> CanonicalProblemResult {
        .fixture(problemSessionId: problemSessionId)
    }

    func requestClassification(problemSessionId: UUID) async throws -> ProblemClassificationResult {
        classificationRequests.append(problemSessionId)
        return .fixture(problemSessionId: problemSessionId, jobStatus: "QUEUED")
    }

    func getClassification(problemSessionId: UUID) async throws -> ProblemClassificationResult {
        .fixture(problemSessionId: problemSessionId, jobStatus: "QUEUED")
    }

    func recognitionSessionIds() -> [UUID] {
        recognitionRequests
    }

    func parseSessionIds() -> [UUID] {
        parseRequests
    }

    func canonicalizeSessionIds() -> [UUID] {
        canonicalizeRequests
    }

    func classificationSessionIds() -> [UUID] {
        classificationRequests
    }
}

@MainActor
private final class StubNetworkMonitor: NetworkMonitoring {
    var isReachable: Bool

    init(isReachable: Bool) {
        self.isReachable = isReachable
    }
}

private extension ProblemSessionHistoryItem {
    static func fixture(
        problemSessionId: UUID = UUID(uuidString: "00000000-0000-0000-0000-000000004901")!,
        nextAction: ProblemSessionNextAction = .reviewParse
    ) -> ProblemSessionHistoryItem {
        ProblemSessionHistoryItem(
            problemSessionId: problemSessionId,
            status: .reviewRequired,
            stage: .parseReview,
            inputMode: "CAMERA",
            nextAction: nextAction,
            retryable: false,
            reviewRequired: true,
            currentParseRevision: 2,
            currentParseSource: "USER",
            classificationStatus: nil,
            primarySkillId: nil,
            difficulty: nil,
            createdAt: Date(timeIntervalSince1970: 1_800_000_000),
            updatedAt: Date(timeIntervalSince1970: 1_800_000_100),
            completedAt: nil
        )
    }
}

private extension ProblemSessionDetail {
    static func fixture(
        problemSessionId: UUID = UUID(uuidString: "00000000-0000-0000-0000-000000004901")!,
        nextAction: ProblemSessionNextAction = .reviewParse
    ) -> ProblemSessionDetail {
        ProblemSessionDetail(
            problemSessionId: problemSessionId,
            status: .reviewRequired,
            stage: nextAction == .waitParse ? .parsing : .parseReview,
            inputMode: "CAMERA",
            nextAction: nextAction,
            retryable: nextAction == .retryParse,
            reviewRequired: nextAction == .reviewParse,
            failureCode: nil,
            currentParse: ProblemSessionCurrentParseSummary(
                problemParseId: UUID(uuidString: "00000000-0000-0000-0000-000000004902")!,
                revision: 2,
                source: "USER",
                supportStatus: "REVIEW_REQUIRED",
                reviewRequired: true
            ),
            canonicalProblem: nil,
            classification: nil,
            activeJob: nil,
            createdAt: Date(timeIntervalSince1970: 1_800_000_000),
            updatedAt: Date(timeIntervalSince1970: 1_800_000_100),
            completedAt: nil,
            version: 3
        )
    }
}

private extension ProblemRecognitionResult {
    static func fixture(problemSessionId: UUID, status: String) -> ProblemRecognitionResult {
        ProblemRecognitionResult(
            recognitionJobId: UUID(uuidString: "00000000-0000-0000-0000-000000004903")!,
            problemSessionId: problemSessionId,
            sourceAssetId: nil,
            inputDerivativeId: nil,
            status: status,
            capability: "VISION_PARSE",
            attemptCount: 0,
            maxAttempts: 2,
            lastErrorCode: nil,
            lastFailureClass: nil,
            reviewRequired: false,
            schemaVersion: nil,
            promptId: nil,
            promptVersion: nil,
            routePolicyVersion: nil,
            provider: nil,
            model: nil,
            blockCount: 0,
            blocks: [],
            completedAt: nil
        )
    }
}

private extension ProblemParseResult {
    static func fixture(problemSessionId: UUID, status: String) -> ProblemParseResult {
        ProblemParseResult(
            parseJobId: UUID(uuidString: "00000000-0000-0000-0000-000000004904")!,
            problemSessionId: problemSessionId,
            recognitionEvidenceId: nil,
            recognitionEvidenceRevision: nil,
            jobStatus: status,
            capability: "PROBLEM_NORMALIZE",
            attemptCount: 0,
            maxAttempts: 2,
            lastErrorCode: nil,
            lastFailureClass: nil,
            problemParseId: nil,
            parseRevision: nil,
            supportStatus: nil,
            unsupportedReason: nil,
            reviewRequired: false,
            schemaVersion: nil,
            promptId: nil,
            promptVersion: nil,
            routePolicyVersion: nil,
            provider: nil,
            model: nil,
            normalizedProblem: nil,
            createdAt: nil,
            updatedAt: nil,
            completedAt: nil
        )
    }
}

private extension CanonicalProblemResult {
    static func fixture(problemSessionId: UUID) -> CanonicalProblemResult {
        CanonicalProblemResult(
            canonicalProblemId: UUID(uuidString: "00000000-0000-0000-0000-000000004905")!,
            problemSessionId: problemSessionId,
            problemParseId: UUID(uuidString: "00000000-0000-0000-0000-000000004902")!,
            problemParseRevision: 2,
            canonicalRevision: 1,
            schemaVersion: "canonical-problem-v1",
            verifierSchemaVersion: "safe-verifier-v1",
            problemType: "EQUATION",
            taskType: "SOLVE_EQUATION",
            normalizedText: "x + 2 = 5",
            displayLatex: "x + 2 = 5",
            variables: ["x"],
            sourceConstraintCount: 0,
            derivedRestrictionCount: 0,
            createdAt: Date(timeIntervalSince1970: 1_800_000_200)
        )
    }
}

private extension ProblemClassificationResult {
    static func fixture(problemSessionId: UUID, jobStatus: String) -> ProblemClassificationResult {
        ProblemClassificationResult(
            classificationJobId: UUID(uuidString: "00000000-0000-0000-0000-000000004906")!,
            problemSessionId: problemSessionId,
            canonicalProblemId: UUID(uuidString: "00000000-0000-0000-0000-000000004905")!,
            canonicalProblemRevision: 1,
            jobStatus: jobStatus,
            capability: "PROBLEM_CLASSIFY",
            attemptCount: 0,
            maxAttempts: 2,
            lastErrorCode: nil,
            lastFailureClass: nil,
            classificationId: nil,
            classificationRevision: nil,
            classificationSource: nil,
            classificationStatus: nil,
            reviewReason: nil,
            subjectId: nil,
            topicId: nil,
            primarySkillId: nil,
            secondarySkillIds: [],
            difficulty: nil,
            confidenceBand: nil,
            confidenceCalibration: nil,
            provider: nil,
            model: nil,
            fallbackUsed: nil,
            ontologyVersion: "curriculum-v1-seed",
            projectionVersion: "classification-projection-v1",
            schemaVersion: "problem-classification-v1",
            difficultyPolicyVersion: "classification-difficulty-v1",
            confidencePolicyVersion: "classification-confidence-v1",
            createdAt: nil,
            updatedAt: nil,
            completedAt: nil,
            classificationCreatedAt: nil
        )
    }
}
