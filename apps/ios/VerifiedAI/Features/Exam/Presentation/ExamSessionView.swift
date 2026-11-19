import SwiftUI

public struct ExamSessionView: View {
    @ObservedObject public var viewModel: ExamSessionViewModel
    @Environment(\.accessibilityReduceMotion) private var systemReduceMotion

    public init(viewModel: ExamSessionViewModel) {
        self.viewModel = viewModel
    }

    private var strokeColor: Color {
        viewModel.highContrastMode ? ColorTokens.borderStrong : ColorTokens.borderSubtle
    }

    private var strokeWidth: CGFloat {
        viewModel.highContrastMode ? 2.0 : 1.0
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                headerStatusSection

                if viewModel.isOfflineGraceActive || viewModel.isOfflineOutboxPending {
                    graceBufferBanner
                }

                if viewModel.pendingSubmitIntent != nil {
                    pendingSubmitBanner
                }

                if viewModel.clockSkewAnomaly {
                    clockSkewBanner
                }

                if let receipt = viewModel.submissionReceipt ?? submittedReceiptFromSession {
                    submissionReceiptSection(receipt: receipt)
                } else if viewModel.isLoading && viewModel.session == nil {
                    ProgressView()
                        .frame(maxWidth: .infinity, minHeight: 160)
                } else if let session = viewModel.session {
                    navigatorSection(session: session)
                    if let currentItem = viewModel.currentItem {
                        activeQuestionSection(item: currentItem)
                        scratchpadSection(item: currentItem)
                    }
                    submitActionSection(session: session)
                }

                if let errorKey = viewModel.errorLocalizationKey {
                    Text(LocalizedStringKey(errorKey))
                        .font(.callout.weight(.medium))
                        .foregroundStyle(ColorTokens.warning)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(ColorTokens.surfaceElevated)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .accessibilityIdentifier("exam.session.errorBanner")
                } else if let errorMessage = viewModel.errorMessage {
                    Text(errorMessage)
                        .font(.callout.weight(.medium))
                        .foregroundStyle(ColorTokens.warning)
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(ColorTokens.surfaceElevated)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .accessibilityIdentifier("exam.session.errorBanner")
                }
            }
            .padding(16)
        }
        .background(ColorTokens.background.ignoresSafeArea())
        .navigationTitle(LocalizedStringKey("exam.session.title"))
        .navigationBarTitleDisplayMode(.inline)
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.session.screen")
        .confirmationDialog(
            LocalizedStringKey("exam.session.submit.confirmation_title"),
            isPresented: $viewModel.showSubmitConfirmation,
            titleVisibility: .visible
        ) {
            Button(LocalizedStringKey("exam.session.action.submit_exam"), role: .destructive) {
                Task {
                    await viewModel.submitSession(reason: .userInitiated)
                }
            }
            .accessibilityIdentifier("exam.session.confirmSubmitButton")
            Button("Cancel", role: .cancel) {}
        }
        .sheet(isPresented: $viewModel.showScoreSummary) {
            NavigationStack {
                ExamScoreSummaryView(viewModel: viewModel.makeScoreSummaryViewModel())
            }
        }
        .alert(
            LocalizedStringKey("exam.session.no_hint.badge"),
            isPresented: $viewModel.noHintAlertVisible
        ) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(LocalizedStringKey("exam.session.no_hint.notice"))
        }
        .task {
            if viewModel.session == nil {
                await viewModel.startOrResumeSession()
            }
        }
    }

    private var submittedReceiptFromSession: ExamSessionSubmitResponseDTO? {
        guard let session = viewModel.session,
              !session.status.isActive,
              let manifest = session.f28VerificationBatchManifest,
              let submittedAt = session.submittedAt else {
            return nil
        }
        return ExamSessionSubmitResponseDTO(
            sessionId: session.sessionId,
            assemblyId: session.assemblyId,
            blueprintId: session.blueprintId,
            examCode: session.examCode,
            status: session.status,
            submissionReason: session.submissionReason ?? "USER_INITIATED",
            submittedAt: submittedAt,
            submitIdempotencyKey: "session-\(session.sessionId.uuidString.lowercased())",
            totalItemCount: session.totalItemCount,
            answeredItemCount: session.answeredItemCount,
            flaggedItemCount: session.flaggedItemCount,
            omittedItemCount: session.omittedItemCount,
            scratchpadAttachmentCount: session.scratchpadAttachmentCount,
            f28VerificationBatchManifest: manifest,
            clockSync: session.clockSync,
            llmTokensUsed: 0,
            trainingEligible: false
        )
    }

    private var headerStatusSection: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(LocalizedStringKey("exam.session.timer.remaining_label"))
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(ColorTokens.textSecondary)
                Text(viewModel.formattedRemainingTime)
                    .font(.title3.monospacedDigit().weight(.bold))
                    .foregroundStyle(viewModel.remainingSeconds <= 300 ? ColorTokens.warning : ColorTokens.textPrimary)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(ColorTokens.surfaceElevated)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(strokeColor, lineWidth: strokeWidth)
            )
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("exam.session.timerPill")

            Spacer()

            Button {
                Task {
                    await viewModel.attemptHintAccess(requestedSurface: "SESSION_NO_HINT_BADGE")
                }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "lock.shield.fill")
                        .font(.caption.weight(.semibold))
                    Text(LocalizedStringKey("exam.session.no_hint.badge"))
                        .font(.caption.weight(.semibold))
                        .multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .foregroundStyle(ColorTokens.action)
                .padding(.horizontal, 10)
                .padding(.vertical, 8)
                .background(ColorTokens.actionSoft)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(ColorTokens.action, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("exam.session.noHintBadge")
        }
    }

    private var graceBufferBanner: some View {
        HStack(spacing: 10) {
            Image(systemName: "clock.badge.exclamationmark")
                .foregroundStyle(ColorTokens.warning)
            VStack(alignment: .leading, spacing: 2) {
                Text(LocalizedStringKey("exam.session.timer.grace_buffer_active"))
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(ColorTokens.warning)
                if viewModel.outboxPendingCount > 0 || viewModel.localScratchpadDraftCount > 0 {
                    Text("\(viewModel.outboxPendingCount + viewModel.localScratchpadDraftCount) queued updates • \(viewModel.graceRemainingSeconds)s grace")
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(ColorTokens.textSecondary)
                }
            }
            Spacer()
        }
        .padding(12)
        .background(ColorTokens.warningSoft)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(ColorTokens.warning, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("exam.session.graceBufferBanner")
    }

    private var pendingSubmitBanner: some View {
        HStack(spacing: 10) {
            Image(systemName: "arrow.triangle.2.circlepath")
                .foregroundStyle(ColorTokens.action)
            Text(LocalizedStringKey("exam.integrity.status.pending_submit_recovery"))
                .font(.caption.weight(.semibold))
                .foregroundStyle(ColorTokens.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
            Spacer()
        }
        .padding(12)
        .background(ColorTokens.actionSoft)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(ColorTokens.action, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("exam.session.pendingSubmitBanner")
    }

    private var clockSkewBanner: some View {
        HStack(spacing: 8) {
            Image(systemName: "deskclock")
                .foregroundStyle(ColorTokens.textSecondary)
            Text(LocalizedStringKey("exam.session.timer.clock_skew_notice"))
                .font(.caption)
                .foregroundStyle(ColorTokens.textSecondary)
            Spacer()
        }
        .padding(10)
        .background(ColorTokens.surfaceElevated)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(strokeColor, lineWidth: 1)
        )
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .accessibilityIdentifier("exam.session.clockSkewNotice")
    }

    private func navigatorSection(session: ExamSessionDTO) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(LocalizedStringKey("exam.session.navigator.title"))
                    .font(.headline)
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Text("\(session.answeredItemCount)/\(session.totalItemCount)")
                    .font(.caption.monospacedDigit().weight(.semibold))
                    .foregroundStyle(ColorTokens.textSecondary)
            }

            Picker(LocalizedStringKey("exam.session.navigator.title"), selection: $viewModel.navigatorFilter) {
                Text(LocalizedStringKey("exam.session.navigator.filter_all"))
                    .tag(ExamSessionViewModel.NavigatorFilter.all)
                Text(LocalizedStringKey("exam.session.navigator.filter_flagged"))
                    .tag(ExamSessionViewModel.NavigatorFilter.flagged)
                Text(LocalizedStringKey("exam.session.navigator.filter_omitted"))
                    .tag(ExamSessionViewModel.NavigatorFilter.omitted)
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("exam.session.navigatorFilterPicker")

            let columns = [GridItem(.adaptive(minimum: 42), spacing: 8)]
            LazyVGrid(columns: columns, spacing: 8) {
                ForEach(viewModel.filteredNavigatorItems) { item in
                    let isCurrent = item.itemPosition == viewModel.currentItemPosition
                    Button {
                        viewModel.selectItem(position: item.itemPosition)
                    } label: {
                        HStack(spacing: 2) {
                            Text("\(item.itemPosition)")
                                .font(.caption.monospacedDigit().weight(.bold))
                            if item.flaggedForReview {
                                Image(systemName: "flag.fill")
                                    .font(.caption2)
                            }
                        }
                        .frame(maxWidth: .infinity, minHeight: 34)
                        .foregroundStyle(cellForegroundColor(item: item, isCurrent: isCurrent))
                        .background(cellBackgroundColor(item: item, isCurrent: isCurrent))
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(isCurrent ? ColorTokens.action : strokeColor, lineWidth: isCurrent ? 2 : 1)
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    .buttonStyle(.plain)
                    .accessibilityIdentifier("exam.session.navigatorCell.\(item.itemPosition)")
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityIdentifier("exam.session.navigatorGrid")
        }
        .padding(14)
        .background(ColorTokens.surface)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(strokeColor, lineWidth: strokeWidth)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func activeQuestionSection(item: ExamSessionItemDTO) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text("Q\(item.itemPosition) • \(item.topicCode)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(ColorTokens.action)
                Spacer()
                Button {
                    Task {
                        await viewModel.toggleFlagForReview()
                    }
                } label: {
                    Label(
                        LocalizedStringKey("exam.session.action.flag_review"),
                        systemImage: item.flaggedForReview ? "flag.fill" : "flag"
                    )
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(item.flaggedForReview ? ColorTokens.warning : ColorTokens.textSecondary)
                }
                .buttonStyle(.plain)
                .accessibilityIdentifier("exam.session.flagToggleButton")
            }

            Text(item.stemMarkdown)
                .font(.body)
                .foregroundStyle(ColorTokens.textPrimary)
                .multilineTextAlignment(.leading)
                .fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityLabel(Text(verbatim: SpokenMathTurkishAccessibilityFormatter.formatMarkdown(item.stemMarkdown)))
                .accessibilityIdentifier("exam.session.questionStemCard")

            if let alt = item.figureAltText, !alt.isEmpty {
                Text(alt)
                    .font(.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                    .multilineTextAlignment(.leading)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .accessibilityLabel(alt)
                    .accessibilityIdentifier("exam.session.figureAltText")
            }

            if !item.optionsSnapshot.isEmpty {
                VStack(spacing: 8) {
                    ForEach(item.optionsSnapshot.sorted(by: { $0.displayOrder < $1.displayOrder })) { option in
                        let isSelected = item.selectedOptionKey == option.optionKey
                        let isEliminated = item.eliminatedOptionKeys.contains(option.optionKey)
                        HStack(spacing: 10) {
                            Button {
                                Task {
                                    await viewModel.selectOption(option.optionKey)
                                }
                            } label: {
                                HStack(alignment: .top, spacing: 10) {
                                    Text(option.optionKey)
                                        .font(.subheadline.monospacedDigit().weight(.bold))
                                        .padding(.horizontal, 4)
                                        .frame(minWidth: 28, minHeight: 28)
                                        .foregroundStyle(isSelected ? ColorTokens.actionForeground : ColorTokens.textPrimary)
                                        .background(isSelected ? ColorTokens.action : ColorTokens.surface)
                                        .clipShape(RoundedRectangle(cornerRadius: 6))

                                    Text(option.contentMarkdown)
                                        .font(.subheadline)
                                        .strikethrough(isEliminated, color: ColorTokens.textTertiary)
                                        .foregroundStyle(isEliminated ? ColorTokens.textTertiary : ColorTokens.textPrimary)
                                        .multilineTextAlignment(.leading)
                                        .fixedSize(horizontal: false, vertical: true)

                                    Spacer(minLength: 0)
                                }
                                .padding(10)
                                .frame(minHeight: 44)
                                .background(isSelected ? ColorTokens.actionSoft : ColorTokens.surfaceElevated)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10)
                                        .stroke(isSelected ? ColorTokens.action : strokeColor, lineWidth: isSelected ? 1.5 : 1)
                                )
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(
                                Text(
                                    verbatim: SpokenMathTurkishAccessibilityFormatter.formatOption(
                                        key: option.optionKey,
                                        contentMarkdown: option.contentMarkdown,
                                        contentLatex: option.contentLatex,
                                        isSelected: isSelected,
                                        isEliminated: isEliminated
                                    )
                                )
                            )
                            .accessibilityIdentifier("exam.session.optionRow.\(option.optionKey)")

                            Button {
                                Task {
                                    await viewModel.toggleEliminatedOption(option.optionKey)
                                }
                            } label: {
                                Image(systemName: isEliminated ? "eye.slash.fill" : "eye.slash")
                                    .font(.caption)
                                    .foregroundStyle(isEliminated ? ColorTokens.warning : ColorTokens.textSecondary)
                                    .frame(width: 36, height: 44)
                                    .background(ColorTokens.surfaceElevated)
                                    .clipShape(RoundedRectangle(cornerRadius: 8))
                            }
                            .buttonStyle(.plain)
                            .accessibilityIdentifier("exam.session.eliminateButton.\(option.optionKey)")
                        }
                    }
                }
            } else {
                HStack(spacing: 8) {
                    TextField("Numeric Answer", text: $viewModel.numericInputText)
                        .keyboardType(.decimalPad)
                        .textFieldStyle(.roundedBorder)
                    Button("Save") {
                        if let val = Double(viewModel.numericInputText) {
                            Task {
                                await viewModel.setNumericResponse(val)
                            }
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(ColorTokens.action)
                }
            }

            HStack(spacing: 10) {
                Button {
                    Task {
                        await viewModel.previousItem()
                    }
                } label: {
                    Text(LocalizedStringKey("exam.session.action.previous_item"))
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 40)
                }
                .buttonStyle(.bordered)
                .disabled(item.itemPosition <= 1)
                .accessibilityIdentifier("exam.session.previousButton")

                Button {
                    Task {
                        await viewModel.clearAnswer()
                    }
                } label: {
                    Text(LocalizedStringKey("exam.session.action.clear_answer"))
                        .font(.subheadline.weight(.medium))
                        .frame(maxWidth: .infinity, minHeight: 40)
                }
                .buttonStyle(.bordered)
                .disabled(item.selectedOptionKey == nil && item.numericResponseValue == nil)
                .accessibilityIdentifier("exam.session.clearAnswerButton")

                Button {
                    Task {
                        await viewModel.nextItem()
                    }
                } label: {
                    Text(LocalizedStringKey("exam.session.action.next_item"))
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 40)
                }
                .buttonStyle(.borderedProminent)
                .tint(ColorTokens.action)
                .disabled(item.itemPosition >= (viewModel.session?.totalItemCount ?? item.itemPosition))
                .accessibilityIdentifier("exam.session.nextButton")
            }
            .animation(systemReduceMotion ? nil : .easeInOut(duration: 0.15), value: item.itemPosition)
        }
        .padding(16)
        .background(ColorTokens.surface)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(strokeColor, lineWidth: strokeWidth)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func scratchpadSection(item: ExamSessionItemDTO) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(LocalizedStringKey("exam.session.scratchpad.title"))
                    .font(.headline)
                    .foregroundStyle(ColorTokens.textPrimary)
                Spacer()
                Text("\(item.scratchpadAssets.count)/4")
                    .font(.caption.monospacedDigit().weight(.semibold))
                    .foregroundStyle(ColorTokens.textSecondary)
            }

            Picker(LocalizedStringKey("exam.session.scratchpad.title"), selection: $viewModel.selectedScratchpadSource) {
                Text("Deneme Booklet")
                    .tag(ExamScratchpadCaptureSource.paperDenemeBooklet)
                Text("Digital Scratchpad")
                    .tag(ExamScratchpadCaptureSource.digitalMockScratchpad)
            }
            .pickerStyle(.segmented)
            .accessibilityIdentifier("exam.session.scratchpadSourcePicker")

            if viewModel.selectedScratchpadSource == .paperDenemeBooklet {
                TextField("Deneme Publisher / Booklet (optional)", text: $viewModel.bookletPublisherText)
                    .textFieldStyle(.roundedBorder)
            }

            Button {
                let nextPage = min(4, item.scratchpadAssets.count + 1)
                Task {
                    await viewModel.attachScratchpadPage(pageNumber: nextPage)
                }
            } label: {
                HStack {
                    Image(systemName: "camera.viewfinder")
                    Text(LocalizedStringKey("exam.session.scratchpad.attach_button"))
                        .font(.subheadline.weight(.semibold))
                }
                .frame(maxWidth: .infinity, minHeight: 44)
            }
            .buttonStyle(.bordered)
            .tint(ColorTokens.action)
            .disabled(item.scratchpadAssets.count >= 4 || viewModel.isSaving)
            .accessibilityIdentifier("exam.session.attachScratchpadButton")

            if !item.scratchpadAssets.isEmpty || viewModel.localScratchpadDraftCount > 0 {
                HStack(spacing: 6) {
                    Image(systemName: "tray.full.fill")
                        .foregroundStyle(ColorTokens.success)
                    Text(LocalizedStringKey("exam.session.scratchpad.queued_badge"))
                        .font(.caption.weight(.medium))
                        .foregroundStyle(ColorTokens.success)
                }
                .padding(.vertical, 4)
                .accessibilityElement(children: .combine)
                .accessibilityIdentifier("exam.session.scratchpadQueuedBadge")
            }
        }
        .padding(14)
        .background(ColorTokens.surface)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(strokeColor, lineWidth: strokeWidth)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.session.scratchpadSection")
    }

    private func submitActionSection(session: ExamSessionDTO) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(LocalizedStringKey("exam.session.no_hint.notice"))
                .font(.caption)
                .foregroundStyle(ColorTokens.textSecondary)

            Button {
                viewModel.showSubmitConfirmation = true
            } label: {
                HStack {
                    if viewModel.isSubmitting {
                        ProgressView()
                    }
                    Text(LocalizedStringKey("exam.session.action.submit_exam"))
                        .font(.headline)
                }
                .frame(maxWidth: .infinity, minHeight: 48)
            }
            .buttonStyle(.borderedProminent)
            .tint(ColorTokens.action)
            .disabled(viewModel.isSubmitting || !session.status.isActive)
            .accessibilityIdentifier("exam.session.submitExamButton")
        }
        .padding(14)
        .background(ColorTokens.surfaceElevated)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(strokeColor, lineWidth: strokeWidth)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }

    private func submissionReceiptSection(receipt: ExamSessionSubmitResponseDTO) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            Label(LocalizedStringKey("exam.session.submit.receipt_title"), systemImage: "checkmark.seal.fill")
                .font(.headline)
                .foregroundStyle(ColorTokens.success)

            HStack {
                Text("Status: \(receipt.status.rawValue)")
                    .font(.caption.monospaced())
                Spacer()
                Text("F28 Assets: \(receipt.f28VerificationBatchManifest.totalAssets)")
                    .font(.caption.monospacedDigit().weight(.semibold))
            }
            .foregroundStyle(ColorTokens.textPrimary)

            Text("Answered: \(receipt.answeredItemCount)/\(receipt.totalItemCount) • Flagged: \(receipt.flaggedItemCount) • Omitted: \(receipt.omittedItemCount)")
                .font(.caption)
                .foregroundStyle(ColorTokens.textSecondary)

            Text("F28 Batch: \(receipt.f28VerificationBatchManifest.batchStatus) • Runtime LLM Tokens: \(receipt.llmTokensUsed)")
                .font(.caption2.monospaced())
                .foregroundStyle(ColorTokens.textSecondary)

            Button {
                viewModel.showScoreSummary = true
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "chart.bar.doc.horizontal")
                    Text(LocalizedStringKey("exam.session.view_score_summary"))
                        .font(.subheadline.weight(.semibold))
                }
                .frame(maxWidth: .infinity, minHeight: 44)
                .foregroundStyle(ColorTokens.actionForeground)
                .background(ColorTokens.action)
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            .buttonStyle(.plain)
            .accessibilityIdentifier("exam.session.viewScoreSummaryButton")
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ColorTokens.successSoft)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(ColorTokens.success, lineWidth: strokeWidth)
        )
        .clipShape(RoundedRectangle(cornerRadius: 14))
        .accessibilityElement(children: .contain)
        .accessibilityIdentifier("exam.session.submitReceiptCard")
    }

    private func cellForegroundColor(item: ExamSessionItemDTO, isCurrent: Bool) -> Color {
        if isCurrent { return ColorTokens.action }
        if item.selectedOptionKey != nil || item.numericResponseValue != nil {
            return ColorTokens.success
        }
        if item.flaggedForReview { return ColorTokens.warning }
        return ColorTokens.textPrimary
    }

    private func cellBackgroundColor(item: ExamSessionItemDTO, isCurrent: Bool) -> Color {
        if isCurrent { return ColorTokens.actionSoft }
        if item.selectedOptionKey != nil || item.numericResponseValue != nil {
            return ColorTokens.successSoft
        }
        if item.flaggedForReview { return ColorTokens.warningSoft }
        return ColorTokens.surfaceElevated
    }
}
