import XCTest
@testable import VerifiedAI

@MainActor final class MathVisualizationTests: XCTestCase {
    func fixture() throws -> MathVisualization { try JSONDecoder().decode(MathVisualization.self, from: FixtureVisualizationAPI.data()) }
    func testSourceBoundFixtureAndExactApproximateSeparation() throws {
        let value = try fixture()
        _ = try value.validated(problem: value.problemId, source: value.source, kind: .function, viewport: VisualizationViewport())
        XCTAssertEqual(value.result.sampleSemantics, "APPROXIMATE_DISPLAY_ONLY")
        XCTAssertEqual(value.result.features.first?.x, "0")
        XCTAssertEqual(value.result.numberLine.first?.lower, "-oo")
        XCTAssertNil(value.result.numberLine.first?.lowerValue)
        XCTAssertThrowsError(try value.validated(problem: UUID(), source: value.source, kind: .function, viewport: VisualizationViewport()))
    }
    func testUnknownFieldsAndBadPolicyRejected() throws {
        var raw = try JSONSerialization.jsonObject(with: FixtureVisualizationAPI.data()) as! [String: Any]
        raw["verificationStatus"] = "VERIFIED"
        XCTAssertThrowsError(try JSONDecoder().decode(MathVisualization.self, from: JSONSerialization.data(withJSONObject: raw)))
        raw.removeValue(forKey: "verificationStatus")
        var result = raw["result"] as! [String: Any]; result["policyVersion"] = "v0"; raw["result"] = result
        let value = try JSONDecoder().decode(MathVisualization.self, from: JSONSerialization.data(withJSONObject: raw))
        XCTAssertThrowsError(try value.validated(problem: value.problemId, source: value.source, kind: .function, viewport: VisualizationViewport()))
    }
    func testViewportBoundsAndNumberLineSemantics() throws {
        XCTAssertFalse(VisualizationViewport(xMin: 1, xMax: 1.001).valid)
        XCTAssertFalse(VisualizationViewport(xMin: -.infinity).valid)
        XCTAssertEqual(VisualizationViewport().scaled(0.5).xMin, -5)
        XCTAssertTrue(try XCTUnwrap(fixture().result.numberLine.first).leftOpen)
    }
    func settled(_ model: MathVisualizationViewModel) async {
        for _ in 0..<200 where model.state == .loading { try? await Task.sleep(nanoseconds: 5_000_000) }
    }
    func testOfflineRetryToggleAndSelection() async throws {
        let value = try fixture()
        let model = MathVisualizationViewModel(api: FixtureVisualizationAPI(failFirst: true), problem: value.problemId, ownsAccount: { true })
        model.load(); await settled(model); XCTAssertEqual(model.state, .offline)
        model.load(); await settled(model); XCTAssertEqual(model.state, .ready)
        model.selectedX = 0; XCTAssertNotNil(model.selectedSample)
        model.showFunction = false; XCTAssertTrue(model.visibleSegments.isEmpty)
        model.changeViewport(model.viewport.scaled(0.5)); await settled(model)
        XCTAssertEqual(model.viewport.xMin, -5)
    }
    func testRepeatedSelectionMatchesNearestVisibleSample() async throws {
        let value = try fixture()
        let model = MathVisualizationViewModel(api: FixtureVisualizationAPI(), problem: value.problemId, ownsAccount: { true })
        model.load(); await settled(model)
        let points = model.visibleSegments.flatMap(\.points)
        XCTAssertFalse(points.isEmpty)
        for index in 0..<100 {
            let x = -10.0 + Double(index) / 5.0
            model.selectedX = x
            let expected = points.min { abs($0.x - x) < abs($1.x - x) }
            XCTAssertEqual(model.selectedSample?.x, expected?.x)
            XCTAssertEqual(model.selectedSample?.y, expected?.y)
        }
        measure {
            for index in 0..<100 {
                model.selectedX = Double(index) / 10.0
                _ = model.selectedSample
            }
        }
        model.showFunction = false; model.showDerivative = false
        XCTAssertNil(model.selectedSample)
    }

    func testCancellationAndAccountFenceDiscardLateResponse() async throws {
        let value = try fixture(); var owned = true
        let model = MathVisualizationViewModel(api: FixtureVisualizationAPI(delay: 100_000_000), problem: value.problemId, ownsAccount: { owned })
        model.load(); model.cancel(); try? await Task.sleep(nanoseconds: 150_000_000)
        XCTAssertNil(model.value); XCTAssertEqual(model.state, .idle)
        model.load(); owned = false; await settled(model)
        XCTAssertEqual(model.state, .unauthorized); XCTAssertNil(model.value)
    }
    func testSingleFlightAndBackgroundSafeRestoration() async throws {
        let value = try fixture()
        let model = MathVisualizationViewModel(api: FixtureVisualizationAPI(delay: 50_000_000), problem: value.problemId, ownsAccount: { true })
        model.load(); model.load(); await settled(model); XCTAssertEqual(model.state, .ready)
        model.cancel(); XCTAssertNil(model.value)
        model.load(); await settled(model); XCTAssertEqual(model.state, .ready)
    }
}

private actor OutcomeVisualizationAPI: MathVisualizationServicing {
    let status: String
    let reason: String
    init(status: String, reason: String) { self.status = status; self.reason = reason }
    func context(problem: UUID) async throws -> AttemptSourceIdentity {
        try JSONDecoder().decode(MathVisualization.self, from: FixtureVisualizationAPI.data()).source
    }
    func load(problem: UUID, source: AttemptSourceIdentity, kind: VisualizationKind, viewport: VisualizationViewport) async throws -> MathVisualization {
        var raw = try JSONSerialization.jsonObject(with: FixtureVisualizationAPI.data()) as! [String: Any]
        var result = raw["result"] as! [String: Any]
        result["type"] = kind.rawValue; result["status"] = status; result["reasonCode"] = reason
        for key in ["expression", "derivativeExpression", "naturalDomain", "sourceDomain", "solutionSet"] { result[key] = NSNull() }
        for key in ["segments", "features", "numberLine", "concepts"] { result[key] = [Any]() }
        raw["result"] = result; raw["problemId"] = problem.uuidString
        return try JSONDecoder().decode(MathVisualization.self, from: JSONSerialization.data(withJSONObject: raw))
    }
}

extension MathVisualizationTests {
    func testUnsupportedStoryAndUnavailableNeverShowFakeGraph() async throws {
        let value = try fixture()
        let story = MathVisualizationViewModel(api: OutcomeVisualizationAPI(status: "UNSUPPORTED", reason: "PROVENANCE_UNAVAILABLE"), problem: value.problemId, ownsAccount: { true })
        story.kind = .story; story.load(); await settled(story)
        XCTAssertEqual(story.state, .unsupported); XCTAssertEqual(story.errorKey, "visualization.provenance")
        XCTAssertTrue(story.visibleSegments.isEmpty)
        let unavailable = MathVisualizationViewModel(api: OutcomeVisualizationAPI(status: "UNAVAILABLE", reason: "TIMEOUT"), problem: value.problemId, ownsAccount: { true })
        unavailable.load(); await settled(unavailable)
        XCTAssertEqual(unavailable.state, .unavailable); XCTAssertTrue(unavailable.visibleSegments.isEmpty)
    }
    func testLateViewportCannotReplaceNewViewport() async throws {
        let value = try fixture()
        let model = MathVisualizationViewModel(api: FixtureVisualizationAPI(delay: 100_000_000), problem: value.problemId, ownsAccount: { true })
        model.load(); try? await Task.sleep(nanoseconds: 10_000_000)
        model.changeViewport(model.viewport.scaled(0.5)); await settled(model)
        XCTAssertEqual(model.value?.result.viewport.xMin, -5)
    }
}
