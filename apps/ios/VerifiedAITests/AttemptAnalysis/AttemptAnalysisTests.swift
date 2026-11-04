import XCTest
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
