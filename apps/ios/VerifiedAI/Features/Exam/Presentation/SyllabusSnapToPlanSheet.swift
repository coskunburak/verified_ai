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
            .padding()
        }
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
                    .font(.caption.bold())
                    .accessibilityIdentifier("syllabus.remainingQuotaLabel")
            }
        }
    }

    @ViewBuilder
    private var errorOrStatusSection: some View {
        if let errKey = viewModel.errorLocalizationKey {
            Text(LocalizedStringKey(errKey))
                .font(.callout)
                .foregroundStyle(.red)
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.red.opacity(0.10), in: RoundedRectangle(cornerRadius: 10))
                .accessibilityIdentifier("syllabus.errorBanner")
        }
        if let statusKey = viewModel.statusLocalizationKey {
            Label(LocalizedStringKey(statusKey), systemImage: "checkmark.seal.fill")
                .font(.callout)
                .foregroundStyle(.green)
                .padding(10)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.green.opacity(0.12), in: RoundedRectangle(cornerRadius: 10))
                .accessibilityIdentifier("syllabus.statusBanner")
        }
    }

    private var uploadControlsSection: some View {
        VStack(spacing: 10) {
            PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                Label(LocalizedStringKey("exam.syllabus.choose_photo_button"), systemImage: "photo.on.rectangle")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .disabled(viewModel.isIngesting)
            .accessibilityIdentifier("syllabus.choosePhotoButton")

            Button {
                isFileImporterPresented = true
            } label: {
                Label(LocalizedStringKey("exam.syllabus.choose_pdf_button"), systemImage: "doc.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
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
                                .font(.subheadline.bold())
                            Spacer()
                            if let week = item.targetWeek {
                                Text("W\(week)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        if item.autoConfirmed, let matched = item.matchedSkillId {
                            Label(matched, systemImage: "checkmark.circle")
                                .font(.caption)
                                .foregroundStyle(.green)
                        } else if !item.candidates.isEmpty {
                            Text("exam.syllabus.disambiguate_prompt")
                                .font(.caption)
                                .foregroundStyle(.orange)
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
                            .accessibilityIdentifier("syllabus.disambiguationPicker.\(item.itemIndex)")
                        }
                    }
                    .padding()
                    .background(Color.secondary.opacity(0.08), in: RoundedRectangle(cornerRadius: 12))
                }

                if upload.status == .needsDisambiguation {
                    Button {
                        Task { await viewModel.confirmDisambiguation() }
                    } label: {
                        Text("exam.syllabus.confirm_mappings_button")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(viewModel.isIngesting)
                    .accessibilityIdentifier("syllabus.confirmMappingsButton")
                }
            }
        }
    }
}
