import Foundation
import Observation

@MainActor
@Observable
final class OnboardingViewModel {
    private let learningProfileAPI: LearningProfileServicing
    private let networkMonitor: NetworkMonitoring
    private let logger: AppLogger

    private(set) var state: OnboardingState = .idle
    var draft = LearningProfileDraft()
    var currentStep: OnboardingStep = .education
    private(set) var message: String?
    private var accountId: UUID?
    private var generation = 0
    private var preferenceGeneration = 0
    private var presentationOwner: UUID?
    @ObservationIgnored private var preferenceTask: Task<Void, Never>?
    private var preferenceNeedsReload = false
    private(set) var temporaryExplanationDepth: ExplanationDepth?
    private(set) var explanationPreferenceStatus: ExplanationPreferenceStatus = .fallback

    var acceptedProfile: LearningProfile? {
        guard case .ready(let profile) = state, profile.userId == accountId else { return nil }
        return profile
    }
    var explanationDepth: ExplanationDepth {
        temporaryExplanationDepth ?? acceptedProfile?.explanationDepth ?? .standard
    }

    init(
        learningProfileAPI: LearningProfileServicing,
        networkMonitor: NetworkMonitoring,
        logger: AppLogger
    ) {
        self.learningProfileAPI = learningProfileAPI
        self.networkMonitor = networkMonitor
        self.logger = logger
    }

    func bootstrap(userId: UUID? = nil) async {
        if let userId, accountId != userId { reset(); accountId = userId }
        preferenceGeneration += 1
        preferenceTask?.cancel()
        preferenceTask = nil
        if presentationOwner != nil {
            preferenceNeedsReload = true
            explanationPreferenceStatus = .loading
        }
        generation += 1
        let token = generation
        #if DEBUG
        if ProcessInfo.processInfo.arguments.contains("--ui-testing-authenticated"),
           !ProcessInfo.processInfo.arguments.contains("--solve-fixture") {
            state = .ready(LearningProfile(
                exists: true,
                id: UUID(uuidString: "00000000-0000-0000-0000-0000000000B1")!,
                userId: UUID(uuidString: "00000000-0000-0000-0000-0000000000A1")!,
                educationLevel: .highSchool,
                preferredLanguage: "en",
                explanationDepth: .standard,
                dailyStudyMinutes: 25,
                timezone: TimeZone.current.identifier,
                goalContext: "UI testing",
                onboardingStatus: .completed,
                version: 1,
                createdAt: Date(),
                updatedAt: Date()
            ))
            accountId = userId ?? UUID(uuidString: "00000000-0000-0000-0000-0000000000A1")!
            message = nil
            return
        }
        #endif

        guard networkMonitor.isReachable else {
            logger.warning("onboarding.bootstrap.offline")
            if presentationOwner != nil { preferenceFailed(.offline) }
            state = .offline
            return
        }

        state = .loading
        message = nil

        do {
            let profile = try await learningProfileAPI.currentProfile()
            guard token == generation, !Task.isCancelled else { return }
            guard accountId == nil || accountId == profile.userId else { throw NetworkError.invalidResponse }
            accountId = profile.userId
            apply(profile)
            if presentationOwner != nil {
                preferenceNeedsReload = false
                explanationPreferenceStatus = temporaryExplanationDepth != nil ? .unsaved
                    : (profile.explanationDepth == nil ? .fallback : .saved)
            }
            logger.info(profile.isOnboardingComplete ? "onboarding.bootstrap.ready" : "onboarding.bootstrap.required")
        } catch NetworkError.server(let problem) where problem.code == "AUTH_TOKEN_EXPIRED" {
            guard token == generation, !Task.isCancelled else { return }
            if presentationOwner != nil { preferenceFailed(.auth) }
            state = .failed(.unauthorized)
            message = "Sign in again to continue."
            logger.warning("onboarding.bootstrap.unauthorized")
        } catch {
            guard token == generation, !Task.isCancelled else { return }
            if presentationOwner != nil { preferenceFailed(ExplanationPreferenceError.from(error)) }
            state = .failed(.network)
            message = "Profile could not be loaded. Try again."
            logger.warning("onboarding.bootstrap.failed")
        }
    }

    func reset() {
        generation += 1
        preferenceGeneration += 1
        preferenceTask?.cancel()
        preferenceTask = nil
        accountId = nil
        presentationOwner = nil
        temporaryExplanationDepth = nil
        explanationPreferenceStatus = .fallback
        preferenceNeedsReload = false
        state = .idle
        draft = LearningProfileDraft()
        currentStep = .education
        message = nil
    }

    func advance() async {
        guard validateCurrentStep() else {
            return
        }

        if let next = currentStep.next {
            let token = generation
            await saveProgress()
            guard token == generation, !Task.isCancelled else { return }
            if state == .needsOnboarding {
                currentStep = next
            }
        }
    }

    func completeOnboarding() async -> Bool {
        let token = generation
        guard networkMonitor.isReachable else {
            state = .offline
            logger.warning("onboarding.complete.offline")
            return false
        }
        guard validateCompletion() else {
            return false
        }

        state = .saving
        message = nil

        do {
            let profile = try await learningProfileAPI.saveProfile(draft, completeOnboarding: true)
            guard token == generation, !Task.isCancelled else { return false }
            guard accountId == nil || accountId == profile.userId else { throw NetworkError.invalidResponse }
            accountId = profile.userId
            apply(profile)
            logger.info("onboarding.completed")
            return profile.isOnboardingComplete
        } catch NetworkError.server(let problem) where problem.code == "OPTIMISTIC_CONFLICT" {
            guard token == generation, !Task.isCancelled else { return false }
            state = .failed(.conflict)
            message = "Profile changed on another device. Reload and review your answers."
            logger.warning("onboarding.complete.conflict")
            return false
        } catch NetworkError.server(let problem) where problem.code == "PROFILE_VALIDATION_FAILED" {
            guard token == generation, !Task.isCancelled else { return false }
            state = .failed(.validation)
            message = problem.title
            logger.warning("onboarding.complete.validation_failed")
            return false
        } catch {
            guard token == generation, !Task.isCancelled else { return false }
            state = .failed(.network)
            message = "Profile could not be saved. Try again."
            logger.warning("onboarding.complete.failed")
            return false
        }
    }

    private func saveProgress() async {
        let token = generation
        guard networkMonitor.isReachable else {
            state = .offline
            logger.warning("onboarding.progress.offline")
            return
        }

        do {
            let profile = try await learningProfileAPI.saveProfile(draft, completeOnboarding: false)
            guard token == generation, !Task.isCancelled else { return }
            guard accountId == nil || accountId == profile.userId else { throw NetworkError.invalidResponse }
            accountId = profile.userId
            draft.expectedVersion = profile.version
            state = .needsOnboarding
            logger.info("onboarding.profile_saved")
        } catch NetworkError.server(let problem) where problem.code == "OPTIMISTIC_CONFLICT" {
            guard token == generation, !Task.isCancelled else { return }
            state = .failed(.conflict)
            message = "Profile changed on another device. Reload and review your answers."
            logger.warning("onboarding.progress.conflict")
        } catch {
            guard token == generation, !Task.isCancelled else { return }
            state = .failed(.network)
            message = "Progress could not be saved. Your answers are still on this screen."
            logger.warning("onboarding.progress.failed")
        }
    }

    func beginExplanationPresentation(owner: UUID, userId: UUID) {
        guard accountId == userId else { return }
        if presentationOwner == owner { return }
        if let previous = presentationOwner { endExplanationPresentation(owner: previous) }
        presentationOwner = owner
        temporaryExplanationDepth = nil
        explanationPreferenceStatus = acceptedProfile?.explanationDepth == nil ? .fallback : .saved
        logger.info("explanation.preference.loaded")
        if preferenceNeedsReload { reloadExplanationPreference(owner: owner) }
    }

    func ownsExplanationPresentation(owner: UUID, userId: UUID) -> Bool {
        presentationOwner == owner && accountId == userId
    }

    func endExplanationPresentation(owner: UUID) {
        guard presentationOwner == owner else { return }
        if preferenceTask != nil { preferenceNeedsReload = true }
        preferenceGeneration += 1
        preferenceTask?.cancel()
        preferenceTask = nil
        presentationOwner = nil
        temporaryExplanationDepth = nil
        explanationPreferenceStatus = .fallback
    }

    func selectExplanationDepth(_ depth: ExplanationDepth, owner: UUID) {
        guard presentationOwner == owner else { return }
        temporaryExplanationDepth = depth
        logger.info("explanation.mode.selected")
        if depth == .deep { logger.info("explanation.extended.unavailable") }
        guard preferenceTask == nil else { return } // Latest selection is coalesced by the save loop.
        guard !preferenceNeedsReload else { return }
        saveExplanationPreference(owner: owner)
    }

    func saveExplanationPreference(owner: UUID) {
        guard presentationOwner == owner, preferenceTask == nil, !preferenceNeedsReload else { return }
        guard let selected = temporaryExplanationDepth else { return }
        if acceptedProfile?.explanationDepth == selected {
            temporaryExplanationDepth = nil
            explanationPreferenceStatus = .saved
            return
        }
        guard networkMonitor.isReachable else { preferenceFailed(.offline); return }
        guard acceptedProfile?.version != nil else { preferenceFailed(.unavailable); return }
        let token = preferenceGeneration
        explanationPreferenceStatus = .saving
        preferenceTask = Task { [weak self] in
            guard let self else { return }
            defer { if token == self.preferenceGeneration { self.preferenceTask = nil } }
            do {
                while let selected = self.temporaryExplanationDepth,
                      let profile = self.acceptedProfile, let version = profile.version {
                    if profile.explanationDepth == selected {
                        self.temporaryExplanationDepth = nil
                        self.explanationPreferenceStatus = .saved
                        return
                    }
                    let saved = try await self.learningProfileAPI.saveExplanationDepth(selected, expectedVersion: version)
                    guard self.isCurrentPreference(token, owner: owner) else { return }
                    guard saved.userId == self.accountId, saved.id == profile.id,
                          saved.isOnboardingComplete, saved.explanationDepth == selected,
                          let savedVersion = saved.version, savedVersion > version else {
                        throw NetworkError.invalidResponse
                    }
                    self.apply(saved)
                    self.logger.info("explanation.preference.save_succeeded")
                    // A later selection remains visible while only the accepted response updates the profile.
                }
            } catch {
                guard self.isCurrentPreference(token, owner: owner) else { return }
                self.preferenceFailed(ExplanationPreferenceError.from(error))
            }
        }
    }

    /// GET only. Never automatically overwrites a conflict with a retrying PATCH.
    func reloadExplanationPreference(owner: UUID) {
        guard presentationOwner == owner, preferenceTask == nil else { return }
        guard networkMonitor.isReachable else { preferenceFailed(.offline); return }
        let token = preferenceGeneration
        explanationPreferenceStatus = .loading
        preferenceTask = Task { [weak self] in
            guard let self else { return }
            defer { if token == self.preferenceGeneration { self.preferenceTask = nil } }
            do {
                let profile = try await self.learningProfileAPI.currentProfile()
                guard self.isCurrentPreference(token, owner: owner) else { return }
                guard profile.userId == self.accountId, profile.isOnboardingComplete,
                      profile.version != nil else { throw NetworkError.invalidResponse }
                self.apply(profile)
                self.preferenceNeedsReload = false
                if self.temporaryExplanationDepth == profile.explanationDepth {
                    self.temporaryExplanationDepth = nil
                }
                self.explanationPreferenceStatus = self.temporaryExplanationDepth != nil ? .unsaved
                    : (profile.explanationDepth == nil ? .fallback : .saved)
                self.logger.info("explanation.preference.loaded")
            } catch {
                guard self.isCurrentPreference(token, owner: owner) else { return }
                self.preferenceFailed(ExplanationPreferenceError.from(error))
            }
        }
    }

    private func isCurrentPreference(_ token: Int, owner: UUID) -> Bool {
        token == preferenceGeneration && presentationOwner == owner && !Task.isCancelled
    }

    private func preferenceFailed(_ error: ExplanationPreferenceError) {
        // A timeout/cancellation may have committed remotely. Reload before any further write.
        preferenceNeedsReload = true
        explanationPreferenceStatus = .failed(error)
        logger.warning("explanation.preference.save_or_load_failed")
    }

    private func apply(_ profile: LearningProfile) {
        if profile.isOnboardingComplete {
            draft = LearningProfileDraft(profile: profile)
            currentStep = .review
            state = .ready(profile)
            message = nil
        } else {
            draft = LearningProfileDraft(profile: profile)
            currentStep = profile.resumeStep
            state = .needsOnboarding
            message = nil
        }
    }

    private func validateCurrentStep() -> Bool {
        if let validationMessage = draft.validationMessage(for: currentStep) {
            state = .failed(.validation)
            message = validationMessage
            logger.warning("onboarding.step.validation_failed")
            return false
        }
        message = nil
        return true
    }

    private func validateCompletion() -> Bool {
        if let validationMessage = draft.completionValidationMessage {
            state = .failed(.validation)
            message = validationMessage
            logger.warning("onboarding.complete.validation_failed")
            return false
        }
        return true
    }
}

enum OnboardingState: Equatable {
    case idle
    case loading
    case needsOnboarding
    case saving
    case ready(LearningProfile)
    case offline
    case failed(OnboardingClientError)
}

enum OnboardingClientError: Equatable {
    case validation
    case conflict
    case unauthorized
    case network
}

enum ExplanationPreferenceStatus: Equatable {
    case fallback, loading, saving, saved, unsaved
    case failed(ExplanationPreferenceError)

    var messageKey: String {
        switch self {
        case .fallback: "explanation.preference.fallback"
        case .loading: "explanation.preference.loading"
        case .saving: "explanation.preference.saving"
        case .saved: "explanation.preference.saved"
        case .unsaved: "explanation.preference.unsaved"
        case .failed(let error): "explanation.preference.error.\(error.rawValue)"
        }
    }
    var canReload: Bool {
        if case .failed(let error) = self { return error != .auth && error != .forbidden }
        return self == .fallback
    }
}

enum ExplanationPreferenceError: String, Equatable {
    case offline, network, conflict, validation, auth, forbidden, unavailable

    static func from(_ error: Error) -> Self {
        switch error {
        case NetworkError.server(let problem):
            switch problem.code {
            case "OPTIMISTIC_CONFLICT": return .conflict
            case "PROFILE_VALIDATION_FAILED": return .validation
            case "AUTH_TOKEN_EXPIRED": return .auth
            case "ACCOUNT_NOT_ACTIVE": return .forbidden
            default: return problem.status == 401 ? .auth : (problem.status == 403 ? .forbidden : .network)
            }
        case NetworkError.httpStatus(401): return .auth
        case NetworkError.httpStatus(403): return .forbidden
        case NetworkError.invalidResponse, NetworkError.decoding: return .unavailable
        default: return .network
        }
    }
}
