import Foundation
import Combine

@MainActor
public final class DailyCoachViewModel: ObservableObject {
    @Published public private(set) var summary: DailyCoachSummaryModel? = nil
    @Published public var selectedMode: DailyCoachMode = .socraticCoachMode
    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var isOfflineBannerVisible: Bool = false
    @Published public var showingStudyPlanSheet: Bool = false
    @Published public private(set) var errorMessage: String? = nil

    private let userId: UUID
    private let cacheStore: DailyCoachCacheStoreProtocol
    private let fetchSummaryHandler: (@Sendable (UUID, String) async throws -> DailyCoachSummaryModel)?
    private let updatePreferencesHandler: (@Sendable (UUID, DailyCoachMode, Int) async throws -> DailyCoachSummaryModel)?
    private let completeTaskHandler: (@Sendable (UUID, UUID, UUID) async throws -> DailyCoachSummaryModel)?
    private let downshiftHandler: (@Sendable (UUID, UUID) async throws -> DailyCoachSummaryModel)?

    public init(
        userId: UUID,
        cacheStore: DailyCoachCacheStoreProtocol? = nil,
        fetchSummaryHandler: (@Sendable (UUID, String) async throws -> DailyCoachSummaryModel)? = nil,
        updatePreferencesHandler: (@Sendable (UUID, DailyCoachMode, Int) async throws -> DailyCoachSummaryModel)? = nil,
        completeTaskHandler: (@Sendable (UUID, UUID, UUID) async throws -> DailyCoachSummaryModel)? = nil,
        downshiftHandler: (@Sendable (UUID, UUID) async throws -> DailyCoachSummaryModel)? = nil
    ) {
        self.userId = userId
        self.cacheStore = cacheStore ?? DailyCoachSQLiteCache()
        self.fetchSummaryHandler = fetchSummaryHandler
        self.updatePreferencesHandler = updatePreferencesHandler
        self.completeTaskHandler = completeTaskHandler
        self.downshiftHandler = downshiftHandler
    }

    public func loadTodaySummary(localDate: String? = nil) async {
        let effectiveDate = localDate ?? Self.todayDateString()
        isLoading = true
        errorMessage = nil

        if let cached = await cacheStore.loadSummary(for: userId, localDate: effectiveDate) {
            self.summary = cached
            self.selectedMode = cached.preferredMode
        } else if let latest = await cacheStore.loadLatestSummary(for: userId) {
            self.summary = latest
            self.selectedMode = latest.preferredMode
        }

        do {
            let remote: DailyCoachSummaryModel
            if let fetchSummaryHandler {
                remote = try await fetchSummaryHandler(userId, effectiveDate)
            } else {
                throw URLError(.notConnectedToInternet)
            }
            self.summary = remote
            self.selectedMode = remote.preferredMode
            self.isOfflineBannerVisible = false
            await cacheStore.saveSummary(remote, for: userId)
        } catch {
            if self.summary != nil {
                self.isOfflineBannerVisible = true
            } else {
                let fallback = Self.deterministicColdStartSummary(userId: userId, localDate: effectiveDate, mode: selectedMode)
                self.summary = fallback
                self.isOfflineBannerVisible = true
                await cacheStore.saveSummary(fallback, for: userId)
            }
        }

        isLoading = false
    }

    public func toggleMode(_ mode: DailyCoachMode) async {
        selectedMode = mode
        if var current = summary {
            current.preferredMode = mode
            summary = current
            await cacheStore.saveSummary(current, for: userId)
        }

        let budget = summary?.budgetMinutes ?? 15
        if let updatePreferencesHandler {
            do {
                let updated = try await updatePreferencesHandler(userId, mode, budget)
                self.summary = updated
                self.selectedMode = updated.preferredMode
                self.isOfflineBannerVisible = false
                await cacheStore.saveSummary(updated, for: userId)
            } catch {
                self.isOfflineBannerVisible = true
            }
        }
    }

    public func triggerBusyDayDownshift() async {
        guard var current = summary else { return }

        // Optimistic 5-minute mini streak contraction (INV-COACH-002)
        current.budgetMinutes = 5
        current.isDownshifted = true
        current.totalTasksCount = 1

        var taskOneCompleted = false
        for idx in current.tasks.indices {
            if current.tasks[idx].taskOrder == 1 {
                taskOneCompleted = (current.tasks[idx].status == .completed)
            } else if current.tasks[idx].status == .pending {
                current.tasks[idx].status = .skipped
            }
        }
        current.completedTasksCount = taskOneCompleted ? 1 : 0
        current.status = taskOneCompleted ? .completed : .inProgress
        current.briefingText = "Yoğun gün modu aktif: 5 dakikalık tek öncelik ikiziyle günlük serini güvenle koruyorsun."

        self.summary = current
        await cacheStore.saveSummary(current, for: userId)

        if let downshiftHandler {
            do {
                let synced = try await downshiftHandler(userId, current.sessionId)
                self.summary = synced
                self.isOfflineBannerVisible = false
                await cacheStore.saveSummary(synced, for: userId)
            } catch {
                self.isOfflineBannerVisible = true
            }
        }
    }

    public func completeTask(taskId: UUID) async {
        guard var current = summary else { return }
        guard let targetIdx = current.tasks.firstIndex(where: { $0.taskId == taskId }) else { return }

        if current.tasks[targetIdx].status == .pending {
            current.tasks[targetIdx].status = .completed
            current.tasks[targetIdx].completedAt = Date()
        }

        let completedCount = current.tasks.filter { $0.status == .completed }.count
        let anyPending = current.tasks.contains { $0.status == .pending }
        current.completedTasksCount = min(completedCount, current.totalTasksCount)
        if !anyPending && current.status != .completed {
            current.status = .completed
            current.streakDays += 1
            current.briefingText = "Günün koç görevi tamamlandı! Bağımsız çözüm serin bir gün daha güçlendi."
        }

        self.summary = current
        await cacheStore.saveSummary(current, for: userId)

        if let completeTaskHandler {
            do {
                let synced = try await completeTaskHandler(userId, current.sessionId, taskId)
                self.summary = synced
                self.isOfflineBannerVisible = false
                await cacheStore.saveSummary(synced, for: userId)
            } catch {
                self.isOfflineBannerVisible = true
            }
        }
    }

    private static func todayDateString() -> String {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        return formatter.string(from: Date())
    }

    public static func deterministicColdStartSummary(
        userId: UUID,
        localDate: String,
        mode: DailyCoachMode = .socraticCoachMode
    ) -> DailyCoachSummaryModel {
        let sessionId = UUID()
        let tasks = [
            DailyCoachTaskModel(
                taskId: UUID(),
                taskOrder: 1,
                taskType: .trapRepair,
                targetSkillCode: "MATH.ALG.RATIONAL_EQ",
                targetMistakeCode: "ALG_DOMAIN_EXCLUSION_MISSED",
                assignedTwinId: "twin_84_coldstart01",
                estimatedMinutes: 5,
                whyThisTaskRationale: "growth_opportunity: Rasyonel ifadelerde payda kısıtını doğrulayarak güçlü başlangıç yap.",
                promptPreviewLatex: "\\frac{x^2 - 4}{x - 2} = x + 2, \\quad x \\neq 2"
            ),
            DailyCoachTaskModel(
                taskId: UUID(),
                taskOrder: 2,
                taskType: .spacedRetention,
                targetSkillCode: "MATH.ALG.LINEAR_INEQ",
                targetMistakeCode: "INEQ_NEGATIVE_MULT_NO_FLIP",
                assignedTwinId: "twin_84_coldstart02",
                estimatedMinutes: 5,
                whyThisTaskRationale: "retention_refresh: Eşitsizlikte negatif bölen yön kuralını pekiştir.",
                promptPreviewLatex: "-2(x - 3) > 8 \\implies x - 3 < -4"
            ),
            DailyCoachTaskModel(
                taskId: UUID(),
                taskOrder: 3,
                taskType: .examSynthesis,
                targetSkillCode: "MATH.CALC.CHAIN_RULE_COMPOSITE",
                targetMistakeCode: "CALC_CHAIN_RULE_OMITTED",
                assignedTwinId: "twin_84_coldstart03",
                estimatedMinutes: 5,
                whyThisTaskRationale: "immunity_check: Bileşke fonksiyon türevinde iç türev çarpanını kontrol et.",
                promptPreviewLatex: "\\frac{d}{dx}\\left[(3x^2 + 1)^4\\right] = 24x(3x^2 + 1)^3"
            )
        ]

        let demo = ColdStartDemoCardModel(
            title: "Örnek Adım Kontrolü: Eşitsizlik Yön Analizi",
            samplePromptLatex: "-2(x - 3) > 8",
            correctStepLatex: "x - 3 < -4",
            focusStepLatex: "x - 3 > -4",
            hintText: "Her iki tarafı negatif bir sayıya bölerken eşitsizlik yönü tersine döner."
        )

        let radar = [
            TrapRadarPreviewModel(
                skillCode: "MATH.ALG.RATIONAL_EQ",
                mistakeCode: "ALG_DOMAIN_EXCLUSION_MISSED",
                title: "Payda Tanım Kümesi Kısıtı",
                badgeKey: "growth_opportunity",
                statusLabel: "emerging"
            )
        ]

        return DailyCoachSummaryModel(
            sessionId: sessionId,
            userId: userId,
            localDate: localDate,
            budgetMinutes: 15,
            status: .inProgress,
            isDownshifted: false,
            isColdStart: true,
            completedTasksCount: 0,
            totalTasksCount: 3,
            briefingText: "İlk 3 adımlı tanı ve hazırlık görevinle güçlü bir başlangıç yapalım.",
            preferredMode: mode,
            streakDays: 0,
            tasks: tasks,
            activeTrapRadar: radar,
            coldStartDemo: demo
        )
    }
}
