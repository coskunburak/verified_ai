import UIKit
import PDFKit

@MainActor
final class PersonalCheatSheetPDFRenderer {
    static let page = CGRect(x: 0, y: 0, width: 595.2, height: 841.8)
    static let minimumFontSize: CGFloat = 11
    private(set) var temporaryURL: URL?
    private(set) var omittedCount = 0
    func render(_ sheet: PreExamSnapshot, turkish: Bool) throws -> URL {
        try cleanup()
        try sheet.validate(user: sheet.userId, exam: sheet.examCode, schema: "exam-cheat-sheet-v1")
        let cards = sheet.activeTraps + sheet.highYieldFormulas + sheet.genericReferences
        let font = UIFont(name: "HelveticaNeue", size: Self.minimumFontSize) ?? UIFont.systemFont(ofSize: Self.minimumFontSize)
        let titleFont = UIFont(name: "HelveticaNeue-Bold", size: 16) ?? UIFont.boldSystemFont(ofSize: 16)
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: UIColor.black]
        let titleAttrs: [NSAttributedString.Key: Any] = [.font: titleFont, .foregroundColor: UIColor.black]
        let width: CGFloat = 251.6
        var layout: [(String, CGRect)] = []
        var column = 0; var y: CGFloat = 105
        var selected = 0
        for card in cards {
            let text = [turkish ? card.titleTr : card.titleEn, card.ruleDisplay,
                        turkish ? card.domainTr : card.domainEn, card.counterExampleDisplay].compactMap { $0 }.joined(separator: "\n")
            let height = ceil((text as NSString).boundingRect(with: CGSize(width: width, height: .greatestFiniteMagnitude),
                options: [.usesLineFragmentOrigin, .usesFontLeading], attributes: attrs, context: nil).height) + 12
            if y + height > 777 { column += 1; y = 105 }
            if column > 1 || height > 672 { break }
            layout.append((text, CGRect(x: 36 + CGFloat(column) * (width + 20), y: y, width: width, height: height)))
            y += height + 8; selected += 1
        }
        omittedCount = cards.count - selected
        let title = turkish ? "Sınav öncesi formül ve tuzak özeti" : "Pre-exam formula and trap summary"
        let note = turkish ? "Genel referanslar kişisel ustalık kanıtı değildir." : "Generic references are not evidence of personal mastery."
        let drawOptions: NSStringDrawingOptions = [.usesLineFragmentOrigin, .usesFontLeading]
        let data = UIGraphicsPDFRenderer(bounds: Self.page).pdfData { context in
            context.beginPage()
            (title as NSString).draw(with: CGRect(x: 36, y: 28, width: 523, height: 30), options: drawOptions, attributes: titleAttrs, context: nil)
            (note as NSString).draw(with: CGRect(x: 36, y: 64, width: 523, height: 35), options: drawOptions, attributes: attrs, context: nil)
            for (text, rect) in layout {
                (text as NSString).draw(with: rect, options: drawOptions, attributes: attrs, context: nil)
            }
            let footer = turkish ? "Gösterilmeyen kayıt: \(omittedCount). Tam liste uygulamada." : "Omitted entries: \(omittedCount). Full list in the app."
            (footer as NSString).draw(with: CGRect(x: 36, y: 797, width: 523, height: 28), options: drawOptions, attributes: attrs, context: nil)
        }
        guard let document = PDFDocument(data: data), document.pageCount == 1 else { throw PreExamError.integrity }
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("pre-exam-\(sheet.userId)-\(UUID()).pdf")
        try data.write(to: url, options: [.atomic, .completeFileProtection])
        temporaryURL = url
        return url
    }
    func cleanup() throws {
        if let temporaryURL, FileManager.default.fileExists(atPath: temporaryURL.path) {
            try FileManager.default.removeItem(at: temporaryURL)
        }
        temporaryURL = nil
    }
}
