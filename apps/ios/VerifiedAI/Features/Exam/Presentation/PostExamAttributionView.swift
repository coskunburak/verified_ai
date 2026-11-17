import SwiftUI

public struct PostExamAttributionView: View {
    @StateObject private var model: PostExamAttributionViewModel
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    public init(viewModel: PostExamAttributionViewModel) { _model = StateObject(wrappedValue: viewModel) }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                if model.isLoading { ProgressView("Hata karnesi yükleniyor") }
                if model.isOffline { text("Çevrimdışı kayıt gösteriliyor. Planı güncellemek için bağlan.").foregroundStyle(ColorTokens.warning) }
                if let error = model.errorMessage {
                    text(error).foregroundStyle(ColorTokens.danger)
                    Button("Tekrar Dene") { Task { await model.load() } }
                }
                if let report = model.report {
                    VStack(alignment: .leading, spacing: 12) {
                        text("Toplam Kayıp Net").font(.headline)
                        text(report.totalLostNet.formatted(.number.precision(.fractionLength(2)))).font(.largeTitle.bold())
                        ForEach(report.categorySummary) { category in
                            adaptiveRow {
                                text(category.headline).font(.headline)
                                text("\(category.itemCount) soru · \(category.lostNet, specifier: "%.2f") net · %\(category.sharePercentage, specifier: "%.1f")")
                            }
                        }
                    }.cardSurface()
                    text("H-03 · Saat Başına Net Kazanımı").font(.title2.bold())
                    if report.topRoiAllocations.isEmpty { text("Şu anda önerilecek ek çalışma bulunmuyor veya simülatör kapalı.") }
                    ForEach(report.topRoiAllocations) { item in
                        VStack(alignment: .leading, spacing: 12) {
                            text("\(item.rank). \(item.topicTitle)").font(.headline)
                            adaptiveRow {
                                text("+\(item.expectedNetGainPerHour, specifier: "%.2f") Net / Saat").foregroundStyle(ColorTokens.action).font(.title3.bold())
                                text("\(item.estimatedRemediationHours, specifier: "%.2f") saat · Toplam +\(item.estimatedNetRecoveryTotal, specifier: "%.2f") net")
                            }
                            text(item.rationale)
                            Button { model.startImmediateRemediation(for: item) } label: {
                                text(ExamRemediationRoute(item: item).buttonTitle).font(.headline)
                            }.buttonStyle(.bordered).tint(ColorTokens.action)
                        }.cardSurface()
                    }
                    Button { Task { await model.applyTopRoiToStudyPlan() } } label: {
                        text(model.isApplyingRebalance ? "Plan güncelleniyor…" : "En Yüksek Getirili 3 Konuyu 7 Günlük Planıma Uygula")
                            .font(.headline).frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .buttonStyle(.borderedProminent).tint(ColorTokens.action)
                    .disabled(model.isApplyingRebalance || model.isOffline || report.studyPlanRebalanced || report.topRoiAllocations.isEmpty)
                    .accessibilityIdentifier("postExamAttribution.applyStudyRebalanceButton")
                    if report.studyPlanRebalanced { text("7 günlük planın güncellendi.").foregroundStyle(ColorTokens.success) }
                    text("Soru Bazlı Hata Karnesi").font(.title2.bold())
                    ForEach(report.itemAttributions) { item in
                        VStack(alignment: .leading, spacing: 8) {
                            text("Soru \(item.itemOrder) · \(categoryTitle(item.attributionCategory))").font(.headline)
                            text("Harcanan: \(item.timeSpentSeconds) sn · Beklenen: \(item.expectedSolveSeconds) sn")
                            if let code = item.matchedMistakeCode { text(code).font(.caption).foregroundStyle(ColorTokens.warning) }
                            text(item.rationale)
                        }.cardSurface()
                    }
                }
            }.padding(16)
        }
        .background(ColorTokens.background)
        .navigationTitle("Hata Karnesi ve Net Kazanımı")
        .task { await model.load() }
        .sheet(item: $model.selectedRemediationRoute) { route in
            PostExamRemediationDestination(route: route, attributionModel: model)
        }
    }
    private func text(_ value: LocalizedStringKey) -> some View {
        Text(value).fixedSize(horizontal: false, vertical: true)
    }
    private func text(_ value: String) -> some View {
        Text(value).fixedSize(horizontal: false, vertical: true)
    }
    @ViewBuilder private func adaptiveRow<Content: View>(@ViewBuilder content: () -> Content) -> some View {
        if dynamicTypeSize.isAccessibilitySize { VStack(alignment: .leading, spacing: 8, content: content) }
        else { ViewThatFits(in: .horizontal) { HStack(alignment: .top, spacing: 12, content: content); VStack(alignment: .leading, spacing: 8, content: content) } }
    }
    private func categoryTitle(_ category: ExamAttributionCategory) -> String {
        switch category {
        case .conceptualGap: return "Kavramsal Eksiklik"
        case .recurringMistakeTrap: return "Tekrarlayan Tuzak"
        case .timePressureSlip: return "Süre / İşlem Kayması"
        case .strategicOmission: return "Stratejik Boş"
        }
    }
}
private extension View {
    func cardSurface() -> some View {
        self.padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .background(ColorTokens.surface).clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay(RoundedRectangle(cornerRadius: 16).stroke(ColorTokens.borderSubtle, lineWidth: 1))
    }
}
