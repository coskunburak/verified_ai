import Foundation

@MainActor
final class WeeklyProgressReportViewModel: ObservableObject {
    enum ViewState: Equatable {
        case idle
        case loading
        case loaded(WeeklyDigestV1)
        case degraded(message: String, retryable: Bool, cachedDigest: WeeklyDigestV1?)
        case error(message: String)

        var digest: WeeklyDigestV1? {
            switch self {
            case .loaded(let digest):
                return digest
            case .degraded(_, _, let cached):
                return cached
            case .idle, .loading, .error:
                return nil
            }
        }
    }

    @Published private(set) var state: ViewState = .idle
    @Published var selectedWeekIso: String
    @Published var locale: String
    @Published var expandedRecommendationIds: Set<String> = []
    @Published var isShowingShareScopePreview: Bool = false
    @Published var selectedRecipientRole: ShareRecipientRole = .tutor
    @Published var customDisplayAlias: String = ""
    @Published private(set) var isExportingPDF: Bool = false
    @Published private(set) var exportedPDFURL: URL?
    @Published private(set) var latestSharePacket: WeeklySharePacketV1?
    @Published private(set) var exportErrorMessage: String?

    private let service: WeeklyReportServicing
    private let pdfRenderer: TutorSharePacketPDFRendering
    private let nowProvider: () -> Date

    init(
        service: WeeklyReportServicing,
        pdfRenderer: TutorSharePacketPDFRendering = TutorSharePacketPDFRenderer(),
        initialWeekIso: String? = nil,
        locale: String = "en",
        nowProvider: @escaping () -> Date = Date.init
    ) {
        self.service = service
        self.pdfRenderer = pdfRenderer
        self.nowProvider = nowProvider
        self.selectedWeekIso = initialWeekIso ?? ISOWeekCalendarHelper.currentWeekIso(for: nowProvider())
        self.locale = locale.lowercased().hasPrefix("tr") ? "tr" : "en"
    }

    var canSelectNextWeek: Bool {
        let currentIso = ISOWeekCalendarHelper.currentWeekIso(for: nowProvider())
        guard let selectedMonday = ISOWeekCalendarHelper.mondayDate(fromWeekIso: selectedWeekIso),
              let currentMonday = ISOWeekCalendarHelper.mondayDate(fromWeekIso: currentIso) else {
            return false
        }
        return selectedMonday < currentMonday
    }

    func loadWeeklyDigest(forceRefresh: Bool = false) async {
        let previousDigest = state.digest
        if !forceRefresh {
            state = .loading
        }

        do {
            let digest = try await service.fetchWeeklyDigest(
                weekIso: selectedWeekIso,
                locale: locale,
                forceRefresh: forceRefresh
            )
            state = .loaded(digest)
        } catch let networkError as NetworkError {
            handleNetworkFailure(networkError, cachedDigest: previousDigest)
        } catch {
            let msg = locale == "tr"
                ? "Haftalık ilerleme raporu şu anda yüklenemedi."
                : "Weekly progress report could not be loaded."
            state = .error(message: msg)
        }
    }

    func selectPreviousWeek() async {
        guard let prev = ISOWeekCalendarHelper.shiftWeekIso(selectedWeekIso, byWeeks: -1) else {
            return
        }
        selectedWeekIso = prev
        clearTransientExportArtifacts()
        await loadWeeklyDigest(forceRefresh: false)
    }

    func selectNextWeek() async {
        guard canSelectNextWeek,
              let next = ISOWeekCalendarHelper.shiftWeekIso(selectedWeekIso, byWeeks: 1) else {
            return
        }
        selectedWeekIso = next
        clearTransientExportArtifacts()
        await loadWeeklyDigest(forceRefresh: false)
    }

    func toggleRecommendationExplanation(_ recommendationId: String) {
        if expandedRecommendationIds.contains(recommendationId) {
            expandedRecommendationIds.remove(recommendationId)
        } else {
            expandedRecommendationIds.insert(recommendationId)
        }
    }

    func openShareScopePreview(role: ShareRecipientRole = .tutor) {
        selectedRecipientRole = role
        exportErrorMessage = nil
        isShowingShareScopePreview = true
    }

    @discardableResult
    func exportSharePacketPDF() async -> URL? {
        guard !isExportingPDF else { return nil }
        isExportingPDF = true
        exportErrorMessage = nil
        defer { isExportingPDF = false }

        cleanupExportedFile()
        let trimmedAlias = customDisplayAlias.trimmingCharacters(in: .whitespacesAndNewlines)
        let request = WeeklySharePacketRequest(
            recipientRole: selectedRecipientRole,
            displayAlias: trimmedAlias.isEmpty ? nil : String(trimmedAlias.prefix(40)),
            locale: locale
        )

        do {
            let packet = try await service.createSharePacket(weekIso: selectedWeekIso, request: request)
            latestSharePacket = packet
            let url = try pdfRenderer.writeTemporaryPDF(from: packet)
            exportedPDFURL = url
            return url
        } catch let networkError as NetworkError {
            exportErrorMessage = localizedShareError(networkError)
            return nil
        } catch {
            exportErrorMessage = locale == "tr"
                ? "Paylaşım PDF dosyası hazırlanamadı. Lütfen tekrar deneyin."
                : "Could not prepare the shareable PDF packet. Please try again."
            return nil
        }
    }

    func reset() {
        state = .idle
        expandedRecommendationIds.removeAll()
        isShowingShareScopePreview = false
        customDisplayAlias = ""
        clearTransientExportArtifacts()
    }

    private func handleNetworkFailure(_ error: NetworkError, cachedDigest: WeeklyDigestV1?) {
        switch error {
        case .server(let problem) where problem.status == 503 || problem.code == "WEEKLY_REPORT_FEATURE_DISABLED":
            let message = locale == "tr"
                ? "Haftalık özet özelliği geçici olarak bakımda. Daha sonra tekrar deneyebilirsiniz."
                : "Weekly reports are temporarily paused. Please try again shortly."
            state = .degraded(message: message, retryable: true, cachedDigest: cachedDigest)
        case .transport:
            let message = locale == "tr"
                ? "Bağlantı geçici olarak kesildi. Yeniden denemek için dokunun."
                : "Connection unavailable. Tap Retry to refresh your weekly report."
            state = .degraded(message: message, retryable: true, cachedDigest: cachedDigest)
        case .server(let problem):
            if problem.details?.recoverable == true {
                state = .degraded(message: problem.title, retryable: true, cachedDigest: cachedDigest)
            } else {
                state = .error(message: problem.title)
            }
        case .httpStatus(let status) where (500...599).contains(status):
            let message = locale == "tr"
                ? "Rapor servisi geçici olarak meşgul."
                : "Report service temporarily unavailable."
            state = .degraded(message: message, retryable: true, cachedDigest: cachedDigest)
        default:
            let message = locale == "tr"
                ? "Haftalık rapor yüklenemedi."
                : "Unable to load weekly learning report."
            state = .error(message: message)
        }
    }

    private func localizedShareError(_ error: NetworkError) -> String {
        switch error {
        case .server(let problem):
            return problem.title
        default:
            return locale == "tr"
                ? "Paylaşım paketi oluşturulamadı."
                : "Unable to build share packet."
        }
    }

    private func clearTransientExportArtifacts() {
        latestSharePacket = nil
        exportErrorMessage = nil
        cleanupExportedFile()
    }

    private func cleanupExportedFile() {
        if let existing = exportedPDFURL {
            try? FileManager.default.removeItem(at: existing)
            exportedPDFURL = nil
        }
    }
}
