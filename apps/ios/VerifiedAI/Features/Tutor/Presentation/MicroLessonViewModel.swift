import Combine
import Foundation

@MainActor
public final class MicroLessonViewModel: ObservableObject {
    @Published public private(set) var card: MicroLessonCard?
    @Published public private(set) var isLoading = false
    @Published public private(set) var isSubmitting = false
    @Published public private(set) var currentBeat: MicroLessonBeat = .coreRule
    @Published public var selectedOptionId: String?
    @Published public private(set) var hasSubmittedQuickCheck = false
    @Published public private(set) var quickCheckPassed = false
    @Published public private(set) var completionReceipt: MicroLessonCompleteResponse?
    @Published public private(set) var errorLocalizationKey: String?
    @Published public private(set) var shouldFallbackToHints = false
    @Published public private(set) var isServingCachedOfflineCard = false

    private let userId: UUID
    private let client: MicroLessonClientProtocol
    private let cache: MicroLessonSQLiteCache
    private var triggerSource: MicroLessonTriggerSource = .coachRepairDrawer
    private var sourceAttemptId: UUID?
    private var openedAt: Date = Date()
    private var activeIdempotencyKey: String = ""

    public init(
        userId: UUID,
        client: MicroLessonClientProtocol,
        cache: MicroLessonSQLiteCache = MicroLessonSQLiteCache()
    ) {
        self.userId = userId
        self.client = client
        self.cache = cache
    }

    public func load(
        mistakeCode: String,
        locale: String = "tr-TR",
        triggerSource: MicroLessonTriggerSource,
        sourceAttemptId: UUID? = nil
    ) async {
        self.triggerSource = triggerSource
        self.sourceAttemptId = sourceAttemptId
        self.isLoading = true
        self.errorLocalizationKey = nil
        self.shouldFallbackToHints = false
        self.isServingCachedOfflineCard = false
        self.currentBeat = .coreRule
        self.selectedOptionId = nil
        self.hasSubmittedQuickCheck = false
        self.quickCheckPassed = false
        self.completionReceipt = nil
        self.openedAt = Date()
        self.activeIdempotencyKey = "micro-\(mistakeCode.lowercased())-\(UUID().uuidString.prefix(12))"

        defer { self.isLoading = false }

        do {
            let fetched = try await client.fetchMicroLesson(
                mistakeCode: mistakeCode,
                locale: locale,
                triggerSource: triggerSource,
                sourceAttemptId: sourceAttemptId
            )
            guard fetched.passesLocalIntegrityChecks else {
                self.errorLocalizationKey = "micro_lesson.error.verification_unavailable"
                self.shouldFallbackToHints = true
                return
            }
            self.card = fetched
            try? await cache.saveCard(fetched, userId: userId)
        } catch MicroLessonClientError.verificationFailed,
                MicroLessonClientError.featureDisabled,
                MicroLessonClientError.diagnosisNotFound,
                MicroLessonClientError.triggerNotEligible {
            self.card = nil
            self.errorLocalizationKey = "micro_lesson.error.verification_unavailable"
            self.shouldFallbackToHints = true
        } catch {
            if let cached = try? await cache.loadCard(userId: userId, mistakeCode: mistakeCode, locale: locale),
               cached.passesLocalIntegrityChecks {
                self.card = cached
                self.isServingCachedOfflineCard = true
            } else {
                self.card = nil
                self.errorLocalizationKey = "micro_lesson.error.verification_unavailable"
                self.shouldFallbackToHints = true
            }
        }
    }

    public func selectBeat(_ beat: MicroLessonBeat) {
        currentBeat = beat
    }

    public func nextBeat() {
        switch currentBeat {
        case .coreRule: currentBeat = .counterexample
        case .counterexample: currentBeat = .quickCheck
        case .quickCheck: break
        }
    }

    public func previousBeat() {
        switch currentBeat {
        case .coreRule: break
        case .counterexample: currentBeat = .coreRule
        case .quickCheck: currentBeat = .counterexample
        }
    }

    public var selectedOption: MicroLessonQuickCheckOption? {
        guard let card, let selectedOptionId else { return nil }
        return card.interactiveQuickCheck.options.first(where: { $0.optionId == selectedOptionId })
    }

    public func submitQuickCheck(now: Date = Date()) async {
        guard let card, let selectedOptionId, !isSubmitting else { return }
        isSubmitting = true
        defer { isSubmitting = false }

        let elapsedMs = Int(now.timeIntervalSince(openedAt) * 1000.0)
        let boundedDwellMs = min(max(elapsedMs, 1000), 600_000)

        let req = MicroLessonCompleteRequest(
            locale: card.locale,
            triggerSource: triggerSource,
            sourceAttemptId: sourceAttemptId,
            selectedOptionId: selectedOptionId,
            dwellTimeMs: boundedDwellMs,
            idempotencyKey: activeIdempotencyKey
        )

        let localMatch = selectedOptionId == card.interactiveQuickCheck.correctOptionId
        hasSubmittedQuickCheck = true
        quickCheckPassed = localMatch

        do {
            let receipt = try await client.completeMicroLesson(mistakeCode: card.mistakeCode, request: req)
            self.completionReceipt = receipt
            self.quickCheckPassed = receipt.quickCheckPassed
            try? await cache.removePendingCompletion(userId: userId, idempotencyKey: req.idempotencyKey)
        } catch {
            // Queue locally with idempotencyKey for deterministic replay on reconnect
            let pending = PendingMicroLessonCompletion(
                userId: userId,
                mistakeCode: card.mistakeCode,
                request: req
            )
            try? await cache.enqueuePendingCompletion(pending)
        }
    }

    public func flushPendingCompletions() async {
        guard let pending = try? await cache.pendingCompletions(userId: userId), !pending.isEmpty else {
            return
        }
        for item in pending {
            if let _ = try? await client.completeMicroLesson(mistakeCode: item.mistakeCode, request: item.request) {
                try? await cache.removePendingCompletion(userId: userId, idempotencyKey: item.request.idempotencyKey)
            }
        }
    }
}
