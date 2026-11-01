import Foundation
import XCTest
@testable import VerifiedAI

@MainActor
final class ExplanationPresentationTests: XCTestCase {
    func testFourModesPreserveCandidateLineageClaimsRestrictionsAndUncertainty() throws {
        let candidate = try fixture(restricted: true)
        for mode in ExplanationDepth.allCases {
            let presentation = ExplanationPresentation(candidate: candidate, mode: mode)
            XCTAssertEqual(presentation.candidate, candidate)
            XCTAssertEqual(presentation.candidate.finalAnswer, candidate.finalAnswer)
            XCTAssertEqual(presentation.candidate.canonicalProblemId, candidate.canonicalProblemId)
            XCTAssertEqual(presentation.candidate.problemParseId, candidate.problemParseId)
            XCTAssertEqual(presentation.candidate.problemParseRevision, candidate.problemParseRevision)
            XCTAssertEqual(presentation.candidate.sourceConstraintIds, ["constraint-1"])
            XCTAssertEqual(presentation.candidate.derivedRestrictionIds, ["restriction-1"])
            XCTAssertFalse(presentation.candidate.ambiguities.isEmpty)
            XCTAssertFalse(presentation.candidate.unresolvedIssues.isEmpty)
            XCTAssertEqual(presentation.visibleSteps, candidate.steps.filter { original in
                presentation.visibleSteps.contains { $0.id == original.id }
            })
        }
    }

    func testQuickCondensesAndRetainsCompleteOriginalSequence() throws {
        let candidate = try fixture()
        let quick = ExplanationPresentation(candidate: candidate, mode: .quick)
        XCTAssertEqual(quick.visibleSteps, [candidate.steps.last!])
        XCTAssertEqual(quick.omittedStepCount, candidate.steps.count - 1)
        XCTAssertGreaterThan(quick.omittedStepCount, 0)
        XCTAssertEqual(quick.candidate.steps, candidate.steps)
        XCTAssertEqual(quick.visibleSteps[0].dependsOn, ["s1"])
    }

    func testStandardIsCompleteAndDeepExposesExactOriginalDependencies() throws {
        let candidate = try fixture()
        let standard = ExplanationPresentation(candidate: candidate, mode: .standard)
        let deep = ExplanationPresentation(candidate: candidate, mode: .deep)
        XCTAssertEqual(standard.visibleSteps, candidate.steps)
        XCTAssertEqual(deep.visibleSteps, candidate.steps)
        XCTAssertFalse(standard.usesIndividualDisclosure)
        XCTAssertTrue(standard.dependencies(for: candidate.steps[1]).isEmpty)
        XCTAssertEqual(deep.dependencies(for: candidate.steps[1]), [candidate.steps[0]])
        XCTAssertTrue(deep.dependencies(for: candidate.steps[0]).isEmpty)
        XCTAssertEqual(deep.availabilityKey, "explanation.availability.DEEP")
    }

    func testBeginnerCatalogHasBoundedArithmeticAndEquationCoverageAndHonestFallback() throws {
        let equation = try fixture()
        let beginner = ExplanationPresentation(candidate: equation, mode: .beginner)
        XCTAssertTrue(beginner.usesIndividualDisclosure)
        XCTAssertEqual(beginner.visibleSteps, equation.steps)
        XCTAssertEqual(beginner.prerequisiteKey, "explanation.term.equation")
        let arithmetic = try fixture(kind: "ARITHMETIC_EXPRESSION", task: "EVALUATE", method: "ARITHMETIC")
        XCTAssertEqual(ExplanationPresentation(candidate: arithmetic, mode: .beginner).prerequisiteKey, "explanation.term.expression")
        let algebra = try fixture(kind: "ALGEBRAIC_EXPRESSION", task: "SIMPLIFY", method: "ALGEBRAIC_TRANSFORMATION")
        XCTAssertNil(ExplanationPresentation(candidate: algebra, mode: .beginner).prerequisiteKey)
        XCTAssertNil(ExplanationPresentation(candidate: equation, mode: .deep).prerequisiteKey)
    }

    func testSingleStepQuickDoesNotInventOmittedSteps() throws {
        let candidate = try fixture(singleStep: true)
        let quick = ExplanationPresentation(candidate: candidate, mode: .quick)
        XCTAssertEqual(quick.visibleSteps, candidate.steps)
        XCTAssertEqual(quick.omittedStepCount, 0)
    }

    func testExplanationLocalizationHasCompleteEnglishAndTurkishCoverage() throws {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let catalog = try JSONSerialization.jsonObject(with: Data(contentsOf: root.appendingPathComponent("VerifiedAI/Resources/Localizable.xcstrings"))) as! [String: Any]
        let strings = catalog["strings"] as! [String: [String: Any]]
        let keys = strings.keys.filter { $0.hasPrefix("explanation.") } + ExplanationDepth.allCases.map(\.title) + ["Explanation depth"]
        XCTAssertGreaterThan(keys.count, 30)
        for key in keys {
            let localizations = try XCTUnwrap(strings[key]?["localizations"] as? [String: [String: Any]], key)
            for language in ["en", "tr"] {
                let unit = try XCTUnwrap(localizations[language]?["stringUnit"] as? [String: String], "\(key):\(language)")
                XCTAssertFalse(try XCTUnwrap(unit["value"]).isEmpty)
                XCTAssertEqual(unit["state"], "translated")
            }
        }
    }

    private func fixture(kind: String = "EQUATION", task: String = "SOLVE_EQUATION", method: String = "ISOLATE_VARIABLE",
                         restricted: Bool = false, singleStep: Bool = false) throws -> SolutionCandidate {
        var job = try JSONSerialization.jsonObject(with: FixturePrimarySolveAPI.data(state: .reviewRequired)) as! [String: Any]
        var candidate = job["candidate"] as! [String: Any]
        candidate["problemType"] = kind; candidate["taskType"] = task; candidate["method"] = method
        if kind != "EQUATION" {
            let one: [String: Any] = ["kind": "NUMBER", "numericType": "INTEGER", "value": "1"]
            let two: [String: Any] = ["kind": "NUMBER", "numericType": "INTEGER", "value": "2"]
            let sum: [String: Any] = ["kind": "EXPRESSION", "expression": ["kind": "BINARY", "operator": "ADD", "left": one, "right": one]]
            let result: [String: Any] = ["kind": "EXPRESSION", "expression": two]
            candidate["steps"] = [
                ["id": "s1", "order": 1, "category": "RESTATE", "claim": sum, "explanation": "Synthetic expression.", "dependsOn": []],
                ["id": "s2", "order": 2, "category": "CONCLUDE", "claim": result, "explanation": "Synthetic proposed value.", "dependsOn": ["s1"]]
            ]
            candidate["finalAnswer"] = result
        }
        if restricted {
            candidate["sourceConstraintIds"] = ["constraint-1"]
            candidate["derivedRestrictionIds"] = ["restriction-1"]
            candidate["unresolvedIssues"] = ["Synthetic unresolved condition"]
        }
        if singleStep { candidate["steps"] = Array((candidate["steps"] as! [[String: Any]]).prefix(1)) }
        job["candidate"] = candidate
        return try XCTUnwrap(JSONDecoder().decode(SolveJobSnapshot.self, from: JSONSerialization.data(withJSONObject: job)).validated().candidate)
    }
}
