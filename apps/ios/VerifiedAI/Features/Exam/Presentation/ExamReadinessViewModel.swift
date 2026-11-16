import Foundation
import Combine

@MainActor
public final class ExamReadinessViewModel: ObservableObject {
    @Published public var selectedExamCode: String
    @Published public private(set) var projection: ExamReadinessProjection?
    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var isRecomputing: Bool = false
    @Published public private(set) var isOfflineFallback: Bool = false
    @Published public private(set) var isFeatureDisabled: Bool = false
    @Published public private(set) var errorLocalizationKey: String?
    @Published public private(set) var errorMessage: String?

    @Published public var simulatedTrapReductionRatio: Double = 0.50
    @Published public var simulatedAdditionalStudyHours: Double = 4.0

    public let userId: UUID
    public let highContrastMode: Bool
    private let apiService: ExamAPIServicing
    private let offlineStore: ExamOfflineSQLiteStore?

    public init(
        userId: UUID = ExamBlueprintAssemblyViewModel.defaultLearnerId,
        selectedExamCode: String = "TR_YKS_TYT_MATH",
        highContrastMode: Bool = false,
        apiService: ExamAPIServicing,
        offlineStore: ExamOfflineSQLiteStore? = nil
    ) {
        self.userId = userId
        self.selectedExamCode = Self.canonicalExamCode(selectedExamCode)
        self.highContrastMode = highContrastMode
        self.apiService = apiService
        self.offlineStore = offlineStore
    }

    public func loadReadiness(forceRecompute: Bool = false) async {
        if forceRecompute {
            isRecomputing = true
        } else {
            isLoading = true
        }
        errorLocalizationKey = nil
        errorMessage = nil
        isFeatureDisabled = false
        defer {
            isLoading = false
            isRecomputing = false
        }

        let targetCode = Self.canonicalExamCode(selectedExamCode)
        do {
            let fetched = try await apiService.fetchExamReadiness(examCode: targetCode, recompute: forceRecompute)
            projection = fetched
            isOfflineFallback = false
            await offlineStore?.saveReadinessProjection(fetched, userId: userId.uuidString)
        } catch let error as ExamServiceError {
            await handleServiceError(error, examCode: targetCode, allowOfflineCache: !forceRecompute)
        } catch {
            errorLocalizationKey = "exam.readiness.error.generic"
            errorMessage = error.localizedDescription
        }
    }

    public func recompute() async {
        isRecomputing = true
        errorLocalizationKey = nil
        errorMessage = nil
        isFeatureDisabled = false
        defer { isRecomputing = false }

        let targetCode = Self.canonicalExamCode(selectedExamCode)
        do {
            let updated = try await apiService.recomputeExamReadiness(examCode: targetCode)
            projection = updated
            isOfflineFallback = false
            await offlineStore?.saveReadinessProjection(updated, userId: userId.uuidString)
        } catch let error as ExamServiceError {
            await handleServiceError(error, examCode: targetCode, allowOfflineCache: false)
        } catch {
            errorLocalizationKey = "exam.readiness.error.generic"
            errorMessage = error.localizedDescription
        }
    }

    public func switchExam(to examCode: String) async {
        let canonical = Self.canonicalExamCode(examCode)
        guard canonical != selectedExamCode || projection == nil else { return }
        selectedExamCode = canonical
        await loadReadiness(forceRecompute: false)
    }

    // MARK: - Deterministic On-Device Net-Gain Simulator ($0.00 LLM Cost)

    public var simulatedTrapRecoveryNet: Double {
        guard let projection else { return 0.0 }
        let ratio = min(1.0, max(0.0, simulatedTrapReductionRatio))
        return round2(projection.netLostToTraps * ratio)
    }

    public var simulatedStudyGainNet: Double {
        guard let projection else { return 0.0 }
        let hours = min(20.0, max(0.0, simulatedAdditionalStudyHours))
        if hours <= 0.0 {
            return 0.0
        }
        let topRoiValues = projection.topRoiAllocations.prefix(3).map(\.expectedNetGainPerHour)
        let effectiveHourlyRate: Double = {
            if topRoiValues.isEmpty {
                return 0.55
            }
            return max(0.35, topRoiValues.reduce(0.0, +) / Double(topRoiValues.count))
        }()
        // Diminishing returns curve calibrated to realistic exam topic mastery consolidation
        let rawGain = effectiveHourlyRate * (4.0 * (1.0 - exp(-0.25 * hours)))
        return round2(max(0.0, rawGain))
    }

    public var simulatedProjectedNetMid: Double {
        guard let projection else { return 0.0 }
        let maxNet = Double(projection.totalQuestionCount)
        let minNet = -(maxNet * projection.wrongAnswerPenaltyRatio)
        let projected = projection.estimatedNetMid + simulatedTrapRecoveryNet + simulatedStudyGainNet
        return round2(min(maxNet, max(minNet, projected)))
    }

    public var simulatedProjectedNetLow: Double {
        guard let projection else { return 0.0 }
        let maxNet = Double(projection.totalQuestionCount)
        let minNet = -(maxNet * projection.wrongAnswerPenaltyRatio)
        let halfWidth = max(0.25, (projection.estimatedNetHigh - projection.estimatedNetLow) * 0.5)
        let studyRatio = min(1.0, max(0.0, simulatedAdditionalStudyHours) / 20.0)
        let adjustedHalfWidth = max(0.50, halfWidth * (1.0 - 0.15 * studyRatio))
        let candidate = simulatedProjectedNetMid - adjustedHalfWidth
        return round2(min(simulatedProjectedNetMid, max(minNet, candidate)))
    }

    public var simulatedProjectedNetHigh: Double {
        guard let projection else { return 0.0 }
        let maxNet = Double(projection.totalQuestionCount)
        let halfWidth = max(0.25, (projection.estimatedNetHigh - projection.estimatedNetLow) * 0.5)
        let studyRatio = min(1.0, max(0.0, simulatedAdditionalStudyHours) / 20.0)
        let adjustedHalfWidth = max(0.50, halfWidth * (1.0 - 0.15 * studyRatio))
        let candidate = simulatedProjectedNetMid + adjustedHalfWidth
        return round2(max(simulatedProjectedNetMid, min(maxNet, candidate)))
    }

    public var confidenceTierLocalizationKey: String {
        projection?.confidenceTier.localizationKey ?? "exam.readiness.tier.preliminary"
    }

    private func handleServiceError(
        _ error: ExamServiceError,
        examCode: String,
        allowOfflineCache: Bool
    ) async {
        switch error {
        case .offline:
            if allowOfflineCache {
                var cached = await offlineStore?.loadReadinessProjection(
                    userId: userId.uuidString,
                    examCode: examCode
                )
                if cached == nil && examCode == "TR_YKS_TYT_MATH" {
                    cached = await offlineStore?.loadReadinessProjection(userId: userId.uuidString, examCode: "YKS_TYT")
                } else if cached == nil && examCode == "TR_YKS_AYT_MATH" {
                    cached = await offlineStore?.loadReadinessProjection(userId: userId.uuidString, examCode: "YKS_AYT_SAY")
                }
                if let cached {
                    projection = cached
                    isOfflineFallback = true
                    errorLocalizationKey = nil
                    errorMessage = nil
                    return
                }
            }
            isOfflineFallback = true
            errorLocalizationKey = "exam.readiness.error.offline_unavailable"
            errorMessage = "Cached exam readiness projection is unavailable offline."
        case .featureDisabled:
            isFeatureDisabled = true
            errorLocalizationKey = "exam.readiness.error.feature_disabled"
            errorMessage = "Exam readiness projection is disabled."
        default:
            errorLocalizationKey = "exam.readiness.error.generic"
            errorMessage = String(describing: error)
        }
    }

    public static func canonicalExamCode(_ raw: String) -> String {
        switch raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() {
        case "YKS_TYT", "YKS_TYT_MATH":
            return "TR_YKS_TYT_MATH"
        case "YKS_AYT_SAY", "YKS_AYT", "YKS_AYT_MATH":
            return "TR_YKS_AYT_MATH"
        default:
            let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            return trimmed.isEmpty ? "TR_YKS_TYT_MATH" : trimmed
        }
    }

    private func round2(_ value: Double) -> Double {
        (value * 100.0).rounded() / 100.0
    }
}
