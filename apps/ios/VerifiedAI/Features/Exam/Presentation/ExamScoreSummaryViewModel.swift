import Foundation
import Combine

@MainActor
public final class ExamScoreSummaryViewModel: ObservableObject {
    public let sessionId: UUID
    public let userId: UUID
    public let highContrastMode: Bool

    @Published public private(set) var scoreSummary: ExamSessionScoreSummaryResponse?
    @Published public private(set) var regradeAudits: [ExamScoreRegradeAuditItem] = []
    @Published public var selectedFilter: ExamItemEvidenceFilter = .all
    @Published public var selectedTopicCode: String?
    @Published public var isLoading: Bool = false
    @Published public var isRegrading: Bool = false
    @Published public var showOfflineBanner: Bool = false
    @Published public var errorMessage: String?
    @Published public var errorLocalizationKey: String?
    @Published public var regradeReasonCode: ExamScoreRegradeReasonCode = .answerKeyCorrection
    @Published public var regradeReasonDetail: String = ""

    private let apiService: ExamAPIServicing
    private let offlineStore: ExamOfflineSQLiteStore?

    public init(
        sessionId: UUID,
        userId: UUID,
        highContrastMode: Bool = false,
        apiService: ExamAPIServicing,
        offlineStore: ExamOfflineSQLiteStore? = nil
    ) {
        self.sessionId = sessionId
        self.userId = userId
        self.highContrastMode = highContrastMode
        self.apiService = apiService
        self.offlineStore = offlineStore
    }

    public var filteredItemEvaluations: [ExamSessionItemEvaluation] {
        guard let evaluations = scoreSummary?.itemEvaluations else { return [] }
        return evaluations
            .filter { item in
                if let selectedTopicCode, item.topicCode != selectedTopicCode {
                    return false
                }
                switch selectedFilter {
                case .all:
                    return true
                case .correct:
                    return item.evaluationOutcome == .correct
                case .wrong:
                    return item.evaluationOutcome == .wrong
                case .blank:
                    return item.evaluationOutcome == .blank
                case .flagged:
                    return item.flaggedForReview
                }
            }
            .sorted { $0.positionIndex < $1.positionIndex }
    }

    public var formulaDescriptionKey: String {
        guard let summary = scoreSummary else {
            return "exam.score.formula.osym_4_1"
        }
        switch summary.scoringFormulaType {
        case .correctMinusQuarterWrong:
            if summary.wrongPenaltyRatio > 0.30 {
                return "exam.score.formula.osym_3_1"
            }
            return "exam.score.formula.osym_4_1"
        case .rawCorrectOnly:
            return "exam.score.formula.no_penalty"
        case .weightedSectionRubric:
            return "exam.score.formula.weighted"
        }
    }

    public var llmTokensUsedDisplay: Int {
        scoreSummary?.llmTokensUsed ?? 0
    }

    public var inferenceCostUsdDisplay: Double {
        scoreSummary?.inferenceCostUsd ?? 0.0
    }

    public var hasRegradeHistory: Bool {
        !regradeAudits.isEmpty
    }

    public func loadScoreSummary(computeIfMissing: Bool = true) async {
        isLoading = true
        errorMessage = nil
        errorLocalizationKey = nil
        defer { isLoading = false }

        do {
            let loaded: ExamSessionScoreSummaryResponse
            if computeIfMissing {
                loaded = try await apiService.computeOrGetScoreSummary(sessionId: sessionId)
            } else {
                loaded = try await apiService.fetchScoreSummary(sessionId: sessionId)
            }
            scoreSummary = loaded
            showOfflineBanner = false
            await offlineStore?.saveScoreSummary(loaded, for: userId)

            if let auditsResponse = try? await apiService.fetchRegradeAudits(sessionId: sessionId) {
                regradeAudits = auditsResponse.audits
            }
        } catch let error as ExamServiceError {
            await handleServiceError(error, checkOfflineCache: true)
        } catch {
            errorLocalizationKey = "exam.score.error.generic"
            errorMessage = error.localizedDescription
        }
    }

    public func selectTopicFilter(_ topicCode: String?) {
        guard let topicCode else {
            selectedTopicCode = nil
            return
        }
        if selectedTopicCode == topicCode {
            selectedTopicCode = nil
        } else {
            selectedTopicCode = topicCode
        }
    }

    public func triggerRegrade(
        reasonCode: ExamScoreRegradeReasonCode? = nil,
        reasonDetail: String,
        triggeredBy: String = "learner-self-check"
    ) async {
        let trimmedDetail = reasonDetail.trimmingCharacters(in: .whitespacesAndNewlines)
        let trimmedActor = triggeredBy.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedDetail.isEmpty, !trimmedActor.isEmpty else {
            errorLocalizationKey = "exam.score.error.regrade_invalid"
            errorMessage = "Regrade detail is required."
            return
        }

        isRegrading = true
        errorMessage = nil
        errorLocalizationKey = nil
        defer { isRegrading = false }

        do {
            let request = ExamScoreRegradeRequest(
                reasonCode: reasonCode ?? regradeReasonCode,
                reasonDetail: trimmedDetail,
                triggeredBy: trimmedActor,
                trainingEligible: false
            )
            let response = try await apiService.regradeSession(sessionId: sessionId, request: request)
            scoreSummary = response.scoreSummary
            showOfflineBanner = false
            await offlineStore?.saveScoreSummary(response.scoreSummary, for: userId)

            if let auditsResponse = try? await apiService.fetchRegradeAudits(sessionId: sessionId) {
                regradeAudits = auditsResponse.audits
            } else {
                regradeAudits.insert(response.regradeAudit, at: 0)
            }
        } catch let error as ExamServiceError {
            await handleServiceError(error, checkOfflineCache: false)
        } catch {
            errorLocalizationKey = "exam.score.error.generic"
            errorMessage = error.localizedDescription
        }
    }

    private func handleServiceError(_ error: ExamServiceError, checkOfflineCache: Bool) async {
        switch error {
        case .offline:
            showOfflineBanner = true
            if checkOfflineCache, let cached = await offlineStore?.loadScoreSummary(sessionId: sessionId, userId: userId) {
                scoreSummary = cached
                errorLocalizationKey = nil
                errorMessage = nil
            } else {
                errorLocalizationKey = "exam.score.error.offline_unavailable"
                errorMessage = "Cached score summary unavailable while offline."
            }
        case .featureDisabled:
            errorLocalizationKey = "exam.score.error.feature_disabled"
            errorMessage = "Deterministic scoring engine is disabled."
        case .sessionNotScorable:
            errorLocalizationKey = "exam.score.error.not_scorable"
            errorMessage = "Submit the exam session before viewing score summary."
        case .notFound:
            errorLocalizationKey = "exam.score.error.not_found"
            errorMessage = "Score summary not found."
        case .regradeInvalidRequest:
            errorLocalizationKey = "exam.score.error.regrade_invalid"
            errorMessage = "Regrade request reason is invalid."
        default:
            errorLocalizationKey = "exam.score.error.generic"
            errorMessage = String(describing: error)
        }
    }
}
