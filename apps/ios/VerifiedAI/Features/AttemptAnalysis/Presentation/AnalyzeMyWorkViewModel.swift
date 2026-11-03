import Foundation
import Observation

@MainActor @Observable
final class AnalyzeMyWorkViewModel {
    let owner: UUID
    let problem: UUID
    let learning: AttemptLearningViewModel?
    let capture: AttemptCaptureDependencies?
    var realDomainConfirmed = false
    private(set) var reviewPreview: Data?
    var recognizedLines: [AttemptRecognizedLine] = []
    private var captureReview: AttemptCaptureReview?
    private var extractionReference: (UUID, UUID)?
    private let extractor = NativeAttemptExtraction()
    private let service: AttemptAnalysisServicing
    private let store: AttemptAnalysisPendingStore
    private let ownsAccount: @MainActor () async -> Bool
    @ObservationIgnored private var task: Task<Void, Never>?
    private var generation = 0
    private var active = false
    private(set) var busy = false
    private(set) var context: AttemptContext?
    private(set) var result: StudentAttempt?
    private(set) var history: [StudentAttempt] = []
    private(set) var pending: AttemptPending?
    private(set) var errorKey: String?
    var draft = ""
    init(owner: UUID, problem: UUID, service: AttemptAnalysisServicing, store: AttemptAnalysisPendingStore,
         capture: AttemptCaptureDependencies? = nil, learningAPI: AttemptLearningAPI? = nil, ownsAccount: @escaping @MainActor () async -> Bool) {
        self.learning = learningAPI.map { AttemptLearningViewModel(api: $0, owner: owner, problem: problem, ownsAccount: ownsAccount) }
        self.capture = capture; self.owner = owner; self.problem = problem; self.service = service; self.store = store; self.ownsAccount = ownsAccount
    }
    var isFixture: Bool {
        #if DEBUG
        return service is FixtureAttemptAnalysisAPI
        #else
        return false
        #endif
    }
    var lines: [String] { draft.components(separatedBy: .newlines) }
    private var submission: AttemptSubmission? {
        guard let context else { return nil }
        return AttemptSubmission(source: context.source, steps: lines, realDomainConfirmed: realDomainConfirmed ? true : nil,
            captureReview: captureReview?.rawLines.count == lines.count ? captureReview : nil)
    }
    var payloadFits: Bool {
        guard let submission, let data = try? JSONEncoder().encode(AttemptPending(key: UUID().uuidString, submission: submission)) else { return false }
        return data.count <= 16384
    }
    var canSubmit: Bool { active && !busy && pending == nil && context?.enabled == true && payloadFits && (1...12).contains(lines.count)
        && lines.allSatisfy { !$0.trimmingCharacters(in: .whitespaces).isEmpty && $0.utf16.count <= 256 && $0.utf8.count <= 1024 } }
    func resume() {
        active = true
        run {
            self.context = nil
            self.pending = try self.store.load(owner: self.owner, problem: self.problem)
            if let pending = self.pending {
                self.draft = pending.submission.steps.joined(separator: "\n"); self.realDomainConfirmed = pending.submission.realDomainConfirmed == true; self.captureReview = pending.submission.captureReview
            } else if let saved = try self.store.loadDraft(owner: self.owner, problem: self.problem) {
                self.draft = saved.text; self.realDomainConfirmed = saved.realDomainConfirmed; self.captureReview = saved.captureReview
            }
            let history = try await self.service.list(problem: self.problem)
            guard await self.authorized() else { return }
            self.history = history; self.result = history.last
            let context = try await self.service.context(problem: self.problem)
            guard await self.authorized() else { return }
            self.context = context
        }
    }
    func submit() {
        guard canSubmit, let submission else { return }
        let operation = AttemptPending(key: UUID().uuidString, submission: submission)
        run(clearRejectedSubmission: true) {
            try self.store.save(operation, owner: self.owner, problem: self.problem)
            self.pending = operation
            try await self.resolve(operation)
        }
    }
    func extract(_ asset: CapturedAsset) async {
        guard !busy, await authorized() else { return }
        busy = true; errorKey = nil
        let expected = generation
        do {
            let lines = try await extractor.extract(asset)
            let preview = await extractor.preview(asset)
            guard await authorized(), expected == generation else { return }
            extractionReference = nil
            reviewPreview = preview
            recognizedLines = lines
        } catch {
            guard await authorized(), expected == generation else { return }
            errorKey = "attempt.ocr.failed"
        }
        if expected == generation { busy = false }
    }
    func extractGateway(_ reference: RecognizedProblemReference) async {
        guard let api = service as? AttemptAnalysisAPI, !busy, await authorized() else { return }
        busy = true; errorKey = nil
        let expected = generation
        do {
            let preview: Data?
            if let asset = capture?.capture.currentAsset { preview = await extractor.preview(asset) } else { preview = nil }
            let result = try await api.extraction(problem: problem, assetSession: reference.preprocessedAsset.durableAsset.problemSessionId)
            guard await authorized(), expected == generation else { return }
            reviewPreview = preview
            extractionReference = (result.assetSessionId, result.recognitionJobId)
            recognizedLines = result.steps.map { line in
                AttemptRecognizedLine(id: UUID(), raw: line.rawTranscription, text: line.rawTranscription,
                    confidence: Float(line.confidence ?? 0), box: CGRect(x: line.boundingBox.x, y: line.boundingBox.y, width: line.boundingBox.width, height: line.boundingBox.height))
            }
        } catch { if await authorized(), expected == generation { errorKey = "attempt.ocr.failed" } }
        if expected == generation { busy = false }
    }
    func acceptRecognizedLines() {
        guard recognizedLines.allSatisfy({ $0.confirmed }), !recognizedLines.isEmpty else { return }
        captureReview = AttemptCaptureReview(schemaVersion: "attempt-capture-review-v1", engine: extractionReference == nil ? "NATIVE_VISION" : "GATEWAY_RECOGNITION",
            rawLines: recognizedLines.map(\.raw), confirmed: true, assetSessionId: extractionReference?.0, recognitionJobId: extractionReference?.1)
        draft = recognizedLines.map(\.text).joined(separator: "\n")
        cancelReview()
        saveDraft()
    }
    func cancelReview() { recognizedLines = []; reviewPreview = nil; extractionReference = nil }
    func saveDraft() {
        guard active, pending == nil else { return }
        do { try store.saveDraft(.init(text: draft, realDomainConfirmed: realDomainConfirmed, captureReview: captureReview), owner: owner, problem: problem) }
        catch { errorKey = "attempt.storage" }
    }
    func retry() { guard let pending else { resume(); return }; run(clearRejectedSubmission: true) { try await self.resolve(pending) } }
    func select(_ attempt: StudentAttempt) { guard !busy else { return }; result = attempt }
    func suspend() { active = false; generation += 1; task?.cancel(); task = nil; busy = false }
    func invalidate() {
        suspend(); learning?.invalidate(); draft = ""; cancelReview(); realDomainConfirmed = false; captureReview = nil; extractionReference = nil; pending = nil; result = nil; context = nil; history = []; errorKey = "attempt.authorization"
    }
    private func authorized() async -> Bool {
        let expected = generation
        let owns = await ownsAccount()
        guard active, expected == generation, !Task.isCancelled else { return false }
        if !owns { invalidate(); return false }
        return true
    }
    private func resolve(_ operation: AttemptPending) async throws {
        let response = try await service.submit(problem: problem, key: operation.key, submission: operation.submission)
        guard await authorized() else { return }
        result = response
        if response.status == .submitted { errorKey = "attempt.pending"; return }
        try store.remove(owner: owner, problem: problem)
        pending = nil
        history.removeAll { $0.id == response.id }; history.append(response)
        if response.status == .stale { errorKey = "attempt.stale" }
    }
    private func run(clearRejectedSubmission: Bool = false, _ operation: @escaping @MainActor () async throws -> Void) {
        guard active, !busy else { return }
        busy = true; errorKey = nil
        let expected = generation
        task = Task {
            guard await authorized() else { return }
            do { try await operation() }
            catch {
                guard await authorized(), expected == generation else { return }
                errorKey = "attempt.retry"
                if case NetworkError.server(let error) = error {
                    if clearRejectedSubmission && [400, 409, 413, 422].contains(error.status) {
                        // Definitive non-commit: retain text but release the rejected operation identity.
                        do { try store.remove(owner: owner, problem: problem); pending = nil; errorKey = "attempt.review" }
                        catch { errorKey = "attempt.storage" }
                        context = nil
                    }
                    if [401, 403, 404].contains(error.status) { invalidate(); return }
                    if error.code == "ATTEMPT_UNAVAILABLE" { errorKey = "attempt.unavailable" }
                }
            }
            if expected == generation && active { busy = false }
        }
    }
}
