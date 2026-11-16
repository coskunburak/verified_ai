import SwiftUI
import UIKit
import XCTest
@testable import VerifiedAI

@MainActor
final class ExamTurkishYksAccessibilityTests: XCTestCase {

    func testLongTurkishNewGenWordProblemWithFiveOptionsAndDiagramAltTextRendersAtAX3WithoutClipping() async throws {
        let userA = UUID(uuidString: "96000000-0000-4000-8000-000000000a01")!
        let assemblyId = UUID(uuidString: "94100000-0000-0000-8000-000000000201")!
        let sessionId = UUID(uuidString: "95000000-0000-4000-8000-000000000201")!
        let blueprintId = UUID(uuidString: "94000000-0000-0000-8000-000000000201")!
        let questionId = UUID(uuidString: "93000000-0000-4000-8000-000000000001")!

        let longTurkishStem = """
        Bir lojistik firmasının şehirler arası taşıma filosunda bulunan elektrikli kamyonetin bataryası tam dolu iken yalnızca otoyol sürüşünde 4 saatte, yalnızca şehir içi dağıtımda ise 10 saatte tamamen tükenmektedir. Sürücü sabah 08:00'de tam şarjla yola çıkıp 2 saat boyunca otoyolda ilerledikten sonra şehir içi dağıtıma geçiyor. Batarya kapasitesi yüzde 20'nin altına düştüğünde otomatik uyarı veren sistem dikkate alındığında, kalan batarya ile kesintisiz en fazla kaç saat şehir içi dağıtım yapılabilir? (Denklem: $\\frac{2}{4} + \\frac{t}{10} = 1$)
        """
        XCTAssertGreaterThan(longTurkishStem.count, 400)

        let figureAltText = "İki aşamalı batarya tüketim grafiği: İlk 2 saatte dik azalan otoyol tüketimi ve ardından eğimi daha düşük şehir içi dağıtım doğrusu."
        XCTAssertGreaterThanOrEqual(figureAltText.count, 10)

        let options: [AssembledQuestionOptionDTO] = [
            AssembledQuestionOptionDTO(optionKey: "A", displayOrder: 1, contentMarkdown: "$2$ saat (Yalnızca kalan otoyol süresini eşitleme hatası)", contentLatex: "2"),
            AssembledQuestionOptionDTO(optionKey: "B", displayOrder: 2, contentMarkdown: "$4$ saat", contentLatex: "4"),
            AssembledQuestionOptionDTO(optionKey: "C", displayOrder: 3, contentMarkdown: "$5$ saat", contentLatex: "5"),
            AssembledQuestionOptionDTO(optionKey: "D", displayOrder: 4, contentMarkdown: "$6$ saat", contentLatex: "6"),
            AssembledQuestionOptionDTO(optionKey: "E", displayOrder: 5, contentMarkdown: "$8$ saat", contentLatex: "8"),
        ]

        let item = ExamSessionItemDTO(
            id: UUID(),
            itemPosition: 1,
            questionItemId: questionId,
            questionCode: "TYT-TR-2026-WP-NF-004",
            sectionCode: "TYT_MATH",
            sectionOrder: 1,
            topicCode: "MATH.WORD_PROBLEMS.NUMBER_FRACTION",
            itemFormat: "SINGLE_CHOICE_MCQ",
            difficultyBand: .expert,
            stemMarkdown: longTurkishStem,
            optionsSnapshot: options,
            figureAltText: figureAltText,
            maxPoints: 1.0,
            negativeMarkingWeight: 0.25,
            responseState: .unvisited,
            trainingEligible: false
        )

        let session = ExamSessionDTO(
            sessionId: sessionId,
            userId: userA,
            assemblyId: assemblyId,
            blueprintId: blueprintId,
            examCode: "TR_YKS_TYT_MATH",
            examVersionNumber: 2,
            status: .inProgress,
            navigationPolicy: "FREE_JUMP",
            noHintPolicyEnforced: true,
            accommodationsSnapshot: AppliedAccommodationSnapshotDTO(extraTimeMultiplier: 1.0, highContrastMode: true, screenReaderOptimized: true),
            baseDurationSeconds: 1440,
            extraTimeMultiplier: 1.0,
            effectiveDurationSeconds: 1440,
            offlineGraceBufferSeconds: 120,
            startedAt: "2026-11-16T10:00:00Z",
            serverDeadlineAt: "2026-11-16T10:24:00Z",
            graceDeadlineAt: "2026-11-16T10:26:00Z",
            lastHeartbeatAt: "2026-11-16T10:00:00Z",
            totalItemCount: 1,
            answeredItemCount: 0,
            flaggedItemCount: 0,
            omittedItemCount: 1,
            scratchpadAttachmentCount: 0,
            clockSync: ExamServerClockSyncDTO(serverNow: "2026-11-16T10:00:00Z", remainingSeconds: 1440, graceRemainingSeconds: 1560),
            sectionProgress: [],
            items: [item],
            llmTokensUsed: 0,
            trainingEligible: false
        )

        let vm = ExamSessionViewModel(
            apiService: FixtureExamAPIService(),
            offlineStore: ExamOfflineSQLiteStore(inMemory: true),
            assemblyId: assemblyId,
            userId: userA,
            initialSession: session
        )

        XCTAssertEqual(vm.currentItem?.optionsSnapshot.count, 5)
        XCTAssertEqual(vm.currentItem?.optionsSnapshot.map { $0.optionKey }, ["A", "B", "C", "D", "E"])
        XCTAssertEqual(vm.currentItem?.figureAltText, figureAltText)
        XCTAssertFalse(vm.currentItem?.trainingEligible ?? true)

        let ax3View = ExamSessionView(viewModel: vm)
            .environment(\.sizeCategory, .accessibilityExtraExtraExtraLarge)

        let host = UIHostingController(rootView: ax3View)
        host.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        host.view.layoutIfNeeded()
        let fittingSize = host.sizeThatFits(in: CGSize(width: 390, height: CGFloat.greatestFiniteMagnitude))
        XCTAssertGreaterThan(fittingSize.height, 400)
        XCTAssertLessThanOrEqual(fittingSize.width, 390.5)
    }

    func testTurkishBlueprintAssemblyPreviewRendersAtAX3WithoutClipping() async throws {
        let userA = UUID(uuidString: "96000000-0000-4000-8000-000000000a01")!
        let vm = ExamBlueprintAssemblyViewModel(
            apiService: FixtureExamAPIService(),
            offlineStore: ExamOfflineSQLiteStore(inMemory: true),
            examDefinitionId: UUID(uuidString: "91000000-0000-4000-8000-000000000201")!,
            userId: userA
        )

        let ax3View = ExamBlueprintAssemblyView(viewModel: vm)
            .environment(\.sizeCategory, .accessibilityExtraExtraExtraLarge)
        let host = UIHostingController(rootView: ax3View)
        host.view.frame = CGRect(x: 0, y: 0, width: 390, height: 844)
        host.view.layoutIfNeeded()
        let fittingSize = host.sizeThatFits(in: CGSize(width: 390, height: CGFloat.greatestFiniteMagnitude))
        XCTAssertGreaterThan(fittingSize.height, 0)
        XCTAssertLessThanOrEqual(fittingSize.width, 390.5)
    }
}
