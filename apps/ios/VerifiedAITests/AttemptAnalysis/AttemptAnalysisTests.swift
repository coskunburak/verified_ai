import XCTest
import SwiftUI
@testable import VerifiedAI

@MainActor
final class AttemptAnalysisTests: XCTestCase {
    private func store() -> AttemptAnalysisPendingStore {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        return AttemptAnalysisPendingStore(directory: directory)
    }
    private func idle(_ model: AnalyzeMyWorkViewModel) async {
        for _ in 0..<100 { if !model.busy { return }; try? await Task.sleep(for: .milliseconds(10)) }
        XCTFail("Operation did not settle")
    }
    func testManualSubmissionPreservesWorkAndDoesNotClaimCompletion() async throws {
        let model = AnalyzeMyWorkViewModel(owner: UUID(), problem: UUID(), service: FixtureAttemptAnalysisAPI(), store: store(), ownsAccount: { true })
        model.resume(); await idle(model); model.draft = "x=4"; XCTAssertTrue(model.canSubmit)
        model.submit(); await idle(model)
        XCTAssertEqual(model.result?.analysis?.outcome, .progress)
        XCTAssertEqual(model.result?.analysis?.steps.first?.verdict, .valid)
        XCTAssertEqual(model.draft, "x=4"); XCTAssertNil(model.pending)
        XCTAssertFalse(try XCTUnwrap(model.result).trainingEligible)
    }
    func testLostAcknowledgementRetryAndReopenUseExactIdentity() async throws {
        let owner = UUID(), problem = UUID(), pending = store()
        let api = FixtureAttemptAnalysisAPI(loseAcknowledgement: true)
        let first = AnalyzeMyWorkViewModel(owner: owner, problem: problem, service: api, store: pending, ownsAccount: { true })
        first.resume(); await idle(first); first.draft = "x=5"; first.submit(); await idle(first)
        let key = try XCTUnwrap(first.pending?.key)
        XCTAssertEqual(first.draft, "x=5"); first.suspend()
        let reopened = AnalyzeMyWorkViewModel(owner: owner, problem: problem, service: api, store: pending, ownsAccount: { true })
        reopened.resume(); await idle(reopened); XCTAssertEqual(reopened.pending?.key, key)
        reopened.retry(); await idle(reopened)
        XCTAssertNil(reopened.pending)
        XCTAssertEqual(reopened.result?.analysis?.firstWrongStepIndex, 1)
        XCTAssertEqual(reopened.result?.analysis?.steps.first?.counterexample?.witnessType, "EQUATION_TRUTH")
        let history = try await api.list(problem: problem); XCTAssertEqual(history.count, 1)
    }
    func testUnknownMathRemainsUncertainWithoutFalseFirstError() async {
        let model = AnalyzeMyWorkViewModel(owner: UUID(), problem: UUID(), service: FixtureAttemptAnalysisAPI(), store: store(), ownsAccount: { true })
        model.resume(); await idle(model); model.draft = "sqrt(x)=-2\nx=4"; model.submit(); await idle(model)
        XCTAssertNil(model.result?.analysis?.firstWrongStepIndex)
        XCTAssertEqual(model.result?.analysis?.steps.map(\.verdict), [.uncertain, .notAssessed])
    }
    func testAccountSwitchAndCancellationClearPresentation() async {
        var authorized = true
        let model = AnalyzeMyWorkViewModel(owner: UUID(), problem: UUID(), service: FixtureAttemptAnalysisAPI(), store: store(), ownsAccount: { authorized })
        model.resume(); await idle(model); model.draft = "x=4"
        authorized = false; model.submit(); await idle(model)
        XCTAssertNil(model.result); XCTAssertTrue(model.draft.isEmpty); XCTAssertNil(model.context)
        model.suspend(); XCTAssertFalse(model.busy)
    }
    func testSerializationAndOwnerIsolation() async throws {
        let api = FixtureAttemptAnalysisAPI(), problem = UUID(), owner = UUID(), pending = store()
        let context = try await api.context(problem: problem)
        let body = AttemptSubmission(source: context.source, steps: ["x=4"])
        XCTAssertEqual(try JSONDecoder().decode(AttemptSubmission.self, from: JSONEncoder().encode(body)), body)
        let operation = AttemptPending(key: "synthetic-key", submission: body)
        try pending.save(operation, owner: owner, problem: problem)
        XCTAssertEqual(try pending.load(owner: owner, problem: problem), operation)
        XCTAssertNil(try pending.load(owner: UUID(), problem: problem))
        try pending.clearAll(); XCTAssertNil(try pending.load(owner: owner, problem: problem))
    }
    func testDisabledPhotoAndSubmissionRemainHonest() async throws {
        let api = FixtureAttemptAnalysisAPI(enabled: false), problem = UUID()
        let context = try await api.context(problem: problem)
        XCTAssertEqual(context.handwriting, "UNAVAILABLE"); XCTAssertEqual(context.twinDrill, "UNAVAILABLE")
        let model = AnalyzeMyWorkViewModel(owner: UUID(), problem: problem, service: api, store: store(), ownsAccount: { true })
        model.resume(); await idle(model); model.draft = "x=4"; XCTAssertFalse(model.canSubmit)
    }
    func testDiagnosisDecodingValidatesIndices() async throws {
        let api = FixtureAttemptAnalysisAPI(), problem = UUID()
        let context = try await api.context(problem: problem)
        let result = try await api.submit(problem: problem, key: "decode-attempt", submission: .init(source: context.source, steps: ["x=5"]))
        let encoded = try JSONEncoder().encode(result)
        let decoded = try JSONDecoder().decode(StudentAttempt.self, from: encoded).validated(problem: problem)
        XCTAssertEqual(decoded.analysis?.firstWrongStepIndex, 1)
        var document = try XCTUnwrap(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
        var analysis = try XCTUnwrap(document["analysis"] as? [String: Any]); analysis["firstWrongStepIndex"] = 0
        document["analysis"] = analysis
        let forged = try JSONDecoder().decode(StudentAttempt.self, from: JSONSerialization.data(withJSONObject: document))
        XCTAssertThrowsError(try forged.validated(problem: problem))
    }
    func testOfflineFailurePreservesExactPendingSubmission() async throws {
        let model = AnalyzeMyWorkViewModel(owner: UUID(), problem: UUID(), service: InterruptedAttemptAPI(offline: true), store: store(), ownsAccount: { true })
        model.resume(); await idle(model); model.draft = "x=4"; model.submit(); await idle(model)
        let pending = try XCTUnwrap(model.pending)
        XCTAssertEqual(pending.submission.steps, ["x=4"]); XCTAssertEqual(model.draft, "x=4")
        model.retry(); await idle(model); XCTAssertEqual(model.pending?.key, pending.key)
    }
    func testCancelledLateResponseCannotPublish() async throws {
        let model = AnalyzeMyWorkViewModel(owner: UUID(), problem: UUID(), service: InterruptedAttemptAPI(offline: false), store: store(), ownsAccount: { true })
        model.resume(); await idle(model); model.draft = "x=4"; model.submit()
        try await Task.sleep(for: .milliseconds(30)); model.suspend()
        try await Task.sleep(for: .milliseconds(200))
        XCTAssertNil(model.result); XCTAssertEqual(model.draft, "x=4"); XCTAssertNotNil(model.pending)
    }
    func testVerdictsHaveNonColorSemanticsAndLocalizedKeys() {
        for verdict in [AttemptStepVerdict.valid, .wrong, .uncertain, .notAssessed] {
            XCTAssertFalse(verdict.symbol.isEmpty)
            XCTAssertNotEqual(NSLocalizedString(verdict.titleKey, comment: ""), verdict.titleKey)
        }
    }
    func testContextConflictPreservesPendingSubmissionForExactRetry() async throws {
        let owner = UUID(), problem = UUID(), pending = store()
        let source = try await FixtureAttemptAnalysisAPI().context(problem: problem).source
        let operation = AttemptPending(key: "synthetic-retry-key", submission: .init(source: source, steps: ["x=4"]))
        try pending.save(operation, owner: owner, problem: problem)
        let model = AnalyzeMyWorkViewModel(owner: owner, problem: problem, service: ContextConflictAttemptAPI(), store: pending, ownsAccount: { true })
        model.resume(); await idle(model)
        XCTAssertEqual(model.pending, operation)
        XCTAssertEqual(try pending.load(owner: owner, problem: problem), operation)
        XCTAssertEqual(model.draft, "x=4")
    }
    func testPendingStoreRejectsBlankAndOversizedLearnerLines() async throws {
        let problem = UUID(), owner = UUID()
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        addTeardownBlock { try? FileManager.default.removeItem(at: directory) }
        let pending = AttemptAnalysisPendingStore(directory: directory)
        let source = try await FixtureAttemptAnalysisAPI().context(problem: problem).source
        for text in ["   ", String(repeating: "a", count: 257), String(repeating: "😀", count: 129)] {
            let operation = AttemptPending(key: "synthetic-bounds", submission: .init(source: source, steps: [text]))
            XCTAssertThrowsError(try pending.save(operation, owner: owner, problem: problem))
            XCTAssertNil(try pending.load(owner: owner, problem: problem))
        }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data(repeating: 32, count: 16385).write(to: directory.appendingPathComponent("\(owner)_\(problem).json"))
        XCTAssertThrowsError(try pending.load(owner: owner, problem: problem))
    }
    func testMalformedWitnessAndUnknownReasonAreRejected() async throws {
        let api = FixtureAttemptAnalysisAPI(), problem = UUID()
        let source = try await api.context(problem: problem).source
        let original = try await api.submit(problem: problem, key: "synthetic-malformed", submission: .init(source: source, steps: ["x=5"]))
        let encoded = try JSONEncoder().encode(original)
        for mutation in ["reason", "witnessType", "truth", "status", "normalization"] {
            var document = try XCTUnwrap(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
            var analysis = try XCTUnwrap(document["analysis"] as? [String: Any])
            var steps = try XCTUnwrap(analysis["steps"] as? [[String: Any]])
            var witness = try XCTUnwrap(steps[0]["counterexample"] as? [String: Any])
            switch mutation {
            case "reason": steps[0]["reasonCode"] = "EXACT_IDENTITY"
            case "witnessType": witness["witnessType"] = "UNKNOWN"
            case "truth": witness["previousStepEvaluation"] = "1"
            case "status": steps[0]["counterexampleStatus"] = "UNKNOWN"
            default: document["normalizationVersion"] = "unreviewed-version"
            }
            steps[0]["counterexample"] = witness; analysis["steps"] = steps; document["analysis"] = analysis
            let forged = try JSONDecoder().decode(StudentAttempt.self, from: JSONSerialization.data(withJSONObject: document))
            XCTAssertThrowsError(try forged.validated(problem: problem), mutation)
        }
    }
    func testAnswerOnlyIsSavedWithoutAssessmentAndDoesNotLeavePendingOrDraft() async throws {
        let owner = UUID(), problem = UUID(), pending = store(), api = FixtureAttemptAnalysisAPI()
        let model = AnalyzeMyWorkViewModel(owner: owner, problem: problem, service: api, store: pending, ownsAccount: { true })
        model.resume(); await idle(model); model.answerMode = true; model.finalAnswer = "  x = 4  "
        model.saveDraft(); XCTAssertTrue(model.canSubmit)
        model.submit(); await idle(model)
        let result = try XCTUnwrap(model.result)
        XCTAssertEqual(result.finalAnswer, "  x = 4  "); XCTAssertEqual(result.status, .submitted)
        XCTAssertTrue(result.steps.isEmpty); XCTAssertNil(result.analysis); XCTAssertNil(model.pending)
        XCTAssertEqual(model.history.count, 1); XCTAssertNil(model.errorKey)
        _ = try result.validated(problem: problem)
        model.suspend(); XCTAssertNil(try pending.loadDraft(owner: owner, problem: problem))
        let reopened = AnalyzeMyWorkViewModel(owner: owner, problem: problem, service: api, store: pending, ownsAccount: { true })
        reopened.resume(); await idle(reopened)
        XCTAssertEqual(reopened.history.first?.finalAnswer, "  x = 4  "); XCTAssertTrue(reopened.finalAnswer.isEmpty)
    }
    func testAnswerLostAcknowledgementReusesKeyAcrossRelaunch() async throws {
        let owner = UUID(), problem = UUID(), pending = store(), api = FixtureAttemptAnalysisAPI(loseAcknowledgement: true)
        let model = AnalyzeMyWorkViewModel(owner: owner, problem: problem, service: api, store: pending, ownsAccount: { true })
        model.resume(); await idle(model); model.answerMode = true; model.finalAnswer = "4"; model.draft = "2*x=8"
        model.submit(); await idle(model); let operation = try XCTUnwrap(model.pending); model.suspend()
        let reopened = AnalyzeMyWorkViewModel(owner: owner, problem: problem, service: api, store: pending, ownsAccount: { true })
        reopened.resume(); await idle(reopened); XCTAssertEqual(reopened.pending, operation)
        XCTAssertTrue(reopened.answerMode); XCTAssertEqual(reopened.finalAnswer, "4")
        reopened.retry(); await idle(reopened); XCTAssertNil(reopened.pending)
        XCTAssertEqual(reopened.history.count, 1); XCTAssertNil(reopened.result?.analysis)
    }
    func testDraftSourceChangeRequiresReviewWithoutDiscardingOriginalText() async throws {
        let owner = UUID(), problem = UUID(), pending = store()
        let stale = AttemptSourceIdentity(canonicalId: UUID(), canonicalRevision: 1, parseId: UUID(), parseRevision: 1)
        try pending.saveDraft(.init(text: "2*x=8", realDomainConfirmed: false, source: stale, finalAnswer: "4"), owner: owner, problem: problem)
        let model = AnalyzeMyWorkViewModel(owner: owner, problem: problem, service: FixtureAttemptAnalysisAPI(), store: pending, ownsAccount: { true })
        model.resume(); await idle(model)
        XCTAssertTrue(model.draftNeedsReview); XCTAssertFalse(model.canSubmit); XCTAssertEqual(model.finalAnswer, "4")
        model.confirmDraftSource(); XCTAssertTrue(model.canSubmit); XCTAssertFalse(model.draftNeedsReview)
        XCTAssertEqual(try pending.loadDraft(owner: owner, problem: problem)?.source, model.context?.source)
    }
    func testAnswerVersionAndForgeryAreRejected() async throws {
        let api = FixtureAttemptAnalysisAPI(), problem = UUID(), pending = store(), owner = UUID()
        let source = try await api.context(problem: problem).source
        let valid = AttemptSubmission(source: source, steps: [], schemaVersion: "attempt-submission-v3", finalAnswer: "4")
        try pending.save(.init(key: "answer-pending-key", submission: valid), owner: owner, problem: problem)
        XCTAssertEqual(try pending.load(owner: owner, problem: problem)?.submission, valid)
        var invalid = valid; invalid.schemaVersion = nil
        XCTAssertFalse(invalid.isValid)
        invalid = valid; invalid.finalAnswer = String(repeating: "😀", count: 129); XCTAssertFalse(invalid.isValid)
        let saved = try await api.submit(problem: problem, key: "answer-test-key", submission: valid)
        var raw = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(saved)) as? [String: Any])
        raw["schemaVersion"] = "student-attempt-v1"
        let forged = try JSONDecoder().decode(StudentAttempt.self, from: JSONSerialization.data(withJSONObject: raw))
        XCTAssertThrowsError(try forged.validated(problem: problem))
    }
    func testAccountSwitchClearsAnswerAndOwnerDraftCannotLeak() async throws {
        let owner = UUID(), problem = UUID(), pending = store(); var authorized = true
        let model = AnalyzeMyWorkViewModel(owner: owner, problem: problem, service: FixtureAttemptAnalysisAPI(), store: pending, ownsAccount: { authorized })
        model.resume(); await idle(model); model.answerMode = true; model.finalAnswer = "4"; model.saveDraft()
        XCTAssertNil(try pending.loadDraft(owner: UUID(), problem: problem))
        authorized = false; model.submit(); await idle(model)
        XCTAssertTrue(model.finalAnswer.isEmpty); XCTAssertNil(model.result); XCTAssertTrue(model.history.isEmpty)
    }
    func testRecoverableServerFailureRetainsAnswerAndExactKey() async throws {
        let api = RetryableAnswerAPI()
        let model = AnalyzeMyWorkViewModel(owner: UUID(), problem: UUID(), service: api, store: store(), ownsAccount: { true })
        model.resume(); await idle(model); model.answerMode = true; model.finalAnswer = "4"
        model.submit(); await idle(model)
        let key = try XCTUnwrap(model.pending?.key)
        XCTAssertEqual(model.pending?.submission.finalAnswer, "4"); XCTAssertNil(model.result)
        model.retry(); await idle(model)
        let keys = await api.keys
        XCTAssertEqual(keys, [key, key]); XCTAssertNil(model.pending); XCTAssertEqual(model.result?.finalAnswer, "4")
    }
    func testDefinitiveAnswerRejectionRetainsSourceBoundDraft() async throws {
        let owner = UUID(), problem = UUID(), pending = store()
        let model = AnalyzeMyWorkViewModel(owner: owner, problem: problem, service: RetryableAnswerAPI(reject: true), store: pending, ownsAccount: { true })
        model.resume(); await idle(model); let source = model.context?.source
        model.answerMode = true; model.finalAnswer = " 4 "; model.draft = "2*x=8"
        model.submit(); await idle(model)
        XCTAssertNil(model.pending); XCTAssertNil(model.context)
        let draft = try XCTUnwrap(pending.loadDraft(owner: owner, problem: problem))
        XCTAssertEqual(draft.finalAnswer, " 4 "); XCTAssertEqual(draft.text, "2*x=8"); XCTAssertEqual(draft.source, source)
    }
    func testSharedFinalAnswerFixtureMatchesClientContract() throws {
        let url = try XCTUnwrap(Bundle.main.url(forResource: "student-attempt-v2", withExtension: "json"))
        let decoder = JSONDecoder(); decoder.dateDecodingStrategy = .iso8601
        let value = try decoder.decode(StudentAttempt.self, from: Data(contentsOf: url))
        _ = try value.validated(problem: value.problemSessionId)
        XCTAssertEqual(value.finalAnswer, "  x = 4  "); XCTAssertNil(value.analysis); XCTAssertTrue(value.steps.isEmpty)
    }
    func testAnswerAndRecoveryEnglishTurkishCopyExists() throws {
        for language in ["en", "tr"] {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"))
            let bundle = try XCTUnwrap(Bundle(path: path))
            for key in ["attempt.answer.saved", "attempt.answer.scope", "attempt.answer.review", "attempt.draft.sourceChanged", "attempt.draft.confirmSource"] {
                XCTAssertNotEqual(bundle.localizedString(forKey: key, value: nil, table: nil), key)
            }
        }
    }

}

private struct ContextConflictAttemptAPI: AttemptAnalysisServicing {
    func context(problem: UUID) async throws -> AttemptContext {
        throw NetworkError.server(problem: ProblemDetails(type: "about:blank", title: "Source changed", status: 409,
            code: "SOLVE_SOURCE_NOT_READY", traceId: nil, details: nil))
    }
    func list(problem: UUID) async throws -> [StudentAttempt] { [] }
    func submit(problem: UUID, key: String, submission: AttemptSubmission) async throws -> StudentAttempt {
        throw URLError(.notConnectedToInternet)
    }
}

private struct InterruptedAttemptAPI: AttemptAnalysisServicing {
    let offline: Bool
    private let backing = FixtureAttemptAnalysisAPI()
    init(offline: Bool) { self.offline = offline }
    func context(problem: UUID) async throws -> AttemptContext { try await backing.context(problem: problem) }
    func list(problem: UUID) async throws -> [StudentAttempt] { try await backing.list(problem: problem) }
    func submit(problem: UUID, key: String, submission: AttemptSubmission) async throws -> StudentAttempt {
        if offline { throw URLError(.notConnectedToInternet) }
        // Intentionally returns despite cancellation to exercise the UI generation fence.
        try? await Task.sleep(for: .milliseconds(150))
        return try await backing.submit(problem: problem, key: key, submission: submission)
    }
}

final class AttemptProductContractTests: XCTestCase {
    func testNativeMathUsesStructuredFractionsAndRejectsInjectedTokens() throws {
        let data = Data(#"{"kind":"BINARY","operator":"DIVIDE","left":{"kind":"NUMBER","numericType":"INTEGER","value":"1"},"right":{"kind":"NUMBER","numericType":"INTEGER","value":"2"}}"#.utf8)
        let node = try JSONDecoder().decode(AttemptJSON.self, from: data)
        XCTAssertEqual(AttemptMathPresentation.latex(node), #"\frac{1}{2}"#)
        let malicious = AttemptJSON.object(["kind": .string("VARIABLE"), "symbol": .string(#"x\input{file}"#)])
        XCTAssertNil(AttemptMathPresentation.latex(malicious))
    }
    func testTwinNeverAcceptsAnAnswerLeakedBeforeCompletion() throws {
        let id = UUID(), attempt = UUID()
        let node = AttemptJSON.object(["kind": .string("EXPRESSION"), "expression": .object(["kind": .string("NUMBER"), "value": .string("1"), "numericType": .string("INTEGER")])])
        let value = CounterfactualTwin(schemaVersion: "counterfactual-twin-v1", id: id, attemptId: attempt,
            problem: node, options: [node,node,node,node], domainLabel: "real", difficulty: "FOUNDATION", status: "OPEN",
            selectedOption: nil, correct: nil, correctOption: 1, explanationKey: nil, assistance: nil, createdAt: Date(), completedAt: nil, trainingEligible: false)
        XCTAssertThrowsError(try value.validated(attempt: attempt))
    }
    @MainActor func testReviewedDraftIsAccountIsolatedAndSurvivesRelaunch() throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: directory) }
        let owner = UUID(), problem = UUID()
        let store = AttemptAnalysisPendingStore(directory: directory)
        try store.saveDraft(.init(text: "x = 4", realDomainConfirmed: true), owner: owner, problem: problem)
        let reopened = AttemptAnalysisPendingStore(directory: directory)
        XCTAssertEqual(try reopened.loadDraft(owner: owner, problem: problem)?.text, "x = 4")
        XCTAssertNil(try reopened.loadDraft(owner: UUID(), problem: problem))
        try reopened.clearAll()
        XCTAssertNil(try reopened.loadDraft(owner: owner, problem: problem))
    }
}


@MainActor
final class MethodComparisonTests: XCTestCase {
    func testAllFamiliesRenderAndCompactKeepsOutcome() throws {
        let f = try FixtureMethodComparisonAPI.fixture()
        _ = try f.validated(problem: f.problemId, attempt: nil, expected: f.source)
        XCTAssertEqual(Set(f.evidence.methods.map(\.id)), Set(["FACTORING", "COMPLETING_SQUARE", "QUADRATIC_FORMULA"]))
        for method in f.evidence.methods {
            XCTAssertEqual(method.indices(compact: true).last, method.steps.count - 1)
            XCTAssertTrue(method.steps.allSatisfy { AttemptMathPresentation.latex($0) != nil })
            XCTAssertTrue(method.steps.allSatisfy { !AttemptMathPresentation.spoken($0).isEmpty })
        }
    }
    func testHistoricalV1AndMalformedV2() throws {
        let legacy = Data(#"{"schemaVersion":"attempt-learning-result-v1","verifierVersion":"bounded-learning-v1","action":"METHODS","status":"VERIFIED","methods":[{"methodId":"SYMBOLIC_SIMPLIFICATION","evidenceVersion":"verified-method-v1","steps":[{"kind":"EXPRESSION","expression":{"kind":"VARIABLE","symbol":"x"}}]}]}"#.utf8)
        let v1 = try JSONDecoder().decode(AttemptAlternativeMethods.self, from: legacy).validated()
        XCTAssertEqual(v1.methods[0].indices(compact: true), [0])
        var raw = try JSONSerialization.jsonObject(with: FixtureMethodComparisonAPI.fixtureData()) as! [String: Any]
        raw["trainingEligible"] = true
        let bad = try JSONDecoder().decode(MethodComparison.self, from: JSONSerialization.data(withJSONObject: raw))
        XCTAssertThrowsError(try bad.validated(problem: bad.problemId, attempt: nil, expected: bad.source))
    }
    func testUnsupportedEmptyAndSingleMethodRemainTruthful() async throws {
        let f = try FixtureMethodComparisonAPI.fixture()
        for status in ["UNSUPPORTED", "NO_ALTERNATIVE"] {
            let r = try await FixtureMethodComparisonAPI(status: status).comparison(problem: f.problemId, attempt: nil, source: f.source)
            XCTAssertEqual(r.evidence.status, status); XCTAssertTrue(r.evidence.methods.isEmpty)
        }
        let one = try await FixtureMethodComparisonAPI(single: true).comparison(problem: f.problemId, attempt: nil, source: f.source)
        XCTAssertEqual(one.evidence.methods.count, 1)
    }
    private func settle(_ model: AttemptLearningViewModel) async {
        for _ in 0..<200 where model.busy { await Task.yield(); try? await Task.sleep(nanoseconds: 1_000_000) }
        XCTAssertFalse(model.busy)
    }
    func testNetworkRetryAndDuplicateTapFence() async throws {
        let f = try FixtureMethodComparisonAPI.fixture()
        let model = AttemptLearningViewModel(api: FixtureMethodComparisonAPI(failFirst: true), owner: UUID(), problem: f.problemId, ownsAccount: { true })
        model.sourceAlternatives(); model.sourceAlternatives()
        await settle(model)
        XCTAssertNil(model.sourceMethods); XCTAssertNotNil(model.methodErrorKey)
        model.sourceAlternatives(); await settle(model)
        XCTAssertNotNil(model.sourceMethods); XCTAssertNil(model.methodErrorKey)
    }
    func testAccountSwitchAndSuspensionDiscardDelayedResponses() async throws {
        let f = try FixtureMethodComparisonAPI.fixture()
        var owns = true
        let model = AttemptLearningViewModel(api: FixtureMethodComparisonAPI(delay: 20_000_000), owner: UUID(), problem: f.problemId, ownsAccount: { owns })
        model.sourceAlternatives(); await Task.yield(); owns = false
        await settle(model); XCTAssertNil(model.sourceMethods)
        owns = true; model.sourceAlternatives(); model.suspend()
        try await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertNil(model.sourceMethods); XCTAssertFalse(model.busy)
    }
    func testEnglishAndTurkishComparisonCopyExists() throws {
        for language in ["en", "tr"] {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"))
            let bundle = try XCTUnwrap(Bundle(path: path))
            for key in ["attempt.methods.scope", "attempt.methods.learnerSeparate", "attempt.methods.SOURCE_RESTRICTIONS", "attempt.methods.exactEvidence",
                        "attempt.methods.NATURAL_DOMAIN_EXCLUSIONS", "attempt.methods.learnerInvalid", "attempt.methods.learnerVerified"] {
                XCTAssertNotEqual(bundle.localizedString(forKey: key, value: nil, table: nil), key)
            }
        }
    }
    func testMethodComparisonFailureClassificationAndRecovery() {
        XCTAssertEqual(MethodComparisonFailure.classify(NetworkError.transport("offline")), .network)
        XCTAssertTrue(MethodComparisonFailure.network.recoverable)
        XCTAssertEqual(MethodComparisonFailure.classify(NetworkError.httpStatus(409)), .stale)
        XCTAssertTrue(MethodComparisonFailure.stale.recoverable)
        XCTAssertEqual(MethodComparisonFailure.classify(NetworkError.httpStatus(429)), .rateLimit)
        XCTAssertTrue(MethodComparisonFailure.rateLimit.recoverable)
        XCTAssertEqual(MethodComparisonFailure.classify(NetworkError.httpStatus(503)), .unavailable)
        XCTAssertTrue(MethodComparisonFailure.unavailable.recoverable)
        XCTAssertEqual(MethodComparisonFailure.classify(NetworkError.httpStatus(401)), .authorization)
        XCTAssertFalse(MethodComparisonFailure.authorization.recoverable)
        XCTAssertEqual(MethodComparisonFailure.classify(NetworkError.httpStatus(422)), .request)
        XCTAssertFalse(MethodComparisonFailure.request.recoverable)
    }
}

private actor RetryableAnswerAPI: AttemptAnalysisServicing {
    private let backing = FixtureAttemptAnalysisAPI()
    private let reject: Bool
    private(set) var keys: [String] = []
    init(reject: Bool = false) { self.reject = reject }
    func context(problem: UUID) async throws -> AttemptContext { try await backing.context(problem: problem) }
    func list(problem: UUID) async throws -> [StudentAttempt] { try await backing.list(problem: problem) }
    func submit(problem: UUID, key: String, submission: AttemptSubmission) async throws -> StudentAttempt {
        keys.append(key)
        if keys.count == 1 || reject {
            throw NetworkError.server(problem: ProblemDetails(type: "about:blank", title: "Synthetic failure", status: reject ? 409 : 503,
                code: reject ? "ATTEMPT_STALE_SOURCE" : "TEMPORARY_UNAVAILABLE", traceId: nil, details: nil))
        }
        return try await backing.submit(problem: problem, key: key, submission: submission)
    }
}

@MainActor
final class AttemptStructuredWorkTests: XCTestCase {
    private func store() -> AttemptAnalysisPendingStore {
        AttemptAnalysisPendingStore(directory: FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString))
    }
    private func idle(_ model: AnalyzeMyWorkViewModel) async {
        for _ in 0..<150 { if !model.busy { return }; try? await Task.sleep(nanoseconds: 10_000_000) }
        XCTFail("Structured work operation did not finish")
    }
    func testRowBindingSurvivesAcknowledgementWithoutResurrectingDraftOrEditingSuccessor() async throws {
        let storage = store(), owner = UUID(), problem = UUID()
        let model = AnalyzeMyWorkViewModel(owner: owner, problem: problem, service: FixtureAttemptAnalysisAPI(), store: storage, ownsAccount: { true })
        model.resume(); await idle(model); model.draft = "x=4"; model.startStructured()
        let snapshot = try XCTUnwrap(model.workSteps.first)
        let binding = AttemptStructuredEditorView(model: model).stepBinding(snapshot)
        model.submit()
        binding.text.wrappedValue = "late edit during submit"
        XCTAssertEqual(model.workSteps.first?.text, "x=4")
        await idle(model)
        XCTAssertNotNil(model.workResult); XCTAssertNil(model.workDraft)
        // This is the Toggle/TextEditor read that previously subscripted an empty array.
        XCTAssertEqual(binding.wrappedValue, snapshot)
        binding.confirmed.wrappedValue = false
        XCTAssertNil(model.workDraft); XCTAssertNil(try storage.loadDraft(owner: owner, problem: problem))
        model.reviseStructured()
        XCTAssertEqual(model.workSteps.first?.id, snapshot.id)
        binding.text.wrappedValue = "retired row callback"
        XCTAssertEqual(model.workSteps.first?.text, "x=4")
        model.invalidate()
        XCTAssertEqual(binding.wrappedValue, snapshot)
        binding.text.wrappedValue = "after logout"
        XCTAssertNil(model.workDraft)
    }
    func testRowBindingsFollowIdentityAcrossReorderAndIgnoreRemovedRows() async throws {
        let model = AnalyzeMyWorkViewModel(owner: UUID(), problem: UUID(), service: FixtureAttemptAnalysisAPI(), store: store(), ownsAccount: { true })
        model.resume(); await idle(model); model.draft = "2*x=8\nx=4"; model.startStructured()
        let first = model.workSteps[0], second = model.workSteps[1]
        let binding = AttemptStructuredEditorView(model: model).stepBinding(first)
        model.moveWorkStep(first.id, by: 1)
        binding.text.wrappedValue = "x=5"
        XCTAssertEqual(model.workSteps.map(\.id), [second.id, first.id])
        XCTAssertEqual(model.workSteps.map(\.text), ["x=4", "x=5"])
        XCTAssertFalse(binding.confirmed.wrappedValue)
        binding.confirmed.wrappedValue = true
        XCTAssertTrue(binding.confirmed.wrappedValue)
        binding.role.wrappedValue = .finalAnswer
        XCTAssertFalse(binding.confirmed.wrappedValue)
        model.removeWorkStep(first.id)
        XCTAssertEqual(binding.wrappedValue, first)
        binding.text.wrappedValue = "removed row callback"
        XCTAssertEqual(model.workSteps, [second])
    }
    func testManualMultiStepFinalIntentAndNoMathematicalAssessment() async throws {
        let model = AnalyzeMyWorkViewModel(owner: UUID(), problem: UUID(), service: FixtureAttemptAnalysisAPI(), store: store(), ownsAccount: { true })
        model.resume(); await idle(model); model.draft = "2*x=8\nx=4"; model.startStructured()
        model.workDraft?.steps[1].role = .finalAnswer
        XCTAssertTrue(model.canSubmit); model.submit(); await idle(model)
        XCTAssertNil(model.pendingWork); XCTAssertNil(model.workDraft); XCTAssertNil(model.result?.analysis)
        XCTAssertEqual(model.result?.finalAnswer, "x=4")
        XCTAssertEqual(model.workResult?.steps.map(\.reviewedText), ["2*x=8", "x=4"])
        XCTAssertEqual(model.workResult?.steps.map(\.normalizationStatus), ["UNSUPPORTED", "UNSUPPORTED"])
        XCTAssertFalse(try XCTUnwrap(model.workResult).trainingEligible)
    }
    func testNativeCorrectionReorderMergeSplitPreservesAllOriginalObservations() async throws {
        let model = AnalyzeMyWorkViewModel(owner: UUID(), problem: UUID(), service: FixtureAttemptAnalysisAPI(), store: store(), ownsAccount: { true })
        model.resume(); await idle(model)
        let source = try XCTUnwrap(model.context?.source), a = UUID(), b = UUID()
        model.workDraft = AttemptWorkRequest(source: source, engine: "NATIVE_VISION", captureId: UUID(),
            observations: [.init(id: a, rawText: "1/l", confidence: 0.1, box: nil), .init(id: b, rawText: "0/O", confidence: 0.2, box: nil)],
            steps: [.init(id: UUID(), text: "x=1", role: .intermediate, observationIds: [a], confirmed: false),
                    .init(id: UUID(), text: "x=0", role: .intermediate, observationIds: [b], confirmed: false)])
        let second = model.workSteps[1].id
        model.moveWorkStep(second, by: -1); XCTAssertEqual(model.workSteps.first?.id, second)
        model.mergeWorkStep(second); XCTAssertEqual(model.workSteps.count, 1)
        XCTAssertEqual(Set(model.workSteps[0].observationIds), Set([a, b])); XCTAssertEqual(model.workSteps[0].role, .ambiguous)
        model.splitWorkStep(second); XCTAssertEqual(model.workSteps.count, 2)
        XCTAssertTrue(model.workDraft?.isValid == true); model.submit(); await idle(model)
        XCTAssertEqual(model.workResult?.observations.map(\.rawText), ["1/l", "0/O"])
        XCTAssertTrue(model.workResult?.steps.allSatisfy { $0.normalizedStatement == nil && $0.reviewStatus == "REQUIRED" } == true)
    }
    func testInterruptedSubmissionUsesSameKeyAndOwnerScopedPending() async throws {
        let owner = UUID(), problem = UUID(), storage = store(), api = FixtureAttemptAnalysisAPI(loseAcknowledgement: true)
        let first = AnalyzeMyWorkViewModel(owner: owner, problem: problem, service: api, store: storage, ownsAccount: { true })
        first.resume(); await idle(first); first.draft = "x=4"; first.startStructured(); first.submit(); await idle(first)
        let pending = try XCTUnwrap(first.pendingWork)
        XCTAssertNil(try storage.loadWork(owner: UUID(), problem: problem)); first.suspend()
        let second = AnalyzeMyWorkViewModel(owner: owner, problem: problem, service: api, store: storage, ownsAccount: { true })
        second.resume(); await idle(second); XCTAssertEqual(second.pendingWork, pending)
        second.retry(); await idle(second)
        XCTAssertNil(second.pendingWork); XCTAssertNil(try storage.loadWork(owner: owner, problem: problem))
        let history = try await api.list(problem: problem); XCTAssertEqual(history.count, 1)
    }
    func testSourceBoundDraftRequiresReviewAndAccountSwitchClearsStructuredPresentation() async throws {
        let owner = UUID(), problem = UUID(), storage = store(), api = FixtureAttemptAnalysisAPI()
        let context = try await api.context(problem: problem)
        let source = AttemptSourceIdentity(canonicalId: context.source.canonicalId, canonicalRevision: 99, parseId: context.source.parseId, parseRevision: 99)
        let input = AttemptWorkRequest(source: source, engine: "MANUAL", observations: [], steps: [.init(id: UUID(), text: "4", role: .finalAnswer, observationIds: [], confirmed: true)])
        try storage.saveDraft(.init(text: "", realDomainConfirmed: false, source: source, structuredWork: input), owner: owner, problem: problem)
        var authorized = true
        let model = AnalyzeMyWorkViewModel(owner: owner, problem: problem, service: api, store: storage, ownsAccount: { authorized })
        model.resume(); await idle(model); XCTAssertTrue(model.draftNeedsReview); XCTAssertFalse(model.canSubmit)
        model.confirmDraftSource(); XCTAssertTrue(model.canSubmit); XCTAssertEqual(model.workDraft?.source, context.source)
        authorized = false; model.submit(); await idle(model)
        XCTAssertNil(model.workDraft); XCTAssertNil(model.workResult); XCTAssertNil(model.pendingWork)
    }
    func testBoundsNullEncodingAndNoForgedProvenance() async throws {
        let context = try await FixtureAttemptAnalysisAPI().context(problem: UUID())
        var input = AttemptWorkRequest(source: context.source, engine: "MANUAL", observations: [], steps: [.init(id: UUID(), text: "4", role: .finalAnswer, observationIds: [], confirmed: true)])
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: JSONEncoder().encode(input)) as? [String: Any])
        XCTAssertTrue(json["captureId"] is NSNull); XCTAssertTrue(json["recognitionJobId"] is NSNull); XCTAssertNil(json["provider"])
        input.steps = (0..<12).map { .init(id: UUID(), text: "x=\($0)", role: .intermediate, observationIds: [], confirmed: true) }
        XCTAssertTrue(input.isValid); input.steps.append(input.steps[0]); XCTAssertFalse(input.isValid)
        input.steps = [.init(id: UUID(), text: String(repeating: "😀", count: 129), role: .intermediate, observationIds: [], confirmed: true)]
        XCTAssertFalse(input.isValid)
    }
    func testSharedResponseAndLegacyDecodingRemainSeparate() throws {
        let url = try XCTUnwrap(Bundle.main.url(forResource: "attempt-structured-work-v1", withExtension: "json"))
        let work = try JSONDecoder().decode(AttemptStructuredWork.self, from: Data(contentsOf: url))
        XCTAssertNoThrow(try work.validated(problem: work.problemSessionId))
        XCTAssertEqual(work.steps.last?.role, .finalAnswer); XCTAssertFalse(work.trainingEligible)
        XCTAssertThrowsError(try JSONDecoder().decode(StudentAttempt.self, from: Data(contentsOf: url)))
    }
    func testEnglishTurkishAccessibleStateCopy() throws {
        for language in ["en", "tr"] {
            let path = try XCTUnwrap(Bundle.main.path(forResource: language, ofType: "lproj"))
            let bundle = try XCTUnwrap(Bundle(path: path))
            for key in ["scope", "saved", "sourceRegion", "UNSUPPORTED", "REVIEW_REQUIRED", "NORMALIZED", "moveUp", "moveDown", "role.FINAL_ANSWER", "originalOCR"] {
                let full = "attempt.work." + key
                XCTAssertNotEqual(bundle.localizedString(forKey: full, value: nil, table: nil), full)
            }
        }
    }
}

extension AttemptStructuredWorkTests {
    func testNativePreviewIsBoundedImmutableOwnerIsolatedAndPurged() throws {
        let storage = store(), owner = UUID(), problem = UUID(), capture = UUID(), bytes = Data([1, 2, 3])
        try storage.saveWorkPreview(bytes, owner: owner, problem: problem, capture: capture)
        XCTAssertEqual(try storage.loadWorkPreview(owner: owner, problem: problem, capture: capture), bytes)
        XCTAssertNil(try storage.loadWorkPreview(owner: UUID(), problem: problem, capture: capture))
        XCTAssertThrowsError(try storage.saveWorkPreview(Data([4]), owner: owner, problem: problem, capture: capture))
        XCTAssertThrowsError(try storage.saveWorkPreview(Data(count: 2_000_001), owner: owner, problem: problem, capture: UUID()))
        try storage.clearAll(); XCTAssertNil(try storage.loadWorkPreview(owner: owner, problem: problem, capture: capture))
    }
    func testCanceledStructuredResponseCannotPublishIntoReopenedState() async throws {
        let owner = UUID(uuidString: "00000000-0000-0000-0000-000000007200")!
        let api = DelayedStructuredAPI(), storage = store()
        let model = AnalyzeMyWorkViewModel(owner: owner, problem: UUID(), service: api, store: storage, ownsAccount: { true })
        model.resume(); await idle(model); model.draft = "x=4"; model.startStructured(); model.submit()
        for _ in 0..<100 { if await api.started { break }; try await Task.sleep(nanoseconds: 5_000_000) }
        model.suspend()
        await api.release()
        try await Task.sleep(nanoseconds: 30_000_000)
        XCTAssertNil(model.workResult); XCTAssertNotNil(model.pendingWork)
    }
}
private actor DelayedStructuredAPI: AttemptAnalysisServicing {
    private let backing = FixtureAttemptAnalysisAPI()
    private var continuation: CheckedContinuation<Void, Never>?
    private(set) var started = false
    func context(problem: UUID) async throws -> AttemptContext { try await backing.context(problem: problem) }
    func list(problem: UUID) async throws -> [StudentAttempt] { try await backing.list(problem: problem) }
    func submit(problem: UUID, key: String, submission: AttemptSubmission) async throws -> StudentAttempt { try await backing.submit(problem: problem, key: key, submission: submission) }
    func submitWork(problem: UUID, key: String, input: AttemptWorkRequest) async throws -> AttemptStructuredWork {
        started = true
        await withCheckedContinuation { continuation = $0 }
        return try await backing.submitWork(problem: problem, key: key, input: input)
    }
    func release() { continuation?.resume(); continuation = nil }
}

extension AttemptStructuredWorkTests {
    func testUnconfirmedMultilineOCRCanContinueToReviewWithoutAttestation() {
        let lines = [AttemptRecognizedLine(id: UUID(), raw: "x+1\nx-3", text: "x+1\nx-3", confidence: 0.2, box: .zero)]
        let structured = AttemptStepReviewView(lines: .constant(lines), preview: nil, confirm: {}, cancel: {}, structured: true)
        let legacy = AttemptStepReviewView(lines: .constant(lines), preview: nil, confirm: {}, cancel: {})
        XCTAssertTrue(structured.canContinue); XCTAssertFalse(legacy.canContinue)
        XCTAssertFalse(lines[0].confirmed)
    }
}
