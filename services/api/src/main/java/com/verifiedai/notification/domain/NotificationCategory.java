package com.verifiedai.notification.domain;

/**
 * Closed enum of notification categories supported by Sprint 10.4 infrastructure.
 */
public enum NotificationCategory {
    STUDY_PLAN(false, true, true),
    SPACED_REVIEW(false, true, true),
    EXAM_DEADLINE(false, true, true),
    BILLING_TRIAL_REMINDER(false, true, true),
    ACCOUNT_SECURITY(true, false, false);

    private final boolean securityMandatory;
    private final boolean subjectToQuietHours;
    private final boolean subjectToDailyCap;

    NotificationCategory(boolean securityMandatory, boolean subjectToQuietHours, boolean subjectToDailyCap) {
        this.securityMandatory = securityMandatory;
        this.subjectToQuietHours = subjectToQuietHours;
        this.subjectToDailyCap = subjectToDailyCap;
    }

    public boolean isSecurityMandatory() {
        return securityMandatory;
    }

    public boolean isSubjectToQuietHours() {
        return subjectToQuietHours;
    }

    public boolean isSubjectToDailyCap() {
        return subjectToDailyCap;
    }

    public boolean defaultOptIn() {
        return securityMandatory;
    }
}
