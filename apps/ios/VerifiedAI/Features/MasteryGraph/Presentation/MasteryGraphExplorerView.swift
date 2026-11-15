import SwiftUI

public struct MasteryGraphExplorerView: View {
    @Bindable public var viewModel: MasteryGraphViewModel
    public var onLaunchTwinRemediation: ((String) -> Void)?

    public init(
        viewModel: MasteryGraphViewModel,
        onLaunchTwinRemediation: ((String) -> Void)? = nil
    ) {
        self.viewModel = viewModel
        self.onLaunchTwinRemediation = onLaunchTwinRemediation
    }

    public var body: some View {
        NavigationStack {
            Group {
                if viewModel.isLoading {
                    ProgressView()
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .accessibilityIdentifier("mastery.graph.loading")
                } else if let errorKey = viewModel.errorMessageKey {
                    AppSurfaceCard(elevated: true) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.largeTitle)
                            .foregroundStyle(ColorTokens.warning)
                        Text(LocalizedStringKey(errorKey))
                            .font(TypographyTokens.body)
                            .foregroundStyle(ColorTokens.textPrimary)
                            .multilineTextAlignment(.center)
                        Button(action: { viewModel.loadGraph() }) {
                            Text("Retry")
                        }
                        .buttonStyle(AppPrimaryButtonStyle())
                    }
                    .padding(SpacingTokens.lg)
                } else if let projection = viewModel.projection {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: SpacingTokens.lg) {
                            ForEach(groupedGenerations(nodes: projection.nodes), id: \.generation) { group in
                                VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                                    Text("Tier \(group.generation)")
                                        .font(TypographyTokens.microLabel)
                                        .monospacedDigit()
                                        .foregroundStyle(ColorTokens.textSecondary)
                                        .textCase(.uppercase)

                                    ForEach(group.nodes) { node in
                                        skillNodeCard(node)
                                            .onTapGesture {
                                                viewModel.selectNode(node)
                                            }
                                    }
                                }
                            }
                        }
                        .padding(SpacingTokens.lg)
                    }
                } else {
                    AppEmptyState(
                        systemImage: "network",
                        title: "No Graph Data",
                        subtitle: "No skills found for this curriculum."
                    )
                }
            }
            .background(ColorTokens.canvas)
            .navigationTitle(Text("mastery.graph.title"))
            .accessibilityIdentifier("mastery.graph.root")
            .sheet(item: Binding(
                get: { viewModel.selectedNode },
                set: { viewModel.selectNode($0) }
            )) { node in
                skillDetailSheet(node)
            }
            .onAppear {
                viewModel.loadGraph()
            }
            .onDisappear {
                viewModel.cancel()
            }
        }
    }

    private func skillNodeCard(_ node: SkillGraphNode) -> some View {
        HStack(spacing: SpacingTokens.md) {
            DualRingSkillNodeView(
                skillNodeId: node.skillCode,
                skillTitle: node.displayName,
                metrics: SkillFiveMetricsSnapshot(
                    observedOutcomeScore: node.effectiveMastery,
                    independentCompetenceScore: node.effectiveMastery,
                    posteriorMasteryProbability: node.effectiveMastery,
                    retentionProbability: min(1.0, max(0.05, node.effectiveMastery * 0.9)),
                    scaffoldDependencyIndex: node.status == .atRiskRemediationRequired ? 0.35 : 0.05
                ),
                activeExamTrapCount: node.consecutiveFailures > 0 ? node.consecutiveFailures : 0,
                onTapNode: {
                    viewModel.selectNode(node)
                }
            )

            VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                Text(node.displayName)
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(ColorTokens.textPrimary)

                HStack(spacing: SpacingTokens.xs) {
                    AppStatusBadge(LocalizedStringKey(node.status.localizedKey), tone: statusTone(node.status))

                    if node.blockedByPrerequisiteBottleneck {
                        AppStatusBadge(
                            LocalizedStringKey("mastery.graph.blocked_bottleneck"),
                            systemImage: "exclamationmark.triangle.fill",
                            tone: .warning
                        )
                        .accessibilityIdentifier("mastery.graph.bottleneck.\(node.skillCode)")
                    }
                }
            }
            Spacer()
        }
        .padding(SpacingTokens.md)
        .background(ColorTokens.surface)
        .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: RadiusTokens.large, style: .continuous)
                .stroke(
                    node.blockedByPrerequisiteBottleneck ? ColorTokens.warning.opacity(0.6) : ColorTokens.borderSubtle,
                    lineWidth: node.blockedByPrerequisiteBottleneck ? 1.5 : 1
                )
        )
        .accessibilityIdentifier("mastery.graph.node.\(node.skillCode)")
    }

    private func skillDetailSheet(_ node: SkillGraphNode) -> some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: SpacingTokens.md) {
                Text(node.displayName)
                    .font(TypographyTokens.sectionTitle)
                    .foregroundStyle(ColorTokens.textPrimary)

                Text(node.skillCode)
                    .font(TypographyTokens.caption.monospaced())
                    .foregroundStyle(ColorTokens.textSecondary)

                Rectangle()
                    .fill(ColorTokens.borderSubtle)
                    .frame(height: 1)

                AppSurfaceCard(elevated: true) {
                    HStack {
                        Text("Effective Mastery")
                            .foregroundStyle(ColorTokens.textSecondary)
                        Spacer()
                        Text(String(format: "%.2f", node.effectiveMastery))
                            .font(TypographyTokens.cardTitle)
                            .monospacedDigit()
                            .foregroundStyle(ColorTokens.action)
                    }

                    HStack {
                        Text("Direct Practice Count")
                            .foregroundStyle(ColorTokens.textSecondary)
                        Spacer()
                        Text("\(node.directPracticeCount)")
                            .monospacedDigit()
                    }

                    HStack {
                        Text("Consecutive Failures")
                            .foregroundStyle(ColorTokens.textSecondary)
                        Spacer()
                        Text("\(node.consecutiveFailures)")
                            .monospacedDigit()
                    }
                }

                if node.status == .atRiskRemediationRequired || node.blockedByPrerequisiteBottleneck {
                    Button(action: {
                        viewModel.selectNode(nil)
                        onLaunchTwinRemediation?(node.skillCode)
                    }) {
                        Label("Practice Remediation Drill", systemImage: "bolt.fill")
                    }
                    .buttonStyle(AppPrimaryButtonStyle())
                    .padding(.top, SpacingTokens.sm)
                    .accessibilityIdentifier("mastery.graph.remediate_button")
                }

                Spacer()
            }
            .padding(SpacingTokens.lg)
            .background(ColorTokens.canvas)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") {
                        viewModel.selectNode(nil)
                    }
                    .foregroundStyle(ColorTokens.action)
                }
            }
        }
        .presentationDetents([.medium])
    }

    private struct GenerationGroup {
        let generation: Int
        let nodes: [SkillGraphNode]
    }

    private func groupedGenerations(nodes: [SkillGraphNode]) -> [GenerationGroup] {
        let dict = Dictionary(grouping: nodes, by: \.generation)
        return dict.keys.sorted().map { gen in
            GenerationGroup(generation: gen, nodes: dict[gen] ?? [])
        }
    }

    private func statusTone(_ status: SkillMasteryStatus) -> AppBadgeTone {
        switch status {
        case .mastered: return .success
        case .practicing: return .action
        case .introduced: return .neutral
        case .atRiskRemediationRequired: return .danger
        }
    }
}
