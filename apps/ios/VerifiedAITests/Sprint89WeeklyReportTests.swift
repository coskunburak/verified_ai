import Foundation
import PDFKit
import XCTest
@testable import VerifiedAI

@MainActor
final class Sprint89WeeklyReportTests: XCTestCase {

    func testWeeklyDigestJSONDecodingIncludesRecommendationsAndTrainingEligibleFalse() throws {
        let json = """
        {
          "schemaVersion": "weekly-digest-v1",
          "digestId": "00000000-0000-4000-8000-000000000891",
          "userId": "00000000-0000-4000-8000-000000000890",
          "weekIso": "2026-W46",
          "weekStartDate": "2026-11-09",
          "weekEndDate": "2026-11-15",
          "locale": "en",
          "status": "GENERATED",
          "training_eligible": false,
          "generatedAt": "2026-11-13T10:30:00Z",
          "executiveSummary": {
            "headline": "Steady momentum",
            "encouragementNote": "Practiced calmly across 4 days.",
            "solvedAttemptsCount": 9,
            "independentTwinsSolvedCount": 3,
            "studyMinutes": 74,
            "activeDaysCount": 4,
            "independentShareRate": 0.67,
            "completedPlanItemsCount": 5,
            "currentStreakDays": 4
          },
          "wins": [],
          "recurringMistakes": [],
          "masteryMomentum": {
            "overallMasteryAvg": 0.68,
            "overallRetentionAvg": 0.74,
            "skillsImprovedCount": 3,
            "skillsPracticedCount": 4,
            "dueReviewsCount": 1,
            "dailyActivityBuckets": [],
            "topSkillDeltas": []
          },
          "recommendations": [
            {
              "recommendationId": "rec-01",
              "targetSkillId": "MATH.ALGEBRA.DISTRIBUTIVE_PROPERTY",
              "targetSkillTitle": "Distributive Property",
              "reasonCode": "RECURRING_MISTAKE_PATTERN",
              "priorityScore": 0.6285,
              "estimatedMinutes": 12,
              "recommendedActionType": "TWIN_DRILL",
              "headline": "Practice one short sign-check twin set",
              "explanation": {
                "summaryText": "Negative-sign distribution appeared twice this week.",
                "factorBreakdown": {
                  "masteryGapWeight": 0.4,
                  "mistakeWeight": 0.25,
                  "retentionWeight": 0.2,
                  "prerequisiteWeight": 0.15,
                  "observedMasteryScore": 0.56,
                  "observedMistakeCount": 2,
                  "observedRetentionScore": 0.63,
                  "dependentBlockedSkillsCount": 2
                }
              },
              "evidenceRefs": ["mistake_dna:SIGN_DISTRIBUTION_FLIP"]
            }
          ]
        }
        """.data(using: .utf8)!

        let decoded = try JSONDecoder().decode(WeeklyDigestV1.self, from: json)
        XCTAssertEqual(decoded.schemaVersion, "weekly-digest-v1")
        XCTAssertFalse(decoded.trainingEligible)
        XCTAssertEqual(decoded.status, .generated)
        XCTAssertEqual(decoded.recommendations.count, 1)
        XCTAssertEqual(decoded.recommendations[0].reasonCode, .recurringMistakePattern)
        XCTAssertFalse(decoded.recommendations[0].evidenceRefs.isEmpty)
    }

    func testWeeklySharePacketJSONDecodingEnforcesPrivacyAttestation() throws {
        let packet = WeeklyReportFixtures.sampleSharePacket(weekIso: "2026-W46", role: .parent, alias: "Deniz A.")
        let data = try JSONEncoder().encode(packet)
        let decoded = try JSONDecoder().decode(WeeklySharePacketV1.self, from: data)

        XCTAssertEqual(decoded.schemaVersion, "weekly-share-packet-v1")
        XCTAssertFalse(decoded.trainingEligible)
        XCTAssertEqual(decoded.recipientRole, .parent)
        XCTAssertEqual(decoded.privacyAttestation.policyId, "G-PRIV-03")
        XCTAssertTrue(decoded.privacyAttestation.piiExcluded)
        XCTAssertFalse(decoded.privacyAttestation.rawTutorTranscriptsIncluded)
        XCTAssertFalse(decoded.privacyAttestation.rawStudentPhotosIncluded)
        XCTAssertFalse(decoded.privacyAttestation.exactSessionTimestampsIncluded)
        XCTAssertFalse(decoded.privacyAttestation.trainingEligible)
    }

    func testViewModelLoadSuccessTransitionsToLoadedState() async {
        let mockService = MockWeeklyReportService()
        let vm = WeeklyProgressReportViewModel(
            service: mockService,
            initialWeekIso: "2026-W46",
            nowProvider: { ISO8601DateFormatter().date(from: "2026-11-13T10:30:00Z")! }
        )

        await vm.loadWeeklyDigest(forceRefresh: true)

        guard case .loaded(let digest) = vm.state else {
            return XCTFail("Expected loaded state")
        }
        XCTAssertEqual(digest.weekIso, "2026-W46")
        XCTAssertEqual(mockService.fetchCalls.count, 1)
        XCTAssertEqual(mockService.fetchCalls[0].forceRefresh, true)
    }

    func testViewModelHandles503FeatureDisabledWithRecoverableDegradedState() async {
        let problem = ProblemDetails(
            type: "https://errors.verified-ai-learning.example/weekly-report-feature-disabled",
            title: "Weekly learning reports are temporarily disabled.",
            status: 503,
            code: "WEEKLY_REPORT_FEATURE_DISABLED",
            traceId: "trace-89",
            details: ProblemDetails.RecoveryDetails(recoverable: true, userAction: "RETRY_LATER")
        )
        let mockService = MockWeeklyReportService(stubbedDigest: .failure(NetworkError.server(problem: problem)))
        let vm = WeeklyProgressReportViewModel(service: mockService, initialWeekIso: "2026-W46")

        await vm.loadWeeklyDigest()

        guard case .degraded(_, let retryable, let cached) = vm.state else {
            return XCTFail("Expected degraded state on 503")
        }
        XCTAssertTrue(retryable)
        XCTAssertNil(cached)
    }

    func testViewModelHandlesTransportErrorWithDegradedRetryableState() async {
        let mockService = MockWeeklyReportService(stubbedDigest: .failure(NetworkError.transport("offline")))
        let vm = WeeklyProgressReportViewModel(service: mockService, initialWeekIso: "2026-W46")

        await vm.loadWeeklyDigest()

        guard case .degraded(_, let retryable, _) = vm.state else {
            return XCTFail("Expected degraded state on transport failure")
        }
        XCTAssertTrue(retryable)
    }

    func testViewModelHandlesEmptyWeekGracefullyWithStarterRecommendations() async {
        let base = WeeklyReportFixtures.sampleDigest(weekIso: "2026-W45")
        let emptyDigest = WeeklyDigestV1(
            schemaVersion: base.schemaVersion,
            digestId: base.digestId,
            userId: base.userId,
            weekIso: "2026-W45",
            weekStartDate: "2026-11-02",
            weekEndDate: "2026-11-08",
            locale: "tr",
            status: .emptyWeek,
            trainingEligible: false,
            generatedAt: base.generatedAt,
            executiveSummary: ExecutiveSummary(
                headline: "Sakin bir başlangıç için hazır",
                encouragementNote: "Hazır olduğunuzda ilk adımı seçebilirsiniz.",
                solvedAttemptsCount: 0,
                independentTwinsSolvedCount: 0,
                studyMinutes: 0,
                activeDaysCount: 0,
                independentShareRate: 0.0,
                completedPlanItemsCount: 0,
                currentStreakDays: 0
            ),
            wins: [],
            recurringMistakes: [],
            masteryMomentum: base.masteryMomentum,
            recommendations: base.recommendations
        )
        let mockService = MockWeeklyReportService(stubbedDigest: .success(emptyDigest))
        let vm = WeeklyProgressReportViewModel(service: mockService, initialWeekIso: "2026-W45", locale: "tr")

        await vm.loadWeeklyDigest()

        guard case .loaded(let loaded) = vm.state else {
            return XCTFail("Expected loaded state for EMPTY_WEEK")
        }
        XCTAssertEqual(loaded.status, .emptyWeek)
        XCTAssertFalse(loaded.trainingEligible)
        XCTAssertFalse(loaded.recommendations.isEmpty)
    }

    func testViewModelToggleRecommendationExplanationUpdatesExpandedSet() {
        let vm = WeeklyProgressReportViewModel(service: MockWeeklyReportService(), initialWeekIso: "2026-W46")
        XCTAssertFalse(vm.expandedRecommendationIds.contains("rec-01"))
        vm.toggleRecommendationExplanation("rec-01")
        XCTAssertTrue(vm.expandedRecommendationIds.contains("rec-01"))
        vm.toggleRecommendationExplanation("rec-01")
        XCTAssertFalse(vm.expandedRecommendationIds.contains("rec-01"))
    }

    func testWeekNavigationPreventsFutureWeekAndShiftsISOWeeksCorrectly() async {
        let fixedNow = ISO8601DateFormatter().date(from: "2026-11-13T10:30:00Z")!
        let vm = WeeklyProgressReportViewModel(
            service: MockWeeklyReportService(),
            initialWeekIso: "2026-W46",
            nowProvider: { fixedNow }
        )
        XCTAssertFalse(vm.canSelectNextWeek)

        await vm.selectPreviousWeek()
        XCTAssertEqual(vm.selectedWeekIso, "2026-W45")
        XCTAssertTrue(vm.canSelectNextWeek)

        await vm.selectNextWeek()
        XCTAssertEqual(vm.selectedWeekIso, "2026-W46")
        XCTAssertFalse(vm.canSelectNextWeek)
        XCTAssertEqual(ISOWeekCalendarHelper.shiftWeekIso("2026-W01", byWeeks: -1), "2025-W52")
    }

    func testPDFRendererGeneratesValidSinglePageA4DocumentWithPrivacyFooter() throws {
        let renderer = TutorSharePacketPDFRenderer()
        let packet = WeeklyReportFixtures.sampleSharePacket(weekIso: "2026-W46", role: .tutor, alias: "Deniz A.")
        let data = try renderer.renderPDFData(from: packet)

        XCTAssertGreaterThan(data.count, 500)
        XCTAssertEqual(String(decoding: data.prefix(5), as: UTF8.self), "%PDF-")

        let document = try XCTUnwrap(PDFDocument(data: data))
        XCTAssertEqual(document.pageCount, 1)
        let page = try XCTUnwrap(document.page(at: 0))
        let bounds = page.bounds(for: .mediaBox)
        XCTAssertEqual(Double(bounds.width), 595.2, accuracy: 1.0)
        XCTAssertEqual(Double(bounds.height), 841.8, accuracy: 1.0)
        let extractedText = page.string ?? ""
        XCTAssertTrue(extractedText.contains("G-PRIV-03"))
    }

    func testExportSharePacketPDFWritesTemporaryFileAndResetCleansUp() async {
        let mockService = MockWeeklyReportService()
        let vm = WeeklyProgressReportViewModel(service: mockService, initialWeekIso: "2026-W46")
        vm.openShareScopePreview(role: .parent)
        vm.customDisplayAlias = "Deniz A."

        let url = await vm.exportSharePacketPDF()
        guard let url else {
            return XCTFail("Expected exported PDF URL")
        }
        XCTAssertTrue(FileManager.default.fileExists(atPath: url.path))
        XCTAssertEqual(mockService.shareCalls.count, 1)
        XCTAssertEqual(mockService.shareCalls[0].request.recipientRole, .parent)

        vm.reset()
        XCTAssertEqual(vm.state, .idle)
        XCTAssertNil(vm.exportedPDFURL)
        XCTAssertFalse(FileManager.default.fileExists(atPath: url.path))
    }
}
