import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class OnboardingViewModelTests: XCTestCase {
    func testAuthenticatedUserWithoutProfileRoutesToOnboarding() async {
        let service = StubLearningProfileService(currentProfile: .missing())
        let viewModel = makeViewModel(service: service)

        await viewModel.bootstrap()

        XCTAssertEqual(viewModel.state, .needsOnboarding)
        XCTAssertEqual(viewModel.currentStep, .education)
    }

    func testCompletedProfileRoutesToReadyState() async {
        let service = StubLearningProfileService(currentProfile: .completed())
        let viewModel = makeViewModel(service: service)

        await viewModel.bootstrap()

        XCTAssertEqual(viewModel.state, .ready(.completed()))
    }

    func testOnboardingResumeUsesPersistedProfileProgress() async {
        let partial = LearningProfile.partial(
            educationLevel: .highSchool,
            preferredLanguage: nil,
            explanationDepth: nil,
            dailyStudyMinutes: nil,
            timezone: nil
        )
        let service = StubLearningProfileService(currentProfile: partial)
        let viewModel = makeViewModel(service: service)

        await viewModel.bootstrap()

        XCTAssertEqual(viewModel.state, .needsOnboarding)
        XCTAssertEqual(viewModel.currentStep, .preferences)
        XCTAssertEqual(viewModel.draft.educationLevel, .highSchool)
    }

    func testCompletionValidationKeepsDraftWhenRequiredFieldsMissing() async {
        let service = StubLearningProfileService(currentProfile: .missing())
        let viewModel = makeViewModel(service: service)
        viewModel.draft.goalContext = "Prepare for limits"

        let completed = await viewModel.completeOnboarding()

        XCTAssertFalse(completed)
        XCTAssertEqual(viewModel.state, .failed(.validation))
        XCTAssertEqual(viewModel.draft.goalContext, "Prepare for limits")
        let saveRequests = await service.recordedSaveRequests()
        XCTAssertEqual(saveRequests.count, 0)
    }

    func testNetworkFailureCanRecoverOnRetry() async {
        let network = StubNetworkMonitor(isReachable: false)
        let service = StubLearningProfileService(currentProfile: .completed())
        let viewModel = makeViewModel(service: service, networkMonitor: network)

        await viewModel.bootstrap()
        XCTAssertEqual(viewModel.state, .offline)

        network.isReachable = true
        await viewModel.bootstrap()

        XCTAssertEqual(viewModel.state, .ready(.completed()))
    }

    func testProfileSubmissionSuccessTransitionsToReady() async {
        let service = StubLearningProfileService(currentProfile: .missing(), savedProfile: .completed(version: 1))
        let viewModel = makeViewModel(service: service)
        viewModel.draft.educationLevel = .highSchool
        viewModel.draft.preferredLanguage = "en"
        viewModel.draft.explanationDepth = .standard
        viewModel.draft.dailyStudyMinutes = 30
        viewModel.draft.timezone = "Europe/Istanbul"
        viewModel.draft.goalContext = "Prepare for calculus"

        let completed = await viewModel.completeOnboarding()

        XCTAssertTrue(completed)
        XCTAssertEqual(viewModel.state, .ready(.completed(version: 1)))
        let saveRequests = await service.recordedSaveRequests()
        XCTAssertEqual(saveRequests.map(\.completeOnboarding), [true])
    }

    func testAppRelaunchSkipsOnboardingAfterProfileWasCompleted() async {
        let service = StubLearningProfileService(currentProfile: .completed(version: 2))
        let firstLaunch = makeViewModel(service: service)

        await firstLaunch.bootstrap()
        XCTAssertEqual(firstLaunch.state, .ready(.completed(version: 2)))

        let secondLaunch = makeViewModel(service: service)
        await secondLaunch.bootstrap()

        XCTAssertEqual(secondLaunch.state, .ready(.completed(version: 2)))
    }

    private func makeViewModel(
        service: StubLearningProfileService,
        networkMonitor: StubNetworkMonitor = StubNetworkMonitor()
    ) -> OnboardingViewModel {
        OnboardingViewModel(
            learningProfileAPI: service,
            networkMonitor: networkMonitor,
            logger: AppLogger(subsystem: "com.verifiedai.learning.tests", category: "onboarding")
        )
    }
}

@MainActor
private final class StubNetworkMonitor: NetworkMonitoring {
    var isReachable: Bool

    init(isReachable: Bool = true) {
        self.isReachable = isReachable
    }
}

private actor StubLearningProfileService: LearningProfileServicing {
    struct SaveRequest: Equatable, Sendable {
        let draft: LearningProfileDraft
        let completeOnboarding: Bool
    }

    var profile: LearningProfile
    var savedProfile: LearningProfile
    var currentError: Error?
    var saveError: Error?
    private(set) var saveRequests: [SaveRequest] = []

    init(currentProfile: LearningProfile, savedProfile: LearningProfile = .completed()) {
        self.profile = currentProfile
        self.savedProfile = savedProfile
    }

    func currentProfile() async throws -> LearningProfile {
        if let currentError {
            throw currentError
        }
        return profile
    }

    func saveProfile(_ draft: LearningProfileDraft, completeOnboarding: Bool) async throws -> LearningProfile {
        if let saveError {
            throw saveError
        }
        saveRequests.append(SaveRequest(draft: draft, completeOnboarding: completeOnboarding))
        return savedProfile
    }

    func recordedSaveRequests() -> [SaveRequest] {
        saveRequests
    }
}

private extension LearningProfile {
    static func missing(userId: UUID = UUID(uuidString: "00000000-0000-0000-0000-000000000101")!) -> LearningProfile {
        LearningProfile(
            exists: false,
            id: nil,
            userId: userId,
            educationLevel: nil,
            preferredLanguage: nil,
            explanationDepth: nil,
            dailyStudyMinutes: nil,
            timezone: nil,
            goalContext: nil,
            onboardingStatus: .notStarted,
            version: nil,
            createdAt: nil,
            updatedAt: nil
        )
    }

    static func partial(
        educationLevel: EducationLevel?,
        preferredLanguage: String?,
        explanationDepth: ExplanationDepth?,
        dailyStudyMinutes: Int?,
        timezone: String?,
        version: Int64 = 0
    ) -> LearningProfile {
        LearningProfile(
            exists: true,
            id: UUID(uuidString: "00000000-0000-0000-0000-000000000202")!,
            userId: UUID(uuidString: "00000000-0000-0000-0000-000000000101")!,
            educationLevel: educationLevel,
            preferredLanguage: preferredLanguage,
            explanationDepth: explanationDepth,
            dailyStudyMinutes: dailyStudyMinutes,
            timezone: timezone,
            goalContext: nil,
            onboardingStatus: .inProgress,
            version: version,
            createdAt: Date(timeIntervalSince1970: 1_800_000_000),
            updatedAt: Date(timeIntervalSince1970: 1_800_000_000)
        )
    }

    static func completed(version: Int64 = 0) -> LearningProfile {
        LearningProfile(
            exists: true,
            id: UUID(uuidString: "00000000-0000-0000-0000-000000000202")!,
            userId: UUID(uuidString: "00000000-0000-0000-0000-000000000101")!,
            educationLevel: .highSchool,
            preferredLanguage: "en",
            explanationDepth: .standard,
            dailyStudyMinutes: 30,
            timezone: "Europe/Istanbul",
            goalContext: "Prepare for calculus",
            onboardingStatus: .completed,
            version: version,
            createdAt: Date(timeIntervalSince1970: 1_800_000_000),
            updatedAt: Date(timeIntervalSince1970: 1_800_000_000)
        )
    }
}
