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

    func saveExplanationDepth(_ depth: ExplanationDepth, expectedVersion: Int64) async throws -> LearningProfile {
        if let saveError { throw saveError }
        return savedProfile
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

@MainActor
final class ExplanationPreferenceTests: XCTestCase {
    private let owner = UUID()
    private let user = UUID(uuidString: "00000000-0000-0000-0000-000000000101")!

    private func model(_ service: PreferenceServiceDouble, network: StubNetworkMonitor = StubNetworkMonitor()) -> OnboardingViewModel {
        OnboardingViewModel(learningProfileAPI: service, networkMonitor: network,
            logger: AppLogger(subsystem: "com.verifiedai.tests", category: "preference"))
    }
    private func settle(_ predicate: () -> Bool) async {
        for _ in 0..<300 { if predicate() { return }; await Task.yield() }
        XCTFail("Expected bounded preference transition")
    }

    func testPersistedPreferenceNarrowPatchAndReopeningRestoreAcceptedDepth() async throws {
        let service = PreferenceServiceDouble()
        let vm = model(service)
        await vm.bootstrap(userId: user)
        vm.beginExplanationPresentation(owner: owner, userId: user)
        XCTAssertEqual(vm.explanationDepth, .standard)
        let original = try XCTUnwrap(vm.acceptedProfile)
        vm.selectExplanationDepth(.deep, owner: owner)
        await settle { vm.explanationPreferenceStatus == .saved }
        XCTAssertEqual(vm.acceptedProfile?.explanationDepth, .deep)
        XCTAssertEqual(vm.acceptedProfile?.educationLevel, original.educationLevel)
        XCTAssertEqual(vm.acceptedProfile?.preferredLanguage, original.preferredLanguage)
        XCTAssertEqual(vm.acceptedProfile?.dailyStudyMinutes, original.dailyStudyMinutes)
        XCTAssertEqual(vm.acceptedProfile?.timezone, original.timezone)
        XCTAssertEqual(vm.acceptedProfile?.goalContext, original.goalContext)
        XCTAssertEqual(service.requests.map(\.depth), [.deep])
        XCTAssertEqual(service.requests.map(\.version), [0])
        vm.endExplanationPresentation(owner: owner)
        vm.beginExplanationPresentation(owner: UUID(), userId: user)
        XCTAssertEqual(vm.explanationDepth, .deep)
        let relaunched = model(service)
        await relaunched.bootstrap(userId: user)
        XCTAssertEqual(relaunched.explanationDepth, .deep)
    }

    func testDepthPatchContainsExactlyTwoFields() throws {
        let data = try JSONEncoder().encode(ExplanationDepthPatch(depth: .beginner, expectedVersion: 7))
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
        XCTAssertEqual(Set(json.keys), ["explanationDepth", "expectedVersion"])
        XCTAssertEqual(json["explanationDepth"] as? String, "BEGINNER")
        XCTAssertEqual(json["expectedVersion"] as? Int, 7)
    }

    func testRapidSelectionsSerializeAndCoalesceWithoutApplyingOldSelection() async {
        let service = PreferenceServiceDouble(); service.delaySave = true
        let vm = model(service); await vm.bootstrap(userId: user)
        vm.beginExplanationPresentation(owner: owner, userId: user)
        vm.selectExplanationDepth(.quick, owner: owner)
        await settle { service.pendingSaves.count == 1 }
        vm.selectExplanationDepth(.deep, owner: owner)
        vm.selectExplanationDepth(.beginner, owner: owner)
        XCTAssertEqual(service.requests.count, 1)
        service.releaseSave()
        await settle { service.requests.count == 2 && service.pendingSaves.count == 1 }
        XCTAssertEqual(vm.explanationDepth, .beginner)
        XCTAssertEqual(vm.acceptedProfile?.explanationDepth, .quick)
        XCTAssertEqual(vm.explanationPreferenceStatus, .saving)
        service.releaseSave()
        await settle { vm.explanationPreferenceStatus == .saved }
        XCTAssertEqual(service.requests.map(\.depth), [.quick, .beginner])
        XCTAssertEqual(service.requests.map(\.version), [0, 1])
        XCTAssertEqual(vm.acceptedProfile?.explanationDepth, .beginner)
    }

    func testConflictReloadRequiresExplicitSaveAndPreservesLatestSelection() async {
        let service = PreferenceServiceDouble()
        let vm = model(service); await vm.bootstrap(userId: user)
        vm.beginExplanationPresentation(owner: owner, userId: user)
        service.failure = .server(problem: ProblemDetails(type: "fixture", title: "private fixture detail", status: 409,
            code: "OPTIMISTIC_CONFLICT", traceId: nil, details: nil))
        vm.selectExplanationDepth(.quick, owner: owner)
        await settle { vm.explanationPreferenceStatus == .failed(.conflict) }
        vm.selectExplanationDepth(.deep, owner: owner)
        XCTAssertEqual(service.requests.count, 1)
        XCTAssertEqual(vm.explanationDepth, .deep)
        service.failure = nil
        service.profile = service.profile.replacing(depth: .beginner, version: 5)
        service.delayLoad = true
        vm.reloadExplanationPreference(owner: owner)
        await settle { service.pendingLoads.count == 1 }
        vm.selectExplanationDepth(.standard, owner: owner)
        vm.selectExplanationDepth(.deep, owner: owner)
        XCTAssertEqual(vm.explanationDepth, .deep)
        XCTAssertEqual(service.requests.count, 1)
        service.pendingLoads.removeFirst().resume()
        await settle { vm.explanationPreferenceStatus == .unsaved }
        XCTAssertEqual(vm.acceptedProfile?.explanationDepth, .beginner)
        XCTAssertEqual(service.requests.count, 1)
        vm.saveExplanationPreference(owner: owner)
        await settle { vm.explanationPreferenceStatus == .saved }
        XCTAssertEqual(service.requests.last?.version, 5)
        XCTAssertEqual(vm.explanationDepth, .deep)
    }

    func testOfflineSelectionRemainsTemporaryAndDoesNotOverwriteProfile() async {
        let service = PreferenceServiceDouble(), network = StubNetworkMonitor()
        let vm = model(service, network: network); await vm.bootstrap(userId: user)
        vm.beginExplanationPresentation(owner: owner, userId: user)
        network.isReachable = false
        vm.selectExplanationDepth(.beginner, owner: owner)
        XCTAssertEqual(vm.explanationDepth, .beginner)
        XCTAssertEqual(vm.explanationPreferenceStatus, .failed(.offline))
        XCTAssertEqual(vm.acceptedProfile?.explanationDepth, .standard)
        XCTAssertTrue(service.requests.isEmpty)
        network.isReachable = true
        vm.reloadExplanationPreference(owner: owner)
        await settle { vm.explanationPreferenceStatus == .unsaved }
        XCTAssertTrue(service.requests.isEmpty)
    }

    func testTimeoutAuthForbiddenValidationAndInvalidResponseAreVisible() async {
        let failures: [(NetworkError, ExplanationPreferenceError)] = [
            (.transport("synthetic_timeout"), .network), (.httpStatus(401), .auth), (.httpStatus(403), .forbidden),
            (.server(problem: ProblemDetails(type: "fixture", title: "private detail", status: 422,
                code: "PROFILE_VALIDATION_FAILED", traceId: nil, details: nil)), .validation),
            (.invalidResponse, .unavailable)
        ]
        for (failure, expected) in failures {
            let service = PreferenceServiceDouble()
            // Use one injected service per case; no transport or paid provider participates.
            let subject = model(service)
            await subject.bootstrap(userId: user)
            subject.beginExplanationPresentation(owner: owner, userId: user)
            service.failure = failure
            subject.selectExplanationDepth(.deep, owner: owner)
            await settle { subject.explanationPreferenceStatus == .failed(expected) }
            XCTAssertEqual(subject.acceptedProfile?.explanationDepth, .standard)
            XCTAssertEqual(subject.explanationDepth, .deep)
            XCTAssertEqual(subject.explanationPreferenceStatus.canReload, expected != .auth && expected != .forbidden)
            subject.reset()
        }
    }

    func testAccountSwitchLogoutAndDeletionRejectDelayedPreferenceResponse() async {
        for switchAccount in [false, true] {
            let service = PreferenceServiceDouble(); service.delaySave = true
            let vm = model(service); await vm.bootstrap(userId: user)
            vm.beginExplanationPresentation(owner: owner, userId: user)
            vm.selectExplanationDepth(.deep, owner: owner)
            await settle { service.pendingSaves.count == 1 }
            vm.reset() // RootView uses the same reset for logout and successful deletion.
            let nextUser = UUID()
            if switchAccount {
                service.profile = .completed().replacing(depth: .quick, version: 9, userId: nextUser)
                await vm.bootstrap(userId: nextUser)
            }
            service.releaseSave()
            for _ in 0..<20 { await Task.yield() }
            XCTAssertNil(vm.temporaryExplanationDepth)
            XCTAssertEqual(vm.explanationDepth, switchAccount ? .quick : .standard)
            XCTAssertEqual(vm.acceptedProfile?.userId, switchAccount ? nextUser : nil)
        }
    }

    func testReorderedBootstrapAndDismissedSaveCannotRestoreStalePreference() async {
        let service = PreferenceServiceDouble(); service.delayLoad = true
        let vm = model(service)
        let old = Task { await vm.bootstrap(userId: user) }
        await settle { service.pendingLoads.count == 1 }
        let next = UUID()
        service.profile = .completed().replacing(depth: .beginner, version: 8, userId: next)
        let new = Task { await vm.bootstrap(userId: next) }
        await settle { service.pendingLoads.count == 2 }
        service.pendingLoads.removeLast().resume()
        await new.value
        service.pendingLoads.removeFirst().resume()
        await old.value
        XCTAssertEqual(vm.acceptedProfile?.userId, next)
        XCTAssertEqual(vm.explanationDepth, .beginner)

        service.delaySave = true
        vm.beginExplanationPresentation(owner: owner, userId: next)
        vm.selectExplanationDepth(.quick, owner: owner)
        await settle { service.pendingSaves.count == 1 }
        vm.endExplanationPresentation(owner: owner)
        service.releaseSave()
        for _ in 0..<20 { await Task.yield() }
        XCTAssertEqual(vm.acceptedProfile?.explanationDepth, .beginner)
        service.delayLoad = false
        vm.beginExplanationPresentation(owner: UUID(), userId: next)
        await settle { vm.explanationPreferenceStatus == .saved }
        XCTAssertEqual(vm.explanationDepth, .quick) // Reconciled by GET, never the discarded response.
    }

    func testMissingPreferenceUsesExplicitStandardFallbackAndWrongAccountIsRejected() async {
        let service = PreferenceServiceDouble()
        service.profile = .completed().replacing(depth: nil, version: 1)
        let vm = model(service); await vm.bootstrap(userId: user)
        vm.beginExplanationPresentation(owner: owner, userId: user)
        XCTAssertEqual(vm.explanationDepth, .standard)
        XCTAssertEqual(vm.explanationPreferenceStatus, .fallback)
        vm.reset()
        await vm.bootstrap(userId: UUID())
        XCTAssertNil(vm.acceptedProfile)
        XCTAssertEqual(vm.explanationDepth, .standard)
    }

    func testForeignSaveResponseNeverBecomesAcceptedProfile() async {
        let service = PreferenceServiceDouble()
        let vm = model(service); await vm.bootstrap(userId: user)
        vm.beginExplanationPresentation(owner: owner, userId: user)
        service.responseUserId = UUID()
        vm.selectExplanationDepth(.deep, owner: owner)
        await settle { vm.explanationPreferenceStatus == .failed(.unavailable) }
        XCTAssertEqual(vm.acceptedProfile?.userId, user)
        XCTAssertEqual(vm.acceptedProfile?.explanationDepth, .standard)
        XCTAssertEqual(vm.explanationDepth, .deep)
    }
}

@MainActor
private final class PreferenceServiceDouble: LearningProfileServicing {
    struct Request { let depth: ExplanationDepth; let version: Int64 }
    var profile = LearningProfile.completed()
    var failure: NetworkError?
    var responseUserId: UUID?
    var delaySave = false, delayLoad = false
    var requests: [Request] = []
    var pendingSaves: [CheckedContinuation<Void, Never>] = []
    var pendingLoads: [CheckedContinuation<Void, Never>] = []

    func currentProfile() async throws -> LearningProfile {
        let captured = profile
        if delayLoad { await withCheckedContinuation { pendingLoads.append($0) } }
        return captured
    }
    func saveProfile(_ draft: LearningProfileDraft, completeOnboarding: Bool) async throws -> LearningProfile { profile }
    func saveExplanationDepth(_ depth: ExplanationDepth, expectedVersion: Int64) async throws -> LearningProfile {
        requests.append(Request(depth: depth, version: expectedVersion))
        let saved = profile.replacing(depth: depth, version: expectedVersion + 1, userId: responseUserId)
        if delaySave { await withCheckedContinuation { pendingSaves.append($0) } }
        if let failure { throw failure }
        if profile.userId == saved.userId { profile = saved }
        return saved
    }
    func releaseSave() { pendingSaves.removeFirst().resume() }
}

private extension LearningProfile {
    func replacing(depth: ExplanationDepth?, version: Int64, userId: UUID? = nil) -> LearningProfile {
        LearningProfile(exists: exists, id: id, userId: userId ?? self.userId, educationLevel: educationLevel,
            preferredLanguage: preferredLanguage, explanationDepth: depth, dailyStudyMinutes: dailyStudyMinutes,
            timezone: timezone, goalContext: goalContext, onboardingStatus: onboardingStatus, version: version,
            createdAt: createdAt, updatedAt: updatedAt)
    }
}
