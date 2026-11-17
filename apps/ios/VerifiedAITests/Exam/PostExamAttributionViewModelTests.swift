import XCTest
import SwiftUI
@testable import VerifiedAI

private actor AttributionAPIStub: ExamAttributionServicing {
    let report: ExamAttributionReportResponse
    var error: ExamServiceError?
    var applyCount = 0
    var keys: [String] = []
    var practiceRequests: [String] = []
    var microRequests: [String] = []
    init(report: ExamAttributionReportResponse, error: ExamServiceError? = nil) { self.report = report; self.error = error }
    func fetchAttributionReport(sessionId: UUID) async throws -> ExamAttributionReportResponse {
        if let error { throw error }; return report
    }
    func applyStudyRebalance(sessionId: UUID, request: ExamApplyStudyRebalanceRequest, idempotencyKey: String) async throws -> ExamApplyStudyRebalanceResponse {
        applyCount += 1; keys.append(idempotencyKey)
        try await Task.sleep(for: .milliseconds(20))
        return .init(batchId: UUID(), sourceSessionId: sessionId, userId: report.userId, examCode: report.examCode,
                     appliedToStudyPlan: true, idempotentReplay: false, rebalancedStudyPlanId: UUID(),
                     rebalancedTasksCount: 3, totalExpectedNetRecovery: 0.5, appliedDirectives: [],
                     appliedAt: "2026-11-17T10:00:00Z", trainingEligible: false)
    }
    func calls() -> Int { applyCount }
    func recordedKeys() -> [String] { keys }
    func startRemediationPractice(skillId: String, mode: PracticeSessionMode) async throws -> PracticeSession {
        practiceRequests.append("\(skillId):\(mode.rawValue)")
        return .init(id: report.sessionId, userId: report.userId, sourceRecommendationId: nil,
                     sessionMode: mode, status: .inProgress, targetSkillId: skillId, activeTrapFamily: nil,
                     learnerMasterySnapshot: 0.5, totalItems: 0, completedItems: 0, verifiedCorrectItems: 0,
                     trapClosureAchieved: false, startedAt: Date(timeIntervalSince1970: 0), completedAt: nil,
                     abandonedAt: nil, updatedAt: Date(timeIntervalSince1970: 0), items: [])
    }
    func fetchMicroLesson(mistakeCode: String, locale: String, triggerSource: MicroLessonTriggerSource, sourceAttemptId: UUID?) async throws -> MicroLessonCard {
        microRequests.append("\(mistakeCode):\(locale):\(triggerSource.rawValue)")
        throw MicroLessonClientError.diagnosisNotFound
    }
    func recordedPracticeRequests() -> [String] { practiceRequests }
    func recordedMicroRequests() -> [String] { microRequests }
}

private struct RemediationTokenStub: AuthTokenProvider {
    func accessToken() async throws -> String? { "synthetic-test-token" }
}

private actor RemediationHTTPSessionStub: HTTPSession {
    let payload: Data
    let status: Int
    private var requests: [URLRequest] = []
    init(payload: Data, status: Int = 200) { self.payload = payload; self.status = status }
    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        requests.append(request)
        return (payload, HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!)
    }
    func lastRequest() -> URLRequest? { requests.last }
}

@MainActor
final class PostExamAttributionViewModelTests: XCTestCase {
    private func fixture() throws -> ExamAttributionReportResponse {
        try JSONDecoder().decode(ExamAttributionReportResponse.self, from: Data(Self.fixtureJSON.utf8))
    }
    func testOnlineLoadAndPrivacySafeTelemetry() async throws {
        let report = try fixture(); let cache = ExamOfflineSQLiteStore(inMemory: true)
        var events: [(String, [String: String])] = []
        let vm = PostExamAttributionViewModel(sessionId: report.sessionId, userId: report.userId,
            api: AttributionAPIStub(report: report), cache: cache, telemetry: { events.append(($0, $1)) })
        await vm.load()
        XCTAssertEqual(vm.report, report); XCTAssertFalse(vm.isOffline)
        let saved = try await cache.loadAttributionReport(sessionId: report.sessionId)
        XCTAssertEqual(saved, report)
        XCTAssertEqual(events.first?.0, "exam_attribution_generated")
        XCTAssertEqual(Set(events.first!.1.keys), Set(["sessionId", "examCode"]))
    }
    func testOfflineCacheAndAccountIsolation() async throws {
        let report = try fixture(); let cache = ExamOfflineSQLiteStore(inMemory: true)
        try await cache.saveAttributionReport(report)
        let api = AttributionAPIStub(report: report, error: .offline)
        let vm = PostExamAttributionViewModel(sessionId: report.sessionId, userId: report.userId, api: api, cache: cache)
        await vm.load(); XCTAssertEqual(vm.report, report); XCTAssertTrue(vm.isOffline)
        await vm.applyTopRoiToStudyPlan(); let count = await api.calls(); XCTAssertEqual(count, 0)
        let other = PostExamAttributionViewModel(sessionId: report.sessionId, userId: UUID(), api: api, cache: cache)
        await other.load(); XCTAssertNil(other.report)
    }
    func testForbiddenDoesNotFallBackToCachedReport() async throws {
        let report = try fixture(); let cache = ExamOfflineSQLiteStore(inMemory: true)
        try await cache.saveAttributionReport(report)
        let vm = PostExamAttributionViewModel(sessionId: report.sessionId, userId: report.userId,
            api: AttributionAPIStub(report: report, error: .forbidden), cache: cache)
        await vm.load(); XCTAssertNil(vm.report); XCTAssertFalse(vm.isOffline)
    }
    func testRebalanceDoubleTapUsesStableKeyAndUpdatesCache() async throws {
        let report = try fixture(); let api = AttributionAPIStub(report: report)
        let cache = ExamOfflineSQLiteStore(inMemory: true)
        let vm = PostExamAttributionViewModel(sessionId: report.sessionId, userId: report.userId, api: api, cache: cache)
        await vm.load()
        let first = Task { await vm.applyTopRoiToStudyPlan() }
        await Task.yield(); await vm.applyTopRoiToStudyPlan(); await first.value
        await vm.applyTopRoiToStudyPlan()
        let count = await api.calls(); let keys = await api.recordedKeys()
        XCTAssertEqual(count, 1); XCTAssertEqual(keys, ["rebalance-\(report.sessionId.uuidString.lowercased())"])
        let saved = try await cache.loadAttributionReport(sessionId: report.sessionId)
        XCTAssertEqual(saved?.studyPlanRebalanced, true)
    }
    func testTwinAndMicroLessonRoutesAndTelemetryAllowlist() async throws {
        var report = try fixture(); var micro = report.topRoiAllocations[0]
        micro.skillId = "synthetic-micro-skill"; micro.recommendedActionType = .microLesson
        micro.recommendedTargetRef = "microlesson:synthetic-micro-skill"; report.topRoiAllocations.append(micro)
        var events: [(String, [String: String])] = []
        let vm = PostExamAttributionViewModel(sessionId: report.sessionId, userId: report.userId,
            api: AttributionAPIStub(report: report), telemetry: { events.append(($0, $1)) })
        await vm.load(); vm.startImmediateRemediation(for: report.topRoiAllocations[0])
        XCTAssertEqual(vm.selectedRemediationRoute?.actionType, .twinDrill)
        XCTAssertEqual(vm.selectedRemediationRoute?.destination, .practice(mode: .trapVerificationDrill))
        vm.selectedRemediationRoute = nil // Native sheet dismissal allows the next destination.
        vm.startImmediateRemediation(for: micro)
        XCTAssertEqual(vm.selectedRemediationRoute?.actionType, .microLesson)
        XCTAssertEqual(vm.selectedRemediationRoute?.destination, .microLesson(mistakeCode: "CALC_CHAIN_RULE_OMITTED"))
        XCTAssertEqual(events.last?.0, "net_gain_simulator_task_started")
        XCTAssertEqual(Set(events.last!.1.keys), Set(["sessionId", "examCode", "actionType", "rank"]))
    }
    func testPurgeUserAndClearAllDeleteAttributionCache() async throws {
        let report = try fixture(); let cache = ExamOfflineSQLiteStore(inMemory: true)
        try await cache.saveAttributionReport(report); await cache.purgeUserData(userId: report.userId)
        let first = try await cache.loadAttributionReport(sessionId: report.sessionId); XCTAssertNil(first)
        try await cache.saveAttributionReport(report); await cache.clearAllUserData()
        let second = try await cache.loadAttributionReport(sessionId: report.sessionId); XCTAssertNil(second)
    }
    func testAX3ScreenCanRenderLongTurkishContent() async throws {
        var report = try fixture()
        report.topRoiAllocations[0].rationale = String(repeating: "Zincir kuralını adım adım uygulayarak bileşke fonksiyonun türevini doğrula. ", count: 8)
        let vm = PostExamAttributionViewModel(sessionId: report.sessionId, userId: report.userId, api: AttributionAPIStub(report: report))
        await vm.load()
        let controller = UIHostingController(rootView: PostExamAttributionView(viewModel: vm).environment(\.dynamicTypeSize, .accessibility3))
        controller.loadViewIfNeeded(); controller.view.frame = CGRect(x: 0, y: 0, width: 393, height: 852)
        controller.view.layoutIfNeeded(); XCTAssertNotNil(controller.view)
        XCTAssertEqual(vm.report?.topRoiAllocations[0].rationale, report.topRoiAllocations[0].rationale)
    }

    func testUncodedMicroLessonAndTimingUseNativeSkillPractice() throws {
        var item = try fixture().topRoiAllocations[0]
        item.recommendedActionType = .microLesson
        let codes: [String?] = [nil, "", "   "]
        for code in codes {
            item.matchedMistakeCode = code
            XCTAssertEqual(ExamRemediationRoute(item: item).destination, .practice(mode: .examRoiSynthesis))
        }
        for action in [ExamRemediationActionType.timedSpeedDrill, .triageStrategyReview] {
            item.recommendedActionType = action
            XCTAssertEqual(ExamRemediationRoute(item: item).destination, .practice(mode: .examRoiSynthesis))
        }
    }

    func testRemediationIgnoresUnknownItemAndDuplicateTap() async throws {
        let report = try fixture()
        var events: [String] = []
        let vm = PostExamAttributionViewModel(sessionId: report.sessionId, userId: report.userId,
            api: AttributionAPIStub(report: report), telemetry: { event, _ in events.append(event) })
        await vm.load()
        var unknown = report.topRoiAllocations[0]; unknown.skillId = "unreported-skill"
        vm.startImmediateRemediation(for: unknown); XCTAssertNil(vm.selectedRemediationRoute)
        vm.startImmediateRemediation(for: report.topRoiAllocations[0])
        vm.startImmediateRemediation(for: report.topRoiAllocations[0])
        XCTAssertEqual(events.filter { $0 == "net_gain_simulator_task_started" }.count, 1)
    }

    func testNativePracticeFactoryUsesInjectedServiceAndSkill() async throws {
        let report = try fixture(); let api = AttributionAPIStub(report: report)
        let vm = PostExamAttributionViewModel(sessionId: report.sessionId, userId: report.userId, api: api)
        let route = ExamRemediationRoute(item: report.topRoiAllocations[0])
        let practice = vm.makePracticeViewModel(for: route)
        await practice.loadOrCreateSession(targetSkillId: route.skillId, sessionMode: .trapVerificationDrill)
        let calls = await api.recordedPracticeRequests()
        XCTAssertEqual(calls, ["\(route.skillId):TRAP_VERIFICATION_DRILL"])
        XCTAssertEqual(practice.session?.id, report.sessionId)
        XCTAssertEqual(practice.session?.userId, report.userId)
        XCTAssertFalse(practice.isOffline); XCTAssertNil(practice.errorMessage)
    }

    func testNativePracticeRejectsOtherOwnerWithoutSimulatedFallback() async throws {
        let report = try fixture(); let api = AttributionAPIStub(report: report)
        let vm = PostExamAttributionViewModel(sessionId: report.sessionId, userId: UUID(), api: api)
        let route = ExamRemediationRoute(item: report.topRoiAllocations[0])
        let practice = vm.makePracticeViewModel(for: route)
        await practice.loadOrCreateSession(targetSkillId: route.skillId, sessionMode: .trapVerificationDrill)
        XCTAssertNil(practice.session); XCTAssertNotNil(practice.errorMessage)
    }

    func testNativeMicroLessonFactoryUsesInjectedService() async throws {
        let report = try fixture(); let api = AttributionAPIStub(report: report)
        let vm = PostExamAttributionViewModel(sessionId: report.sessionId, userId: report.userId, api: api)
        let lesson = vm.makeMicroLessonViewModel()
        await lesson.load(mistakeCode: "CALC_CHAIN_RULE_OMITTED", locale: "tr-TR", triggerSource: .coachRepairDrawer)
        let calls = await api.recordedMicroRequests()
        XCTAssertEqual(calls, ["CALC_CHAIN_RULE_OMITTED:tr-TR:COACH_REPAIR_DRAWER"])
        XCTAssertNil(lesson.card); XCTAssertTrue(lesson.shouldFallbackToHints)
    }

    func testNativeContractDecoderAcceptsWholeAndFractionalISO8601Dates() throws {
        struct Timestamp: Decodable { let date: Date }
        for value in ["2026-11-17T10:00:00Z", "2026-11-17T10:00:00.123456Z"] {
            let data = try JSONSerialization.data(withJSONObject: ["date": value])
            let decoded = try APIClient.decodeISO8601Response(Timestamp.self, from: data)
            XCTAssertGreaterThan(decoded.date.timeIntervalSince1970, 0)
        }
        XCTAssertThrowsError(try APIClient.decodeISO8601Response(Timestamp.self, from: Data(#"{"date":"invalid"}"#.utf8)))
    }

    func testLivePracticeAdapterSendsNativeModeAndDecodesResponse() async throws {
        let report = try fixture(); let stub = AttributionAPIStub(report: report)
        let session = try await stub.startRemediationPractice(skillId: "synthetic-skill", mode: .trapVerificationDrill)
        let encoder = JSONEncoder(); encoder.dateEncodingStrategy = .iso8601
        let transport = RemediationHTTPSessionStub(payload: try encoder.encode(session), status: 201)
        let client = APIClient(baseURL: URL(string: "https://synthetic.invalid")!, session: transport,
            authTokenProvider: RemediationTokenStub(), correlationContext: CorrelationContext())
        let result = try await HTTPExamAPIService(apiClient: client).startRemediationPractice(skillId: "synthetic-skill", mode: .trapVerificationDrill)
        XCTAssertEqual(result, session)
        let recorded = await transport.lastRequest(); let request = try XCTUnwrap(recorded)
        XCTAssertEqual(request.url?.path, "/v1/study-plan/practice/sessions")
        XCTAssertEqual(request.httpMethod, "POST")
        let body = try JSONDecoder().decode([String: String].self, from: XCTUnwrap(request.httpBody))
        XCTAssertEqual(body, ["targetSkillId": "synthetic-skill", "sessionMode": "TRAP_VERIFICATION_DRILL", "locale": "tr-TR"])
    }

    func testLiveMicroLessonDenialCannotBecomeOfflineCacheFallback() async throws {
        let transport = RemediationHTTPSessionStub(payload: Data(), status: 403)
        let client = APIClient(baseURL: URL(string: "https://synthetic.invalid")!, session: transport,
            authTokenProvider: RemediationTokenStub(), correlationContext: CorrelationContext())
        do {
            _ = try await HTTPExamAPIService(apiClient: client).fetchMicroLesson(mistakeCode: "CALC_CHAIN_RULE_OMITTED",
                locale: "tr-TR", triggerSource: .coachRepairDrawer, sourceAttemptId: nil)
            XCTFail("Expected eligibility denial")
        } catch { XCTAssertEqual(error as? MicroLessonClientError, .triggerNotEligible) }
        let recorded = await transport.lastRequest(); let request = try XCTUnwrap(recorded)
        XCTAssertEqual(request.url?.path, "/v1/micro-lessons/CALC_CHAIN_RULE_OMITTED")
        let query = URLComponents(url: request.url!, resolvingAgainstBaseURL: false)?.queryItems
        XCTAssertEqual(query?.first(where: { $0.name == "triggerSource" })?.value, "COACH_REPAIR_DRAWER")
    }

    private static let fixtureJSON = #"""
{
  "sessionId": "11111111-1111-4111-8111-111111111111",
  "userId": "11111111-1111-4111-8111-111111111111",
  "examCode": "TR_YKS_AYT_MATH",
  "generatedAt": "2026-11-17T10:00:00Z",
  "algorithmVersion": "H03_ATTRIBUTION_V1",
  "totalLostNet": 1.25,
  "categorySummary": [
    {
      "category": "CONCEPTUAL_GAP",
      "itemCount": 0,
      "lostNet": 0,
      "sharePercentage": 0,
      "headline": "CONCEPTUAL_GAP"
    },
    {
      "category": "RECURRING_MISTAKE_TRAP",
      "itemCount": 1,
      "lostNet": 1.25,
      "sharePercentage": 100,
      "headline": "RECURRING_MISTAKE_TRAP"
    },
    {
      "category": "TIME_PRESSURE_SLIP",
      "itemCount": 0,
      "lostNet": 0,
      "sharePercentage": 0,
      "headline": "TIME_PRESSURE_SLIP"
    },
    {
      "category": "STRATEGIC_OMISSION",
      "itemCount": 0,
      "lostNet": 0,
      "sharePercentage": 0,
      "headline": "STRATEGIC_OMISSION"
    }
  ],
  "topRoiAllocations": [
    {
      "rank": 1,
      "topicId": "MATH.CALCULUS",
      "skillId": "MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE",
      "expectedNetGainPerHour": 0.329,
      "estimatedRemediationHours": 2.96,
      "recommendedActionType": "TWIN_DRILL",
      "recommendedTargetRef": "twin:MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE:CALC_CHAIN_RULE_OMITTED",
      "topicTitle": "Türev",
      "skillTitle": "Zincir kuralı",
      "dominantCategory": "RECURRING_MISTAKE_TRAP",
      "matchedMistakeCode": "CALC_CHAIN_RULE_OMITTED",
      "sessionLostNet": 1.25,
      "currentMastery": 0.5,
      "examSuccessProbability": 0.46,
      "estimatedNetRecoveryTotal": 0.98,
      "rationale": "Zincir kuralını ikiz soruyla pekiştir."
    }
  ],
  "itemAttributions": [
    {
      "attributionId": "11111111-1111-4111-8111-111111111111",
      "itemEvaluationId": "11111111-1111-4111-8111-111111111111",
      "itemOrder": 1,
      "sectionCode": "AYT_MATH",
      "topicId": "MATH.CALCULUS",
      "skillId": "MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE",
      "itemOutcome": "WRONG",
      "attributionCategory": "RECURRING_MISTAKE_TRAP",
      "matchedMistakeCode": "CALC_CHAIN_RULE_OMITTED",
      "evidenceSource": "DISTRACTOR_TRAP_MAP",
      "timeSpentSeconds": 200,
      "expectedSolveSeconds": 135,
      "masterySnapshot": 0.5,
      "estimatedNetImpact": 1.25,
      "recommendedActionType": "TWIN_DRILL",
      "recommendedTargetRef": "twin:MATH.CALCULUS.DIFFERENTIATION.CHAIN_RULE:CALC_CHAIN_RULE_OMITTED",
      "rationale": "Zincir kuralını ikiz soruyla pekiştir."
    }
  ],
  "studyPlanRebalanced": false,
  "lastReallocationBatchId": null,
  "trainingEligible": false
}
"""#
}
