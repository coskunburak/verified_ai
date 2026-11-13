import Foundation
import PDFKit
#if canImport(UIKit)
import UIKit
#endif

enum TutorSharePacketPDFError: Error, Equatable {
    case emptyOutput
    case pageCountInvariantFailed(Int)
}

protocol TutorSharePacketPDFRendering: Sendable {
    func renderPDFData(from packet: WeeklySharePacketV1) throws -> Data
    func writeTemporaryPDF(from packet: WeeklySharePacketV1) throws -> URL
}

struct TutorSharePacketPDFRenderer: TutorSharePacketPDFRendering, Sendable {
    static let a4PageRect = CGRect(x: 0, y: 0, width: 595.2, height: 841.8)
    private static let marginX: CGFloat = 40
    private static let contentWidth: CGFloat = 595.2 - 80

    func renderPDFData(from packet: WeeklySharePacketV1) throws -> Data {
        #if canImport(UIKit)
        let format = UIGraphicsPDFRendererFormat()
        format.documentInfo = [
            kCGPDFContextTitle as String: "Verified AI Weekly Progress Packet (\(packet.weekIso))",
            kCGPDFContextCreator as String: "Verified AI On-Device Renderer"
        ]

        let renderer = UIGraphicsPDFRenderer(bounds: Self.a4PageRect, format: format)
        let data = renderer.pdfData { context in
            context.beginPage()
            var cursorY: CGFloat = 36
            cursorY = drawHeader(packet: packet, startY: cursorY, in: context.cgContext)
            cursorY = drawExecutiveKpis(packet: packet, startY: cursorY, in: context.cgContext)
            cursorY = drawMasterySnapshot(packet: packet, startY: cursorY, in: context.cgContext)
            cursorY = drawFocusThemes(packet: packet, startY: cursorY)
            _ = drawNextActions(packet: packet, startY: cursorY)
            drawPrivacyAttestationFooter(packet: packet, in: context.cgContext)
        }

        guard !data.isEmpty else {
            throw TutorSharePacketPDFError.emptyOutput
        }
        if let pdf = PDFDocument(data: data), pdf.pageCount != 1 {
            throw TutorSharePacketPDFError.pageCountInvariantFailed(pdf.pageCount)
        }
        return data
        #else
        throw TutorSharePacketPDFError.emptyOutput
        #endif
    }

    func writeTemporaryPDF(from packet: WeeklySharePacketV1) throws -> URL {
        let data = try renderPDFData(from: packet)
        let sanitizedWeek = packet.weekIso.replacingOccurrences(of: "/", with: "-")
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("VerifiedAI-Weekly-Share-\(sanitizedWeek)-\(UUID().uuidString.prefix(8)).pdf")
        try data.write(to: url, options: [.atomic])
        return url
    }

    #if canImport(UIKit)
    private func drawHeader(packet: WeeklySharePacketV1, startY: CGFloat, in cg: CGContext) -> CGFloat {
        let isTr = packet.locale == "tr"
        let roleLabel = packet.recipientRole == .tutor
            ? (isTr ? "Özel Ders / Eğitmen Özeti" : "Tutor Review Packet")
            : (isTr ? "Veli İlerleme Özeti" : "Parent Progress Packet")

        cg.saveGState()
        cg.setFillColor(UIColor.systemIndigo.withAlphaComponent(0.08).cgColor)
        cg.fill(CGRect(x: Self.marginX, y: startY, width: Self.contentWidth, height: 68))
        cg.restoreGState()

        let titleAttrs: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 17, weight: .bold),
            .foregroundColor: UIColor.label
        ]
        let subAttrs: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 11, weight: .medium),
            .foregroundColor: UIColor.secondaryLabel
        ]
        let title = isTr ? "Verified AI — Haftalık Öğrenme Paylaşım Raporu" : "Verified AI — Weekly Learning Share Packet"
        (title as NSString).draw(
            in: CGRect(x: Self.marginX + 12, y: startY + 10, width: Self.contentWidth - 24, height: 24),
            withAttributes: titleAttrs
        )
        let subtitle = "\(roleLabel)  •  \(packet.displayAlias)  •  \(packet.weekIso) (\(packet.weekStartDate) – \(packet.weekEndDate))"
        (subtitle as NSString).draw(
            in: CGRect(x: Self.marginX + 12, y: startY + 38, width: Self.contentWidth - 24, height: 20),
            withAttributes: subAttrs
        )
        return startY + 84
    }

    private func drawExecutiveKpis(packet: WeeklySharePacketV1, startY: CGFloat, in cg: CGContext) -> CGFloat {
        let isTr = packet.locale == "tr"
        let sectionTitle = isTr ? "Haftalık Özet Göstergeleri" : "Weekly Executive Metrics"
        drawSectionTitle(sectionTitle, y: startY)

        let m = packet.executiveMetrics
        let ratePct = Int((m.independentShareRate * 100.0).rounded())
        let cards: [(String, String)] = [
            (isTr ? "Çözülen Problem" : "Solved Items", "\(m.solvedAttemptsCount)"),
            (isTr ? "Bağımsız İkiz" : "Independent Twins", "\(m.independentTwinsSolvedCount)"),
            (isTr ? "Çalışma (dk)" : "Study Minutes", "\(m.studyMinutes)"),
            (isTr ? "Aktif Gün" : "Active Days", "\(m.activeDaysCount)/7"),
            (isTr ? "Bağımsız Oran" : "Independent Rate", "\(ratePct)%")
        ]

        let boxSpacing: CGFloat = 8
        let boxWidth = (Self.contentWidth - CGFloat(cards.count - 1) * boxSpacing) / CGFloat(cards.count)
        let boxY = startY + 22
        let boxHeight: CGFloat = 52

        for (index, card) in cards.enumerated() {
            let x = Self.marginX + CGFloat(index) * (boxWidth + boxSpacing)
            let rect = CGRect(x: x, y: boxY, width: boxWidth, height: boxHeight)
            cg.saveGState()
            cg.setStrokeColor(UIColor.separator.cgColor)
            cg.setLineWidth(0.75)
            cg.stroke(rect)
            cg.restoreGState()

            (card.1 as NSString).draw(
                in: CGRect(x: x + 6, y: boxY + 8, width: boxWidth - 12, height: 20),
                withAttributes: [
                    .font: UIFont.monospacedDigitSystemFont(ofSize: 15, weight: .bold),
                    .foregroundColor: UIColor.systemIndigo
                ]
            )
            (card.0 as NSString).draw(
                in: CGRect(x: x + 6, y: boxY + 30, width: boxWidth - 12, height: 16),
                withAttributes: [
                    .font: UIFont.systemFont(ofSize: 8.5, weight: .regular),
                    .foregroundColor: UIColor.secondaryLabel
                ]
            )
        }
        return boxY + boxHeight + 18
    }

    private func drawMasterySnapshot(packet: WeeklySharePacketV1, startY: CGFloat, in cg: CGContext) -> CGFloat {
        let isTr = packet.locale == "tr"
        drawSectionTitle(isTr ? "Beceri Ustalık Durumu" : "Mastery Snapshot", y: startY)
        var y = startY + 22
        let rows = Array(packet.masterySnapshot.prefix(4))
        if rows.isEmpty {
            let emptyText = isTr
                ? "Henüz beceri düzeyi ölçümü kaydedilmedi; ilk odak adımları aşağıdadır."
                : "No skill mastery checkpoints recorded yet; starter targets are listed below."
            (emptyText as NSString).draw(
                in: CGRect(x: Self.marginX, y: y, width: Self.contentWidth, height: 18),
                withAttributes: [.font: UIFont.systemFont(ofSize: 10), .foregroundColor: UIColor.secondaryLabel]
            )
            return y + 26
        }

        for row in rows {
            let pct = Int((row.masteryScore * 100.0).rounded())
            let deltaPct = Int((row.masteryDelta * 100.0).rounded())
            let deltaLabel = deltaPct >= 0 ? "+\(deltaPct)%" : "\(deltaPct)%"
            let line = "\(row.skillTitle)"
            (line as NSString).draw(
                in: CGRect(x: Self.marginX, y: y, width: Self.contentWidth - 120, height: 16),
                withAttributes: [.font: UIFont.systemFont(ofSize: 10.5, weight: .medium), .foregroundColor: UIColor.label]
            )
            ("\(pct)% (\(deltaLabel))" as NSString).draw(
                in: CGRect(x: Self.marginX + Self.contentWidth - 115, y: y, width: 115, height: 16),
                withAttributes: [.font: UIFont.monospacedDigitSystemFont(ofSize: 10.5, weight: .semibold), .foregroundColor: UIColor.secondaryLabel]
            )
            let barRect = CGRect(x: Self.marginX, y: y + 17, width: Self.contentWidth, height: 6)
            cg.saveGState()
            cg.setFillColor(UIColor.systemGray5.cgColor)
            cg.fill(barRect)
            cg.setFillColor(UIColor.systemIndigo.cgColor)
            cg.fill(CGRect(x: barRect.minX, y: barRect.minY, width: barRect.width * CGFloat(max(0.0, min(1.0, row.masteryScore))), height: barRect.height))
            cg.restoreGState()
            y += 30
        }
        return y + 10
    }

    private func drawFocusThemes(packet: WeeklySharePacketV1, startY: CGFloat) -> CGFloat {
        let isTr = packet.locale == "tr"
        drawSectionTitle(isTr ? "Desteklenen Odak Temaları" : "Constructive Focus Themes", y: startY)
        var y = startY + 22
        let items = Array(packet.focusThemes.prefix(3))
        if items.isEmpty {
            let text = isTr
                ? "Bu hafta tekrarlayan dikkat gerektiren belirgin bir tema kaydedilmedi."
                : "No repeated slip patterns needed extra attention this week."
            (text as NSString).draw(
                in: CGRect(x: Self.marginX, y: y, width: Self.contentWidth, height: 18),
                withAttributes: [.font: UIFont.systemFont(ofSize: 10), .foregroundColor: UIColor.secondaryLabel]
            )
            return y + 26
        }
        for item in items {
            let line = "• \(item.label) (\(item.skillTitle)) — \(item.guidance)"
            (line as NSString).draw(
                in: CGRect(x: Self.marginX, y: y, width: Self.contentWidth, height: 32),
                withAttributes: [.font: UIFont.systemFont(ofSize: 10), .foregroundColor: UIColor.label]
            )
            y += 34
        }
        return y + 8
    }

    private func drawNextActions(packet: WeeklySharePacketV1, startY: CGFloat) -> CGFloat {
        let isTr = packet.locale == "tr"
        drawSectionTitle(isTr ? "Önerilen Sonraki Adımlar" : "Explainable Recommended Next Steps", y: startY)
        var y = startY + 22
        let actions = Array(packet.recommendedNextActions.prefix(3))
        for action in actions {
            let priorityPct = Int((action.priorityScore * 100.0).rounded())
            let header = "• \(action.headline) [~\(action.estimatedMinutes) dk • \(priorityPct)%]"
            (header as NSString).draw(
                in: CGRect(x: Self.marginX, y: y, width: Self.contentWidth, height: 16),
                withAttributes: [.font: UIFont.systemFont(ofSize: 10.5, weight: .semibold), .foregroundColor: UIColor.label]
            )
            (action.explanationSummary as NSString).draw(
                in: CGRect(x: Self.marginX + 10, y: y + 16, width: Self.contentWidth - 10, height: 28),
                withAttributes: [.font: UIFont.systemFont(ofSize: 9.5), .foregroundColor: UIColor.secondaryLabel]
            )
            y += 48
            if y > 710 { break }
        }
        return y
    }

    private func drawPrivacyAttestationFooter(packet: WeeklySharePacketV1, in cg: CGContext) {
        let footerTop: CGFloat = 752
        cg.saveGState()
        cg.setFillColor(UIColor.systemGray6.cgColor)
        cg.fill(CGRect(x: Self.marginX, y: footerTop, width: Self.contentWidth, height: 58))
        cg.setStrokeColor(UIColor.systemIndigo.withAlphaComponent(0.4).cgColor)
        cg.setLineWidth(0.75)
        cg.stroke(CGRect(x: Self.marginX, y: footerTop, width: Self.contentWidth, height: 58))
        cg.restoreGState()

        let attest = packet.privacyAttestation
        let headerText = "G-PRIV-03 Privacy Attestation Footer • training_eligible: \(packet.trainingEligible) • piiExcluded: \(attest.piiExcluded)"
        (headerText as NSString).draw(
            in: CGRect(x: Self.marginX + 10, y: footerTop + 8, width: Self.contentWidth - 20, height: 14),
            withAttributes: [.font: UIFont.systemFont(ofSize: 8.5, weight: .bold), .foregroundColor: UIColor.systemIndigo]
        )
        let detailsText = "rawTutorTranscriptsIncluded=\(attest.rawTutorTranscriptsIncluded) | rawStudentPhotosIncluded=\(attest.rawStudentPhotosIncluded) | exactSessionTimestampsIncluded=\(attest.exactSessionTimestampsIncluded). \(attest.shareableScopeSummary)"
        (detailsText as NSString).draw(
            in: CGRect(x: Self.marginX + 10, y: footerTop + 23, width: Self.contentWidth - 20, height: 30),
            withAttributes: [.font: UIFont.systemFont(ofSize: 7.5), .foregroundColor: UIColor.secondaryLabel]
        )
    }

    private func drawSectionTitle(_ title: String, y: CGFloat) {
        (title as NSString).draw(
            in: CGRect(x: Self.marginX, y: y, width: Self.contentWidth, height: 18),
            withAttributes: [
                .font: UIFont.systemFont(ofSize: 12, weight: .bold),
                .foregroundColor: UIColor.label
            ]
        )
    }
    #endif
}
