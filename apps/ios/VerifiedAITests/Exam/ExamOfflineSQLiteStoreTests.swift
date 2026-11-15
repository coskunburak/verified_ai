import XCTest
@testable import VerifiedAI

final class ExamOfflineSQLiteStoreTests: XCTestCase {

    func testBlueprintsAndAssembledAssessmentCacheAndMultiUserPurgeIsolation() async throws {
        let store = ExamOfflineSQLiteStore(inMemory: true)
        let service = FixtureExamAPIService()
        let tytDefId = UUID(uuidString: "91000000-0000-4000-8000-000000000101")!
        let userA = UUID(uuidString: "91000000-0000-4000-8000-000000000a01")!
        let userB = UUID(uuidString: "91000000-0000-4000-8000-000000000b02")!

        let blueprints = try await service.fetchBlueprints(examDefinitionId: tytDefId)
        await store.saveBlueprints(blueprints, examDefinitionId: tytDefId)

        let loadedBlueprints = await store.loadBlueprints(examDefinitionId: tytDefId)
        XCTAssertEqual(loadedBlueprints.count, 2)
        XCTAssertEqual(loadedBlueprints.first?.blueprintCode, "BP-TYT-MATH-DIAG-2026-V1")

        let diagBlueprint = loadedBlueprints[0]
        let assembledA = try await service.assembleAssessment(
            examDefinitionId: tytDefId,
            request: AssessmentAssemblyRequestDTO(
                userId: userA,
                blueprintId: diagBlueprint.id,
                assemblySeed: "USER-A-SEED-001",
                overrideTimeMultiplier: 1.25
            )
        )
        let assembledB = try await service.assembleAssessment(
            examDefinitionId: tytDefId,
            request: AssessmentAssemblyRequestDTO(
                userId: userB,
                blueprintId: diagBlueprint.id,
                assemblySeed: "USER-B-SEED-002",
                overrideTimeMultiplier: 1.50
            )
        )

        await store.saveAssembledAssessment(assembledA)
        await store.saveAssembledAssessment(assembledB)

        let fetchedA = await store.loadAssembledAssessment(userId: userA, assessmentId: assembledA.id)
        let fetchedB = await store.loadAssembledAssessment(userId: userB, assessmentId: assembledB.id)
        let crossAccess = await store.loadAssembledAssessment(userId: userA, assessmentId: assembledB.id)

        XCTAssertEqual(fetchedA?.id, assembledA.id)
        XCTAssertEqual(fetchedB?.id, assembledB.id)
        XCTAssertNil(crossAccess, "User A must not load User B's assessment by ID")

        // Purge User A data only
        await store.clearUserData(userId: userA)

        let afterPurgeA = await store.loadLatestAssembledAssessment(userId: userA, blueprintId: diagBlueprint.id)
        let afterPurgeB = await store.loadLatestAssembledAssessment(userId: userB, blueprintId: diagBlueprint.id)
        let blueprintsAfterPurge = await store.loadBlueprints(examDefinitionId: tytDefId)

        XCTAssertNil(afterPurgeA, "User A's cached assembled assessments must be deleted on clearUserData")
        XCTAssertEqual(afterPurgeB?.id, assembledB.id, "User B's cached assembled assessments must remain intact")
        XCTAssertEqual(blueprintsAfterPurge.count, 2, "Shared blueprint definitions must remain cached")
    }
}
