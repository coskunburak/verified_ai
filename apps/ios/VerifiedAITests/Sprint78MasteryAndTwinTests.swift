import XCTest
import SwiftUI
@testable import VerifiedAI

@MainActor
final class Sprint78MasteryAndTwinTests: XCTestCase {

    // MARK: - Test Mocks

    final class MockSkillGraphService: SkillGraphServicing, @unchecked Sendable {
        var shouldFail: Bool = false
        var graphToReturn: SkillGraphProjection?

        func fetchGraph(curriculum: String) async throws -> SkillGraphProjection {
            if shouldFail {
                throw URLError(.badServerResponse)
            }
            if let graph = graphToReturn {
                return graph
            }
            return SkillGraphProjection(
                curriculumCode: curriculum,
                nodes: [
                    SkillGraphNode(
                        skillCode: "ALG_LIN_ISO_01",
                        displayName: "Linear Variable Isolation",
                        generation: 0,
                        prerequisiteSkillCodes: [],
                        effectiveMastery: 0.45,
                        status: .practicing,
                        directPracticeCount: 3,
                        consecutiveFailures: 0,
                        blockedByPrerequisiteBottleneck: false
                    ),
                    SkillGraphNode(
                        skillCode: "ALG_LIN_EQ_02",
                        displayName: "Multi-Step Linear Equations",
                        generation: 1,
                        prerequisiteSkillCodes: ["ALG_LIN_ISO_01"],
                        effectiveMastery: 0.25,
                        status: .atRiskRemediationRequired,
                        directPracticeCount: 4,
                        consecutiveFailures: 2,
                        blockedByPrerequisiteBottleneck: true
                    )
                ],
                edges: [
                    SkillGraphEdge(
                        fromSkillCode: "ALG_LIN_ISO_01",
                        toSkillCode: "ALG_LIN_EQ_02",
                        edgeWeight: 1.0,
                        prerequisiteBottleneck: true
                    )
                ],
                generatedAt: Date()
            )
        }
    }

    final class MockTwinChallengeService: TwinChallengeServicing, @unchecked Sendable {
        var remainingLockSeconds: Int = 60
        var submittedPayload: TwinSubmissionPayload?

        func generateTwin(attemptId: UUID) async throws -> VerifiedTwinChallenge {
            return VerifiedTwinChallenge(
                twinId: UUID(),
                attemptId: attemptId,
                targetSkillCode: "ALG_LIN_EQ_02",
                rootCauseSkillCode: "ALG_LIN_ISO_01",
                problemStatement: "Solve for x: 3x + 4 = 19",
                problemLatex: "3x + 4 = 19",
                lockUntil: Date().addingTimeInterval(TimeInterval(remainingLockSeconds)),
                remainingLockSeconds: remainingLockSeconds,
                status: remainingLockSeconds > 0 ? .activeLocked : .activeUnlocked,
                evidenceClassification: "INITIAL_SYNTHESIS",
                remediationCreditGranted: 0.0,
                createdAt: Date()
            )
        }

        func submitTwin(twinId: UUID, payload: TwinSubmissionPayload) async throws -> VerifiedTwinChallenge {
            self.submittedPayload = payload
            let isUnassistedSuccess = payload.submissionType == "SUBMIT_ANSWER"
            return VerifiedTwinChallenge(
                twinId: twinId,
                attemptId: UUID(),
                targetSkillCode: "ALG_LIN_EQ_02",
                rootCauseSkillCode: "ALG_LIN_ISO_01",
                problemStatement: "Solve for x: 3x + 4 = 19",
                problemLatex: "3x + 4 = 19",
                lockUntil: Date(),
                remainingLockSeconds: 0,
                status: isUnassistedSuccess ? .verifiedUnassistedSuccess : .solutionRevealedAssisted,
                evidenceClassification: isUnassistedSuccess ? "VERIFIED_UNASSISTED_SUCCESS" : "SOLUTION_REVEALED_ASSISTED",
                remediationCreditGranted: isUnassistedSuccess ? 0.1500 : 0.0000,
                createdAt: Date()
            )
        }
    }

    // MARK: - Mastery Graph ViewModel Tests

    func testGraphLoadsSuccessfullyAndProjectsTopologicalTiers() async throws {
        let service = MockSkillGraphService()
        let vm = MasteryGraphViewModel(curriculumCode: "MATH_GRADE_09", service: service, ownsAccount: { true })

        vm.loadGraph()

        // Wait for task to finish
        for _ in 0..<50 {
            if !vm.isLoading { break }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }

        XCTAssertFalse(vm.isLoading)
        XCTAssertNil(vm.errorMessageKey)
        let projection = try XCTUnwrap(vm.projection)
        XCTAssertEqual(projection.curriculumCode, "MATH_GRADE_09")
        XCTAssertEqual(projection.nodes.count, 2)
        XCTAssertEqual(projection.edges.count, 1)

        let targetNode = try XCTUnwrap(projection.nodes.first { $0.skillCode == "ALG_LIN_EQ_02" })
        XCTAssertTrue(targetNode.blockedByPrerequisiteBottleneck)
        XCTAssertEqual(targetNode.status, .atRiskRemediationRequired)
        XCTAssertEqual(targetNode.consecutiveFailures, 2)
    }

    func testUnauthorizedAccountAccessBlocksGraphProjection() async throws {
        let service = MockSkillGraphService()
        let vm = MasteryGraphViewModel(curriculumCode: "MATH_GRADE_09", service: service, ownsAccount: { false })

        vm.loadGraph()

        for _ in 0..<50 {
            if !vm.isLoading { break }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }

        XCTAssertFalse(vm.isLoading)
        XCTAssertNil(vm.projection)
        XCTAssertEqual(vm.errorMessageKey, "mastery.graph.error.unauthorized")
    }

    func testTaskFencingCancelsInFlightGraphRequestOnSuspend() async throws {
        let service = MockSkillGraphService()
        let vm = MasteryGraphViewModel(curriculumCode: "MATH_GRADE_09", service: service, ownsAccount: { true })

        vm.loadGraph()
        vm.cancel()

        XCTAssertFalse(vm.isLoading)
    }

    // MARK: - Twin Challenge ViewModel Tests

    func test60SecondLockMonotonicCountdown() async throws {
        let service = MockTwinChallengeService()
        service.remainingLockSeconds = 60
        let attemptId = UUID()
        let vm = TwinChallengeViewModel(attemptId: attemptId, service: service, ownsAccount: { true })

        vm.loadChallenge()

        for _ in 0..<50 {
            if !vm.isLoading { break }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }

        XCTAssertFalse(vm.isLoading)
        XCTAssertNotNil(vm.challenge)
        XCTAssertTrue(vm.isLocked)
        XCTAssertEqual(vm.remainingSeconds, 60)
        vm.cancel()
    }

    func testEarlyRevealTriggersForfeitConfirmationWithoutImmediateForfeit() async throws {
        let service = MockTwinChallengeService()
        service.remainingLockSeconds = 45
        let attemptId = UUID()
        let vm = TwinChallengeViewModel(attemptId: attemptId, service: service, ownsAccount: { true })

        vm.loadChallenge()

        for _ in 0..<50 {
            if !vm.isLoading { break }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }

        XCTAssertTrue(vm.isLocked)
        vm.requestRevealSolution()

        XCTAssertTrue(vm.showEarlyForfeitConfirmation)
        XCTAssertNil(service.submittedPayload)
        vm.cancel()
    }

    func testConfirmEarlyForfeitSubmitsWithForfeitTrueAndSetsZeroRemediation() async throws {
        let service = MockTwinChallengeService()
        service.remainingLockSeconds = 30
        let attemptId = UUID()
        let vm = TwinChallengeViewModel(attemptId: attemptId, service: service, ownsAccount: { true })

        vm.loadChallenge()

        for _ in 0..<50 {
            if !vm.isLoading { break }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }

        vm.confirmEarlyForfeitAndReveal()

        for _ in 0..<50 {
            if !vm.isSubmitting { break }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }

        XCTAssertFalse(vm.showEarlyForfeitConfirmation)
        let payload = try XCTUnwrap(service.submittedPayload)
        XCTAssertEqual(payload.submissionType, "REVEAL_SOLUTION")
        XCTAssertTrue(payload.forceEarlyForfeit)

        let result = try XCTUnwrap(vm.challenge)
        XCTAssertEqual(result.status, .solutionRevealedAssisted)
        XCTAssertEqual(result.remediationCreditGranted, 0.0000)
        vm.cancel()
    }

    func testUnassistedAnswerSubmissionAwardsRemediationCredit() async throws {
        let service = MockTwinChallengeService()
        service.remainingLockSeconds = 40
        let attemptId = UUID()
        let vm = TwinChallengeViewModel(attemptId: attemptId, service: service, ownsAccount: { true })

        vm.loadChallenge()

        for _ in 0..<50 {
            if !vm.isLoading { break }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }

        vm.learnerAnswer = "x = 5"
        vm.submitAnswer()

        for _ in 0..<50 {
            if !vm.isSubmitting { break }
            try? await Task.sleep(nanoseconds: 20_000_000)
        }

        let payload = try XCTUnwrap(service.submittedPayload)
        XCTAssertEqual(payload.submissionType, "SUBMIT_ANSWER")
        XCTAssertEqual(payload.learnerAnswerLatex, "x = 5")
        XCTAssertFalse(payload.forceEarlyForfeit)

        let result = try XCTUnwrap(vm.challenge)
        XCTAssertEqual(result.status, .verifiedUnassistedSuccess)
        XCTAssertEqual(result.remediationCreditGranted, 0.1500)
        vm.cancel()
    }
}
