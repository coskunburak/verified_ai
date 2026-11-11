import Foundation
import Observation

@MainActor
@Observable
public final class TwinChallengeViewModel {
    public let attemptId: UUID
    private let service: any TwinChallengeServicing
    private let ownsAccount: @MainActor () async -> Bool

    public private(set) var challenge: VerifiedTwinChallenge?
    public private(set) var remainingSeconds: Int = 60
    public private(set) var isLoading: Bool = false
    public private(set) var isSubmitting: Bool = false
    public private(set) var errorMessage: String?
    public var learnerAnswer: String = ""
    public var showEarlyForfeitConfirmation: Bool = false

    public var isLocked: Bool {
        remainingSeconds > 0
    }

    @ObservationIgnored
    private var timerTask: Task<Void, Never>?
    @ObservationIgnored
    private var operationTask: Task<Void, Never>?

    public init(
        attemptId: UUID,
        service: any TwinChallengeServicing,
        ownsAccount: @escaping @MainActor () async -> Bool
    ) {
        self.attemptId = attemptId
        self.service = service
        self.ownsAccount = ownsAccount
    }

    public func loadChallenge() {
        operationTask?.cancel()
        isLoading = true
        errorMessage = nil

        operationTask = Task { [weak self] in
            guard let self = self else { return }

            guard await self.ownsAccount() else {
                self.isLoading = false
                self.errorMessage = "Unauthorized account access"
                return
            }

            do {
                let twin = try await self.service.generateTwin(attemptId: self.attemptId)
                guard !Task.isCancelled else { return }
                self.challenge = twin
                self.remainingSeconds = max(0, twin.remainingLockSeconds)
                self.isLoading = false
                self.startLockTimer()
            } catch {
                guard !Task.isCancelled else { return }
                self.isLoading = false
                self.errorMessage = "Failed to load twin drill"
            }
        }
    }

    public func startLockTimer() {
        timerTask?.cancel()
        guard remainingSeconds > 0 else { return }

        timerTask = Task { [weak self] in
            while true {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard !Task.isCancelled else { break }
                guard let self = self else { break }

                if self.remainingSeconds > 1 {
                    self.remainingSeconds -= 1
                } else {
                    self.remainingSeconds = 0
                    break
                }
            }
        }
    }

    public func submitAnswer() {
        guard let challenge = challenge, !learnerAnswer.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return }
        isSubmitting = true
        errorMessage = nil

        operationTask = Task { [weak self] in
            guard let self = self else { return }
            guard await self.ownsAccount() else {
                self.isSubmitting = false
                self.errorMessage = "Unauthorized account access"
                return
            }

            let payload = TwinSubmissionPayload(
                submissionType: "SUBMIT_ANSWER",
                learnerAnswerLatex: self.learnerAnswer,
                forceEarlyForfeit: false
            )

            do {
                let updated = try await self.service.submitTwin(twinId: challenge.twinId, payload: payload)
                guard !Task.isCancelled else { return }
                self.challenge = updated
                self.isSubmitting = false
            } catch {
                guard !Task.isCancelled else { return }
                self.isSubmitting = false
                self.errorMessage = "Answer verification failed"
            }
        }
    }

    public func requestRevealSolution() {
        if isLocked {
            showEarlyForfeitConfirmation = true
        } else {
            executeReveal(forceEarlyForfeit: false)
        }
    }

    public func confirmEarlyForfeitAndReveal() {
        showEarlyForfeitConfirmation = false
        executeReveal(forceEarlyForfeit: true)
    }

    private func executeReveal(forceEarlyForfeit: Bool) {
        guard let challenge = challenge else { return }
        isSubmitting = true
        errorMessage = nil

        operationTask = Task { [weak self] in
            guard let self = self else { return }
            guard await self.ownsAccount() else {
                self.isSubmitting = false
                self.errorMessage = "Unauthorized account access"
                return
            }

            let payload = TwinSubmissionPayload(
                submissionType: "REVEAL_SOLUTION",
                learnerAnswerLatex: nil,
                forceEarlyForfeit: forceEarlyForfeit
            )

            do {
                let updated = try await self.service.submitTwin(twinId: challenge.twinId, payload: payload)
                guard !Task.isCancelled else { return }
                self.challenge = updated
                self.remainingSeconds = 0
                self.timerTask?.cancel()
                self.isSubmitting = false
            } catch {
                guard !Task.isCancelled else { return }
                self.isSubmitting = false
                self.errorMessage = "Solution is locked for \(self.remainingSeconds) seconds"
            }
        }
    }

    public func cancel() {
        timerTask?.cancel()
        timerTask = nil
        operationTask?.cancel()
        operationTask = nil
        isLoading = false
        isSubmitting = false
    }

    deinit {
        timerTask?.cancel()
        operationTask?.cancel()
    }
}
