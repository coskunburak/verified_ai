import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class PrimarySolveContractTests: XCTestCase {
    func testExplanationPreferenceUsesOnlyExistingProfilePatchAndDoesNotReplayAuthFailure() async throws {
        let response = Data("""
        {"exists":true,"id":"00000000-0000-0000-0000-0000000000B1",
         "userId":"00000000-0000-0000-0000-0000000000A1","educationLevel":"HIGH_SCHOOL",
         "preferredLanguage":"tr","explanationDepth":"DEEP","dailyStudyMinutes":25,
         "timezone":"Europe/Istanbul","goalContext":"Synthetic fixture","onboardingStatus":"COMPLETED","version":8}
        """.utf8)
        let transport = SolveContractSession(responses: [response])
        let api = LearningProfileAPI(apiClient: APIClient(baseURL: URL(string: "https://example.invalid")!,
            session: transport, authTokenProvider: InMemorySecureStorage(), correlationContext: CorrelationContext()))
        let saved = try await api.saveExplanationDepth(.deep, expectedVersion: 7)
        XCTAssertEqual(saved.explanationDepth, .deep)
        let requests = await transport.requests
        XCTAssertEqual(requests.count, 1)
        XCTAssertEqual(requests[0].url?.path, "/api/v1/me/learning-profile")
        XCTAssertEqual(requests[0].httpMethod, "PATCH")
        let payload = try JSONSerialization.jsonObject(with: XCTUnwrap(requests[0].httpBody)) as! [String: Any]
        XCTAssertEqual(Set(payload.keys), ["explanationDepth", "expectedVersion"])
        XCTAssertEqual(payload["explanationDepth"] as? String, "DEEP")
        XCTAssertEqual(payload["expectedVersion"] as? Int, 7)

        let expired = SolveContractSession(responses: [Data("""
        {"type":"fixture","title":"Synthetic expiry","status":401,"code":"AUTH_TOKEN_EXPIRED"}
        """.utf8)], status: 401)
        let expiredAPI = LearningProfileAPI(apiClient: APIClient(baseURL: URL(string: "https://example.invalid")!,
            session: expired, authTokenProvider: InMemorySecureStorage(), correlationContext: CorrelationContext()))
        do { _ = try await expiredAPI.saveExplanationDepth(.quick, expectedVersion: 7); XCTFail("Expected auth error") }
        catch { XCTAssertEqual(ExplanationPreferenceError.from(error), .auth) }
        let expiredRequests = await expired.requests
        XCTAssertEqual(expiredRequests.count, 1)
    }

    func testAllLifecycleStatesDecodeAndOnlyCandidateStatesExposeAnswers() throws {
        for state in SolveState.allCases {
            let job = try FixturePrimarySolveAPI.decode(state: state)
            XCTAssertEqual(job.state, state)
            XCTAssertEqual(job.visibleCandidate != nil, state.showsCandidate)
            XCTAssertFalse(job.verificationNotice.contains("VERIFIED"))
            XCTAssertFalse(state.symbolName.isEmpty)
        }
    }
    func testMissingFieldsUnknownStateAndUntrustedVerificationNoticeFailClosed() throws {
        let valid = FixturePrimarySolveAPI.data(state: .generatedUnverified)
        for mutation in ["missing", "unknown", "verified", "lineage", "order", "duplicateDep", "blankExplanation", "duplicateAssumption"] {
            var json = try XCTUnwrap(JSONSerialization.jsonObject(with: valid) as? [String: Any])
            switch mutation {
            case "missing": json.removeValue(forKey: "jobId")
            case "unknown": json["state"] = "VERIFIED"
            case "verified": json["verificationNotice"] = "100% correct"
            case "lineage": json["problemParseRevision"] = 2
            case "order":
                var candidate = json["candidate"] as! [String: Any]
                var steps = candidate["steps"] as! [[String: Any]]
                steps[0]["order"] = 2; candidate["steps"] = steps; json["candidate"] = candidate
            case "duplicateDep":
                var candidate = json["candidate"] as! [String: Any]
                var steps = candidate["steps"] as! [[String: Any]]
                steps[1]["dependsOn"] = ["s1", "s1"]; candidate["steps"] = steps; json["candidate"] = candidate
            case "blankExplanation":
                var candidate = json["candidate"] as! [String: Any]
                var steps = candidate["steps"] as! [[String: Any]]
                steps[0]["explanation"] = "   "; candidate["steps"] = steps; json["candidate"] = candidate
            default:
                var candidate = json["candidate"] as! [String: Any]
                candidate["sourceAssumptionIds"] = ["a1", "a1"]; json["candidate"] = candidate
            }
            let data = try JSONSerialization.data(withJSONObject: json)
            XCTAssertThrowsError(try JSONDecoder().decode(SolveJobSnapshot.self, from: data).validated())
        }
    }
    func testUnsupportedCandidateSchemaV2FailsClosedAsUnsupportedRatherThanNetworkOrVerified() async throws {
        let v2JobData = FixturePrimarySolveAPI.unsupportedV2Data()
        do {
            _ = try JSONDecoder().decode(SolveJobSnapshot.self, from: v2JobData).validated()
            XCTFail("Expected solution-candidate-v2 to fail closed in user-facing v1 contract")
        } catch {
            XCTAssertEqual(error as? SolveContractError, .unsupportedCandidateSchema)
            XCTAssertEqual(PrimarySolveViewModel.message(for: error), "solve.error.unsupported")
        }
        let transport = SolveContractSession(responses: [v2JobData])
        do {
            _ = try await client(transport).read(sessionId: UUID(), jobId: UUID())
            XCTFail("Expected HTTP read of v2 candidate to fail closed")
        } catch {
            XCTAssertEqual(error as? SolveContractError, .unsupportedCandidateSchema)
            XCTAssertEqual(PrimarySolveViewModel.message(for: error), "solve.error.unsupported")
        }
    }
    func testTypedLifecycleUsesExactPathsMethodsAndIdempotencyKeys() async throws {
        let job = UUID(), session = UUID()
        let body = FixturePrimarySolveAPI.data(state: .queued, jobId: job)
        let transport = SolveContractSession(responses: [Data("{\"job\":null}".utf8), body, body, body, body])
        let api = client(transport)
        let current = try await api.current(sessionId: session); XCTAssertNil(current)
        _ = try await api.start(sessionId: session, key: "start_original")
        _ = try await api.read(sessionId: session, jobId: job)
        _ = try await api.retry(sessionId: session, jobId: job, key: "retry_fresh_key")
        _ = try await api.cancel(sessionId: session, jobId: job)
        let requests = await transport.requests
        let base = "/api/v1/problem-sessions/\(session.uuidString)/solves"
        XCTAssertEqual(requests.map { $0.url!.path }, [base+"/current", base, base+"/\(job)", base+"/\(job)/retry", base+"/\(job)/cancel"])
        XCTAssertEqual(requests.map(\.httpMethod), ["GET", "POST", "GET", "POST", "POST"])
        XCTAssertEqual(requests[1].value(forHTTPHeaderField: "Idempotency-Key"), "start_original")
        XCTAssertEqual(requests[3].value(forHTTPHeaderField: "Idempotency-Key"), "retry_fresh_key")
        XCTAssertNil(requests[4].value(forHTTPHeaderField: "Idempotency-Key"))
    }
    func testCurrentLookupMissingJobFieldDoesNotBecomePermissionToStart() async {
        do { _ = try await client(SolveContractSession(responses: [Data("{}".utf8)])).current(sessionId: UUID()); XCTFail() }
        catch { XCTAssertEqual(PrimarySolveViewModel.message(for: error), "solve.error.contract") }
    }
    func testStartServerErrorsDoNotReplayMutatingRequests() async throws {
        for (code, status, key) in [("AUTH_TOKEN_EXPIRED",401,"auth"),("SOLVE_DISABLED",409,"disabled"),("SOLVE_STATE_CONFLICT",409,"conflict")] {
            let json = Data("{\"type\":\"test\",\"title\":\"private backend detail\",\"status\":\(status),\"code\":\"\(code)\"}".utf8)
            let transport = SolveContractSession(responses: [json], status: status)
            do { _ = try await client(transport).start(sessionId: UUID(), key: "stable_request"); XCTFail("Expected rejection") }
            catch { XCTAssertEqual(PrimarySolveViewModel.message(for: error), "solve.error." + key) }
            let calls = await transport.requests
            XCTAssertEqual(calls.count, 1)
        }
    }
    func testErrorCodesMapWithoutAssumingHTTPRetryPermission() {
        for (code,key) in [("SOLVE_DISABLED","disabled"),("SOLVE_SOURCE_NOT_READY","source"),("SOLVE_STATE_CONFLICT","conflict"),
                          ("SOLVE_RETRY_LIMIT","limit"),("PROBLEM_UNSUPPORTED","unsupported"),("IDEMPOTENCY_KEY_REUSED","conflict"),("AUTH_TOKEN_EXPIRED","auth")] {
            let error = NetworkError.server(problem: .init(type: "test", title: "raw secret", status: 409, code: code, traceId: nil, details: nil))
            XCTAssertEqual(PrimarySolveViewModel.message(for: error), "solve.error."+key)
        }
    }
    func testVerificationFixturesHaveExplicitScopeAndNeverChangeGenerationState() throws {
        let job = try FixturePrimarySolveAPI.decode(state: .generatedUnverified)
        for scenario in ["VERIFIED", "PARTIALLY_VERIFIED", "UNVERIFIED", "CONTRADICTION"] {
            let decision = try FixturePrimarySolveAPI.decisionFixture(scenario).validated(for: job)
            XCTAssertEqual(decision.hasConflict, scenario == "CONTRADICTION")
            XCTAssertEqual(job.state, .generatedUnverified)
            XCTAssertTrue(decision.uncertainty.contains("EXPLANATION_STEPS_UNCHECKED"))
            XCTAssertFalse(decision.status.titleKey.isEmpty)
            XCTAssertFalse(decision.recovery.titleKey.isEmpty)
            if decision.status != .verified {
                XCTAssertNotEqual(decision.statusSymbolName, "checkmark.shield")
            }
        }
    }
    func testVerificationTransportIsReadOnlyAndMissingDecisionFieldFailsClosed() async throws {
        let session = UUID(), job = UUID()
        let transport = SolveContractSession(responses: [Data("{\"decision\":null}".utf8)])
        let result = try await client(transport).verification(sessionId: session, jobId: job)
        XCTAssertNil(result)
        let calls = await transport.requests
        XCTAssertEqual(calls.count, 1); XCTAssertEqual(calls[0].httpMethod, "GET")
        XCTAssertEqual(calls[0].url?.path, "/api/v1/problem-sessions/\(session)/solves/\(job)/verification")
        do {
            _ = try await client(SolveContractSession(responses: [Data("{}".utf8)])).verification(sessionId: session, jobId: job)
            XCTFail("Missing decision must not silently decode as absent")
        } catch { XCTAssertEqual(PrimarySolveViewModel.message(for: error), "solve.error.contract") }
    }
    func testSharedDecisionFixturesAndUnknownContractValues() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let folder = root.appendingPathComponent("packages/test-fixtures/verification/decision-v1")
        let job = try FixturePrimarySolveAPI.decode(state: .generatedUnverified)
        for name in ["verified", "partial", "unverified", "contradiction", "calculus-derivative", "calculus-integral", "calculus-limit"] {
            let data = try Data(contentsOf: folder.appendingPathComponent(name + ".json"))
            _ = try JSONDecoder().decode(VerificationDecisionSnapshot.self, from: data).validated(for: job)
        }
        let data = try Data(contentsOf: folder.appendingPathComponent("verified.json"))
        for field in ["status", "policyVersion", "scope", "recovery", "problemParseId", "reasons"] {
            var json = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
            if field == "reasons" { json[field] = ["UNKNOWN"] } else { json[field] = "UNKNOWN" }
            XCTAssertThrowsError(try JSONDecoder().decode(VerificationDecisionSnapshot.self,
                from: JSONSerialization.data(withJSONObject: json)).validated(for: job))
        }
    }
    func testCalculusDecisionsCannotClaimGenericFinalAnswerOrAnotherCalculusScope() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let folder = root.appendingPathComponent("packages/test-fixtures/verification/decision-v1")
        let job = try FixturePrimarySolveAPI.decode(state: .generatedUnverified)
        for (name, correctScope) in [("calculus-derivative", "OPEN_INTERVAL"),
            ("calculus-integral", "ANTIDERIVATIVE_REPRESENTATIVE"), ("calculus-limit", "PUNCTURED_NEIGHBORHOOD")] {
            let data = try Data(contentsOf: folder.appendingPathComponent(name + ".json"))
            let base = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
            for wrong in ["FINAL_ANSWER", "OPEN_INTERVAL", "ANTIDERIVATIVE_REPRESENTATIVE", "PUNCTURED_NEIGHBORHOOD"] where wrong != correctScope {
                var json = base; json["scope"] = wrong
                XCTAssertThrowsError(try JSONDecoder().decode(VerificationDecisionSnapshot.self,
                    from: JSONSerialization.data(withJSONObject: json)).validated(for: job))
            }
            var mixed = base; mixed["unchecked"] = ["DOMAIN_CONSTRAINT_CHECK"]
            XCTAssertThrowsError(try JSONDecoder().decode(VerificationDecisionSnapshot.self,
                from: JSONSerialization.data(withJSONObject: mixed)).validated(for: job))
        }
    }
    func testCompleteVerificationSemanticsMatrixAndEvidencePresentationMapper() throws {
        let job = try FixturePrimarySolveAPI.decode(state: .generatedUnverified)
        for scenario in FixturePrimarySolveAPI.supportedVerificationScenarios.sorted() {
            let decision = try FixturePrimarySolveAPI.decisionFixture(scenario).validated(for: job)
            let presentation = decision.evidencePresentation
            XCTAssertTrue(presentation.hasExpandableEvidence)
            XCTAssertTrue(presentation.uncertaintyKeys.contains("verification.stepsUnchecked"))
            XCTAssertEqual(presentation.checkedMethodKeys, decision.checked.map(\.titleKey))
            XCTAssertEqual(presentation.uncheckedMethodKeys, decision.unchecked.map(\.titleKey))
            XCTAssertEqual(presentation.contradictedMethodKeys, decision.contradictions.map(\.titleKey))
            XCTAssertEqual(presentation.reasonKeys, decision.typedReasons.map(\.titleKey))
            XCTAssertEqual(presentation.recoveryKey, decision.recovery.titleKey)
            if decision.status == .verified {
                XCTAssertEqual(presentation.statusSymbolName, "checkmark.shield")
                XCTAssertEqual(presentation.severity, .verified)
                XCTAssertFalse(presentation.hasConflict)
                if decision.scope.calculusMethod != nil {
                    XCTAssertEqual(presentation.statusTitleKey, "verification.scopedStatus")
                    XCTAssertEqual(presentation.summaryKey, "verification.summary.SCOPED_VERIFIED")
                } else {
                    XCTAssertEqual(presentation.statusTitleKey, "verification.status.VERIFIED")
                    XCTAssertEqual(presentation.summaryKey, "verification.summary.VERIFIED")
                }
            } else {
                XCTAssertNotEqual(presentation.statusSymbolName, "checkmark.shield")
                XCTAssertNotEqual(presentation.severity, .verified)
            }
        }

        let secondaryAgreement = try FixturePrimarySolveAPI.decisionFixture("SECONDARY_AGREEMENT").validated(for: job).evidencePresentation
        XCTAssertTrue(secondaryAgreement.showsSecondaryAgreementNotice)
        XCTAssertEqual(secondaryAgreement.severity, .neutral)

        let numericUnproven = try FixturePrimarySolveAPI.decisionFixture("NUMERIC_SAMPLING_UNPROVEN").validated(for: job).evidencePresentation
        XCTAssertTrue(numericUnproven.showsNumericSamplingNotice)
        XCTAssertTrue(numericUnproven.showsSecondaryAgreementNotice)
        XCTAssertEqual(numericUnproven.severity, .neutral)

        for conflictScenario in ["CONTRADICTION", "EVIDENCE_CONFLICT", "SECONDARY_DISAGREEMENT"] {
            let conflictPresentation = try FixturePrimarySolveAPI.decisionFixture(conflictScenario).validated(for: job).evidencePresentation
            XCTAssertTrue(conflictPresentation.hasConflict)
            XCTAssertEqual(conflictPresentation.severity, .warning)
            XCTAssertEqual(conflictPresentation.conflictWarningKey, "verification.conflict")
            XCTAssertEqual(conflictPresentation.summaryKey, "verification.summary.CONFLICT")
            XCTAssertEqual(conflictPresentation.statusSymbolName, "exclamationmark.triangle")
        }

        let absent = VerificationEvidencePresentation.absent(unavailable: false)
        XCTAssertFalse(absent.hasExpandableEvidence)
        XCTAssertEqual(absent.statusIdentifier, "solve.unverified")
        XCTAssertEqual(absent.scopeKey, "verification.absent")
        XCTAssertEqual(absent.summaryKey, "verification.summary.ABSENT")

        let unavailable = VerificationEvidencePresentation.absent(unavailable: true)
        XCTAssertEqual(unavailable.scopeKey, "verification.unavailable")
        XCTAssertEqual(unavailable.summaryKey, "verification.summary.UNAVAILABLE")

        let invalid = VerificationEvidencePresentation.absent(unavailable: true, invalidContract: true)
        XCTAssertEqual(invalid.scopeKey, "verification.invalid")
        XCTAssertEqual(invalid.summaryKey, "verification.summary.INVALID")
        XCTAssertEqual(invalid.severity, .warning)
    }
    func testInvalidStatusReasonContradictionAndMetadataCombinationsFailClosed() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let folder = root.appendingPathComponent("packages/test-fixtures/verification/decision-v1")
        let job = try FixturePrimarySolveAPI.decode(state: .generatedUnverified)
        let verifiedBase = try XCTUnwrap(JSONSerialization.jsonObject(
            with: Data(contentsOf: folder.appendingPathComponent("verified.json"))) as? [String: Any])
        let partialBase = try XCTUnwrap(JSONSerialization.jsonObject(
            with: Data(contentsOf: folder.appendingPathComponent("partial.json"))) as? [String: Any])
        let unverifiedBase = try XCTUnwrap(JSONSerialization.jsonObject(
            with: Data(contentsOf: folder.appendingPathComponent("unverified.json"))) as? [String: Any])

        var verifiedWithMissingProof = verifiedBase
        verifiedWithMissingProof["reasons"] = ["REQUIRED_CLAIM_PROVEN", "MISSING_PROOF"]
        var verifiedWithBadCorrelation = verifiedBase
        verifiedWithBadCorrelation["correlationId"] = "invalid space"
        var verifiedWithBadTimestamp = verifiedBase
        verifiedWithBadTimestamp["createdAt"] = "not-a-timestamp"
        var partialWithContradictionReason = partialBase
        partialWithContradictionReason["reasons"] = ["SUBSET_PROVEN", "DETERMINISTIC_CONTRADICTION"]
        var partialWithoutSubsetProven = partialBase
        partialWithoutSubsetProven["reasons"] = ["COMPLETENESS_UNCHECKED"]
        var unverifiedWithContradictionButNoMethod = unverifiedBase
        unverifiedWithContradictionButNoMethod["reasons"] = ["DETERMINISTIC_CONTRADICTION", "MISSING_PROOF"]
        var unverifiedWithSecondaryDisagreementButNoUncertainty = unverifiedBase
        unverifiedWithSecondaryDisagreementButNoUncertainty["reasons"] = ["SECONDARY_DISAGREEMENT", "MISSING_PROOF"]
        var unverifiedWithRequiredProven = unverifiedBase
        unverifiedWithRequiredProven["reasons"] = ["REQUIRED_CLAIM_PROVEN", "MISSING_PROOF"]
        var unverifiedReleaseDisabledWithoutUncertainty = unverifiedBase
        unverifiedReleaseDisabledWithoutUncertainty["reasons"] = ["RELEASE_DISABLED"]
        unverifiedReleaseDisabledWithoutUncertainty["recovery"] = "RELEASE_BLOCKED"

        for invalidJSON in [
            verifiedWithMissingProof,
            verifiedWithBadCorrelation,
            verifiedWithBadTimestamp,
            partialWithContradictionReason,
            partialWithoutSubsetProven,
            unverifiedWithContradictionButNoMethod,
            unverifiedWithSecondaryDisagreementButNoUncertainty,
            unverifiedWithRequiredProven,
            unverifiedReleaseDisabledWithoutUncertainty
        ] {
            let data = try JSONSerialization.data(withJSONObject: invalidJSON)
            XCTAssertThrowsError(try JSONDecoder().decode(VerificationDecisionSnapshot.self, from: data).validated(for: job))
        }
    }
    func testAllVerificationEnumsHaveCompleteEnglishAndTurkishLocalizationEntries() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let catalogURL = root.appendingPathComponent("apps/ios/VerifiedAI/Resources/Localizable.xcstrings")
        let catalog = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(contentsOf: catalogURL)) as? [String: Any])
        let strings = try XCTUnwrap(catalog["strings"] as? [String: Any])

        let requiredKeys = VerificationDecisionSnapshot.Status.allCases.map(\.titleKey)
            + VerificationDecisionSnapshot.Scope.allCases.map(\.titleKey)
            + VerificationDecisionSnapshot.Reason.allCases.map(\.titleKey)
            + VerificationDecisionSnapshot.Uncertainty.allCases.map(\.titleKey)
            + VerificationDecisionSnapshot.Recovery.allCases.map(\.titleKey)
            + VerificationDecisionSnapshot.Method.allCases.map(\.titleKey)
            + [
                "verification.scopedStatus", "verification.conflict", "verification.checked",
                "verification.unchecked", "verification.stepsUnchecked", "verification.absent",
                "verification.unavailable", "verification.invalid", "verification.refresh",
                "verification.agreement", "verification.numericLimitation",
                "verification.evidence.show", "verification.evidence.hide", "verification.evidence.hint",
                "verification.reasons", "verification.limitations", "verification.contradictions",
                "verification.summary.VERIFIED", "verification.summary.SCOPED_VERIFIED",
                "verification.summary.PARTIALLY_VERIFIED", "verification.summary.UNVERIFIED",
                "verification.summary.CONFLICT", "verification.summary.ABSENT",
                "verification.summary.UNAVAILABLE", "verification.summary.INVALID"
            ]

        for key in requiredKeys {
            let entry = try XCTUnwrap(strings[key] as? [String: Any], "Missing localization key: \(key)")
            let localizations = try XCTUnwrap(entry["localizations"] as? [String: Any], "Missing localizations for \(key)")
            for locale in ["en", "tr"] {
                let localeObj = try XCTUnwrap(localizations[locale] as? [String: Any], "Missing \(locale) for \(key)")
                let unit = try XCTUnwrap(localeObj["stringUnit"] as? [String: Any])
                let value = try XCTUnwrap(unit["value"] as? String)
                XCTAssertFalse(value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty, "Blank \(locale) translation for \(key)")
            }
        }
    }
    private func client(_ transport: SolveContractSession) -> PrimarySolveAPI {
        PrimarySolveAPI(client: APIClient(baseURL: URL(string: "https://example.invalid")!, session: transport,
            authTokenProvider: InMemorySecureStorage(), correlationContext: CorrelationContext()))
    }
}

private actor SolveContractSession: HTTPSession {
    var responses: [Data]
    private(set) var requests: [URLRequest] = []
    let status: Int
    init(responses: [Data], status: Int = 200) { self.responses = responses; self.status = status }
    func data(for request: URLRequest) async throws -> (Data, URLResponse) {
        requests.append(request)
        return (responses.removeFirst(), HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: nil)!)
    }
}
