import Foundation
import SwiftUI

@MainActor
public final class CandidateProfileViewModel: ObservableObject {
    @Published public var selectedExamCode: String = "TR_YKS_TYT_MATH"
    @Published public var selectedExamVersion: String? = "2026.1"
    @Published public var targetScoreText: String = "35.5"
    @Published public var targetDate: Date = Calendar.current.date(byAdding: .day, value: 90, to: Date()) ?? Date().addingTimeInterval(90 * 86400)
    @Published public var dailyStudyMinutes: Int = 60 {
        didSet {
            if weeklyStudyMinutes < dailyStudyMinutes {
                weeklyStudyMinutes = min(6720, dailyStudyMinutes * 5)
            }
        }
    }
    @Published public var weeklyStudyMinutes: Int = 420
    @Published public var extraTimeMultiplier: Double = 1.00
    @Published public var highContrastEnabled: Bool = false
    @Published public var screenReaderOptimized: Bool = false
    @Published public var reducedMotionEnabled: Bool = false
    @Published public var baselineMastery: Double = 0.25

    @Published public private(set) var savedProfile: CandidateProfileResponse?
    @Published public private(set) var historyEntries: [CandidateHorizonHistoryEntry] = []
    @Published public private(set) var latestUpload: SyllabusIngestionResponse?
    @Published public var selectedDisambiguationChoices: [Int: String] = [:]

    @Published public private(set) var isLoading: Bool = false
    @Published public private(set) var isSaving: Bool = false
    @Published public private(set) var isIngesting: Bool = false
    @Published public private(set) var isOfflineFallback: Bool = false
    @Published public private(set) var errorLocalizationKey: String?
    @Published public private(set) var errorMessage: String?
    @Published public private(set) var statusLocalizationKey: String?

    private let apiService: ExamAPIServicing
    private let offlineStore: ExamOfflineSQLiteStore?
    private let userIdProvider: @Sendable () -> String
    private var activeUserId: String?

    public init(
        apiService: ExamAPIServicing,
        offlineStore: ExamOfflineSQLiteStore?,
        userIdProvider: @escaping @Sendable () -> String = { "current-learner" }
    ) {
        self.apiService = apiService
        self.offlineStore = offlineStore
        self.userIdProvider = userIdProvider
    }

    public func updateLearnerId(_ userId: UUID) {
        activeUserId = userId.uuidString
    }

    private var resolvedUserId: String {
        activeUserId ?? userIdProvider()
    }

    public var effectiveDailyCapacityMinutes: Int {
        max(1, Int((min(Double(dailyStudyMinutes), Double(weeklyStudyMinutes) / 7.0)).rounded()))
    }

    public var previewDaysUntilExam: Int {
        let startOfToday = Calendar.current.startOfDay(for: Date())
        let startOfTarget = Calendar.current.startOfDay(for: targetDate)
        let days = Calendar.current.dateComponents([.day], from: startOfToday, to: startOfTarget).day ?? 1
        return max(1, days)
    }

    public var previewRequiredDailyMinutes: Int {
        let totalQuestions: Double = switch selectedExamCode {
        case "INTL_SAT_MATH": 44.0
        case "INTL_AP_CALC_BC": 45.0
        case "INTL_IB_MATH_AA_HL": 50.0
        default: 40.0
        }
        let estimatedTotalMinutes = totalQuestions * (1.0 - baselineMastery) * 90.0
        return Int(ceil(estimatedTotalMinutes / Double(previewDaysUntilExam)))
    }

    public var previewFeasibilityStatus: CandidateFeasibilityStatus {
        let capacity = min(Double(dailyStudyMinutes), Double(weeklyStudyMinutes) / 7.0)
        guard capacity > 0 else { return .unrealistic }
        let ratio = Double(previewRequiredDailyMinutes) / capacity
        if ratio <= 0.85 { return .optimal }
        if ratio <= 1.15 { return .stretch }
        if ratio <= 1.50 { return .atRisk }
        return .unrealistic
    }

    public func adjustedDurationMinutes(baseMinutes: Int) -> Int {
        Int(ceil(Double(baseMinutes) * extraTimeMultiplier))
    }

    public func loadProfile(offlineMode: Bool = false) async {
        isLoading = true
        errorLocalizationKey = nil
        errorMessage = nil
        defer { isLoading = false }

        let userId = resolvedUserId
        if offlineMode {
            if let cached = await offlineStore?.loadCandidateProfile(userId: userId) {
                applyLoadedProfile(cached, offline: true)
            }
            if let cachedUpload = await offlineStore?.loadLatestSyllabusUpload(userId: userId) {
                applyUploadResult(cachedUpload)
            }
            return
        }

        do {
            let profile = try await apiService.fetchCandidateProfile()
            applyLoadedProfile(profile, offline: false)
            await offlineStore?.saveCandidateProfile(profile, userId: userId)
            let history = try await apiService.fetchCandidateProfileHistory()
            historyEntries = history.items
        } catch {
            if let cached = await offlineStore?.loadCandidateProfile(userId: userId) {
                applyLoadedProfile(cached, offline: true)
                if let cachedUpload = await offlineStore?.loadLatestSyllabusUpload(userId: userId) {
                    applyUploadResult(cachedUpload)
                }
            } else {
                // First-time user without profile yet is a normal setup state (not an error banner on initial load unless non-404)
                mapError(error, ignoreNotFound: true)
            }
        }
    }

    public func saveProfile() async {
        errorLocalizationKey = nil
        errorMessage = nil
        statusLocalizationKey = nil

        let normalizedScore = targetScoreText.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespacesAndNewlines)
        guard let score = Double(normalizedScore) else {
            errorLocalizationKey = "exam.candidate.error.invalid_score"
            return
        }

        let startOfToday = Calendar.current.startOfDay(for: Date())
        let startOfTarget = Calendar.current.startOfDay(for: targetDate)
        guard startOfTarget > startOfToday else {
            errorLocalizationKey = "exam.candidate.error.date_in_past"
            return
        }

        guard dailyStudyMinutes >= 15, dailyStudyMinutes <= 960,
              weeklyStudyMinutes >= dailyStudyMinutes, weeklyStudyMinutes <= 6720 else {
            errorLocalizationKey = "exam.candidate.error.budget_invalid"
            return
        }

        isSaving = true
        defer { isSaving = false }

        let dateFormatter = DateFormatter()
        dateFormatter.calendar = Calendar(identifier: .gregorian)
        dateFormatter.locale = Locale(identifier: "en_US_POSIX")
        dateFormatter.timeZone = TimeZone(secondsFromGMT: 0)
        dateFormatter.dateFormat = "yyyy-MM-dd"
        let formattedDate = dateFormatter.string(from: targetDate)

        let request = UpsertCandidateProfileRequest(
            schemaVersion: "candidate-profile-request-v1",
            examCode: selectedExamCode,
            pinnedExamVersion: selectedExamVersion,
            targetScore: score,
            targetDate: formattedDate,
            dailyStudyMinutes: dailyStudyMinutes,
            weeklyStudyMinutes: weeklyStudyMinutes,
            accommodations: AccessibilityAccommodations(
                extraTimeMultiplier: extraTimeMultiplier,
                highContrastEnabled: highContrastEnabled,
                screenReaderOptimized: screenReaderOptimized,
                reducedMotionEnabled: reducedMotionEnabled
            ),
            baselineMastery: baselineMastery,
            changeReason: savedProfile == nil ? "INITIAL_SETUP" : "HORIZON_UPDATE",
            idempotencyKey: "ios-prof-\(UUID().uuidString.lowercased())"
        )

        do {
            let saved = try await apiService.upsertCandidateProfile(request, idempotencyKey: request.idempotencyKey ?? UUID().uuidString)
            applyLoadedProfile(saved, offline: false)
            await offlineStore?.saveCandidateProfile(saved, userId: resolvedUserId)
            if let history = try? await apiService.fetchCandidateProfileHistory() {
                historyEntries = history.items
            }
            statusLocalizationKey = "exam.candidate.saved_banner"
        } catch {
            mapError(error, ignoreNotFound: false)
        }
    }

    public func ingestSyllabus(fileData: Data, mimeType: String, filename: String) async {
        errorLocalizationKey = nil
        errorMessage = nil
        statusLocalizationKey = nil
        isIngesting = true
        defer { isIngesting = false }

        do {
            let result = try await apiService.ingestSyllabus(
                fileData: fileData,
                mimeType: mimeType,
                filename: filename,
                examCode: selectedExamCode,
                examVersion: selectedExamVersion,
                idempotencyKey: "ios-syl-\(UUID().uuidString.lowercased())"
            )
            applyUploadResult(result)
            await offlineStore?.saveSyllabusUpload(result, userId: resolvedUserId)
            statusLocalizationKey = result.status == .needsDisambiguation
                ? "exam.syllabus.status.needs_disambiguation"
                : "exam.syllabus.status.auto_confirmed"
        } catch {
            mapError(error, ignoreNotFound: false)
        }
    }

    public func confirmDisambiguation() async {
        guard let upload = latestUpload else { return }
        errorLocalizationKey = nil
        errorMessage = nil
        statusLocalizationKey = nil

        var mappings: [SyllabusMappingConfirmation] = []
        for item in upload.parsedTopics {
            let chosenSkill = selectedDisambiguationChoices[item.itemIndex]
                ?? item.matchedSkillId
                ?? item.candidates.first?.skillId
            guard let skill = chosenSkill, !skill.isEmpty else {
                errorLocalizationKey = "exam.syllabus.error.disambiguation_required"
                return
            }
            mappings.append(SyllabusMappingConfirmation(itemIndex: item.itemIndex, confirmedSkillId: skill))
        }

        isIngesting = true
        defer { isIngesting = false }

        do {
            let confirmed = try await apiService.confirmSyllabusDisambiguation(uploadId: upload.uploadId, mappings: mappings)
            applyUploadResult(confirmed)
            await offlineStore?.saveSyllabusUpload(confirmed, userId: resolvedUserId)
            statusLocalizationKey = "exam.syllabus.status.user_confirmed"
        } catch {
            mapError(error, ignoreNotFound: false)
        }
    }

    public func purgeUserDataAndReset() async {
        let userId = resolvedUserId
        activeUserId = nil
        await offlineStore?.purgeUserData(userId: userId)
        savedProfile = nil
        historyEntries = []
        latestUpload = nil
        selectedDisambiguationChoices = [:]
        errorLocalizationKey = nil
        errorMessage = nil
        statusLocalizationKey = nil
        isOfflineFallback = false
    }

    private func applyLoadedProfile(_ profile: CandidateProfileResponse, offline: Bool) {
        savedProfile = profile
        selectedExamCode = profile.activeExamCode
        selectedExamVersion = profile.pinnedExamVersion
        targetScoreText = String(format: "%.2f", profile.targetScore)
        dailyStudyMinutes = profile.dailyStudyMinutes
        weeklyStudyMinutes = profile.weeklyStudyMinutes
        extraTimeMultiplier = profile.accommodations.extraTimeMultiplier
        highContrastEnabled = profile.accommodations.highContrastEnabled
        screenReaderOptimized = profile.accommodations.screenReaderOptimized
        reducedMotionEnabled = profile.accommodations.reducedMotionEnabled
        baselineMastery = profile.baselineMastery
        isOfflineFallback = offline

        let parser = DateFormatter()
        parser.calendar = Calendar(identifier: .gregorian)
        parser.locale = Locale(identifier: "en_US_POSIX")
        parser.dateFormat = "yyyy-MM-dd"
        if let parsed = parser.date(from: profile.targetDate) {
            targetDate = parsed
        }
    }

    private func applyUploadResult(_ upload: SyllabusIngestionResponse) {
        latestUpload = upload
        var defaults: [Int: String] = [:]
        for item in upload.parsedTopics {
            if let matched = item.matchedSkillId {
                defaults[item.itemIndex] = matched
            } else if let top = item.candidates.first?.skillId {
                defaults[item.itemIndex] = top
            }
        }
        selectedDisambiguationChoices = defaults
    }

    private func mapError(_ error: Error, ignoreNotFound: Bool) {
        if let serviceError = error as? ExamServiceError {
            if ignoreNotFound, serviceError == .notFound {
                return
            }
            switch serviceError {
            case .featureDisabled:
                errorLocalizationKey = "exam.catalog.feature_disabled"
            case .notFound:
                errorLocalizationKey = "exam.error.not_found"
            case .versionConflict:
                errorLocalizationKey = "exam.error.version_conflict"
            case .scoreOutOfRange:
                errorLocalizationKey = "exam.target.score_out_of_range"
            case .forbidden:
                errorLocalizationKey = "exam.error.forbidden"
            case .offline:
                errorLocalizationKey = "exam.catalog.offline_banner"
            case .dateInPast:
                errorLocalizationKey = "exam.candidate.error.date_in_past"
            case .studyBudgetInvalid:
                errorLocalizationKey = "exam.candidate.error.budget_invalid"
            case .accommodationInvalid:
                errorLocalizationKey = "exam.candidate.error.accommodation_invalid"
            case .quotaExceeded:
                errorLocalizationKey = "exam.syllabus.error.quota_exceeded"
            case .mediaUnsupported:
                errorLocalizationKey = "exam.syllabus.error.media_unsupported"
            case .disambiguationRequired:
                errorLocalizationKey = "exam.syllabus.error.disambiguation_required"
            case .poolInsufficient:
                errorLocalizationKey = "exam.assembly.error.pool_insufficient"
            case .noHintModeActive:
                errorLocalizationKey = "exam.session.no_hint.notice"
            case .deadlineExpired:
                errorLocalizationKey = "exam.session.status.expired_auto_submitted"
            case .sessionAlreadySubmitted:
                errorLocalizationKey = "exam.session.submit.receipt_title"
            case .sessionNotScorable:
                errorLocalizationKey = "exam.score.error.not_scorable"
            case .regradeInvalidRequest:
                errorLocalizationKey = "exam.score.error.regrade_invalid"
            case .autosaveVersionConflict:
                errorLocalizationKey = "exam.error.version_conflict"
            case .invalidScratchpadAsset:
                errorLocalizationKey = "exam.syllabus.error.media_unsupported"
            case .signatureRequired:
                errorLocalizationKey = "exam.integrity.error.signature_required"
            case .integrityFailed:
                errorLocalizationKey = "exam.integrity.error.verification_needed"
            case .clockResyncRequired:
                errorLocalizationKey = "exam.integrity.error.clock_resync"
            case .retryConflict:
                errorLocalizationKey = "exam.integrity.error.retry_conflict"
            case .proofExpired:
                errorLocalizationKey = "exam.integrity.error.proof_expired"
            case .integrityConfigUnavailable:
                errorLocalizationKey = "exam.error.unexpected"
            case .unexpected(let msg):
                errorMessage = msg
            }
            return
        }
        errorLocalizationKey = "exam.error.unexpected"
    }
}
