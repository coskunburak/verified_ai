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

    init(
        learningProfileAPI: LearningProfileServicing,
        networkMonitor: NetworkMonitoring,
        logger: AppLogger
    ) {
        self.learningProfileAPI = learningProfileAPI
        self.networkMonitor = networkMonitor
        self.logger = logger
    }

    func bootstrap() async {
        guard networkMonitor.isReachable else {
            logger.warning("onboarding.bootstrap.offline")
            state = .offline
            return
        }

        state = .loading
        message = nil

        do {
            let profile = try await learningProfileAPI.currentProfile()
            apply(profile)
            logger.info(profile.isOnboardingComplete ? "onboarding.bootstrap.ready" : "onboarding.bootstrap.required")
        } catch NetworkError.server(let problem) where problem.code == "AUTH_TOKEN_EXPIRED" {
            state = .failed(.unauthorized)
            message = "Sign in again to continue."
            logger.warning("onboarding.bootstrap.unauthorized")
        } catch {
            state = .failed(.network)
            message = "Profile could not be loaded. Try again."
            logger.warning("onboarding.bootstrap.failed")
        }
    }

    func reset() {
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
            await saveProgress()
            if state == .needsOnboarding {
                currentStep = next
            }
        }
    }

    func completeOnboarding() async -> Bool {
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
            apply(profile)
            logger.info("onboarding.completed")
            return profile.isOnboardingComplete
        } catch NetworkError.server(let problem) where problem.code == "OPTIMISTIC_CONFLICT" {
            state = .failed(.conflict)
            message = "Profile changed on another device. Reload and review your answers."
            logger.warning("onboarding.complete.conflict")
            return false
        } catch NetworkError.server(let problem) where problem.code == "PROFILE_VALIDATION_FAILED" {
            state = .failed(.validation)
            message = problem.title
            logger.warning("onboarding.complete.validation_failed")
            return false
        } catch {
            state = .failed(.network)
            message = "Profile could not be saved. Try again."
            logger.warning("onboarding.complete.failed")
            return false
        }
    }

    private func saveProgress() async {
        guard networkMonitor.isReachable else {
            state = .offline
            logger.warning("onboarding.progress.offline")
            return
        }

        do {
            let profile = try await learningProfileAPI.saveProfile(draft, completeOnboarding: false)
            draft.expectedVersion = profile.version
            state = .needsOnboarding
            logger.info("onboarding.profile_saved")
        } catch NetworkError.server(let problem) where problem.code == "OPTIMISTIC_CONFLICT" {
            state = .failed(.conflict)
            message = "Profile changed on another device. Reload and review your answers."
            logger.warning("onboarding.progress.conflict")
        } catch {
            state = .failed(.network)
            message = "Progress could not be saved. Your answers are still on this screen."
            logger.warning("onboarding.progress.failed")
        }
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
