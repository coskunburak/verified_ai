import Foundation
import Observation

@MainActor @Observable
final class AnalyzeMyWorkViewModel {
    let owner: UUID
    let problem: UUID
    let learning: AttemptLearningViewModel?
    let capture: AttemptCaptureDependencies?
    var workDraft: AttemptWorkRequest?
    var evaluationRealDomainConfirmed = false
    private(set) var classification: AttemptMistakeView?
    private(set) var evaluation: AttemptEvaluationView?
    private(set) var pendingEvaluation: AttemptEvaluationPending?
    private(set) var workResult: AttemptStructuredWork?
    private(set) var pendingWork: AttemptWorkPending?
    private var acknowledgedWork: AttemptWorkRequest?
    private var extractionSource: AttemptSourceIdentity?
    private(set) var workPreview: Data?
    private var captureIdentity: UUID?
    private var originalObservations: [AttemptWorkRequest.Observation] = []
    var realDomainConfirmed = false
    var answerMode = false
    var finalAnswer = ""
    private var acknowledgedSubmission: AttemptSubmission?
    private var draftSource: AttemptSourceIdentity?
    private(set) var draftNeedsReview = false
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
         capture: AttemptCaptureDependencies? = nil, learningAPI: (any AttemptLearningServicing)? = nil, ownsAccount: @escaping @MainActor () async -> Bool) {
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
    var lines: [String] { answerMode && draft.isEmpty ? [] : draft.components(separatedBy: .newlines) }
    private var submission: AttemptSubmission? {
        guard let context else { return nil }
        if answerMode { return AttemptSubmission(source: context.source, steps: lines,
            schemaVersion: "attempt-submission-v3", finalAnswer: finalAnswer) }
        return AttemptSubmission(source: context.source, steps: lines, realDomainConfirmed: realDomainConfirmed ? true : nil,
            captureReview: captureReview?.rawLines.count == lines.count ? captureReview : nil)
    }
    var payloadFits: Bool {
        if let workDraft { return (try? JSONEncoder().encode(workDraft).count).map { $0 <= 16384 } ?? false }
        guard let submission, let data = try? JSONEncoder().encode(AttemptPending(key: UUID().uuidString, submission: submission)) else { return false }
        return data.count <= 16384
    }
    var canSubmit: Bool { active && !busy && pending == nil && pendingWork == nil && !draftNeedsReview && context?.enabled == true
        && payloadFits && (workDraft.map { $0.isValid && context?.structuredWorkEnabled == true } ?? (submission?.isValid == true)) }
    func confirmDraftSource() {
        guard let context, active, !busy, pending == nil, pendingWork == nil else { return }
        draftSource = context.source; workDraft?.source = context.source; workDraft?.supersedesAttemptId = nil; draftNeedsReview = false; saveDraft()
    }
    func resume() {
        active = true
        run {
            let expected = self.generation
            self.context = nil; self.workResult = nil; self.evaluation = nil; self.classification = nil
            self.pendingEvaluation = try self.store.loadEvaluation(owner: self.owner, problem: self.problem)
            self.evaluationRealDomainConfirmed = self.pendingEvaluation?.input.realDomainConfirmed ?? false
            self.pendingWork = try self.store.loadWork(owner: self.owner, problem: self.problem)
            self.pending = try self.store.load(owner: self.owner, problem: self.problem)
            if let pending = self.pending {
                self.workDraft = nil
                self.draft = pending.submission.steps.joined(separator: "\n"); self.realDomainConfirmed = pending.submission.realDomainConfirmed == true; self.captureReview = pending.submission.captureReview
                self.answerMode = pending.submission.finalAnswer != nil; self.finalAnswer = pending.submission.finalAnswer ?? ""
                self.draftSource = pending.submission.source
            } else if let pendingWork = self.pendingWork {
                self.workDraft = pendingWork.input; self.draftSource = pendingWork.input.source
            } else if let saved = try self.store.loadDraft(owner: self.owner, problem: self.problem) {
                self.workDraft = saved.structuredWork
                self.draft = saved.text; self.realDomainConfirmed = saved.realDomainConfirmed; self.captureReview = saved.captureReview
                self.answerMode = saved.finalAnswer != nil; self.finalAnswer = saved.finalAnswer ?? ""; self.draftSource = saved.source
            } else {
                self.workDraft = nil
                self.draft = ""; self.finalAnswer = ""; self.draftSource = nil; self.captureReview = nil
            }
            self.workPreview = nil
            if let capture = self.workDraft?.captureId { self.workPreview = try self.store.loadWorkPreview(owner: self.owner, problem: self.problem, capture: capture) }
            let history = try await self.service.list(problem: self.problem)
            guard await self.authorized(), expected == self.generation else { return }
            self.history = history; self.result = history.first { $0.id == self.pendingEvaluation?.attemptId } ?? history.last
            do {
                let context = try await self.service.context(problem: self.problem)
                guard await self.authorized(), expected == self.generation else { return }
                self.context = context
                self.draftNeedsReview = self.pending == nil && self.pendingWork == nil && (self.workDraft != nil || !self.draft.isEmpty || !self.finalAnswer.isEmpty) && self.draftSource != context.source
                if self.draftSource == nil && self.draft.isEmpty && self.finalAnswer.isEmpty { self.draftSource = context.source }
            } catch NetworkError.server(let failure) where failure.code == "SOLVE_SOURCE_NOT_READY" {
                guard await self.authorized(), expected == self.generation else { return }
                self.context = nil; self.draftNeedsReview = true
            }
            if let selected = self.result { try await self.loadWork(selected, generation: expected) }
        }
    }
    func submit() {
        if workDraft != nil { submitStructured(); return }
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
        let source = context?.source
        do {
            let lines = try await extractor.extract(asset)
            let preview = await extractor.preview(asset)
            guard await authorized(), expected == generation else { return }
            extractionSource = source
            if context?.structuredWorkEnabled == true, let preview { try store.saveWorkPreview(preview, owner: owner, problem: problem, capture: asset.id) }
            captureIdentity = asset.id
            originalObservations = lines.map { .init(id: $0.id, rawText: $0.raw, confidence: Double($0.confidence),
                box: .init(x: $0.box.minX, y: $0.box.minY, width: $0.box.width, height: $0.box.height)) }
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
        let source = context?.source
        do {
            let preview: Data?
            if let asset = capture?.capture.currentAsset { preview = await extractor.preview(asset) } else { preview = nil }
            let result = try await api.extraction(problem: problem, assetSession: reference.preprocessedAsset.durableAsset.problemSessionId)
            guard await authorized(), expected == generation else { return }
            reviewPreview = preview
            extractionReference = (result.assetSessionId, result.recognitionJobId)
            extractionSource = source
            captureIdentity = nil
            recognizedLines = result.steps.map { line in
                AttemptRecognizedLine(id: UUID(), raw: line.rawTranscription, text: line.rawTranscription,
                    confidence: Float(line.confidence ?? 0), box: CGRect(x: line.boundingBox.x, y: line.boundingBox.y, width: line.boundingBox.width, height: line.boundingBox.height), regionReliable: false)
            }
            originalObservations = zip(recognizedLines, result.steps).map { line, step in
                .init(id: line.id, rawText: step.rawTranscription, confidence: step.confidence,
                    box: .init(x: step.boundingBox.x, y: step.boundingBox.y, width: step.boundingBox.width, height: step.boundingBox.height))
            }
        } catch { if await authorized(), expected == generation { errorKey = "attempt.ocr.failed" } }
        if expected == generation { busy = false }
    }
    func acceptRecognizedLines() {
        if context?.structuredWorkEnabled == true, let context, let source = extractionSource, !recognizedLines.isEmpty {
            workDraft = AttemptWorkRequest(source: source, engine: extractionReference == nil ? "NATIVE_VISION" : "GATEWAY_RECOGNITION",
                captureId: captureIdentity, assetSessionId: extractionReference?.0, recognitionJobId: extractionReference?.1,
                observations: originalObservations, steps: recognizedLines.map {
                    .init(id: $0.id, text: $0.text, role: .intermediate, observationIds: [$0.id], confirmed: $0.confirmed)
                })
            workPreview = reviewPreview
            draftSource = source; draftNeedsReview = source != context.source; answerMode = false; finalAnswer = ""
            cancelReview(); saveDraft(); return
        }
        guard recognizedLines.allSatisfy({ $0.confirmed }), !recognizedLines.isEmpty else { return }
        captureReview = AttemptCaptureReview(schemaVersion: "attempt-capture-review-v1", engine: extractionReference == nil ? "NATIVE_VISION" : "GATEWAY_RECOGNITION",
            rawLines: recognizedLines.map(\.raw), confirmed: true, assetSessionId: extractionReference?.0, recognitionJobId: extractionReference?.1)
        answerMode = false; finalAnswer = ""
        draft = recognizedLines.map(\.text).joined(separator: "\n")
        cancelReview()
        saveDraft()
    }
    func cancelReview() {
        if let captureIdentity, captureIdentity != workDraft?.captureId { try? store.removeWorkPreview(owner: owner, problem: problem, capture: captureIdentity) }
        recognizedLines = []; reviewPreview = nil; extractionReference = nil; captureIdentity = nil; extractionSource = nil; originalObservations = [] }
    func saveDraft() {
        guard active, pending == nil, pendingWork == nil else { return }
        if let acknowledgedWork, workDraft == acknowledgedWork { return }
        if acknowledgedWork != nil && workDraft == nil && draft.isEmpty && finalAnswer.isEmpty { return }
        if workDraft == nil, let acknowledgedSubmission, submission == acknowledgedSubmission { return }
        do { try store.saveDraft(.init(text: workDraft == nil ? draft : "", realDomainConfirmed: workDraft == nil && realDomainConfirmed, captureReview: workDraft == nil && !answerMode ? captureReview : nil, source: draftSource ?? context?.source, finalAnswer: workDraft == nil && answerMode ? finalAnswer : nil, structuredWork: workDraft), owner: owner, problem: problem) }
        catch { errorKey = "attempt.storage" }
    }
    func retry() { if let pendingWork { run { try await self.resolveWork(pendingWork) }; return }; guard let pending else { resume(); return }; run(clearRejectedSubmission: true) { try await self.resolve(pending) } }
    func select(_ attempt: StudentAttempt) {
        guard !busy else { return }; result = attempt; workResult = nil; evaluation = nil; classification = nil
        evaluationRealDomainConfirmed = pendingEvaluation?.attemptId == attempt.id ? pendingEvaluation?.input.realDomainConfirmed ?? false : false
        run { try await self.loadWork(attempt, generation: self.generation) }
    }
    func suspend() { active = false; generation += 1; task?.cancel(); task = nil; busy = false }
    func invalidate() {
        evaluation = nil; classification = nil; evaluationRealDomainConfirmed = false; pendingEvaluation = nil; workDraft = nil; pendingWork = nil; workResult = nil; acknowledgedWork = nil; workPreview = nil
        suspend(); learning?.invalidate(); draft = ""; finalAnswer = ""; answerMode = false; draftSource = nil; acknowledgedSubmission = nil; draftNeedsReview = false; cancelReview(); realDomainConfirmed = false; captureReview = nil; extractionReference = nil; pending = nil; result = nil; context = nil; history = []; errorKey = "attempt.authorization"
    }
    private func authorized() async -> Bool {
        let expected = generation
        let owns = await ownsAccount()
        guard active, expected == generation, !Task.isCancelled else { return false }
        if !owns { invalidate(); return false }
        return true
    }
    private func resolve(_ operation: AttemptPending) async throws {
        let expected = generation
        let response = try await service.submit(problem: problem, key: operation.key, submission: operation.submission)
        guard await authorized(), expected == generation else { return }
        result = response
        history.removeAll { $0.id == response.id }; history.append(response)
        if response.status == .submitted && response.finalAnswer == nil { errorKey = "attempt.pending"; return }
        try store.removeDraft(owner: owner, problem: problem)
        try store.remove(owner: owner, problem: problem)
        pending = nil; acknowledgedSubmission = operation.submission
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
                        // Rejected payload: preserve a draft before releasing the pending identity.
                        do {
                            if let operation = pending {
                                try store.saveDraft(.init(text: operation.submission.steps.joined(separator: "\n"),
                                    realDomainConfirmed: operation.submission.realDomainConfirmed == true,
                                    captureReview: operation.submission.captureReview, source: operation.submission.source,
                                    finalAnswer: operation.submission.finalAnswer), owner: owner, problem: problem)
                            }
                            try store.remove(owner: owner, problem: problem); pending = nil; errorKey = "attempt.review"
                        }
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

extension AnalyzeMyWorkViewModel {
    var editingLocked: Bool { busy || pending != nil || pendingWork != nil }
    var workSteps: [AttemptWorkRequest.Step] { workDraft?.steps ?? [] }
    func updateWorkStep(_ edited: AttemptWorkRequest.Step) {
        guard active, !editingLocked, let index = workSteps.firstIndex(where: { $0.id == edited.id }) else { return }
        let current = workSteps[index]
        // UI callbacks may outlive removal, reordering or acknowledgement. Resolve the ID
        // on every write and retain the source observation links owned by the draft.
        workDraft?.steps[index].text = edited.text
        workDraft?.steps[index].role = edited.role
        workDraft?.steps[index].confirmed = current.text == edited.text && current.role == edited.role ? edited.confirmed : false
        saveDraft()
    }
    func startStructured() {
        guard !editingLocked, context?.structuredWorkEnabled == true, let context else { return }
        let content = draft.isEmpty ? [] : lines
        var steps = content.map { AttemptWorkRequest.Step(id: UUID(), text: $0, role: .intermediate, observationIds: [], confirmed: true) }
        if answerMode, !finalAnswer.isEmpty { steps.append(.init(id: UUID(), text: finalAnswer, role: .finalAnswer, observationIds: [], confirmed: true)) }
        if steps.isEmpty { steps = [.init(id: UUID(), text: "", role: .intermediate, observationIds: [], confirmed: false)] }
        workDraft = AttemptWorkRequest(source: draftSource ?? context.source, engine: "MANUAL", observations: [], steps: steps)
        saveDraft()
    }
    func addWorkStep() {
        guard !editingLocked, workDraft?.engine == "MANUAL", workSteps.count < 12 else { return }
        workDraft?.steps.append(.init(id: UUID(), text: "", role: .intermediate, observationIds: [], confirmed: false)); saveDraft()
    }
    func removeWorkStep(_ id: UUID) {
        guard !editingLocked, workDraft?.engine == "MANUAL", workSteps.count > 1 else { return }
        workDraft?.steps.removeAll { $0.id == id }; saveDraft()
    }
    func moveWorkStep(_ id: UUID, by offset: Int) {
        guard !editingLocked, let index = workSteps.firstIndex(where: { $0.id == id }), workSteps.indices.contains(index + offset) else { return }
        workDraft?.steps.swapAt(index, index + offset); saveDraft()
    }
    func splitWorkStep(_ id: UUID) {
        guard !editingLocked, workSteps.count < 12, let index = workSteps.firstIndex(where: { $0.id == id }) else { return }
        let step = workSteps[index]
        // The learner supplies the split with a newline; geometry never guesses a mathematical split.
        let parts = step.text.split(separator: "\n", maxSplits: 1, omittingEmptySubsequences: false).map(String.init)
        guard parts.count == 2, parts.allSatisfy(AttemptSubmission.validText) else { errorKey = "attempt.work.splitHelp"; return }
        workDraft?.steps[index].text = parts[0]; workDraft?.steps[index].confirmed = false
        workDraft?.steps[index].role = .intermediate
        workDraft?.steps.insert(.init(id: UUID(), text: parts[1], role: step.role, observationIds: step.observationIds, confirmed: false), at: index + 1)
        saveDraft()
    }
    func mergeWorkStep(_ id: UUID) {
        guard !editingLocked, let index = workSteps.firstIndex(where: { $0.id == id }), workSteps.indices.contains(index + 1) else { return }
        let next = workSteps[index + 1], current = workSteps[index]
        let text = current.text + "\n" + next.text
        guard AttemptSubmission.validText(text) else { errorKey = "attempt.payloadTooLarge"; return }
        workDraft?.steps[index].text = text
        workDraft?.steps[index].observationIds = current.observationIds + next.observationIds.filter { !current.observationIds.contains($0) }
        workDraft?.steps[index].role = .ambiguous; workDraft?.steps[index].confirmed = false
        workDraft?.steps.remove(at: index + 1); saveDraft()
    }
    private func submitStructured() {
        guard canSubmit, let input = workDraft else { return }
        let operation = AttemptWorkPending(key: UUID().uuidString, input: input)
        run {
            try self.store.saveWork(operation, owner: self.owner, problem: self.problem); self.pendingWork = operation
            try await self.resolveWork(operation)
        }
    }
    private func resolveWork(_ operation: AttemptWorkPending) async throws {
        let expected = generation
        do {
            let work = try await service.submitWork(problem: problem, key: operation.key, input: operation.input)
            guard await authorized(), expected == generation else { return }
            // Production response ownership is checked independently of the authenticated transport.
            guard isFixture || work.ownerId == owner else { invalidate(); return }
            workResult = work; evaluation = nil; classification = nil; evaluationRealDomainConfirmed = false
            let updated = try await service.list(problem: problem)
            guard await authorized(), expected == generation else { return }
            history = updated; result = updated.first { $0.id == work.attemptId }
            try store.removeDraft(owner: owner, problem: problem)
            try store.removeWork(owner: owner, problem: problem)
            pendingWork = nil; acknowledgedWork = operation.input
            workDraft = nil; draft = ""; finalAnswer = ""; answerMode = false; captureReview = nil; workPreview = nil
            // Capture acknowledgement is durable. A subsequent evaluation GET failure must
            // not enter the submission-rejection handler or resurrect the accepted draft.
            do { try await loadEvaluation(work, generation: expected) }
            catch {
                guard await authorized(), expected == generation else { return }
                if case NetworkError.server(let failure) = error, [401, 403].contains(failure.status) { invalidate(); return }
                errorKey = "attempt.retry"
            }
        } catch NetworkError.server(let failure) where [400, 409, 413, 422].contains(failure.status) {
            guard await authorized(), expected == generation else { return }
            try store.saveDraft(.init(text: "", realDomainConfirmed: false, source: operation.input.source, structuredWork: operation.input), owner: owner, problem: problem)
            try store.removeWork(owner: owner, problem: problem); pendingWork = nil
            workDraft = operation.input; draftSource = operation.input.source; draftNeedsReview = true; context = nil
            errorKey = "attempt.review"
        }
    }
    private func loadWork(_ attempt: StudentAttempt, generation expected: Int) async throws {
        let work = try await service.readWork(problem: problem, attempt: attempt.id)
        guard await authorized(), expected == generation, result?.id == attempt.id else { return }
        if let work, !isFixture && work.ownerId != owner { invalidate(); return }
        workResult = work
        if let work { try await loadEvaluation(work, generation: expected) }
    }
    func reviseStructured() {
        guard !editingLocked, let work = workResult, context?.source == work.source, context?.structuredWorkEnabled == true else { return }
        workDraft = AttemptWorkRequest(source: work.source, engine: work.engine, captureId: work.captureId,
            assetSessionId: work.assetSessionId, recognitionJobId: work.recognitionJobId, supersedesAttemptId: work.attemptId,
            observations: work.observations, steps: work.steps.map {
                .init(id: $0.id, text: $0.reviewedText, role: $0.role, observationIds: $0.observationIds, confirmed: $0.reviewStatus == "CONFIRMED")
            })
        if let capture = work.captureId { workPreview = try? store.loadWorkPreview(owner: owner, problem: problem, capture: capture) }
        draftSource = work.source; draftNeedsReview = false; acknowledgedWork = nil; saveDraft()
    }
}

extension AnalyzeMyWorkViewModel {
    var canEvaluate: Bool {
        active && !busy && workResult != nil && evaluation?.creationEnabled == true && evaluation?.historical == false
            && evaluation?.status != .completed && evaluation?.status != .inProgress
            && (pendingEvaluation == nil || pendingEvaluation?.attemptId == workResult?.attemptId)
    }
    private func loadEvaluation(_ work: AttemptStructuredWork, generation expected: Int) async throws {
        let response = try await service.readEvaluation(problem: problem, attempt: work.attemptId)
        guard await authorized(), generation == expected, workResult?.attemptId == work.attemptId else { return }
        evaluation = try response.validated(work: work)
        if [.completed, .stale].contains(response.status), pendingEvaluation?.attemptId == work.attemptId {
            try store.removeEvaluation(owner: owner, problem: problem); pendingEvaluation = nil
        }
        try await loadClassification(generation: expected)
    }
    func evaluateWork() {
        guard canEvaluate, let work = workResult else { return }
        let operation = pendingEvaluation ?? AttemptEvaluationPending(attemptId: work.attemptId, key: UUID().uuidString, input: .init(work: work, realDomainConfirmed: evaluationRealDomainConfirmed))
        guard operation.input == AttemptEvaluationRequest(work: work, realDomainConfirmed: operation.input.realDomainConfirmed) else { errorKey = "attempt.review"; return }
        run {
            let expected = self.generation
            try self.store.saveEvaluation(operation, owner: self.owner, problem: self.problem); self.pendingEvaluation = operation
            do {
                let response = try await self.service.evaluate(problem: self.problem, attempt: work.attemptId, key: operation.key, input: operation.input)
                guard await self.authorized(), expected == self.generation, self.workResult?.attemptId == work.attemptId else { return }
                guard response.evaluation == nil || response.evaluation?.realDomainConfirmed == operation.input.realDomainConfirmed else { throw NetworkError.invalidResponse }
                self.evaluation = try response.validated(work: work)
                if [.completed, .stale].contains(response.status) {
                    try self.store.removeEvaluation(owner: self.owner, problem: self.problem); self.pendingEvaluation = nil
                }
                try await self.loadClassification(generation: expected)
                if response.status == .retryable { self.errorKey = "attempt.evaluation.RETRYABLE" }
            } catch NetworkError.server(let failure) where failure.code == "ATTEMPT_STALE_SOURCE" {
                guard await self.authorized(), expected == self.generation else { return }
                try self.store.removeEvaluation(owner: self.owner, problem: self.problem); self.pendingEvaluation = nil
                self.evaluation = nil; self.classification = nil; self.errorKey = "attempt.stale"
            }
        }
    }
}

extension AnalyzeMyWorkViewModel {
    var canClassify: Bool {
        active && !busy && evaluation?.status == .completed && evaluation?.historical == false
            && classification?.creationEnabled == true && classification?.status == .notClassified && classification?.historical == false
    }
    private func loadClassification(generation expected: Int) async throws {
        guard let evidence = evaluation?.evaluation else { classification = nil; return }
        let response = try await service.readClassification(problem: problem, attempt: evidence.attemptId)
        guard await authorized(), generation == expected, evaluation?.evaluation?.id == evidence.id else { return }
        classification = try response.validated(evaluation: evidence)
    }
    func classifyWork() {
        guard canClassify, let evidence = evaluation?.evaluation else { return }
        let input = AttemptMistakeRequest(evaluation: evidence)
        run {
            let expected = self.generation
            do {
                let response = try await self.service.classify(problem: self.problem, attempt: evidence.attemptId, key: input.retryKey, input: input)
                guard await self.authorized(), self.generation == expected, self.evaluation?.evaluation?.id == evidence.id else { return }
                self.classification = try response.validated(evaluation: evidence)
            } catch NetworkError.server(let failure) where failure.code == "ATTEMPT_STALE_SOURCE" {
                guard await self.authorized(), self.generation == expected, self.evaluation?.evaluation?.id == evidence.id else { return }
                self.classification = .init(schemaVersion: "attempt-mistake-view-v1", attemptId: evidence.attemptId,
                    status: .stale, historical: true, creationEnabled: false, classification: nil)
                self.errorKey = "attempt.stale"
            }
        }
    }
}
