import Foundation
import Combine

public enum VerificationUrgencyPhase: String, Sendable, CaseIterable {
    case calm
    case focused
    case finalCountdown
    case unlocked

    public static func fromRemainingSeconds(_ seconds: Int) -> VerificationUrgencyPhase {
        if seconds <= 0 {
            return .unlocked
        } else if seconds <= 10 {
            return .finalCountdown
        } else if seconds <= 30 {
            return .focused
        } else {
            return .calm
        }
    }
}

@MainActor
public final class PracticeSessionViewModel: ObservableObject {
    @Published public private(set) var session: PracticeSession?
    @Published public private(set) var currentItemIndex: Int = 0
    @Published public private(set) var remainingLockSeconds: Int = 60
    @Published public private(set) var isLockActive: Bool = false
    @Published public private(set) var urgencyPhase: VerificationUrgencyPhase = .calm
    @Published public var submittedAnswer: String = ""
    @Published public private(set) var isSubmitting: Bool = false
    @Published public private(set) var isOffline: Bool = false
    @Published public private(set) var rapidGuessWarningActive: Bool = false
    @Published public private(set) var trapFeedback: String? = nil
    @Published public private(set) var trapClosureCelebration: Bool = false
    @Published public private(set) var errorMessage: String? = nil

    private let userId: UUID
    private let cacheStore: PracticeCacheStoreProtocol
    private let startRemoteHandler: (@Sendable (UUID, String, PracticeSessionMode) async throws -> PracticeSession)?
    private let submitRemoteHandler: (@Sendable (UUID, UUID, String, Int, Bool) async throws -> PracticeSubmissionResponse)?
    private let abandonRemoteHandler: (@Sendable (UUID) async throws -> PracticeSession)?

    public init(
        userId: UUID,
        cacheStore: PracticeCacheStoreProtocol? = nil,
        startRemoteHandler: (@Sendable (UUID, String, PracticeSessionMode) async throws -> PracticeSession)? = nil,
        submitRemoteHandler: (@Sendable (UUID, UUID, String, Int, Bool) async throws -> PracticeSubmissionResponse)? = nil,
        abandonRemoteHandler: (@Sendable (UUID) async throws -> PracticeSession)? = nil
    ) {
        self.userId = userId
        self.cacheStore = cacheStore ?? PracticeSQLiteCache()
        self.startRemoteHandler = startRemoteHandler
        self.submitRemoteHandler = submitRemoteHandler
        self.abandonRemoteHandler = abandonRemoteHandler
    }

    public var currentItem: PracticeSessionItem? {
        guard let s = session, currentItemIndex >= 0 && currentItemIndex < s.items.count else {
            return nil
        }
        return s.items[currentItemIndex]
    }

    public var totalItemsCount: Int {
        session?.totalItems ?? 4
    }

    public var isSessionCompleted: Bool {
        session?.status == .completed
    }

    public func loadOrCreateSession(targetSkillId: String, sessionMode: PracticeSessionMode = .daily121) async {
        isSubmitting = true
        errorMessage = nil

        do {
            let fetched: PracticeSession
            if let handler = startRemoteHandler {
                fetched = try await handler(userId, targetSkillId, sessionMode)
            } else {
                // Offline fallback from local cache if available
                if let cached = await cacheStore.loadLatestActiveSession(for: userId) {
                    fetched = cached
                    self.isOffline = true
                } else {
                    // Create simulated session
                    fetched = createSimulatedSession(targetSkillId: targetSkillId, mode: sessionMode)
                    self.isOffline = true
                }
            }

            self.session = fetched
            await cacheStore.saveSession(fetched, for: userId)
            syncCurrentItemLockState()
        } catch {
            if let cached = await cacheStore.loadLatestActiveSession(for: userId) {
                self.session = cached
                self.isOffline = true
                self.errorMessage = nil
            } else {
                self.errorMessage = error.localizedDescription
            }
        }

        isSubmitting = false
    }

    public func tickVerificationTimer() {
        guard isLockActive && remainingLockSeconds > 0 else {
            if isLockActive && remainingLockSeconds <= 0 {
                isLockActive = false
                urgencyPhase = .unlocked
            }
            return
        }

        remainingLockSeconds -= 1
        urgencyPhase = VerificationUrgencyPhase.fromRemainingSeconds(remainingLockSeconds)
        if remainingLockSeconds <= 0 {
            isLockActive = false
        }
    }

    public func submitCurrentAnswer(clientElapsedSeconds: Int = 15) async {
        guard let current = currentItem, let s = session else { return }

        // Rapid guess prevention guard (INV-PRAC-004)
        if current.isTwinVerification && clientElapsedSeconds < 8 && !submittedAnswer.isEmpty {
            rapidGuessWarningActive = true
            return
        }

        isSubmitting = true
        rapidGuessWarningActive = false
        errorMessage = nil

        do {
            let response: PracticeSubmissionResponse
            if let handler = submitRemoteHandler {
                response = try await handler(s.id, current.id, submittedAnswer, clientElapsedSeconds, false)
            } else {
                response = simulateSubmission(session: s, item: current, answer: submittedAnswer)
            }

            self.session = response.session
            await cacheStore.saveSession(response.session, for: userId)

            if response.isCorrect {
                trapFeedback = nil
                if response.trapClosureAchieved {
                    trapClosureCelebration = true
                }
            } else {
                if response.trapTriggered {
                    trapFeedback = response.trapDiagnosticMessageEn ?? response.trapDiagnosticMessageTr
                }
            }
        } catch {
            errorMessage = error.localizedDescription
        }

        isSubmitting = false
    }

    public func requestHint() async {
        guard !isLockActive else { return }
        guard let current = currentItem, let s = session else { return }

        do {
            if let handler = submitRemoteHandler {
                let response = try await handler(s.id, current.id, "", 0, true)
                self.session = response.session
                await cacheStore.saveSession(response.session, for: userId)
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    public func moveToNextQuestion() {
        guard let s = session else { return }
        if currentItemIndex + 1 < s.items.count {
            currentItemIndex += 1
            submittedAnswer = ""
            trapFeedback = nil
            rapidGuessWarningActive = false
            syncCurrentItemLockState()
        }
    }

    public func abandonSession() async {
        guard let s = session else { return }
        isSubmitting = true

        do {
            let updated: PracticeSession
            if let handler = abandonRemoteHandler {
                updated = try await handler(s.id)
            } else {
                updated = PracticeSession(
                    id: s.id,
                    userId: s.userId,
                    sourceRecommendationId: s.sourceRecommendationId,
                    sessionMode: s.sessionMode,
                    status: .abandoned,
                    targetSkillId: s.targetSkillId,
                    activeTrapFamily: s.activeTrapFamily,
                    learnerMasterySnapshot: s.learnerMasterySnapshot,
                    totalItems: s.totalItems,
                    completedItems: s.completedItems,
                    verifiedCorrectItems: s.verifiedCorrectItems,
                    trapClosureAchieved: s.trapClosureAchieved,
                    startedAt: s.startedAt,
                    completedAt: s.completedAt,
                    abandonedAt: Date(),
                    updatedAt: Date(),
                    items: s.items
                )
            }
            self.session = updated
            await cacheStore.saveSession(updated, for: userId)
        } catch {
            errorMessage = error.localizedDescription
        }

        isSubmitting = false
    }

    private func syncCurrentItemLockState() {
        if let current = currentItem, current.isTwinVerification && current.itemStatus == .pending {
            remainingLockSeconds = current.lockState.remainingLockSeconds > 0 ? current.lockState.remainingLockSeconds : 60
            isLockActive = remainingLockSeconds > 0
            urgencyPhase = VerificationUrgencyPhase.fromRemainingSeconds(remainingLockSeconds)
        } else {
            remainingLockSeconds = 0
            isLockActive = false
            urgencyPhase = .unlocked
        }
    }

    private func createSimulatedSession(targetSkillId: String, mode: PracticeSessionMode) -> PracticeSession {
        let sid = UUID()
        let item1 = PracticeSessionItem(
            id: UUID(),
            sessionId: sid,
            userId: userId,
            ordinalIndex: 0,
            slotRole: .trapClosure,
            questionSource: .deterministicTwinTier1,
            sourceQuestionId: "SIM-TWIN-01",
            skillId: targetSkillId,
            trapFamily: "ALG_DOMAIN_EXCLUSION_MISSED",
            targetDifficulty: 0.5000,
            itemDifficulty: 0.4900,
            zpdWindow: ZpdWindow(lowerBound: 0.4000, upperBound: 0.6000, targetDifficulty: 0.5000),
            promptLatex: "\\frac{2x}{x-3} + \\frac{4}{x-5} = \\frac{6}{x-3}",
            promptInstructionTr: "Denklemi çözünüz.",
            promptInstructionEn: "Solve the equation.",
            canonicalSolutionExpr: "3",
            trapDistractorExpr: "{3, 5}",
            sympyVerificationHash: "a1b2c3d4e5f67890123456789abcdef0123456789abcdef0123456789abcdef0",
            isTwinVerification: true,
            lockState: VerificationLockState(isLocked: true, lockDurationSeconds: 60, remainingLockSeconds: 60, unlockedAt: Date()),
            hintsUnlockedCount: 0,
            attemptCount: 0,
            itemStatus: .pending,
            submittedAnswerLatex: nil,
            detectedTrapTriggered: false,
            verificationDurationMs: nil,
            submittedAt: nil,
            createdAt: Date()
        )

        return PracticeSession(
            id: sid,
            userId: userId,
            sourceRecommendationId: nil,
            sessionMode: mode,
            status: .inProgress,
            targetSkillId: targetSkillId,
            activeTrapFamily: "ALG_DOMAIN_EXCLUSION_MISSED",
            learnerMasterySnapshot: 0.5500,
            totalItems: 4,
            completedItems: 0,
            verifiedCorrectItems: 0,
            trapClosureAchieved: false,
            startedAt: Date(),
            completedAt: nil,
            abandonedAt: nil,
            updatedAt: Date(),
            items: [item1]
        )
    }

    private func simulateSubmission(session: PracticeSession, item: PracticeSessionItem, answer: String) -> PracticeSubmissionResponse {
        let isCorrect = (answer.trimmingCharacters(in: .whitespaces) == item.canonicalSolutionExpr)
        let isTrap = (item.trapDistractorExpr != nil && answer.trimmingCharacters(in: .whitespaces) == item.trapDistractorExpr)

        var updatedItems = session.items
        if let idx = updatedItems.firstIndex(where: { $0.id == item.id }) {
            let updated = PracticeSessionItem(
                id: item.id,
                sessionId: item.sessionId,
                userId: item.userId,
                ordinalIndex: item.ordinalIndex,
                slotRole: item.slotRole,
                questionSource: item.questionSource,
                sourceQuestionId: item.sourceQuestionId,
                skillId: item.skillId,
                trapFamily: item.trapFamily,
                targetDifficulty: item.targetDifficulty,
                itemDifficulty: item.itemDifficulty,
                zpdWindow: item.zpdWindow,
                promptLatex: item.promptLatex,
                promptInstructionTr: item.promptInstructionTr,
                promptInstructionEn: item.promptInstructionEn,
                canonicalSolutionExpr: item.canonicalSolutionExpr,
                trapDistractorExpr: item.trapDistractorExpr,
                sympyVerificationHash: item.sympyVerificationHash,
                isTwinVerification: item.isTwinVerification,
                lockState: item.lockState,
                hintsUnlockedCount: item.hintsUnlockedCount,
                attemptCount: item.attemptCount + 1,
                itemStatus: isCorrect ? .submittedCorrect : .submittedIncorrect,
                submittedAnswerLatex: answer,
                detectedTrapTriggered: isTrap,
                verificationDurationMs: 45,
                submittedAt: Date(),
                createdAt: item.createdAt
            )
            updatedItems[idx] = updated
        }

        let updatedSession = PracticeSession(
            id: session.id,
            userId: session.userId,
            sourceRecommendationId: session.sourceRecommendationId,
            sessionMode: session.sessionMode,
            status: session.completedItems + 1 >= session.totalItems ? .completed : session.status,
            targetSkillId: session.targetSkillId,
            activeTrapFamily: session.activeTrapFamily,
            learnerMasterySnapshot: session.learnerMasterySnapshot,
            totalItems: session.totalItems,
            completedItems: session.completedItems + 1,
            verifiedCorrectItems: isCorrect ? session.verifiedCorrectItems + 1 : session.verifiedCorrectItems,
            trapClosureAchieved: isCorrect && item.isTwinVerification,
            startedAt: session.startedAt,
            completedAt: session.completedItems + 1 >= session.totalItems ? Date() : nil,
            abandonedAt: session.abandonedAt,
            updatedAt: Date(),
            items: updatedItems
        )

        return PracticeSubmissionResponse(
            itemStatus: isCorrect ? .submittedCorrect : .submittedIncorrect,
            isCorrect: isCorrect,
            trapTriggered: isTrap,
            trapDiagnosticKey: isTrap ? "TRAP_DOMAIN_EXCLUSION" : nil,
            trapDiagnosticMessageTr: isTrap ? "Payda sıfırlayan kök kurallarını inceleyiniz." : nil,
            trapDiagnosticMessageEn: isTrap ? "Check denominator exclusion rules." : nil,
            verificationLockActive: false,
            remainingLockSeconds: 0,
            trapClosureAchieved: isCorrect && item.isTwinVerification,
            session: updatedSession
        )
    }
}
