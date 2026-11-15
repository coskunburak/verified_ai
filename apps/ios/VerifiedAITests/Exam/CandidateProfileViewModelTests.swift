import XCTest
@testable import VerifiedAI

@MainActor
final class CandidateProfileViewModelTests: XCTestCase {

    func testSaveLoadAndOfflineFallbackAndCrossUserPurge() async throws {
        let store = ExamOfflineSQLiteStore(inMemory: true)
        let service = FixtureExamAPIService()
        let userA = UUID()
        let userB = UUID()

        let vmA = CandidateProfileViewModel(apiService: service, offlineStore: store, userIdProvider: { userA.uuidString })
        vmA.selectedExamCode = "TR_YKS_TYT_MATH"
        vmA.selectedExamVersion = "2026.1"
        vmA.targetScoreText = "35.5"
        vmA.targetDate = Calendar.current.date(byAdding: .day, value: 90, to: Date()) ?? Date().addingTimeInterval(90 * 86400)
        vmA.dailyStudyMinutes = 90
        vmA.weeklyStudyMinutes = 630
        vmA.extraTimeMultiplier = 1.25
        vmA.highContrastEnabled = true

        await vmA.saveProfile()

        XCTAssertNotNil(vmA.savedProfile)
        XCTAssertEqual(vmA.statusLocalizationKey, "exam.candidate.saved_banner")
        XCTAssertNil(vmA.errorLocalizationKey)
        XCTAssertEqual(vmA.savedProfile?.adjustedSections.first?.adjustedDurationMinutes, 82)
        XCTAssertEqual(vmA.historyEntries.count, 1)

        // Seed separate user B cache in SQLite to verify purge isolation
        let vmB = CandidateProfileViewModel(apiService: FixtureExamAPIService(), offlineStore: store, userIdProvider: { userB.uuidString })
        await vmB.saveProfile()

        let offlineVmA = CandidateProfileViewModel(
            apiService: FixtureExamAPIService(simulateOffline: true),
            offlineStore: store,
            userIdProvider: { userA.uuidString }
        )
        await offlineVmA.loadProfile(offlineMode: false)
        XCTAssertTrue(offlineVmA.isOfflineFallback)
        XCTAssertEqual(offlineVmA.savedProfile?.activeExamCode, "TR_YKS_TYT_MATH")
        XCTAssertEqual(offlineVmA.savedProfile?.accommodations.extraTimeMultiplier, 1.25)

        // Purge user A only
        await offlineVmA.purgeUserDataAndReset()
        XCTAssertNil(offlineVmA.savedProfile)
        let cachedAAfterPurge = await store.loadCandidateProfile(userId: userA.uuidString)
        let cachedBAfterPurge = await store.loadCandidateProfile(userId: userB.uuidString)
        XCTAssertNil(cachedAAfterPurge)
        XCTAssertNotNil(cachedBAfterPurge)
    }

    func testPacingFeasibilityTransitionsAndAccommodationCeilingMath() {
        let store = ExamOfflineSQLiteStore(inMemory: true)
        let vm = CandidateProfileViewModel(apiService: FixtureExamAPIService(), offlineStore: store)
        vm.selectedExamCode = "TR_YKS_TYT_MATH" // 40 questions
        vm.baselineMastery = 0.25               // 40 * 0.75 * 90 = 2700 estimated minutes

        // 2700 / 90 days = 30 min/day required -> 60 min/day capacity => ratio 0.50 -> optimal
        vm.targetDate = Calendar.current.date(byAdding: .day, value: 90, to: Date()) ?? Date().addingTimeInterval(90 * 86400)
        vm.dailyStudyMinutes = 60
        vm.weeklyStudyMinutes = 420
        XCTAssertEqual(vm.previewFeasibilityStatus, .optimal)

        // 2700 / 45 days = 60 min/day -> 60 min/day capacity => ratio 1.00 -> stretch
        vm.targetDate = Calendar.current.date(byAdding: .day, value: 45, to: Date()) ?? Date().addingTimeInterval(45 * 86400)
        XCTAssertEqual(vm.previewFeasibilityStatus, .stretch)

        // 2700 / 34 days = 80 min/day -> 60 min/day capacity => ratio ~1.33 -> atRisk
        vm.targetDate = Calendar.current.date(byAdding: .day, value: 34, to: Date()) ?? Date().addingTimeInterval(34 * 86400)
        XCTAssertEqual(vm.previewFeasibilityStatus, .atRisk)

        // 2700 / 10 days = 270 min/day -> 60 min/day capacity => unrealistic
        vm.targetDate = Calendar.current.date(byAdding: .day, value: 10, to: Date()) ?? Date().addingTimeInterval(10 * 86400)
        XCTAssertEqual(vm.previewFeasibilityStatus, .unrealistic)

        vm.extraTimeMultiplier = 1.00
        XCTAssertEqual(vm.adjustedDurationMinutes(baseMinutes: 65), 65)
        vm.extraTimeMultiplier = 1.25
        XCTAssertEqual(vm.adjustedDurationMinutes(baseMinutes: 65), 82)
        vm.extraTimeMultiplier = 1.50
        XCTAssertEqual(vm.adjustedDurationMinutes(baseMinutes: 65), 98)
        vm.extraTimeMultiplier = 2.00
        XCTAssertEqual(vm.adjustedDurationMinutes(baseMinutes: 65), 130)
    }

    func testSyllabusAutoConfirmDisambiguationDedupAndMonthlyQuota() async {
        let store = ExamOfflineSQLiteStore(inMemory: true)
        let vm = CandidateProfileViewModel(apiService: FixtureExamAPIService(), offlineStore: store)

        let clearBytes = Data("%PDF-1.4\nCLEAR_ONE\n".utf8)
        await vm.ingestSyllabus(fileData: clearBytes, mimeType: "application/pdf", filename: "clear-1.pdf")
        XCTAssertEqual(vm.latestUpload?.status, .parsedAutoConfirmed)
        XCTAssertEqual(vm.latestUpload?.remainingMonthlyQuota, 2)
        XCTAssertEqual(vm.statusLocalizationKey, "exam.syllabus.status.auto_confirmed")

        // Duplicate hash should hit dedup cache without burning quota
        await vm.ingestSyllabus(fileData: clearBytes, mimeType: "application/pdf", filename: "clear-1-copy.pdf")
        XCTAssertEqual(vm.latestUpload?.deduplicatedFromCache, true)
        XCTAssertEqual(vm.latestUpload?.remainingMonthlyQuota, 2)

        // Second unique upload: ambiguous -> disambiguate & confirm
        let ambiguousBytes = Data("%PDF-1.4\nAMBIGUOUS_SYLLABUS_2\n".utf8)
        await vm.ingestSyllabus(fileData: ambiguousBytes, mimeType: "application/pdf", filename: "ambiguous-2.pdf")
        XCTAssertEqual(vm.latestUpload?.status, .needsDisambiguation)
        XCTAssertEqual(vm.latestUpload?.remainingMonthlyQuota, 1)
        XCTAssertEqual(vm.statusLocalizationKey, "exam.syllabus.status.needs_disambiguation")

        vm.selectedDisambiguationChoices[1] = "MATH.EQUATIONS.QUADRATIC_SOLVING"
        await vm.confirmDisambiguation()
        XCTAssertEqual(vm.latestUpload?.status, .userConfirmed)
        XCTAssertEqual(vm.statusLocalizationKey, "exam.syllabus.status.user_confirmed")
        let item1 = vm.latestUpload?.parsedTopics.first(where: { $0.itemIndex == 1 })
        XCTAssertEqual(item1?.matchedSkillId, "MATH.EQUATIONS.QUADRATIC_SOLVING")
        XCTAssertEqual(item1?.userConfirmed, true)

        // Third unique upload succeeds (quota -> 0)
        await vm.ingestSyllabus(fileData: Data("%PDF-1.4\nUNIQUE_3\n".utf8), mimeType: "application/pdf", filename: "clear-3.pdf")
        XCTAssertEqual(vm.latestUpload?.remainingMonthlyQuota, 0)

        // Fourth unique upload triggers monthly quota limit
        await vm.ingestSyllabus(fileData: Data("%PDF-1.4\nUNIQUE_4\n".utf8), mimeType: "application/pdf", filename: "clear-4.pdf")
        XCTAssertEqual(vm.errorLocalizationKey, "exam.syllabus.error.quota_exceeded")
    }
}
