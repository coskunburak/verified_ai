import Foundation

@MainActor
public final class ExamCatalogAndTargetViewModel: ObservableObject {

    @Published public private(set) var definitions: [ExamDefinitionSummaryModel] = []
    @Published public private(set) var selectedDetail: ExamDefinitionModel?
    @Published public private(set) var targets: [StudentExamTargetModel] = []
    @Published public private(set) var isOfflineBannerVisible: Bool = false
    @Published public private(set) var isFeatureDisabled: Bool = false
    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var isSaving: Bool = false
    @Published public private(set) var statusMessageKey: String?
    @Published public private(set) var errorMessageKey: String?
    @Published public private(set) var errorDetailsText: String?
    @Published public var selectedJurisdiction: ExamJurisdiction?
    @Published public var targetScoreInput: String = ""
    @Published public var targetDateInput: Date

    public private(set) var currentUserId: UUID?
    private let service: ExamAPIServicing
    private let cacheStore: ExamCacheStoreProtocol

    private static let isoDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    public init(service: ExamAPIServicing, cacheStore: ExamCacheStoreProtocol) {
        self.service = service
        self.cacheStore = cacheStore
        self.targetDateInput = Calendar.current.date(byAdding: .month, value: 6, to: Date()) ?? Date()
    }

    public var primaryActiveTarget: StudentExamTargetModel? {
        targets.first(where: { $0.targetStatus == .active && $0.isPrimary }) ?? targets.first(where: { $0.targetStatus == .active })
    }

    public func makeReadinessViewModel() -> ExamReadinessViewModel {
        ExamReadinessViewModel(
            userId: currentUserId ?? ExamBlueprintAssemblyViewModel.defaultLearnerId,
            selectedExamCode: primaryActiveTarget?.examCode ?? selectedDetail?.examCode ?? "TR_YKS_TYT_MATH",
            apiService: service,
            offlineStore: cacheStore as? ExamOfflineSQLiteStore
        )
    }

    public func updateLearnerId(_ userId: UUID?) {
        guard currentUserId != userId else { return }
        currentUserId = userId
        targets = []
        statusMessageKey = nil
        errorMessageKey = nil
        errorDetailsText = nil
        isOfflineBannerVisible = false
    }

    public func loadCatalogAndTargets() async {
        isLoading = true
        errorMessageKey = nil
        errorDetailsText = nil
        isFeatureDisabled = false
        defer { isLoading = false }

        do {
            let catalog = try await service.fetchDefinitions(
                jurisdiction: selectedJurisdiction,
                examFamily: nil,
                includeDeprecated: false
            )
            definitions = catalog.definitions
            isOfflineBannerVisible = false
            await cacheStore.saveCatalog(catalog)

            if let userId = currentUserId {
                do {
                    let targetList = try await service.listTargets()
                    targets = targetList.targets
                    await cacheStore.saveTargets(targetList.targets, for: userId)
                } catch let error as ExamServiceError where error == .offline {
                    targets = await cacheStore.loadTargets(for: userId)
                    isOfflineBannerVisible = true
                }
            }

            if let activeTarget = primaryActiveTarget,
               let match = definitions.first(where: { $0.examCode == activeTarget.examCode }) {
                await selectExam(match)
            } else if let current = selectedDetail,
                      let match = definitions.first(where: { $0.examCode == current.examCode }) {
                await selectExam(match)
            } else if let first = definitions.first {
                await selectExam(first)
            }
        } catch let error as ExamServiceError {
            await handleServiceError(error, whileLoadingCatalog: true)
        } catch {
            errorMessageKey = "exam.error.unexpected"
            errorDetailsText = error.localizedDescription
        }
    }

    public func applyJurisdictionFilter(_ jurisdiction: ExamJurisdiction?) async {
        selectedJurisdiction = jurisdiction
        await loadCatalogAndTargets()
    }

    public func selectExam(_ summary: ExamDefinitionSummaryModel) async {
        errorMessageKey = nil
        errorDetailsText = nil

        do {
            let detail = try await service.fetchDefinitionDetail(examCode: summary.examCode)
            selectedDetail = detail
            await cacheStore.saveDefinitionDetail(detail)
            populateDefaultScoreInputIfNeeded(for: detail)
        } catch let error as ExamServiceError {
            if error == .offline || error == .featureDisabled {
                if let cachedDetail = await cacheStore.loadDefinitionDetail(examCode: summary.examCode) {
                    selectedDetail = cachedDetail
                    isOfflineBannerVisible = (error == .offline)
                    populateDefaultScoreInputIfNeeded(for: cachedDetail)
                    return
                }
            }
            await handleServiceError(error, whileLoadingCatalog: false)
        } catch {
            errorMessageKey = "exam.error.unexpected"
            errorDetailsText = error.localizedDescription
        }
    }

    public func savePrimaryTarget() async {
        guard let detail = selectedDetail else { return }
        let normalizedScoreText = targetScoreInput.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespacesAndNewlines)
        guard let parsedScore = Double(normalizedScoreText),
              parsedScore >= detail.minScore,
              parsedScore <= detail.maxScore else {
            errorMessageKey = "exam.target.score_out_of_range"
            errorDetailsText = "\(formatNumber(detail.minScore)) – \(formatNumber(detail.maxScore))"
            statusMessageKey = nil
            return
        }

        isSaving = true
        errorMessageKey = nil
        errorDetailsText = nil
        statusMessageKey = nil
        defer { isSaving = false }

        let targetDateString = Self.isoDateFormatter.string(from: targetDateInput)
        let idempotencyKey = "ios-\(detail.examCode)-\(detail.examVersion)-\(UUID().uuidString.prefix(12))"
        let request = UpsertStudentExamTargetRequestDTO(
            examCode: detail.examCode,
            examVersion: detail.examVersion,
            expectedBlueprintHash: detail.blueprintHash,
            targetScore: parsedScore,
            targetDate: targetDateString,
            priorityRank: 1,
            isPrimary: true,
            idempotencyKey: idempotencyKey
        )

        do {
            let saved = try await service.upsertTarget(request: request)
            isOfflineBannerVisible = false
            var updatedTargets = targets.filter { $0.targetId != saved.targetId }
            updatedTargets.insert(saved, at: 0)
            targets = updatedTargets
            if let userId = currentUserId {
                await cacheStore.saveTargets(updatedTargets, for: userId)
            }
            statusMessageKey = "exam.target.saved_banner"
        } catch let error as ExamServiceError {
            await handleServiceError(error, whileLoadingCatalog: false)
        } catch {
            errorMessageKey = "exam.error.unexpected"
            errorDetailsText = error.localizedDescription
        }
    }

    public func purgeUserDataAndReset() async {
        if let userId = currentUserId {
            await cacheStore.purgeUserData(userId: userId)
        }
        currentUserId = nil
        targets = []
        selectedDetail = nil
        definitions = []
        targetScoreInput = ""
        statusMessageKey = nil
        errorMessageKey = nil
        errorDetailsText = nil
        isOfflineBannerVisible = false
        isFeatureDisabled = false
    }

    public func dismissFeedback() {
        statusMessageKey = nil
        errorMessageKey = nil
        errorDetailsText = nil
    }

    private func populateDefaultScoreInputIfNeeded(for detail: ExamDefinitionModel) {
        if let existing = targets.first(where: { $0.examCode == detail.examCode && $0.targetStatus == .active }) {
            targetScoreInput = formatNumber(existing.targetScore)
        } else {
            let suggested = max(detail.minScore, detail.maxScore * 0.8)
            targetScoreInput = formatNumber(suggested)
        }
    }

    private func handleServiceError(_ error: ExamServiceError, whileLoadingCatalog: Bool) async {
        switch error {
        case .featureDisabled:
            isFeatureDisabled = true
            errorMessageKey = "exam.catalog.feature_disabled"
        case .offline:
            isOfflineBannerVisible = true
            if whileLoadingCatalog, let cachedCatalog = await cacheStore.loadCatalog() {
                definitions = cachedCatalog.definitions.filter { def in
                    selectedJurisdiction == nil || def.jurisdiction == selectedJurisdiction
                }
                if let userId = currentUserId {
                    targets = await cacheStore.loadTargets(for: userId)
                }
                if let first = definitions.first,
                   let cachedDetail = await cacheStore.loadDefinitionDetail(examCode: first.examCode) {
                    selectedDetail = cachedDetail
                    populateDefaultScoreInputIfNeeded(for: cachedDetail)
                }
            } else {
                errorMessageKey = "exam.catalog.offline_banner"
            }
        case .scoreOutOfRange:
            errorMessageKey = "exam.target.score_out_of_range"
        case .versionConflict:
            errorMessageKey = "exam.error.version_conflict"
        case .forbidden:
            errorMessageKey = "exam.error.forbidden"
        case .notFound:
            errorMessageKey = "exam.error.not_found"
        case .dateInPast:
            errorMessageKey = "exam.candidate.error.date_in_past"
        case .studyBudgetInvalid:
            errorMessageKey = "exam.candidate.error.budget_invalid"
        case .accommodationInvalid:
            errorMessageKey = "exam.candidate.error.accommodation_invalid"
        case .quotaExceeded:
            errorMessageKey = "exam.syllabus.error.quota_exceeded"
        case .mediaUnsupported:
            errorMessageKey = "exam.syllabus.error.media_unsupported"
        case .disambiguationRequired:
            errorMessageKey = "exam.syllabus.error.disambiguation_required"
        case .poolInsufficient:
            errorMessageKey = "exam.assembly.error.pool_insufficient"
        case .noHintModeActive:
            errorMessageKey = "exam.session.no_hint.notice"
        case .deadlineExpired:
            errorMessageKey = "exam.session.status.expired_auto_submitted"
        case .sessionAlreadySubmitted:
            errorMessageKey = "exam.session.submit.receipt_title"
        case .sessionNotScorable:
            errorMessageKey = "exam.score.error.not_scorable"
        case .regradeInvalidRequest:
            errorMessageKey = "exam.score.error.regrade_invalid"
        case .autosaveVersionConflict:
            errorMessageKey = "exam.error.version_conflict"
        case .invalidScratchpadAsset:
            errorMessageKey = "exam.syllabus.error.media_unsupported"
        case .unexpected(let message):
            errorMessageKey = "exam.error.unexpected"
            errorDetailsText = message
        }
    }

    private func formatNumber(_ value: Double) -> String {
        if value.rounded() == value {
            return String(Int(value))
        }
        return String(format: "%.2f", value)
    }
}
