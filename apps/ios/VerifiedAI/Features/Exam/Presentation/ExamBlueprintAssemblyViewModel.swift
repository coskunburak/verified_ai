import Foundation
import SwiftUI

@MainActor
public final class ExamBlueprintAssemblyViewModel: ObservableObject {
    public static let defaultExamDefinitionId = UUID(uuidString: "91000000-0000-4000-8000-000000000101")!
    public static let defaultLearnerId = UUID(uuidString: "91000000-0000-4000-8000-000000000a01")!

    @Published public private(set) var examDefinitionId: UUID
    @Published public private(set) var blueprints: [ExamBlueprintDTO] = []
    @Published public var selectedBlueprint: ExamBlueprintDTO?
    @Published public private(set) var assembledAssessment: AssembledAssessmentDTO?

    @Published public var assemblySeed: String = "TYT-DIAG-SEED-2026-ALPHA"
    @Published public var selectedTimeMultiplier: Double = 1.00
    @Published public var highContrastMode: Bool = false
    @Published public var screenReaderOptimized: Bool = false
    @Published public var extendedBreakPrompts: Bool = false

    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var isAssembling: Bool = false
    @Published public private(set) var isOfflineCached: Bool = false
    @Published public private(set) var errorLocalizationKey: String?
    @Published public private(set) var errorMessage: String?
    @Published public private(set) var statusLocalizationKey: String?

    private let apiService: ExamAPIServicing
    private let offlineStore: ExamOfflineSQLiteStore?
    private var activeUserId: UUID

    public init(
        apiService: ExamAPIServicing,
        offlineStore: ExamOfflineSQLiteStore?,
        examDefinitionId: UUID = ExamBlueprintAssemblyViewModel.defaultExamDefinitionId,
        userId: UUID = ExamBlueprintAssemblyViewModel.defaultLearnerId
    ) {
        self.apiService = apiService
        self.offlineStore = offlineStore
        self.examDefinitionId = examDefinitionId
        self.activeUserId = userId
    }

    public var currentUserId: UUID {
        activeUserId
    }

    public func updateLearnerId(_ userId: UUID) {
        activeUserId = userId
    }

    public func updateExamDefinitionId(_ definitionId: UUID) {
        examDefinitionId = definitionId
    }

    public var previewBaseDurationSeconds: Int {
        selectedBlueprint?.baseDurationSeconds ?? 0
    }

    public var previewEffectiveDurationSeconds: Int {
        guard let bp = selectedBlueprint else { return 0 }
        return Int(ceil(Double(bp.baseDurationSeconds) * selectedTimeMultiplier))
    }

    public var previewBaseDurationMinutes: Int {
        guard previewBaseDurationSeconds > 0 else { return 0 }
        return max(1, Int(ceil(Double(previewBaseDurationSeconds) / 60.0)))
    }

    public var previewEffectiveDurationMinutes: Int {
        guard previewEffectiveDurationSeconds > 0 else { return 0 }
        return max(1, Int(ceil(Double(previewEffectiveDurationSeconds) / 60.0)))
    }

    public func selectBlueprint(_ blueprint: ExamBlueprintDTO) {
        selectedBlueprint = blueprint
        errorLocalizationKey = nil
        errorMessage = nil
    }

    public func loadBlueprints(examDefinitionId overrideId: UUID? = nil) async {
        if let overrideId {
            self.examDefinitionId = overrideId
        }
        isLoading = true
        errorLocalizationKey = nil
        errorMessage = nil
        defer { isLoading = false }

        let targetDefId = self.examDefinitionId
        do {
            let fetched = try await apiService.fetchBlueprints(examDefinitionId: targetDefId, examVersionNumber: nil)
            blueprints = fetched
            if selectedBlueprint == nil || !fetched.contains(where: { $0.id == selectedBlueprint?.id }) {
                selectedBlueprint = fetched.first
            }
            isOfflineCached = false
            await offlineStore?.saveBlueprints(fetched, examDefinitionId: targetDefId)
        } catch {
            let cached = await offlineStore?.loadBlueprints(examDefinitionId: targetDefId) ?? []
            if !cached.isEmpty {
                blueprints = cached
                if selectedBlueprint == nil || !cached.contains(where: { $0.id == selectedBlueprint?.id }) {
                    selectedBlueprint = cached.first
                }
                isOfflineCached = true
                if let bp = selectedBlueprint,
                   let cachedAssessment = await offlineStore?.loadLatestAssembledAssessment(userId: activeUserId, blueprintId: bp.id) {
                    assembledAssessment = cachedAssessment
                }
            } else {
                mapError(error)
            }
        }
    }

    public func assembleSelectedBlueprint() async {
        guard let blueprint = selectedBlueprint else { return }
        let trimmedSeed = assemblySeed.trimmingCharacters(in: .whitespacesAndNewlines)
        let effectiveSeed = trimmedSeed.count >= 4 ? trimmedSeed : "TYT-SEED-\(blueprint.blueprintCode)"
        assemblySeed = effectiveSeed

        errorLocalizationKey = nil
        errorMessage = nil
        statusLocalizationKey = nil
        isAssembling = true
        defer { isAssembling = false }

        let request = AssessmentAssemblyRequestDTO(
            userId: activeUserId,
            candidateProfileId: nil,
            blueprintId: blueprint.id,
            assemblySeed: effectiveSeed,
            overrideTimeMultiplier: selectedTimeMultiplier,
            highContrastMode: highContrastMode,
            screenReaderOptimized: screenReaderOptimized,
            extendedBreakPrompts: extendedBreakPrompts || selectedTimeMultiplier > 1.0,
            trainingEligible: false
        )

        do {
            let result = try await apiService.assembleAssessment(
                examDefinitionId: blueprint.examDefinitionId,
                request: request
            )
            assembledAssessment = result
            isOfflineCached = false
            statusLocalizationKey = "exam.assembly.status.ready"
            await offlineStore?.saveAssembledAssessment(result)
        } catch {
            if let cached = await offlineStore?.loadLatestAssembledAssessment(userId: activeUserId, blueprintId: blueprint.id) {
                assembledAssessment = cached
                isOfflineCached = true
                statusLocalizationKey = "exam.assembly.status.ready"
            } else {
                mapError(error)
            }
        }
    }

    public func purgeUserDataAndReset() async {
        let userId = activeUserId
        await offlineStore?.clearUserData(userId: userId)
        assembledAssessment = nil
        errorLocalizationKey = nil
        errorMessage = nil
        statusLocalizationKey = nil
        isOfflineCached = false
    }

    private func mapError(_ error: Error) {
        if let examError = error as? ExamServiceError {
            switch examError {
            case .poolInsufficient:
                errorLocalizationKey = "exam.assembly.error.pool_insufficient"
            case .featureDisabled:
                errorLocalizationKey = "exam.catalog.feature_disabled"
            case .forbidden:
                errorLocalizationKey = "exam.error.forbidden"
            case .notFound:
                errorLocalizationKey = "exam.error.not_found"
            case .versionConflict:
                errorLocalizationKey = "exam.error.version_conflict"
            case .accommodationInvalid:
                errorLocalizationKey = "exam.candidate.error.accommodation_invalid"
            case .offline:
                errorLocalizationKey = "exam.assembly.offline.cached_banner"
            default:
                errorLocalizationKey = "exam.error.unexpected"
            }
        } else {
            errorMessage = error.localizedDescription
        }
    }
}
