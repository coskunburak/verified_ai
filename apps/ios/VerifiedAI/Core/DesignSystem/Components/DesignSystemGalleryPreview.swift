import SwiftUI

/// Visual reference gallery for Design System 2.1 — Concept B ("Photomath Editorial Crisp")
/// across Editorial Paper (Light), Carbon Crisp (Dark), and AX3 Dynamic Type.
struct DesignSystemGalleryPreview: View {
    @State private var selectedSegment = "Timeline"

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                headerCard
                metricsRow
                formulaAndTimelineCard
                componentsCard
            }
            .padding(SpacingTokens.lg)
        }
        .background(ColorTokens.canvas.ignoresSafeArea())
    }

    private var headerCard: some View {
        AppSurfaceCard {
            AppSectionHeader(
                "Design System 2.1",
                subtitle: "Photomath Editorial Crisp — Flat Tonal & 1pt Hairline"
            ) {
                AppStatusBadge(verbatimTitle: "Verified", systemImage: "checkmark.seal.fill", tone: .success)
            }

            AppSegmentedPill(
                selection: $selectedSegment,
                options: [
                    .init(selection: "Timeline", title: "Steps", systemImage: "list.number"),
                    .init(selection: "Why", title: "Why?", systemImage: "questionmark.circle"),
                    .init(selection: "Diff", title: "Check", systemImage: "arrow.left.arrow.right")
                ]
            )

            AppNoticeBanner(
                "No gradients or drop shadows. Signal Crimson drives primary actions; Royal Blue anchors math steps.",
                systemImage: "sparkles",
                tone: .stepHighlight
            )
        }
    }

    private var metricsRow: some View {
        HStack(spacing: SpacingTokens.sm) {
            AppMetricTile(title: "Mastery", value: "94%", systemImage: "scope", tone: .success)
            AppMetricTile(title: "Target", value: "15m", systemImage: "timer", tone: .stepHighlight)
            AppMetricTile(title: "Alerts", value: "2", systemImage: "exclamationmark.triangle", tone: .warning)
        }
    }

    private var formulaAndTimelineCard: some View {
        VStack(alignment: .leading, spacing: SpacingTokens.xs) {
            AppSectionHeader("Step Timeline Connector", subtitle: "Vertical 1pt rail + formula focus + collapsible rationale")

            AppStepTimelineRow(
                stepNumber: 1,
                title: "Factor common coefficient",
                isLast: false,
                state: .verified,
                badgeText: "Symbolic OK",
                badgeTone: .success
            ) {
                AppFormulaBox(formula: "2(x² - 9) = 0", label: "Algebraic Rewrite")
            }

            AppStepTimelineRow(
                stepNumber: 2,
                title: "Apply difference of squares",
                isLast: true,
                state: .active,
                badgeText: "Active Step",
                badgeTone: .stepHighlight
            ) {
                AppFormulaBox(formula: "(x - 3)(x + 3) = 0  =>  x = ±3", label: "Solution Set")
                AppWhyDisclosure(title: "Why does x² - 9 factor this way?", initiallyExpanded: true) {
                    Text("A difference of two squares a² - b² always factors symmetrically into (a - b)(a + b) with cancelling cross terms.")
                }
            }
        }
    }

    private var componentsCard: some View {
        AppSurfaceCard {
            AppSectionHeader("Tokens & Buttons", subtitle: "Semantic badge tones and flat CTAs")
            HStack(spacing: SpacingTokens.xs) {
                AppStatusBadge(verbatimTitle: "Step", tone: .stepHighlight)
                AppStatusBadge(verbatimTitle: "Verified", tone: .success)
                AppStatusBadge(verbatimTitle: "Caution", tone: .warning)
                AppStatusBadge(verbatimTitle: "Mistake", tone: .danger)
            }
            Button("Scan & Verify Problem") {}
                .buttonStyle(AppPrimaryButtonStyle())
            Button("Explore Alternative Method") {}
                .buttonStyle(AppSecondaryButtonStyle())
            AppChatBubble(
                isLearner: false,
                speakerLabel: "Socratic Tutor",
                footnote: "Deterministic check"
            ) {
                Text("Notice how checking x = 3 and x = -3 confirms both roots satisfy the original equation.")
            }
        }
    }
}

struct DesignSystemGalleryPreview_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            DesignSystemGalleryPreview()
                .preferredColorScheme(.light)
                .previewDisplayName("Editorial Paper — Light")

            DesignSystemGalleryPreview()
                .preferredColorScheme(.dark)
                .previewDisplayName("Carbon Crisp — Dark")

            DesignSystemGalleryPreview()
                .preferredColorScheme(.light)
                .environment(\.sizeCategory, .accessibilityExtraLarge)
                .previewDisplayName("Editorial Paper — AXLarge")
        }
    }
}
