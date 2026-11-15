import XCTest
@testable import VerifiedAI

@MainActor
final class ExamBlueprintAssemblyViewModelTests: XCTestCase {

    func testLoadBlueprintsSelectAndDeterministicAssemblyWithAccommodations() async throws {
        let store = ExamOfflineSQLiteStore(inMemory: true)
        let service = FixtureExamAPIService()
        let userA = UUID(uuidString: "91000000-0000-4000-8000-000000000a01")!
        let tytDefId = ExamBlueprintAssemblyViewModel.defaultExamDefinitionId

        let vm = ExamBlueprintAssemblyViewModel(
            apiService: service,
            offlineStore: store,
            examDefinitionId: tytDefId,
            userId: userA
        )

        await vm.loadBlueprints()
        XCTAssertEqual(vm.blueprints.count, 2)
        XCTAssertFalse(vm.isOfflineCached)
        XCTAssertEqual(vm.selectedBlueprint?.blueprintCode, "BP-TYT-MATH-DIAG-2026-V1")
        XCTAssertEqual(vm.previewBaseDurationSeconds, 900)

        vm.selectedTimeMultiplier = 1.00
        XCTAssertEqual(vm.previewEffectiveDurationSeconds, 900)
        vm.selectedTimeMultiplier = 1.25
        XCTAssertEqual(vm.previewEffectiveDurationSeconds, 1125)
        vm.selectedTimeMultiplier = 1.50
        XCTAssertEqual(vm.previewEffectiveDurationSeconds, 1350)
        vm.selectedTimeMultiplier = 2.00
        XCTAssertEqual(vm.previewEffectiveDurationSeconds, 1800)

        vm.selectedTimeMultiplier = 1.50
        vm.highContrastMode = true
        vm.screenReaderOptimized = true
        vm.assemblySeed = "TYT-SEED-ALPHA-2026"

        await vm.assembleSelectedBlueprint()

        guard let firstAssembled = vm.assembledAssessment else {
            XCTFail("Expected assembledAssessment to be populated")
            return
        }
        XCTAssertEqual(vm.statusLocalizationKey, "exam.assembly.status.ready")
        XCTAssertNil(vm.errorLocalizationKey)
        XCTAssertEqual(firstAssembled.totalQuestionCount, 8)
        XCTAssertEqual(firstAssembled.baseDurationSeconds, 900)
        XCTAssertEqual(firstAssembled.effectiveDurationSeconds, 1350)
        XCTAssertEqual(firstAssembled.accommodationsSnapshot.extraTimeMultiplier, 1.50)
        XCTAssertTrue(firstAssembled.accommodationsSnapshot.highContrastMode)
        XCTAssertTrue(firstAssembled.accommodationsSnapshot.screenReaderOptimized)
        XCTAssertTrue(firstAssembled.accommodationsSnapshot.extendedBreakPrompts)
        XCTAssertFalse(firstAssembled.trainingEligible)
        XCTAssertEqual(firstAssembled.reproducibilityHash.count, 64)

        // Same seed on a second service instance must produce identical reproducibilityHash and item sequence
        let vmReplay = ExamBlueprintAssemblyViewModel(
            apiService: FixtureExamAPIService(),
            offlineStore: store,
            examDefinitionId: tytDefId,
            userId: userA
        )
        await vmReplay.loadBlueprints()
        vmReplay.selectedTimeMultiplier = 1.50
        vmReplay.assemblySeed = "TYT-SEED-ALPHA-2026"
        await vmReplay.assembleSelectedBlueprint()
        XCTAssertEqual(vmReplay.assembledAssessment?.reproducibilityHash, firstAssembled.reproducibilityHash)
        XCTAssertEqual(
            vmReplay.assembledAssessment?.items.map(\.questionItemId),
            firstAssembled.items.map(\.questionItemId)
        )

        // Different seed produces distinct reproducibilityHash
        vmReplay.assemblySeed = "TYT-SEED-BETA-2026"
        await vmReplay.assembleSelectedBlueprint()
        XCTAssertNotEqual(vmReplay.assembledAssessment?.reproducibilityHash, firstAssembled.reproducibilityHash)
    }

    func testOfflineFallbackLoadsCachedBlueprintsAndLatestAssembledAssessment() async throws {
        let store = ExamOfflineSQLiteStore(inMemory: true)
        let onlineService = FixtureExamAPIService()
        let userA = UUID(uuidString: "91000000-0000-4000-8000-000000000a01")!
        let tytDefId = ExamBlueprintAssemblyViewModel.defaultExamDefinitionId

        let onlineVm = ExamBlueprintAssemblyViewModel(
            apiService: onlineService,
            offlineStore: store,
            examDefinitionId: tytDefId,
            userId: userA
        )
        await onlineVm.loadBlueprints()
        onlineVm.assemblySeed = "OFFLINE-CACHE-SEED-2026"
        await onlineVm.assembleSelectedBlueprint()
        let cachedId = onlineVm.assembledAssessment?.id
        XCTAssertNotNil(cachedId)

        // Switch to offline service
        let offlineVm = ExamBlueprintAssemblyViewModel(
            apiService: FixtureExamAPIService(simulateOffline: true),
            offlineStore: store,
            examDefinitionId: tytDefId,
            userId: userA
        )
        await offlineVm.loadBlueprints()
        XCTAssertTrue(offlineVm.isOfflineCached)
        XCTAssertEqual(offlineVm.blueprints.count, 2)
        XCTAssertEqual(offlineVm.assembledAssessment?.id, cachedId)
    }
}
