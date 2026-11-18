import XCTest
import SwiftUI
@testable import VerifiedAI

@MainActor
final class ExamProgressHistoryViewModelTests: XCTestCase {
    func testOfflineFallbackPolicyRejectsAuthAndIntegrityErrors() {
        XCTAssertFalse(ExamProgressHistoryViewModel.allowsOffline(NetworkError.httpStatus(400)))
        XCTAssertFalse(ExamProgressHistoryViewModel.allowsOffline(NetworkError.httpStatus(401)))
        XCTAssertFalse(ExamProgressHistoryViewModel.allowsOffline(NetworkError.httpStatus(403)))
        XCTAssertFalse(ExamProgressHistoryViewModel.allowsOffline(NetworkError.httpStatus(404)))
        XCTAssertFalse(ExamProgressHistoryViewModel.allowsOffline(NetworkError.decoding("bad json")))
        XCTAssertFalse(ExamProgressHistoryViewModel.allowsOffline(ExamProgressError.integrity))
        XCTAssertTrue(ExamProgressHistoryViewModel.allowsOffline(NetworkError.httpStatus(503)))
        XCTAssertTrue(ExamProgressHistoryViewModel.allowsOffline(NetworkError.transport("offline")))
    }

    func testEmptyOneTwoAndThreePlusStatesAndExamSwitchClearsStaleReport() async throws {
        let user = UUID()
        let tytEmpty = try ExamProgressFixtures.report(user: user, exam: "TR_YKS_TYT_MATH", mockCount: 0)
        let aytFull = try ExamProgressFixtures.report(user: user, exam: "TR_YKS_AYT_MATH", mockCount: 3)

        let api = FakeExamProgressAPI(responses: [
            "TR_YKS_TYT_MATH": tytEmpty,
            "TR_YKS_AYT_MATH": aytFull
        ])
        let cache = ExamOfflineSQLiteStore(inMemory: true)
        let vm = ExamProgressHistoryViewModel(user: user, exam: "YKS_TYT", api: api, cache: cache)

        await vm.load()
        XCTAssertEqual(vm.exam, "TR_YKS_TYT_MATH")
        XCTAssertEqual(vm.report?.mockCount, 0)
        if case .empty = vm.state {} else { XCTFail("Expected .empty state") }

        await vm.switchExam(to: "TR_YKS_AYT_MATH")
        XCTAssertEqual(vm.exam, "TR_YKS_AYT_MATH")
        XCTAssertEqual(vm.report?.mockCount, 3)
        if case .loaded = vm.state {} else { XCTFail("Expected .loaded state") }

        let oneMock = try ExamProgressFixtures.report(user: user, exam: "TR_YKS_TYT_MATH", mockCount: 1)
        let oneApi = FakeExamProgressAPI(responses: ["TR_YKS_TYT_MATH": oneMock])
        let oneVm = ExamProgressHistoryViewModel(user: user, exam: "TR_YKS_TYT_MATH", api: oneApi, cache: nil)
        await oneVm.load()
        if case .insufficient = oneVm.state {} else { XCTFail("Expected .insufficient state for 1 mock") }

        let twoMock = try ExamProgressFixtures.report(user: user, exam: "TR_YKS_TYT_MATH", mockCount: 2)
        let twoApi = FakeExamProgressAPI(responses: ["TR_YKS_TYT_MATH": twoMock])
        let twoVm = ExamProgressHistoryViewModel(user: user, exam: "TR_YKS_TYT_MATH", api: twoApi, cache: nil)
        await twoVm.load()
        if case .insufficient = twoVm.state {} else { XCTFail("Expected .insufficient state for 2 mocks") }
    }

    func testCacheCrossUserAndCrossExamIsolationAndPurgeInvalidatesInFlightEpoch() async throws {
        let owner = UUID()
        let stranger = UUID()
        let cache = ExamOfflineSQLiteStore(inMemory: true)
        let tytReport = try ExamProgressFixtures.report(user: owner, exam: "TR_YKS_TYT_MATH", mockCount: 3)

        let epoch = await cache.progressEpoch()
        try await cache.saveProgressReport(tytReport, user: owner, exam: "TR_YKS_TYT_MATH", epoch: epoch)

        let loadedOwnerTyt = try await cache.loadProgressReport(user: owner, exam: "TR_YKS_TYT_MATH")
        let loadedOwnerAyt = try await cache.loadProgressReport(user: owner, exam: "TR_YKS_AYT_MATH")
        let loadedStrangerTyt = try await cache.loadProgressReport(user: stranger, exam: "TR_YKS_TYT_MATH")
        XCTAssertNotNil(loadedOwnerTyt)
        XCTAssertNil(loadedOwnerAyt)
        XCTAssertNil(loadedStrangerTyt)

        // 503 uses verified owner+exam cache
        let offlineVm = ExamProgressHistoryViewModel(
            user: owner,
            exam: "TR_YKS_TYT_MATH",
            api: FakeExamProgressAPI(failureStatus: 503),
            cache: cache
        )
        await offlineVm.load()
        XCTAssertTrue(offlineVm.isOffline)
        XCTAssertEqual(offlineVm.report?.snapshotId, tytReport.snapshotId)

        // 403 purges owner cache and never falls back
        let disabledVm = ExamProgressHistoryViewModel(
            user: owner,
            exam: "TR_YKS_TYT_MATH",
            api: FakeExamProgressAPI(failureStatus: 403),
            cache: cache
        )
        await disabledVm.load()
        XCTAssertEqual(disabledVm.state, .disabled)
        XCTAssertNil(disabledVm.report)
        XCTAssertFalse(disabledVm.isOffline)
        let purgedOn403 = try await cache.loadProgressReport(user: owner, exam: "TR_YKS_TYT_MATH")
        XCTAssertNil(purgedOn403)

        // Stale epoch write must be rejected
        do {
            try await cache.saveProgressReport(tytReport, user: owner, exam: "TR_YKS_TYT_MATH", epoch: epoch)
            XCTFail("Purged epoch must reject late cache writes")
        } catch {
            XCTAssertEqual(error as? ExamProgressError, .integrity)
        }
    }

    func test401PurgesCacheAndForeignOwnerPayloadRejectedWithoutFallback() async throws {
        let owner = UUID()
        let stranger = UUID()
        let cache = ExamOfflineSQLiteStore(inMemory: true)
        let ownerReport = try ExamProgressFixtures.report(user: owner, exam: "TR_YKS_TYT_MATH", mockCount: 3)
        let epoch = await cache.progressEpoch()
        try await cache.saveProgressReport(ownerReport, user: owner, exam: "TR_YKS_TYT_MATH", epoch: epoch)

        let unauthVm = ExamProgressHistoryViewModel(
            user: owner,
            exam: "TR_YKS_TYT_MATH",
            api: FakeExamProgressAPI(failureStatus: 401),
            cache: cache
        )
        await unauthVm.load()
        XCTAssertNil(unauthVm.report)
        XCTAssertFalse(unauthVm.isOffline)
        XCTAssertEqual(unauthVm.messageKey, "exam.progress.signIn")
        let purgedOn401 = try await cache.loadProgressReport(user: owner, exam: "TR_YKS_TYT_MATH")
        XCTAssertNil(purgedOn401)

        // Foreign owner response rejected with integrity error and zero fallback
        let foreignReport = try ExamProgressFixtures.report(user: stranger, exam: "TR_YKS_TYT_MATH", mockCount: 3)
        let foreignVm = ExamProgressHistoryViewModel(
            user: owner,
            exam: "TR_YKS_TYT_MATH",
            api: FakeExamProgressAPI(responses: ["TR_YKS_TYT_MATH": foreignReport]),
            cache: cache
        )
        await foreignVm.load()
        XCTAssertNil(foreignVm.report)
        XCTAssertFalse(foreignVm.isOffline)
        XCTAssertEqual(foreignVm.messageKey, "exam.progress.integrityError")
    }

    func testAccessibilityAX3And320ptLayoutHostsCleanly() throws {
        let user = UUID()
        let report = try ExamProgressFixtures.report(user: user, exam: "TR_YKS_AYT_MATH", mockCount: 3)
        let vm = ExamProgressHistoryViewModel(
            user: user,
            exam: "TR_YKS_AYT_MATH",
            api: FakeExamProgressAPI(responses: ["TR_YKS_AYT_MATH": report]),
            cache: nil
        )
        let host = UIHostingController(
            rootView: ExamProgressHistoryView(viewModel: vm)
                .environment(\.dynamicTypeSize, .accessibility3)
                .environment(\.locale, Locale(identifier: "tr-TR"))
        )
        host.loadViewIfNeeded()
        host.view.frame = CGRect(x: 0, y: 0, width: 320, height: 680)
        host.view.layoutIfNeeded()
        XCTAssertEqual(host.view.bounds.width, 320)
    }
}

private enum ExamProgressFixtures {
    static func report(user: UUID, exam: String, mockCount: Int) throws -> ExamProgressReport {
        let now = Date()
        let iso = ISO8601DateFormatter()
        let evidenceStatus: String = switch mockCount {
        case 0: "EMPTY"
        case 1: "INSUFFICIENT_FOR_TREND"
        case 2: "PARTIAL_TREND"
        default: "SUFFICIENT_EVIDENCE"
        }

        let points: [[String: Any]] = (0..<mockCount).map { idx in
            let net = 20.0 + Double(idx) * 2.5
            let date = now.addingTimeInterval(Double(idx - mockCount) * 86400.0)
            return [
                "sessionId": UUID().uuidString,
                "submittedAt": iso.string(from: date),
                "netScore": net,
                "movingAverage3": 20.0 + Double(idx) * 1.25,
                "scoreVersion": 1
            ]
        }

        let pacing: [[String: Any]] = mockCount == 0 ? [] : [
            [
                "topicCode": "MATH.ALGEBRA",
                "subtopicCode": "POLYNOMIALS",
                "skillId": "MATH.ALGEBRA.FACTORING",
                "observedQuestionCount": 6,
                "excludedUnmeasuredCount": 0,
                "meanSecondsPerQuestion": 85.0,
                "expectedSecondsPerQuestion": 100.0,
                "ratio": 0.85,
                "classification": "FAST"
            ],
            [
                "topicCode": "MATH.CALCULUS",
                "subtopicCode": "DEFINITE_INTEGRAL",
                "skillId": "MATH.CALCULUS.INTEGRATION.DEFINITE",
                "observedQuestionCount": 6,
                "excludedUnmeasuredCount": 1,
                "meanSecondsPerQuestion": 195.0,
                "expectedSecondsPerQuestion": 150.0,
                "ratio": 1.3,
                "classification": "BOTTLENECK"
            ]
        ]

        let observations: [[String: Any]] = points.enumerated().map { idx, pt in
            [
                "sessionId": pt["sessionId"] as Any,
                "submittedAt": pt["submittedAt"] as Any,
                "netScore": pt["netScore"] as Any,
                "matchedTrapIncidenceCount": max(0, 4 - idx)
            ]
        }

        let trapSummary: [String: Any] = [
            "sourceKind": "SESSION_MATCHED_TRAP_INCIDENCE",
            "dataStatus": mockCount >= 3 ? "COMPUTED" : "INSUFFICIENT_DATA",
            "observations": observations,
            "activeTrapCountNow": 2,
            "correlationCoefficient": mockCount >= 3 ? -0.95 : NSNull(),
            "correlationSampleSize": mockCount,
            "conqueredTrapsEvidenceStatus": "UNAVAILABLE",
            "associationIsCausal": false
        ]

        let json: [String: Any] = [
            "schemaVersion": "exam-analytics-progress-v1",
            "snapshotId": UUID().uuidString,
            "userId": user.uuidString,
            "examCode": exam,
            "mockCount": mockCount,
            "evidenceStatus": evidenceStatus,
            "deltaNet": mockCount >= 2 ? Double(mockCount - 1) * 2.5 : NSNull(),
            "scoreTrajectory": points,
            "pacingByTopic": pacing,
            "trapRemediation": trapSummary,
            "conqueredTrapsCount": 0,
            "asOfDate": "2026-11-18",
            "algorithmVersion": "INV_EXAM_005_V1",
            "trainingEligible": false,
            "llmTokensUsed": 0,
            "inferenceCostUsd": 0,
            "generatedAt": iso.string(from: now),
            "expiresAt": iso.string(from: now.addingTimeInterval(3600))
        ]
        let data = try JSONSerialization.data(withJSONObject: json)
        return try APIClient.decodeISO8601Response(ExamProgressReport.self, from: data)
    }
}

private actor FakeExamProgressAPI: ExamProgressServicing {
    let responses: [String: ExamProgressReport]
    let failureStatus: Int?

    init(responses: [String: ExamProgressReport] = [:], failureStatus: Int? = nil) {
        self.responses = responses
        self.failureStatus = failureStatus
    }

    func progress(exam: String) async throws -> ExamProgressReport {
        if let failureStatus {
            throw NetworkError.httpStatus(failureStatus)
        }
        guard let report = responses[exam] else {
            throw NetworkError.httpStatus(404)
        }
        return report
    }
}
