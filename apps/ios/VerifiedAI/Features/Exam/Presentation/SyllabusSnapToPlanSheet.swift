import PhotosUI
import SwiftUI
import UniformTypeIdentifiers

public struct SyllabusSnapToPlanSheet: View {
    @ObservedObject private var viewModel: CandidateProfileViewModel
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var isFileImporterPresented = false
    @Environment(\.dismiss) private var dismiss

    public init(viewModel: CandidateProfileViewModel) {
        self.viewModel = viewModel
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                headerSection
                errorOrStatusSection
                uploadControlsSection
                uiTestingQuickSamples
                parsedResultSection
            }
            .padding(SpacingTokens.lg)
        }
        .background(ColorTokens.canvas)
        .navigationTitle(Text("exam.syllabus.sheet_title"))
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button(LocalizedStringKey("exam.syllabus.close_button")) {
                    dismiss()
                }
                .accessibilityIdentifier("syllabus.closeButton")
            }
        }
        .onChange(of: selectedPhotoItem) { _, newItem in
            guard let item = newItem else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self) {
                    await viewModel.ingestSyllabus(fileData: data, mimeType: "image/jpeg", filename: "syllabus-photo.jpg")
                }
            }
        }
        .fileImporter(
            isPresented: $isFileImporterPresented,
            allowedContentTypes: [.pdf, .jpeg, .png, .webP, .heic]
        ) { result in
            guard case .success(let url) = result else { return }
            Task {
                let accessed = url.startAccessingSecurityScopedResource()
                defer {
                    if accessed { url.stopAccessingSecurityScopedResource() }
                }
                if let data = try? Data(contentsOf: url) {
                    let ext = url.pathExtension.lowercased()
                    let mime = switch ext {
                    case "pdf": "application/pdf"
                    case "png": "image/png"
                    case "webp": "image/webp"
                    case "heic": "image/heic"
                    default: "image/jpeg"
                    }
                    await viewModel.ingestSyllabus(fileData: data, mimeType: mime, filename: url.lastPathComponent)
                }
            }
        }
    }

    private var headerSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("exam.syllabus.sheet_subtitle")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text("exam.syllabus.privacy_notice")
                .font(.caption)
                .foregroundStyle(.secondary)
            if let upload = viewModel.latestUpload {
                Text("Remaining monthly quota: \(upload.remainingMonthlyQuota) / 3")
                    .font(TypographyTokens.caption.bold())
                    .monospacedDigit()
                    .foregroundStyle(ColorTokens.textPrimary)
                    .accessibilityIdentifier("syllabus.remainingQuotaLabel")
            }
        }
    }

    @ViewBuilder
    private var errorOrStatusSection: some View {
        if let errKey = viewModel.errorLocalizationKey {
            HStack(spacing: SpacingTokens.xs) {
                Image(systemName: "exclamationmark.triangle")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(ColorTokens.warning)
                Text(LocalizedStringKey(errKey))
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, SpacingTokens.compact)
            .padding(.vertical, SpacingTokens.sm)
            .background(ColorTokens.warningSoft)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                    .stroke(ColorTokens.warning.opacity(0.28), lineWidth: 1)
            )
            .accessibilityIdentifier("syllabus.errorBanner")
        }
        if let statusKey = viewModel.statusLocalizationKey {
            HStack(spacing: SpacingTokens.xs) {
                Image(systemName: "checkmark.seal.fill")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(ColorTokens.success)
                Text(LocalizedStringKey(statusKey))
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textPrimary)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, SpacingTokens.compact)
            .padding(.vertical, SpacingTokens.sm)
            .background(ColorTokens.successSoft)
            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                    .stroke(ColorTokens.success.opacity(0.28), lineWidth: 1)
            )
            .accessibilityIdentifier("syllabus.statusBanner")
        }
    }

    private var uploadControlsSection: some View {
        VStack(spacing: SpacingTokens.sm) {
            PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                Label(LocalizedStringKey("exam.syllabus.choose_photo_button"), systemImage: "photo.on.rectangle")
                    .font(TypographyTokens.bodyMedium.weight(.semibold))
                    .foregroundStyle(ColorTokens.actionForeground)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(ColorTokens.action)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
            }
            .buttonStyle(.plain)
            .disabled(viewModel.isIngesting)
            .accessibilityIdentifier("syllabus.choosePhotoButton")

            Button {
                isFileImporterPresented = true
            } label: {
                Label(LocalizedStringKey("exam.syllabus.choose_pdf_button"), systemImage: "doc.fill")
                    .font(TypographyTokens.bodyMedium.weight(.semibold))
                    .foregroundStyle(ColorTokens.textPrimary)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(ColorTokens.surfaceElevated)
                    .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                            .stroke(ColorTokens.borderStrong, lineWidth: 1)
                    )
            }
            .buttonStyle(.plain)
            .disabled(viewModel.isIngesting)
            .accessibilityIdentifier("syllabus.chooseFileButton")
        }
    }

    @ViewBuilder
    private var uiTestingQuickSamples: some View {
        let args = ProcessInfo.processInfo.arguments
        if args.contains("--ui-testing")
            || args.contains("--ui-testing-candidate-profile")
            || args.contains("-ui-testing-candidate-profile")
        {
            VStack(alignment: .leading, spacing: 8) {
                Button("UI Sample Clear Syllabus PDF") {
                    let samplePdf = Data("%PDF-1.4\n%TOPIC: Tam Sayılarla İşlemler | WEEK: 1 | MONTH: October\n".utf8)
                    Task {
                        await viewModel.ingestSyllabus(fileData: samplePdf, mimeType: "application/pdf", filename: "clear-plan.pdf")
                    }
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("syllabus.sampleClearUploadButton")

                Button("UI Sample Ambiguous Syllabus PDF") {
                    let samplePdf = Data("%PDF-1.4\nAMBIGUOUS_SYLLABUS\n".utf8)
                    Task {
                        await viewModel.ingestSyllabus(fileData: samplePdf, mimeType: "application/pdf", filename: "ambiguous-plan.pdf")
                    }
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("syllabus.sampleAmbiguousUploadButton")
            }
        }
    }

    @ViewBuilder
    private var parsedResultSection: some View {
        if let upload = viewModel.latestUpload {
            VStack(alignment: .leading, spacing: 12) {
                Text("exam.syllabus.parsed_topics_title")
                    .font(.headline)

                ForEach(upload.parsedTopics) { item in
                    VStack(alignment: .leading, spacing: 6) {
                        HStack {
                            Text(item.rawTopicLabel)
                                .font(TypographyTokens.cardTitle)
                                .foregroundStyle(ColorTokens.textPrimary)
                            Spacer()
                            if let week = item.targetWeek {
                                Text("W\(week)")
                                    .font(TypographyTokens.caption.monospacedDigit())
                                    .foregroundStyle(ColorTokens.textSecondary)
                            }
                        }
                        if item.autoConfirmed, let matched = item.matchedSkillId {
                            Label(matched, systemImage: "checkmark.circle")
                                .font(TypographyTokens.caption)
                                .foregroundStyle(ColorTokens.success)
                        } else if !item.candidates.isEmpty {
                            Text("exam.syllabus.disambiguate_prompt")
                                .font(TypographyTokens.caption)
                                .foregroundStyle(ColorTokens.warning)
                            Picker(
                                item.rawTopicLabel,
                                selection: Binding(
                                    get: { viewModel.selectedDisambiguationChoices[item.itemIndex] ?? item.candidates.first?.skillId ?? "" },
                                    set: { viewModel.selectedDisambiguationChoices[item.itemIndex] = $0 }
                                )
                            ) {
                                ForEach(item.candidates) { cand in
                                    Text("\(cand.skillId) (\(Int((cand.similarityScore * 100).rounded()))%)")
                                        .tag(cand.skillId)
                                }
                            }
                            .pickerStyle(.menu)
                            .tint(ColorTokens.action)
                            .accessibilityIdentifier("syllabus.disambiguationPicker.\(item.itemIndex)")
                        }
                    }
                    .padding(SpacingTokens.md)
                    .background(ColorTokens.surface, in: RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous)
                            .stroke(ColorTokens.borderSubtle, lineWidth: 1)
                    )
                }

                if upload.status == .needsDisambiguation {
                    Button {
                        Task { await viewModel.confirmDisambiguation() }
                    } label: {
                        Text("exam.syllabus.confirm_mappings_button")
                            .font(TypographyTokens.bodyMedium.weight(.semibold))
                            .foregroundStyle(ColorTokens.actionForeground)
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .background(ColorTokens.action)
                            .clipShape(RoundedRectangle(cornerRadius: RadiusTokens.medium, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .disabled(viewModel.isIngesting)
                    .accessibilityIdentifier("syllabus.confirmMappingsButton")
                }
            }
        }
    }
}
