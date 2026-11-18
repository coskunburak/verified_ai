import SwiftUI
import UIKit

struct PreExamCramView: View {
    @StateObject var viewModel: PreExamCramViewModel
    @Environment(\.locale) private var locale
    @State private var renderer = PersonalCheatSheetPDFRenderer()
    @State private var share: PDFShareItem?
    @State private var pdfError = false
    private var turkish: Bool { locale.language.languageCode?.identifier == "tr" }
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                if viewModel.loading { ProgressView() }
                if viewModel.offline { Text("exam.preExam.offline") }
                if let message = viewModel.message { Text(LocalizedStringKey(message)) }
                if let bundle = viewModel.bundle {
                    Text(LocalizedStringKey("exam.preExam.time.\(bundle.review.windowStatus.rawValue)"))
                    if bundle.review.evidenceStatus == .insufficient { Text("exam.preExam.generic") }
                    ForEach(bundle.review.activeTraps) { card in cardView(card) }
                    ForEach(bundle.review.highYieldFormulas) { card in cardView(card) }
                    ForEach(bundle.review.genericReferences) { card in cardView(card) }
                    Button("exam.preExam.share") {
                        do { share = PDFShareItem(url: try renderer.render(bundle.sheet, turkish: turkish)) }
                        catch { pdfError = true }
                    }.accessibilityHint(Text("exam.preExam.shareHint"))
                }
                Button("exam.preExam.retry") { Task { await viewModel.load() } }.disabled(viewModel.loading)
            }.padding()
        }
        .navigationTitle(Text("exam.preExam.title"))
        .task { await viewModel.load() }
        .sheet(item: $share, onDismiss: cleanup) { item in
            PreExamShareSheet(url: item.url) { completed in
                if completed { Task { await viewModel.recordExport(key: item.id.uuidString) } }
                share = nil
            }
        }
        .onDisappear { if share == nil { cleanup() } }
        .alert("exam.preExam.pdfError", isPresented: $pdfError) { Button("OK", role: .cancel) {} }
    }
    private func cleanup() {
        do { try renderer.cleanup() } catch { pdfError = true }
    }
    private func cardView(_ card: PreExamCard) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(turkish ? card.titleTr : card.titleEn).font(.headline)
            Text(card.ruleDisplay)
            Text(turkish ? card.domainTr : card.domainEn)
            if let counter = card.counterExampleDisplay { Text(counter) }
            if let prompt = turkish ? card.quickCheckTr : card.quickCheckEn { Text(prompt) }
            if let answer = card.answerDisplay { Text(answer) }
        }
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: .infinity, alignment: .leading).padding()
        .background(Color(uiColor: .secondarySystemBackground))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.secondary, lineWidth: 1))
        .accessibilityElement(children: .combine)
    }
}
private struct PDFShareItem: Identifiable { let id = UUID(); let url: URL }
private struct PreExamShareSheet: UIViewControllerRepresentable {
    let url: URL
    let completion: (Bool) -> Void
    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        controller.completionWithItemsHandler = { _, completed, _, _ in completion(completed) }
        return controller
    }
    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
