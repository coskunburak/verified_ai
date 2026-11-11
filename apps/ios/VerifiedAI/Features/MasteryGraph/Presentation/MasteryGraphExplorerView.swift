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
                        .accessibilityIdentifier("mastery.graph.loading")
                } else if let errorKey = viewModel.errorMessageKey {
                    VStack(spacing: 16) {
                        Image(systemName: "exclamationmark.triangle")
                            .font(.largeTitle)
                            .foregroundStyle(.orange)
                        Text(LocalizedStringKey(errorKey))
                            .multilineTextAlignment(.center)
                        Button(action: { viewModel.loadGraph() }) {
                            Text("Retry")
                        }
                        .buttonStyle(.borderedProminent)
                    }
                    .padding()
                } else if let projection = viewModel.projection {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 20) {
                            ForEach(groupedGenerations(nodes: projection.nodes), id: \.generation) { group in
                                VStack(alignment: .leading, spacing: 8) {
                                    Text("Tier \(group.generation)")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
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
                        .padding()
                    }
                } else {
                    ContentUnavailableView(
                        "No Graph Data",
                        systemImage: "network",
                        description: Text("No skills found for this curriculum.")
                    )
                }
            }
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
        HStack(spacing: 16) {
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

            VStack(alignment: .leading, spacing: 8) {
                Text(node.displayName)
                    .font(.headline)

                HStack(spacing: 8) {
                    Text(LocalizedStringKey(node.status.localizedKey))
                        .font(.caption2.bold())
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(statusBackgroundColor(node.status))
                        .cornerRadius(6)

                    if node.blockedByPrerequisiteBottleneck {
                        HStack(spacing: 4) {
                            Image(systemName: "exclamationmark.triangle.fill")
                            Text(LocalizedStringKey("mastery.graph.blocked_bottleneck"))
                        }
                        .font(.caption2.bold())
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color.orange.opacity(0.2))
                        .foregroundStyle(.orange)
                        .cornerRadius(6)
                        .accessibilityIdentifier("mastery.graph.bottleneck.\(node.skillCode)")
                    }
                }
            }
            Spacer()
        }
        .padding()
        .background(Color(uiColor: .secondarySystemGroupedBackground))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(node.blockedByPrerequisiteBottleneck ? Color.orange : Color.clear, lineWidth: 1.5)
        )
        .accessibilityIdentifier("mastery.graph.node.\(node.skillCode)")
    }

    private func skillDetailSheet(_ node: SkillGraphNode) -> some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 16) {
                Text(node.displayName)
                    .font(.title2.bold())

                Text(node.skillCode)
                    .font(.caption)
                    .foregroundStyle(.secondary)

                Divider()

                HStack {
                    Text("Effective Mastery")
                    Spacer()
                    Text(String(format: "%.2f", node.effectiveMastery))
                        .bold()
                }

                HStack {
                    Text("Direct Practice Count")
                    Spacer()
                    Text("\(node.directPracticeCount)")
                }

                HStack {
                    Text("Consecutive Failures")
                    Spacer()
                    Text("\(node.consecutiveFailures)")
                }

                if node.status == .atRiskRemediationRequired || node.blockedByPrerequisiteBottleneck {
                    Button(action: {
                        viewModel.selectNode(nil)
                        onLaunchTwinRemediation?(node.skillCode)
                    }) {
                        Label("Practice Remediation Drill", systemImage: "bolt.fill")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.orange)
                    .padding(.top, 16)
                    .accessibilityIdentifier("mastery.graph.remediate_button")
                }

                Spacer()
            }
            .padding()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") {
                        viewModel.selectNode(nil)
                    }
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

    private func masteryColor(_ score: Double) -> Color {
        if score >= 0.85 { return .green }
        if score >= 0.50 { return .blue }
        return .orange
    }

    private func statusBackgroundColor(_ status: SkillMasteryStatus) -> Color {
        switch status {
        case .mastered: return Color.green.opacity(0.15)
        case .practicing: return Color.blue.opacity(0.15)
        case .introduced: return Color.gray.opacity(0.15)
        case .atRiskRemediationRequired: return Color.red.opacity(0.15)
        }
    }
}
