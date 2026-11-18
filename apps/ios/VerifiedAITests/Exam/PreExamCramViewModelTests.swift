import XCTest
import SwiftUI
@testable import VerifiedAI

@MainActor
final class PreExamCramViewModelTests: XCTestCase {
    func testAuthorizationAndIntegrityNeverAllowOfflineFallback() {
        XCTAssertFalse(PreExamCramViewModel.allowsOffline(NetworkError.httpStatus(401)))
        XCTAssertFalse(PreExamCramViewModel.allowsOffline(NetworkError.httpStatus(403)))
        XCTAssertFalse(PreExamCramViewModel.allowsOffline(NetworkError.decoding("synthetic")))
        XCTAssertFalse(PreExamCramViewModel.allowsOffline(PreExamError.integrity))
        XCTAssertTrue(PreExamCramViewModel.allowsOffline(NetworkError.httpStatus(503)))
        XCTAssertTrue(PreExamCramViewModel.allowsOffline(NetworkError.transport("synthetic")))
    }
    func testPurgeInvalidatesInFlightGeneration() async {
        let cache = ExamOfflineSQLiteStore(inMemory: true)
        let before = await cache.preExamEpoch()
        await cache.purgeUserData(userId: UUID())
        let after = await cache.preExamEpoch()
        XCTAssertNotEqual(before, after)
        await cache.purgeAll()
        let final = await cache.preExamEpoch()
        XCTAssertNotEqual(after, final)
    }
}

enum PreExamFixtures {
    static func snapshot(user: UUID, session: UUID, sheet: Bool, count: Int = 1) throws -> PreExamSnapshot {
        let now = Date()
        let formatter = ISO8601DateFormatter()
        let cards: [[String: Any]] = (0..<count).map { index in
            ["catalogId": "synthetic-\(index)", "skillCode": "MATH.ALGEBRA.FACTORING",
             "titleTr": "Çalışma: ğ ü ş ı ö ç İ", "titleEn": "Synthetic review",
             "ruleLatex": "a^2", "ruleDisplay": "(a + b)² = a² + 2ab + b²; ≠ ≤ ≥ √ ⇒ ∈ ℝ",
             "domainTr": "a ve b gerçek sayılardır.", "domainEn": "a and b are real numbers.",
             "mistakeCode": "ALG_FRESHMAN_DREAM_POWER", "counterExampleLatex": "25 != 13",
             "counterExampleDisplay": "(3 + 2)² = 25 ≠ 13", "quickCheckTr": "Hesapla", "quickCheckEn": "Calculate", "answerDisplay": "25"]
        }
        let json: [String: Any] = ["schemaVersion": sheet ? "exam-cheat-sheet-v1" : "pre-exam-review-v1",
            "sessionId": session.uuidString, "userId": user.uuidString, "examCode": "TR_YKS_TYT_MATH",
            "generatedAt": formatter.string(from: now), "expiresAt": formatter.string(from: now.addingTimeInterval(3600)),
            "algorithmVersion": "H15_V1", "catalogVersion": "pre-exam-catalog-v1", "catalogContentHash": String(repeating: "a", count: 64),
            "etag": String(repeating: sheet ? "c" : "b", count: 64), "targetExamDate": NSNull(), "targetExamAt": NSNull(),
            "timePrecision": "UNAVAILABLE", "windowStatus": "NO_TARGET_DATE", "hoursRemaining": NSNull(),
            "evidenceStatus": "SUFFICIENT_EVIDENCE", "panicInhibitionActive": true, "masteryFloor": 0.5,
            "excludedLowMasteryTopicCount": 0, "activeTraps": cards, "highYieldFormulas": cards,
            "genericReferences": [], "llmTokensUsed": 0, "trainingEligible": false]
        return try APIClient.decodeISO8601Response(PreExamSnapshot.self, from: JSONSerialization.data(withJSONObject: json))
    }
}
private actor PreExamFakeAPI: PreExamServicing {
    let bundle: PreExamBundle
    let failure: Int?
    let first304: Bool
    var requests = 0
    init(bundle: PreExamBundle, failure: Int? = nil, first304: Bool = false) {
        self.bundle = bundle; self.failure = failure; self.first304 = first304
    }
    func preExam(exam: String, sheet: Bool, etag: String?) async throws -> ConditionalHTTPResponse<PreExamSnapshot> {
        requests += 1
        if let failure { throw NetworkError.httpStatus(failure) }
        if first304 && requests == 1 { return .notModified(nil) }
        let body = sheet ? bundle.sheet : bundle.review
        if etag != nil { return .notModified(etag) }
        return .modified(body, "\"\(body.etag)\"")
    }
    func exported(session: UUID, key: String) async throws {}
}

extension PreExamCramViewModelTests {
    func testCacheless304RetriesAndSameOwnerCacheRevalidates() async throws {
        let user = UUID(), session = UUID(), cache = ExamOfflineSQLiteStore(inMemory: true)
        let bundle = PreExamBundle(review: try PreExamFixtures.snapshot(user: user, session: session, sheet: false),
                                  sheet: try PreExamFixtures.snapshot(user: user, session: session, sheet: true))
        let api = PreExamFakeAPI(bundle: bundle, first304: true)
        let model = PreExamCramViewModel(user: user, exam: bundle.review.examCode, api: api, cache: cache)
        await model.load()
        XCTAssertEqual(model.bundle?.review.sessionId, session)
        let requests = await api.requests
        XCTAssertEqual(requests, 3)
        await model.load()
        XCTAssertEqual(model.bundle?.review.sessionId, session)
        XCTAssertFalse(model.offline)
        let foreign = try await cache.loadPreExam(user: UUID(), exam: bundle.review.examCode)
        XCTAssertNil(foreign)
    }
    func test503UsesCacheBut403PurgesAndNeverShowsCachedData() async throws {
        let user = UUID(), session = UUID(), cache = ExamOfflineSQLiteStore(inMemory: true)
        let bundle = PreExamBundle(review: try PreExamFixtures.snapshot(user: user, session: session, sheet: false),
                                  sheet: try PreExamFixtures.snapshot(user: user, session: session, sheet: true))
        let epoch = await cache.preExamEpoch()
        try await cache.savePreExam(bundle, user: user, exam: bundle.review.examCode, epoch: epoch)
        let offline = PreExamCramViewModel(user: user, exam: bundle.review.examCode, api: PreExamFakeAPI(bundle: bundle, failure: 503), cache: cache)
        await offline.load()
        XCTAssertTrue(offline.offline)
        let forbidden = PreExamCramViewModel(user: user, exam: bundle.review.examCode, api: PreExamFakeAPI(bundle: bundle, failure: 403), cache: cache)
        await forbidden.load()
        XCTAssertNil(forbidden.bundle)
        let purged = try await cache.loadPreExam(user: user, exam: bundle.review.examCode)
        XCTAssertNil(purged)
        do {
            try await cache.savePreExam(bundle, user: user, exam: bundle.review.examCode, epoch: epoch)
            XCTFail("Purged generation must reject late writes")
        } catch { XCTAssertTrue(error is PreExamError) }
    }
    func test401PurgesAndNeverShowsCachedData() async throws {
        let user = UUID(), session = UUID(), cache = ExamOfflineSQLiteStore(inMemory: true)
        let bundle = PreExamBundle(review: try PreExamFixtures.snapshot(user: user, session: session, sheet: false),
                                  sheet: try PreExamFixtures.snapshot(user: user, session: session, sheet: true))
        let epoch = await cache.preExamEpoch()
        try await cache.savePreExam(bundle, user: user, exam: bundle.review.examCode, epoch: epoch)
        let unauthorized = PreExamCramViewModel(user: user, exam: bundle.review.examCode, api: PreExamFakeAPI(bundle: bundle, failure: 401), cache: cache)
        await unauthorized.load()
        XCTAssertNil(unauthorized.bundle)
        XCTAssertFalse(unauthorized.offline)
        XCTAssertEqual(unauthorized.message, "exam.preExam.signIn")
        let purged = try await cache.loadPreExam(user: user, exam: bundle.review.examCode)
        XCTAssertNil(purged)
    }
}

extension PreExamCramViewModelTests {
    func testForeignOwnerResponseIsRejectedWithoutOfflineFallback() async throws {
        let owner = UUID(), stranger = UUID(), session = UUID()
        let foreign = PreExamBundle(review: try PreExamFixtures.snapshot(user: stranger, session: session, sheet: false),
                                   sheet: try PreExamFixtures.snapshot(user: stranger, session: session, sheet: true))
        let model = PreExamCramViewModel(user: owner, exam: foreign.review.examCode,
                                       api: PreExamFakeAPI(bundle: foreign), cache: ExamOfflineSQLiteStore(inMemory: true))
        await model.load()
        XCTAssertNil(model.bundle)
        XCTAssertFalse(model.offline)
    }
}

extension PreExamCramViewModelTests {
    func testAccessibilitySizeLayoutHostsWithoutFixedCardHeight() throws {
        let user = UUID(), session = UUID()
        let bundle = PreExamBundle(review: try PreExamFixtures.snapshot(user: user, session: session, sheet: false),
                                  sheet: try PreExamFixtures.snapshot(user: user, session: session, sheet: true))
        let model = PreExamCramViewModel(user: user, exam: bundle.review.examCode, api: PreExamFakeAPI(bundle: bundle), cache: nil)
        let host = UIHostingController(rootView: PreExamCramView(viewModel: model).environment(\.dynamicTypeSize, .accessibility3))
        host.loadViewIfNeeded()
        host.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        host.view.layoutIfNeeded()
        XCTAssertEqual(host.view.bounds.width, 390)
    }
}
