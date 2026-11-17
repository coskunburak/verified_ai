import Foundation
import Combine
import OSLog

public struct ExamRemediationRoute: Equatable, Identifiable, Sendable {
    public enum Destination: Equatable, Sendable {
        case practice(mode: PracticeSessionMode)
        case microLesson(mistakeCode: String)
    }

    public let actionType: ExamRemediationActionType
    public let skillId: String
    public let targetRef: String
    public let destination: Destination
    public var id: String { actionType.rawValue + ":" + targetRef }

    public init(item: ExamHighRoiAllocationItem) {
        actionType = item.recommendedActionType
        skillId = item.skillId
        targetRef = item.recommendedTargetRef
        switch item.recommendedActionType {
        case .twinDrill:
            destination = .practice(mode: .trapVerificationDrill)
        case .microLesson:
            if let code = item.matchedMistakeCode?.trimmingCharacters(in: .whitespacesAndNewlines), !code.isEmpty {
                destination = .microLesson(mistakeCode: code)
            } else {
                // The existing micro-lesson API requires an evidenced mistake code.
                // A generic skill recommendation uses the existing native skill-practice destination.
                destination = .practice(mode: .examRoiSynthesis)
            }
        case .timedSpeedDrill, .triageStrategyReview:
            destination = .practice(mode: .examRoiSynthesis)
        }
    }

    public var buttonTitle: String {
        switch destination {
        case .microLesson: return "Hemen Telafi Et (Mikro-Ders)"
        case .practice(mode: .trapVerificationDrill): return "Hemen Telafi Et (İkiz Soru)"
        case .practice: return "Hemen Telafi Et (Hedefli Alıştırma)"
        }
    }
}

@MainActor
public final class PostExamAttributionViewModel: ObservableObject {
    public let sessionId: UUID
    public let userId: UUID
    @Published public private(set) var report: ExamAttributionReportResponse?
    @Published public private(set) var isLoading = false
    @Published public private(set) var isApplyingRebalance = false
    @Published public private(set) var isOffline = false
    @Published public private(set) var errorMessage: String?
    @Published public private(set) var rebalanceResponse: ExamApplyStudyRebalanceResponse?
    @Published public var selectedRemediationRoute: ExamRemediationRoute?
    private let api: any ExamAttributionServicing
    private let cache: ExamOfflineSQLiteStore?
    private let telemetry: @MainActor (String, [String: String]) -> Void

    public init(sessionId: UUID, userId: UUID, api: any ExamAttributionServicing,
                cache: ExamOfflineSQLiteStore? = nil,
                telemetry: @escaping @MainActor (String, [String: String]) -> Void = { event, fields in
                    // Values are restricted by the caller to the four allowed keys.
                    let logger = Logger(subsystem: "com.verifiedai", category: "exam.attribution")
                    logger.info("\(event, privacy: .public) \(String(describing: fields), privacy: .private)")
                }) {
        self.sessionId = sessionId; self.userId = userId; self.api = api
        self.cache = cache; self.telemetry = telemetry
    }

    public func load() async {
        guard !isLoading else { return }
        isLoading = true; errorMessage = nil; isOffline = false
        defer { isLoading = false }
        do {
            let fetched = try await api.fetchAttributionReport(sessionId: sessionId)
            try Task.checkCancellation()
            guard fetched.userId == userId, fetched.sessionId == sessionId, !fetched.trainingEligible else { throw ExamServiceError.forbidden }
            report = fetched
            try? await cache?.saveAttributionReport(fetched)
            telemetry("exam_attribution_generated", ["sessionId": sessionId.uuidString.lowercased(), "examCode": fetched.examCode])
        } catch is CancellationError { return }
        catch {
            if (error as? ExamServiceError) == .offline,
               let cached = try? await cache?.loadAttributionReport(sessionId: sessionId), cached.userId == userId {
                report = cached; isOffline = true
            } else {
                report = nil
                selectedRemediationRoute = nil
                errorMessage = (error as? ExamServiceError) == .featureDisabled
                    ? "Hata karnesi şu anda kullanılamıyor."
                    : "Hata karnesi yüklenemedi. Bağlantını kontrol edip tekrar dene."
            }
        }
    }

    public func applyTopRoiToStudyPlan() async {
        guard !isApplyingRebalance, !isOffline, rebalanceResponse == nil,
              let report, !report.studyPlanRebalanced, !report.topRoiAllocations.isEmpty else { return }
        isApplyingRebalance = true; errorMessage = nil
        defer { isApplyingRebalance = false }
        do {
            let response = try await api.applyStudyRebalance(sessionId: sessionId,
                request: .init(maxTopicsToApply: 3, targetHorizonDays: 7, preferredDailyMinutes: 45),
                idempotencyKey: "rebalance-\(sessionId.uuidString.lowercased())")
            try Task.checkCancellation()
            guard response.userId == userId, response.sourceSessionId == sessionId,
                  response.appliedToStudyPlan, !response.trainingEligible else { throw ExamServiceError.forbidden }
            rebalanceResponse = response
            var updated = report
            updated.studyPlanRebalanced = true; updated.lastReallocationBatchId = response.batchId
            self.report = updated
            try? await cache?.saveAttributionReport(updated)
        } catch is CancellationError { return }
        catch { errorMessage = "Plan güncellenemedi. Aynı işlem anahtarıyla güvenle tekrar deneyebilirsin." }
    }

    public func makePracticeViewModel(for route: ExamRemediationRoute) -> PracticeSessionViewModel {
        let service = api
        let owner = userId
        return PracticeSessionViewModel(
            userId: owner,
            cacheStore: PracticeSQLiteCache(inMemory: true),
            startRemoteHandler: { user, skill, mode in
                let session = try await service.startRemediationPractice(skillId: skill, mode: mode)
                guard session.userId == user, session.targetSkillId == skill, session.sessionMode == mode else {
                    throw ExamServiceError.forbidden
                }
                return session
            },
            submitRemoteHandler: { sessionId, itemId, answer, elapsed, hint in
                let response = try await service.submitRemediationPractice(
                    sessionId: sessionId, itemId: itemId,
                    request: .init(submittedAnswerLatex: answer, clientElapsedSeconds: elapsed, unlockHintRequested: hint))
                guard response.session.userId == owner, response.session.id == sessionId,
                      response.session.targetSkillId == route.skillId else { throw ExamServiceError.forbidden }
                return response
            },
            abandonRemoteHandler: { sessionId in
                let session = try await service.abandonRemediationPractice(sessionId: sessionId)
                guard session.userId == owner, session.id == sessionId,
                      session.targetSkillId == route.skillId else { throw ExamServiceError.forbidden }
                return session
            }
        )
    }

    public func makeMicroLessonViewModel() -> MicroLessonViewModel {
        MicroLessonViewModel(userId: userId, client: api)
    }

    public func startImmediateRemediation(for item: ExamHighRoiAllocationItem) {
        guard selectedRemediationRoute == nil, let report, report.topRoiAllocations.contains(item) else { return }
        selectedRemediationRoute = ExamRemediationRoute(item: item)
        telemetry("net_gain_simulator_task_started", ["sessionId": sessionId.uuidString.lowercased(), "examCode": report.examCode, "actionType": item.recommendedActionType.rawValue, "rank": String(item.rank)])
    }
}
