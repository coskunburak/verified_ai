import SwiftUI
import UIKit

struct NotificationPreferencesView: View {
    @Bindable var viewModel: NotificationPreferencesViewModel
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpacingTokens.lg) {
                headerCard
                bannersSection
                if viewModel.showPrePromptBanner {
                    prePromptCard
                }
                if viewModel.state == .loading && viewModel.preferences == nil {
                    loadingCard
                } else {
                    masterPushCard
                    categoriesCard
                    quietHoursCard
                    dailyCapCard
                    saveActionCard
                }
            }
            .padding(SpacingTokens.lg)
            .frame(maxWidth: 620, alignment: .leading)
        }
        .background(ColorTokens.canvas.ignoresSafeArea())
        .navigationTitle(LocalizedStringKey("notification.preferences.title"))
        .navigationBarTitleDisplayMode(.inline)
        .task {
            if viewModel.state == .idle {
                await viewModel.load()
            }
        }
    }

    private var headerCard: some View {
        AppSurfaceCard {
            ViewThatFits(in: .horizontal) {
                HStack(alignment: .top, spacing: SpacingTokens.sm) {
                    VStack(alignment: .leading, spacing: SpacingTokens.xxs) {
                        Text(LocalizedStringKey("notification.preferences.title"))
                            .font(TypographyTokens.sectionTitle)
                            .foregroundStyle(ColorTokens.textPrimary)
                        Text(LocalizedStringKey("notification.preferences.subtitle"))
                            .font(TypographyTokens.caption)
                            .foregroundStyle(ColorTokens.textSecondary)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    Spacer(minLength: SpacingTokens.xs)
                    permissionBadge
                }

                VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                    Text(LocalizedStringKey("notification.preferences.title"))
                        .font(TypographyTokens.sectionTitle)
                        .foregroundStyle(ColorTokens.textPrimary)
                    Text(LocalizedStringKey("notification.preferences.subtitle"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                    permissionBadge
                }
            }
        }
    }

    private var permissionBadge: some View {
        AppStatusBadge(
            LocalizedStringKey(viewModel.osPermissionStatus.isDeliverable
                ? "notification.preferences.permission.badge.authorized"
                : (viewModel.osPermissionStatus == .denied
                    ? "notification.preferences.permission.badge.denied"
                    : "notification.preferences.permission.badge.not_determined")),
            systemImage: viewModel.osPermissionStatus.isDeliverable ? "bell.badge" : "bell.slash",
            tone: viewModel.osPermissionStatus.isDeliverable
                ? .success
                : (viewModel.osPermissionStatus == .denied ? .warning : .neutral)
        )
    }

    @ViewBuilder
    private var bannersSection: some View {
        if let errorKey = viewModel.errorKey {
            AppSurfaceCard {
                AppNoticeBanner(
                    LocalizedStringKey(errorKey),
                    systemImage: viewModel.state == .offline ? "wifi.slash" : "exclamationmark.triangle",
                    tone: .warning
                )
                if viewModel.state == .offline || viewModel.state == .error {
                    Button {
                        Task { await viewModel.load() }
                    } label: {
                        Text(LocalizedStringKey("notification.preferences.action.retry"))
                    }
                    .buttonStyle(AppSecondaryButtonStyle())
                    .accessibilityIdentifier("notifications.retryButton")
                }
            }
        }

        if let noticeKey = viewModel.noticeKey {
            AppSurfaceCard {
                AppNoticeBanner(
                    LocalizedStringKey(noticeKey),
                    systemImage: viewModel.osPermissionStatus == .denied
                        ? "bell.slash"
                        : (viewModel.hasPendingConflictRecovery ? "arrow.triangle.2.circlepath" : "checkmark.seal"),
                    tone: (viewModel.osPermissionStatus == .denied || viewModel.hasPendingConflictRecovery)
                        ? .warning
                        : .success
                )

                if viewModel.osPermissionStatus == .denied {
                    Button {
                        if let url = URL(string: UIApplication.openSettingsURLString) {
                            openURL(url)
                        }
                    } label: {
                        Text(LocalizedStringKey("notification.preferences.permission.open_settings"))
                    }
                    .buttonStyle(AppSecondaryButtonStyle())
                    .accessibilityIdentifier("notifications.openSettingsButton")
                    .accessibilityHint(LocalizedStringKey("notification.preferences.permission.open_settings_hint"))
                }
            }
        }
    }

    private var prePromptCard: some View {
        AppSurfaceCard(elevated: true, isSelected: true) {
            AppSectionHeader(
                LocalizedStringKey("notification.preferences.preprompt.title"),
                subtitle: LocalizedStringKey("notification.preferences.preprompt.body")
            )

            ViewThatFits(in: .horizontal) {
                HStack(spacing: SpacingTokens.sm) {
                    Button {
                        viewModel.dismissPushPrePrompt()
                    } label: {
                        Text(LocalizedStringKey("notification.preferences.preprompt.not_now"))
                    }
                    .buttonStyle(AppSecondaryButtonStyle())

                    Button {
                        Task { await viewModel.confirmPushPrePrompt() }
                    } label: {
                        Text(LocalizedStringKey("notification.preferences.preprompt.allow"))
                    }
                    .buttonStyle(AppPrimaryButtonStyle())
                }

                VStack(spacing: SpacingTokens.xs) {
                    Button {
                        Task { await viewModel.confirmPushPrePrompt() }
                    } label: {
                        Text(LocalizedStringKey("notification.preferences.preprompt.allow"))
                    }
                    .buttonStyle(AppPrimaryButtonStyle())

                    Button {
                        viewModel.dismissPushPrePrompt()
                    } label: {
                        Text(LocalizedStringKey("notification.preferences.preprompt.not_now"))
                    }
                    .buttonStyle(AppSecondaryButtonStyle())
                }
            }
        }
    }

    private var loadingCard: some View {
        AppSurfaceCard {
            HStack(spacing: SpacingTokens.sm) {
                ProgressView()
                    .tint(ColorTokens.action)
                Text(LocalizedStringKey("notification.preferences.loading"))
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
            }
            .frame(minHeight: 48)
        }
    }

    private var masterPushCard: some View {
        AppSurfaceCard {
            Toggle(isOn: Binding(
                get: { viewModel.draft.pushEnabled },
                set: { newValue in
                    if reduceMotion {
                        viewModel.togglePushMaster(newValue)
                    } else {
                        withAnimation(.easeInOut(duration: 0.18)) {
                            viewModel.togglePushMaster(newValue)
                        }
                    }
                }
            )) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(LocalizedStringKey("notification.preferences.master.title"))
                        .font(TypographyTokens.cardTitle)
                        .foregroundStyle(ColorTokens.textPrimary)
                    Text(LocalizedStringKey("notification.preferences.master.subtitle"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .tint(ColorTokens.action)
            .frame(minHeight: 44)
            .accessibilityIdentifier("notifications.pushToggle")
            .accessibilityLabel(LocalizedStringKey("notification.preferences.master.title"))
            .accessibilityHint(LocalizedStringKey("notification.preferences.master.hint"))
        }
    }

    private var categoriesCard: some View {
        AppSurfaceCard {
            AppSectionHeader(
                LocalizedStringKey("notification.preferences.categories.title"),
                subtitle: LocalizedStringKey("notification.preferences.categories.subtitle")
            )

            categoryToggleRow(
                titleKey: "notification.preferences.category.study_plan.title",
                subtitleKey: "notification.preferences.category.study_plan.subtitle",
                identifier: "notifications.category.studyPlan",
                isOn: $viewModel.draft.studyPlanEnabled
            )
            Divider().overlay(ColorTokens.borderSubtle)

            categoryToggleRow(
                titleKey: "notification.preferences.category.spaced_review.title",
                subtitleKey: "notification.preferences.category.spaced_review.subtitle",
                identifier: "notifications.category.spacedReview",
                isOn: $viewModel.draft.spacedReviewEnabled
            )
            Divider().overlay(ColorTokens.borderSubtle)

            categoryToggleRow(
                titleKey: "notification.preferences.category.exam_deadline.title",
                subtitleKey: "notification.preferences.category.exam_deadline.subtitle",
                identifier: "notifications.category.examDeadline",
                isOn: $viewModel.draft.examDeadlineEnabled
            )
            Divider().overlay(ColorTokens.borderSubtle)

            categoryToggleRow(
                titleKey: "notification.preferences.category.billing_trial.title",
                subtitleKey: "notification.preferences.category.billing_trial.subtitle",
                identifier: "notifications.category.billingTrial",
                isOn: $viewModel.draft.billingTrialReminderEnabled
            )
            Divider().overlay(ColorTokens.borderSubtle)

            ViewThatFits(in: .horizontal) {
                HStack(alignment: .top, spacing: SpacingTokens.sm) {
                    securityCategoryText
                    Spacer(minLength: SpacingTokens.xs)
                    AppStatusBadge(
                        LocalizedStringKey("notification.preferences.category.account_security.badge"),
                        systemImage: "lock.shield",
                        tone: .stepHighlight
                    )
                }

                VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                    securityCategoryText
                    AppStatusBadge(
                        LocalizedStringKey("notification.preferences.category.account_security.badge"),
                        systemImage: "lock.shield",
                        tone: .stepHighlight
                    )
                }
            }
            .frame(minHeight: 44)
            .accessibilityElement(children: .combine)
            .accessibilityIdentifier("notifications.category.accountSecurity")
            .accessibilityLabel(LocalizedStringKey("notification.preferences.category.account_security.title"))
            .accessibilityHint(LocalizedStringKey("notification.preferences.category.account_security.subtitle"))
        }
    }

    private var securityCategoryText: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(LocalizedStringKey("notification.preferences.category.account_security.title"))
                .font(TypographyTokens.cardTitle)
                .foregroundStyle(ColorTokens.textPrimary)
            Text(LocalizedStringKey("notification.preferences.category.account_security.subtitle"))
                .font(TypographyTokens.caption)
                .foregroundStyle(ColorTokens.textSecondary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func categoryToggleRow(
        titleKey: String,
        subtitleKey: String,
        identifier: String,
        isOn: Binding<Bool>
    ) -> some View {
        Toggle(isOn: isOn) {
            VStack(alignment: .leading, spacing: 2) {
                Text(LocalizedStringKey(titleKey))
                    .font(TypographyTokens.cardTitle)
                    .foregroundStyle(ColorTokens.textPrimary)
                Text(LocalizedStringKey(subtitleKey))
                    .font(TypographyTokens.caption)
                    .foregroundStyle(ColorTokens.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .tint(ColorTokens.action)
        .disabled(!viewModel.draft.pushEnabled)
        .frame(minHeight: 44)
        .accessibilityIdentifier(identifier)
        .accessibilityLabel(LocalizedStringKey(titleKey))
        .accessibilityHint(LocalizedStringKey(subtitleKey))
    }

    private var quietHoursCard: some View {
        AppSurfaceCard {
            Toggle(isOn: $viewModel.draft.quietHoursEnabled) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(LocalizedStringKey("notification.preferences.quiet_hours.title"))
                        .font(TypographyTokens.cardTitle)
                        .foregroundStyle(ColorTokens.textPrimary)
                    Text(LocalizedStringKey("notification.preferences.quiet_hours.subtitle"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
            .tint(ColorTokens.action)
            .frame(minHeight: 44)
            .accessibilityIdentifier("notifications.quietHoursToggle")
            .accessibilityLabel(LocalizedStringKey("notification.preferences.quiet_hours.title"))
            .accessibilityHint(LocalizedStringKey("notification.preferences.quiet_hours.subtitle"))

            if viewModel.draft.quietHoursEnabled {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: SpacingTokens.sm) {
                        quietTimeRow(
                            labelKey: "notification.preferences.quiet_hours.start",
                            timeString: Binding(
                                get: { viewModel.draft.quietHoursStartLocal },
                                set: { viewModel.setQuietHoursWindow(start: $0, end: viewModel.draft.quietHoursEndLocal) }
                            )
                        )
                        quietTimeRow(
                            labelKey: "notification.preferences.quiet_hours.end",
                            timeString: Binding(
                                get: { viewModel.draft.quietHoursEndLocal },
                                set: { viewModel.setQuietHoursWindow(start: viewModel.draft.quietHoursStartLocal, end: $0) }
                            )
                        )
                    }

                    VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                        quietTimeRow(
                            labelKey: "notification.preferences.quiet_hours.start",
                            timeString: Binding(
                                get: { viewModel.draft.quietHoursStartLocal },
                                set: { viewModel.setQuietHoursWindow(start: $0, end: viewModel.draft.quietHoursEndLocal) }
                            )
                        )
                        quietTimeRow(
                            labelKey: "notification.preferences.quiet_hours.end",
                            timeString: Binding(
                                get: { viewModel.draft.quietHoursEndLocal },
                                set: { viewModel.setQuietHoursWindow(start: viewModel.draft.quietHoursStartLocal, end: $0) }
                            )
                        )
                    }
                }
            }

            ViewThatFits(in: .horizontal) {
                HStack(spacing: SpacingTokens.xs) {
                    Text(LocalizedStringKey("notification.preferences.timezone.label"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                    Spacer(minLength: SpacingTokens.xs)
                    Text(viewModel.draft.timezone)
                        .font(TypographyTokens.monoCode)
                        .foregroundStyle(ColorTokens.textPrimary)
                }

                VStack(alignment: .leading, spacing: SpacingTokens.xxs) {
                    Text(LocalizedStringKey("notification.preferences.timezone.label"))
                        .font(TypographyTokens.caption)
                        .foregroundStyle(ColorTokens.textSecondary)
                    Text(viewModel.draft.timezone)
                        .font(TypographyTokens.monoCode)
                        .foregroundStyle(ColorTokens.textPrimary)
                }
            }
            .frame(minHeight: 32)
        }
    }

    private func quietTimeRow(labelKey: String, timeString: Binding<String>) -> some View {
        DatePicker(
            LocalizedStringKey(labelKey),
            selection: Binding(
                get: { Self.date(fromHHmm: timeString.wrappedValue) },
                set: { timeString.wrappedValue = Self.hhmm(from: $0) }
            ),
            displayedComponents: .hourAndMinute
        )
        .font(TypographyTokens.caption)
        .foregroundStyle(ColorTokens.textPrimary)
        .frame(minHeight: 44)
    }

    private var dailyCapCard: some View {
        AppSurfaceCard {
            AppSectionHeader(
                LocalizedStringKey("notification.preferences.daily_cap.title"),
                subtitle: LocalizedStringKey("notification.preferences.daily_cap.subtitle")
            )

            ViewThatFits(in: .horizontal) {
                HStack(spacing: SpacingTokens.sm) {
                    Text("\(viewModel.draft.dailyCap)")
                        .font(TypographyTokens.metricValue)
                        .foregroundStyle(ColorTokens.textPrimary)
                    Spacer(minLength: SpacingTokens.xs)
                    Stepper(
                        LocalizedStringKey("notification.preferences.daily_cap.stepper_label"),
                        value: Binding(
                            get: { viewModel.draft.dailyCap },
                            set: { viewModel.setDailyCap($0) }
                        ),
                        in: 0...5
                    )
                    .labelsHidden()
                    .accessibilityIdentifier("notifications.dailyCapStepper")
                    .accessibilityLabel(LocalizedStringKey("notification.preferences.daily_cap.stepper_label"))
                    .accessibilityHint(LocalizedStringKey("notification.preferences.daily_cap.subtitle"))
                }

                VStack(alignment: .leading, spacing: SpacingTokens.xs) {
                    Text("\(viewModel.draft.dailyCap)")
                        .font(TypographyTokens.metricValue)
                        .foregroundStyle(ColorTokens.textPrimary)
                    Stepper(
                        LocalizedStringKey("notification.preferences.daily_cap.stepper_label"),
                        value: Binding(
                            get: { viewModel.draft.dailyCap },
                            set: { viewModel.setDailyCap($0) }
                        ),
                        in: 0...5
                    )
                    .labelsHidden()
                    .accessibilityIdentifier("notifications.dailyCapStepper")
                    .accessibilityLabel(LocalizedStringKey("notification.preferences.daily_cap.stepper_label"))
                    .accessibilityHint(LocalizedStringKey("notification.preferences.daily_cap.subtitle"))
                }
            }
            .frame(minHeight: 44)
        }
    }

    private var saveActionCard: some View {
        AppSurfaceCard {
            Button {
                Task { await viewModel.save() }
            } label: {
                if viewModel.state == .saving {
                    ProgressView()
                        .tint(ColorTokens.actionForeground)
                } else {
                    Text(LocalizedStringKey("notification.preferences.save_button"))
                }
            }
            .buttonStyle(AppPrimaryButtonStyle())
            .disabled(viewModel.state == .saving || viewModel.state == .featureDisabled || viewModel.preferences == nil)
            .accessibilityIdentifier("notifications.saveButton")
            .accessibilityLabel(LocalizedStringKey("notification.preferences.save_button"))
            .accessibilityHint(LocalizedStringKey("notification.preferences.save_hint"))
        }
    }

    private static func date(fromHHmm value: String) -> Date {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "HH:mm"
        formatter.timeZone = TimeZone.current
        return formatter.date(from: value) ?? Date()
    }

    private static func hhmm(from date: Date) -> String {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.dateFormat = "HH:mm"
        formatter.timeZone = TimeZone.current
        return formatter.string(from: date)
    }
}
