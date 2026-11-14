import Combine
import Foundation

@MainActor
public final class ColdStartDiagnosticViewModel: ObservableObject {
    public enum FlowStage: Equatable, Sendable {
        case preferences
        case question(Int)
        case result
    }

    @Published public private(set) var latestResult: ColdStartDiagnosticResult?
    @Published public private(set) var hasPendingOfflineSync: Bool = false
    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var isSubmitting: Bool = false
    @Published public var isFlowPresented: Bool = false
    @Published public private(set) var flowStage: FlowStage = .preferences

    @Published public var declaredExamTarget: ColdStartExamTarget = .tyt
    @Published public var declaredDailyBudgetMinutes: Int = 15
    @Published public var preferredModality: ColdStartModality = .socraticCoach
    @Published public var explicitPreferenceOverride: Bool = true
    @Published public var selectedOptionByItem: [ColdStartItemID: String] = [:]
    @Published public private(set) var skippedItemIds: Set<ColdStartItemID> = []
    @Published public private(set) var elapsedSecondsByItem: [ColdStartItemID: Int] = [:]
    @Published public private(set) var remainingSeconds: Int = ColdStartDiagnosticBank.totalTimeLimitSeconds
    @Published public private(set) var syncNoticeKey: String?
    @Published public private(set) var isFeatureDisabled: Bool = false

    public var requiresRevalidation: Bool {
        latestResult?.status == .revalidationRequired
    }

    public var isPreliminaryConfidence: Bool {
        latestResult?.confidenceTier == .preliminary
    }

    public var currentPrompt: ColdStartDiagnosticPrompt? {
        guard case .question(let index) = flowStage,
              ColdStartDiagnosticBank.prompts.indices.contains(index) else {
            return nil
        }
        return ColdStartDiagnosticBank.prompts[index]
    }

    private var learnerId: UUID
    private let localeProvider: @Sendable () -> String
    private let service: ColdStartDiagnosticServicing?
    private let cacheStore: ColdStartCacheStoreProtocol
    private let nowProvider: @Sendable () -> Date
    private var timerTask: Task<Void, Never>?
    private var activeQuestionStartTime: Date?
    private var activeQuestionIndex: Int?
    private var currentFlowIdempotencyKey: String?

    public init(
        learnerId: UUID = UUID(uuidString: "00000000-0000-4000-8000-0000000000A1")!,
        service: ColdStartDiagnosticServicing? = nil,
        cacheStore: ColdStartCacheStoreProtocol? = nil,
        localeProvider: @escaping @Sendable () -> String = {
            Locale.current.language.languageCode?.identifier == "tr" ? "tr-TR" : "en-US"
        },
        nowProvider: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.learnerId = learnerId
        self.service = service
        self.cacheStore = cacheStore ?? ColdStartSQLiteCache()
        self.localeProvider = localeProvider
        self.nowProvider = nowProvider
    }

    deinit {
        timerTask?.cancel()
    }

    public func updateLearnerId(_ newLearnerId: UUID) {
        if learnerId != newLearnerId {
            learnerId = newLearnerId
            latestResult = nil
            hasPendingOfflineSync = false
            syncNoticeKey = nil
        }
    }

    public func applyFixtureResult(_ result: ColdStartDiagnosticResult) {
        self.latestResult = result
        self.isFeatureDisabled = false
        self.hasPendingOfflineSync = false
        self.syncNoticeKey = nil
    }

    public func load(daysInactive: Int? = nil) async {
        isLoading = true
        defer { isLoading = false }

        if let cached = await cacheStore.loadLatestResult(for: learnerId) {
            self.latestResult = cached
        }
        if await cacheStore.dequeuePendingSubmission(for: learnerId) != nil {
            self.hasPendingOfflineSync = true
        }

        await flushPendingIfNeeded()

        guard let service else { return }
        do {
            let remote = try await service.fetchLatestCalibration(learnerId: learnerId, daysInactive: daysInactive)
            self.latestResult = remote
            self.isFeatureDisabled = false
            self.syncNoticeKey = nil
            await cacheStore.saveLatestResult(remote, for: learnerId)
        } catch ColdStartServiceError.notCompleted {
            self.isFeatureDisabled = false
        } catch ColdStartServiceError.featureDisabled {
            self.isFeatureDisabled = true
        } catch {
            // Keep locally cached result if offline or temporarily unreachable.
        }
    }

    public func startDiagnosticFlow() {
        stopTimer()
        activeQuestionStartTime = nil
        activeQuestionIndex = nil
        selectedOptionByItem = [:]
        skippedItemIds = []
        elapsedSecondsByItem = [:]
        remainingSeconds = ColdStartDiagnosticBank.totalTimeLimitSeconds
        currentFlowIdempotencyKey = "coldstart-\(UUID().uuidString.lowercased())"
        syncNoticeKey = nil
        flowStage = .preferences
        isFlowPresented = true
    }

    public func beginQuestions() {
        stopTimer()
        flowStage = .question(0)
        activeQuestionIndex = 0
        activeQuestionStartTime = nowProvider()
        startCountdownTimer()
    }

    public func selectOption(_ optionId: String, for itemId: ColdStartItemID) {
        selectedOptionByItem[itemId] = optionId
        skippedItemIds.remove(itemId)
    }

    public func advanceToNextQuestionOrSubmit() async {
        guard case .question(let index) = flowStage else { return }
        recordElapsedForQuestion(at: index)

        if index + 1 < ColdStartDiagnosticBank.prompts.count {
            let nextIndex = index + 1
            flowStage = .question(nextIndex)
            activeQuestionIndex = nextIndex
            activeQuestionStartTime = nowProvider()
        } else {
            await submitDiagnostic(timedOut: false)
        }
    }

    public func skipCurrentQuestion() async {
        guard case .question(let index) = flowStage,
              ColdStartDiagnosticBank.prompts.indices.contains(index) else {
            return
        }
        let itemId = ColdStartDiagnosticBank.prompts[index].itemId
        skippedItemIds.insert(itemId)
        selectedOptionByItem.removeValue(forKey: itemId)
        await advanceToNextQuestionOrSubmit()
    }

    public func submitDiagnostic(timedOut: Bool = false) async {
        stopTimer()
        if case .question(let index) = flowStage {
            recordElapsedForQuestion(at: index)
        }
        isSubmitting = true
        defer { isSubmitting = false }

        let request = buildRequest(timedOut: timedOut)
        await submitRequest(request)
        flowStage = .result
    }

    public func flushPendingIfNeeded() async {
        guard let service,
              let pending = await cacheStore.dequeuePendingSubmission(for: learnerId) else {
            return
        }
        do {
            let synced = try await service.submitDiagnostic(learnerId: learnerId, request: pending)
            await cacheStore.clearPendingSubmission(for: learnerId)
            await cacheStore.saveLatestResult(synced, for: learnerId)
            self.latestResult = synced
            self.hasPendingOfflineSync = false
            self.syncNoticeKey = nil
        } catch {
            self.hasPendingOfflineSync = true
        }
    }

    public func dismissFlow() {
        stopTimer()
        isFlowPresented = false
    }

    public func reset() {
        stopTimer()
        latestResult = nil
        activeQuestionStartTime = nil
        activeQuestionIndex = nil
        hasPendingOfflineSync = false
        isLoading = false
        isSubmitting = false
        isFlowPresented = false
        flowStage = .preferences
        selectedOptionByItem = [:]
        skippedItemIds = []
        elapsedSecondsByItem = [:]
        remainingSeconds = ColdStartDiagnosticBank.totalTimeLimitSeconds
        syncNoticeKey = nil
        isFeatureDisabled = false
        currentFlowIdempotencyKey = nil
    }

    public func purgeUserData(for userId: UUID) async {
        await cacheStore.purgeUserData(for: userId)
        if userId == learnerId {
            reset()
        }
    }

    public func purgeCurrentLearnerAndReset() async {
        let activeId = learnerId
        await cacheStore.purgeUserData(for: activeId)
        reset()
    }

    public func buildRequest(timedOut: Bool) -> ColdStartDiagnosticRequest {
        let locale = localeProvider().lowercased().hasPrefix("tr") ? "tr-TR" : "en-US"
        let idempotencyKey = currentFlowIdempotencyKey ?? "coldstart-\(UUID().uuidString.lowercased())"

        let submissions: [ColdStartItemSubmission] = ColdStartDiagnosticBank.prompts.map { prompt in
            let itemId = prompt.itemId
            let elapsed = max(1, elapsedSecondsByItem[itemId] ?? 1)
            let selected = selectedOptionByItem[itemId]
            let isSkipped = skippedItemIds.contains(itemId)
            let isTimedOut = timedOut && selected == nil && !isSkipped

            return ColdStartItemSubmission(
                itemId: itemId,
                selectedOptionId: (isSkipped || isTimedOut) ? nil : selected,
                answer: nil,
                elapsedSeconds: elapsed,
                timedOut: isTimedOut,
                skipped: isSkipped
            )
        }

        return ColdStartDiagnosticRequest(
            schemaVersion: "cold-start-v1",
            idempotencyKey: idempotencyKey,
            locale: locale,
            declaredExamTarget: declaredExamTarget.rawValue,
            declaredDailyBudgetMinutes: declaredDailyBudgetMinutes,
            preferredModality: preferredModality.rawValue,
            explicitPreferenceOverride: explicitPreferenceOverride,
            daysSinceLastVerifiedAttempt: nil,
            responses: submissions
        )
    }

    private func submitRequest(_ request: ColdStartDiagnosticRequest) async {
        if let service {
            do {
                let remote = try await service.submitDiagnostic(learnerId: learnerId, request: request)
                await cacheStore.clearPendingSubmission(for: learnerId)
                await cacheStore.saveLatestResult(remote, for: learnerId)
                self.latestResult = remote
                self.hasPendingOfflineSync = false
                self.isFeatureDisabled = false
                self.syncNoticeKey = nil
                return
            } catch ColdStartServiceError.featureDisabled {
                self.isFeatureDisabled = true
            } catch {
                // Fall back to deterministic offline preview & queue retry.
            }
        }

        let offlinePreview = ColdStartLocalEvaluator.buildOfflinePreview(
            learnerId: learnerId,
            request: request
        )
        await cacheStore.enqueuePendingSubmission(request, for: learnerId)
        await cacheStore.saveLatestResult(offlinePreview, for: learnerId)
        self.latestResult = offlinePreview
        self.hasPendingOfflineSync = true
        self.syncNoticeKey = "cold_start.offline.saved_message"
    }

    private func recordElapsedForQuestion(at index: Int) {
        guard ColdStartDiagnosticBank.prompts.indices.contains(index),
              activeQuestionIndex == index else {
            return
        }
        let itemId = ColdStartDiagnosticBank.prompts[index].itemId
        let elapsed: Int
        if let start = activeQuestionStartTime {
            elapsed = max(1, Int(nowProvider().timeIntervalSince(start).rounded()))
        } else {
            elapsed = 1
        }
        elapsedSecondsByItem[itemId, default: 0] += elapsed
        activeQuestionStartTime = nil
        activeQuestionIndex = nil
    }

    private func startCountdownTimer() {
        timerTask?.cancel()
        timerTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard !Task.isCancelled, let self else { return }
                await self.handleTimerTick()
            }
        }
    }

    private func handleTimerTick() async {
        guard case .question = flowStage else {
            stopTimer()
            return
        }
        if remainingSeconds > 1 {
            remainingSeconds -= 1
        } else {
            remainingSeconds = 0
            await submitDiagnostic(timedOut: true)
        }
    }

    private func stopTimer() {
        timerTask?.cancel()
        timerTask = nil
    }
}
