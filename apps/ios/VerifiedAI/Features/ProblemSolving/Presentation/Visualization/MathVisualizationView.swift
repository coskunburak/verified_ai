import SwiftUI
import Charts

struct MathVisualizationView: View {
    @Bindable var model: MathVisualizationViewModel
    @ScaledMetric(relativeTo: .title3) private var mathPointSize = 22
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                Text("visualization.scope").font(.callout)
                Picker("visualization.type", selection: $model.kind) {
                    ForEach(VisualizationKind.allCases, id: \.self) { kind in Text(LocalizedStringKey(kind.key)).tag(kind) }
                }.pickerStyle(.menu).accessibilityIdentifier("visualization.kind")
                if model.state == .loading { ProgressView("visualization.loading") }
                else if model.state == .ready, let result = model.value?.result {
                    if let expression = result.expression { math(expression, title: "visualization.expression") }
                    if let derivative = result.derivativeExpression { math(derivative, title: "visualization.derivativeExpression") }
                    if model.kind != .numberLine {
                        Toggle("visualization.function", isOn: $model.showFunction)
                        if model.kind == .derivative { Toggle("visualization.derivative", isOn: $model.showDerivative) }
                        FunctionPlotView(segments: model.visibleSegments, viewport: model.viewport, selectedX: $model.selectedX)
                        if let point = model.selectedSample {
                            Text(String(format: NSLocalizedString("visualization.selected", comment: "Approximate selected sample"), point.x, point.y))
                                .monospacedDigit().accessibilityIdentifier("visualization.selection")
                        }
                        HStack {
                            Button("visualization.previous") { selectSample(-1) }
                            Button("visualization.next") { selectSample(1) }
                        }.buttonStyle(.bordered).controlSize(.large).frame(minHeight: 44)
                        Text("visualization.approximate")
                    }
                    viewportControls
                    Text("visualization.numberLine").font(.headline)
                    NumberLineView(bands: result.numberLine, viewport: model.viewport)
                    if result.numberLine.isEmpty { Text("visualization.empty") }
                    Text("visualization.exact").font(.headline)
                    ForEach(Array(result.features.enumerated()), id: \.offset) { _, feature in
                        VStack(alignment: .leading) {
                            Text(LocalizedStringKey("visualization.feature." + feature.code))
                            Text("(\(feature.x), \(feature.y))").textSelection(.enabled)
                        }.accessibilityElement(children: .combine)
                    }
                    if let domain = result.naturalDomain { math(domain, title: "visualization.naturalDomain") }
                    if let domain = result.sourceDomain { math(domain, title: "visualization.sourceDomain") }
                    if let set = result.solutionSet { math(set, title: "visualization.solutionSet") }
                    ForEach(result.concepts, id: \.self) { code in
                        Text(LocalizedStringKey("visualization.concept." + code)).fixedSize(horizontal: false, vertical: true)
                    }
                } else if model.state != .idle {
                    Label(LocalizedStringKey(model.errorKey), systemImage: "exclamationmark.triangle")
                        .accessibilityIdentifier("visualization.error")
                    if model.state != .unauthorized {
                        Button("visualization.retry") { model.load() }.buttonStyle(.borderedProminent)
                            .frame(minHeight: 44).accessibilityIdentifier("visualization.retry")
                    }
                }
            }.padding(SpacingTokens.lg)
        }
        .background(ColorTokens.background).foregroundStyle(ColorTokens.textPrimary)
        .navigationTitle("visualization.title").accessibilityIdentifier("visualization.screen")
        .transaction { if reduceMotion { $0.animation = nil } }
        .task { model.load() }.onDisappear { model.cancel() }
        .onChange(of: model.kind) { _, _ in model.changeKind() }
        .onChange(of: scenePhase) { _, phase in if phase == .active { model.load() } else { model.cancel() } }
    }
    private var viewportControls: some View {
        ViewThatFits {
            HStack { controls }
            VStack { controls }
        }.buttonStyle(.bordered).controlSize(.large).frame(minHeight: 44)
    }
    @ViewBuilder private var controls: some View {
        Button("visualization.zoomIn") { model.changeViewport(model.viewport.scaled(0.5)) }
        Button("visualization.zoomOut") { model.changeViewport(model.viewport.scaled(2)) }
        Button("visualization.left") { model.changeViewport(model.viewport.shifted(-1)) }
        Button("visualization.right") { model.changeViewport(model.viewport.shifted(1)) }
        Button("visualization.reset") { model.changeViewport(VisualizationViewport()) }
    }
    private func selectSample(_ delta: Int) {
        let points = model.visibleSegments.flatMap(\.points).sorted { $0.x < $1.x }
        guard !points.isEmpty else { return }
        let index = points.firstIndex { $0.x == model.selectedSample?.x } ?? 0
        model.selectedX = points[min(max(index+delta, 0), points.count-1)].x
    }
    private func math(_ value: AttemptJSON, title: String) -> some View {
        VStack(alignment: .leading) {
            Text(LocalizedStringKey(title)).font(.headline)
            if let latex = AttemptMathPresentation.latex(value) {
                ScrollView(.horizontal) {
                    NativeMathLabel(latex: latex, fallback: latex, pointSize: mathPointSize).fixedSize()
                }.accessibilityElement(children: .ignore)
                    .accessibilityLabel(Text(AttemptMathPresentation.spoken(value)))
            }
        }
    }
}

struct FunctionPlotView: View {
    let segments: [MathVisualization.Segment]
    let viewport: VisualizationViewport
    @Binding var selectedX: Double?
    var body: some View {
        Chart {
            ForEach(segments) { segment in
                ForEach(Array(segment.points.enumerated()), id: \.offset) { _, point in
                    LineMark(x: .value("x", point.x), y: .value("y", point.y), series: .value("segment", segment.id))
                        .foregroundStyle(segment.curve == "FUNCTION" ? ColorTokens.action : ColorTokens.warning)
                        .lineStyle(StrokeStyle(lineWidth: 2, dash: segment.curve == "FUNCTION" ? [] : [5, 4]))
                        .interpolationMethod(.linear)
                    if segment.points.count == 1 {
                        PointMark(x: .value("x", point.x), y: .value("y", point.y))
                    }
                }
            }
            if let selectedX { RuleMark(x: .value("x", selectedX)).foregroundStyle(ColorTokens.textSecondary) }
        }
        .chartXScale(domain: viewport.xMin...viewport.xMax).chartYScale(domain: viewport.yMin...viewport.yMax)
        .chartXAxisLabel("x").chartYAxisLabel("y").chartXSelection(value: $selectedX)
        .frame(height: 260).accessibilityLabel("visualization.chartSummary")
        .accessibilityIdentifier("visualization.chart")
    }
}

struct NumberLineView: View {
    let bands: [MathVisualization.Band]
    let viewport: VisualizationViewport
    var body: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.sm) {
            ForEach(Array(bands.enumerated()), id: \.offset) { _, band in
                VStack(alignment: .leading) {
                    GeometryReader { geometry in
                        let width = max(1, geometry.size.width - 24)
                        let lo = max(viewport.xMin, band.lowerValue ?? viewport.xMin)
                        let hi = min(viewport.xMax, band.upperValue ?? viewport.xMax)
                        let a = 12 + (lo-viewport.xMin)/(viewport.xMax-viewport.xMin)*width
                        let b = 12 + (hi-viewport.xMin)/(viewport.xMax-viewport.xMin)*width
                        if lo <= hi {
                            Path { path in path.move(to: CGPoint(x: a, y: 20)); path.addLine(to: CGPoint(x: b, y: 20)) }
                                .stroke(ColorTokens.action, lineWidth: 3)
                            endpoint(open: band.leftOpen, ray: band.lowerValue == nil || (band.lowerValue ?? lo) < viewport.xMin, left: true)
                                .position(x: a, y: 20)
                            endpoint(open: band.rightOpen, ray: band.upperValue == nil || (band.upperValue ?? hi) > viewport.xMax, left: false)
                                .position(x: b, y: 20)
                        }
                    }.frame(height: 40).accessibilityHidden(true)
                    Text(band.exact).textSelection(.enabled)
                        .accessibilityLabel(Text(String(format: NSLocalizedString("visualization.interval", comment: "Exact interval"), band.exact)))
                }
            }
        }.accessibilityIdentifier("visualization.numberLine")
    }
    @ViewBuilder private func endpoint(open: Bool, ray: Bool, left: Bool) -> some View {
        if ray { Image(systemName: left ? "arrow.left" : "arrow.right").foregroundStyle(ColorTokens.action) }
        else { Circle().fill(open ? ColorTokens.background : ColorTokens.action)
            .overlay(Circle().stroke(ColorTokens.action, lineWidth: 2)).frame(width: 12, height: 12) }
    }
}
