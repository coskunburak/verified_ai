package com.verifiedai.exam.domain;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public record ExamSession(
        UUID id,
        UUID userId,
        UUID assemblyId,
        UUID blueprintId,
        String examCode,
        int examVersionNumber,
        ExamSessionStatus status,
        String navigationPolicy,
        boolean noHintPolicyEnforced,
        AppliedAccommodationSnapshot accommodationsSnapshot,
        int baseDurationSeconds,
        BigDecimal extraTimeMultiplier,
        int effectiveDurationSeconds,
        int offlineGraceBufferSeconds,
        Instant startedAt,
        Instant serverDeadlineAt,
        Instant graceDeadlineAt,
        Instant lastHeartbeatAt,
        Instant offlineDetectedAt,
        Instant submittedAt,
        String submissionReason,
        String submitIdempotencyKey,
        int totalItemCount,
        int answeredItemCount,
        int flaggedItemCount,
        int omittedItemCount,
        int scratchpadAttachmentCount,
        long clientClockSkewMs,
        F28VerificationBatchManifest f28VerificationBatchManifest,
        boolean trainingEligible,
        Instant createdAt,
        Instant updatedAt,
        List<ExamSessionItem> items
) {
    public static final Set<String> ALLOWED_NAVIGATION_POLICIES = Set.of("FREE_NAVIGATION", "SECTION_LOCKED");
    public static final Set<String> ALLOWED_SUBMISSION_REASONS = Set.of(
            "USER_INITIATED",
            "SERVER_DEADLINE_EXPIRED",
            "OFFLINE_GRACE_EXHAUSTED",
            "USER_ABANDONED"
    );

    public record ServerClockSync(
            Instant serverNow,
            long remainingSeconds,
            long graceRemainingSeconds,
            long clientClockSkewMs,
            boolean clockSkewAnomaly,
            boolean deadlineExpired,
            boolean offlineGraceActive
    ) {
    }

    public record SectionProgressSummary(
            String sectionCode,
            int sectionOrder,
            int totalItems,
            int answeredItems,
            int flaggedItems,
            int omittedItems,
            boolean locked
    ) {
    }

    public record F28VerificationBatchEntry(
            UUID assetId,
            UUID sessionItemId,
            int itemPosition,
            int pageNumber,
            String questionCode,
            String topicCode,
            String storageKey,
            String sha256Checksum,
            String captureSource,
            String bookletPublisher,
            String f28VerificationStatus
    ) {
    }

    public record F28VerificationBatchManifest(
            String batchVersion,
            String batchStatus,
            UUID sessionId,
            UUID userId,
            String examCode,
            int totalAssets,
            int llmTokensUsed,
            boolean trainingEligible,
            Instant generatedAt,
            List<F28VerificationBatchEntry> assets
    ) {
        public F28VerificationBatchManifest {
            Objects.requireNonNull(batchVersion, "batchVersion is required");
            Objects.requireNonNull(batchStatus, "batchStatus is required");
            Objects.requireNonNull(sessionId, "sessionId is required");
            Objects.requireNonNull(userId, "userId is required");
            Objects.requireNonNull(examCode, "examCode is required");
            Objects.requireNonNull(generatedAt, "generatedAt is required");
            assets = assets == null ? List.of() : List.copyOf(assets);
            if (llmTokensUsed != 0) {
                throw new IllegalArgumentException("llmTokensUsed must be 0 during exam runtime");
            }
            if (trainingEligible) {
                throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
            }
        }
    }

    public ExamSession {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(userId, "userId is required");
        Objects.requireNonNull(assemblyId, "assemblyId is required");
        Objects.requireNonNull(blueprintId, "blueprintId is required");
        Objects.requireNonNull(examCode, "examCode is required");
        Objects.requireNonNull(status, "status is required");
        Objects.requireNonNull(startedAt, "startedAt is required");
        Objects.requireNonNull(serverDeadlineAt, "serverDeadlineAt is required");
        Objects.requireNonNull(graceDeadlineAt, "graceDeadlineAt is required");
        Objects.requireNonNull(lastHeartbeatAt, "lastHeartbeatAt is required");
        Objects.requireNonNull(createdAt, "createdAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");

        if (examVersionNumber < 1) {
            throw new IllegalArgumentException("examVersionNumber must be >= 1");
        }
        navigationPolicy = navigationPolicy == null ? "FREE_NAVIGATION" : navigationPolicy.trim();
        if (!ALLOWED_NAVIGATION_POLICIES.contains(navigationPolicy)) {
            throw new IllegalArgumentException("Invalid navigationPolicy: " + navigationPolicy);
        }
        if (!noHintPolicyEnforced) {
            throw new IllegalArgumentException("noHintPolicyEnforced must be true per CNT-LOS-10");
        }
        accommodationsSnapshot = accommodationsSnapshot == null
                ? AppliedAccommodationSnapshot.standardDefaults()
                : accommodationsSnapshot;
        extraTimeMultiplier = ExamClockPolicy.normalizeMultiplier(extraTimeMultiplier);
        offlineGraceBufferSeconds = ExamClockPolicy.validateOfflineGraceBufferSeconds(offlineGraceBufferSeconds);
        if (!serverDeadlineAt.isAfter(startedAt) || graceDeadlineAt.isBefore(serverDeadlineAt)) {
            throw new IllegalArgumentException("Invalid session deadline ordering");
        }
        if (submissionReason != null && !ALLOWED_SUBMISSION_REASONS.contains(submissionReason)) {
            throw new IllegalArgumentException("Invalid submissionReason: " + submissionReason);
        }
        if (totalItemCount <= 0) {
            throw new IllegalArgumentException("totalItemCount must be > 0");
        }
        if (trainingEligible) {
            throw new IllegalArgumentException("trainingEligible must be false per ADR-006");
        }
        items = items == null
                ? List.of()
                : items.stream().sorted(Comparator.comparingInt(ExamSessionItem::itemPosition)).toList();
    }

    public Optional<ExamSessionItem> findItemByPosition(int itemPosition) {
        return items.stream().filter(i -> i.itemPosition() == itemPosition).findFirst();
    }

    public int currentActiveSectionOrder() {
        for (ExamSessionItem item : items) {
            if (item.responseState() == ExamItemResponseState.UNVISITED) {
                return item.sectionOrder();
            }
        }
        return items.isEmpty() ? 1 : items.getLast().sectionOrder();
    }

    public void validateSectionNavigationAllowed(ExamSessionItem targetItem) {
        if (!"SECTION_LOCKED".equals(navigationPolicy)) {
            return;
        }
        int activeSection = currentActiveSectionOrder();
        if (targetItem.sectionOrder() < activeSection) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_SESSION_INVALID_STATE_TRANSITION,
                    "Section " + targetItem.sectionCode() + " is locked after advancing to section " + activeSection,
                    false,
                    "In section-locked mode, you cannot return to an earlier completed section."
            );
        }
    }

    public ServerClockSync computeClockSync(Instant serverNow) {
        Objects.requireNonNull(serverNow, "serverNow is required");
        if (status.isTerminal()) {
            return new ServerClockSync(
                    serverNow,
                    0L,
                    0L,
                    clientClockSkewMs,
                    ExamClockPolicy.isClockSkewAnomaly(clientClockSkewMs),
                    true,
                    false
            );
        }
        long remaining = ExamClockPolicy.computeRemainingSeconds(serverNow, serverDeadlineAt);
        long graceRemaining = ExamClockPolicy.computeGraceRemainingSeconds(serverNow, graceDeadlineAt);
        boolean deadlineExpired = ExamClockPolicy.isPastServerDeadline(serverNow, serverDeadlineAt);
        boolean graceActive = status == ExamSessionStatus.OFFLINE_GRACE_BUFFER
                || (deadlineExpired && !ExamClockPolicy.isPastGraceDeadline(serverNow, graceDeadlineAt));
        return new ServerClockSync(
                serverNow,
                remaining,
                graceRemaining,
                clientClockSkewMs,
                ExamClockPolicy.isClockSkewAnomaly(clientClockSkewMs),
                deadlineExpired,
                graceActive
        );
    }

    public List<SectionProgressSummary> computeSectionProgress() {
        Map<Integer, List<ExamSessionItem>> grouped = new LinkedHashMap<>();
        for (ExamSessionItem item : items) {
            grouped.computeIfAbsent(item.sectionOrder(), ignored -> new ArrayList<>()).add(item);
        }
        int activeSection = currentActiveSectionOrder();
        List<SectionProgressSummary> summaries = new ArrayList<>();
        for (Map.Entry<Integer, List<ExamSessionItem>> entry : grouped.entrySet()) {
            List<ExamSessionItem> sectionItems = entry.getValue();
            int answered = (int) sectionItems.stream().filter(i -> i.responseState().isAnswered()).count();
            int flagged = (int) sectionItems.stream().filter(i -> i.responseState().isFlagged()).count();
            int omitted = (int) sectionItems.stream().filter(i -> i.responseState().isOmitted()).count();
            boolean locked = "SECTION_LOCKED".equals(navigationPolicy) && entry.getKey() < activeSection;
            summaries.add(new SectionProgressSummary(
                    sectionItems.getFirst().sectionCode(),
                    entry.getKey(),
                    sectionItems.size(),
                    answered,
                    flagged,
                    omitted,
                    locked
            ));
        }
        return List.copyOf(summaries);
    }

    public ExamSession withUpdatedHeartbeatAndSkew(Instant serverNow, Instant clientReportedTimestamp) {
        long skewMs = ExamClockPolicy.computeClientClockSkewMs(serverNow, clientReportedTimestamp);
        return new ExamSession(
                id,
                userId,
                assemblyId,
                blueprintId,
                examCode,
                examVersionNumber,
                status,
                navigationPolicy,
                true,
                accommodationsSnapshot,
                baseDurationSeconds,
                extraTimeMultiplier,
                effectiveDurationSeconds,
                offlineGraceBufferSeconds,
                startedAt,
                serverDeadlineAt,
                graceDeadlineAt,
                serverNow,
                offlineDetectedAt,
                submittedAt,
                submissionReason,
                submitIdempotencyKey,
                totalItemCount,
                answeredItemCount,
                flaggedItemCount,
                omittedItemCount,
                scratchpadAttachmentCount,
                skewMs,
                f28VerificationBatchManifest,
                false,
                createdAt,
                serverNow,
                items
        );
    }

    public ExamSession enterOfflineGraceBuffer(Instant serverNow, long skewMs) {
        if (status.isTerminal()) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_SESSION_ALREADY_TERMINATED,
                    "Exam session is already terminated: " + status,
                    false,
                    "Start a new exam session or view your submission summary."
            );
        }
        return new ExamSession(
                id,
                userId,
                assemblyId,
                blueprintId,
                examCode,
                examVersionNumber,
                ExamSessionStatus.OFFLINE_GRACE_BUFFER,
                navigationPolicy,
                true,
                accommodationsSnapshot,
                baseDurationSeconds,
                extraTimeMultiplier,
                effectiveDurationSeconds,
                offlineGraceBufferSeconds,
                startedAt,
                serverDeadlineAt,
                graceDeadlineAt,
                serverNow,
                offlineDetectedAt == null ? serverNow : offlineDetectedAt,
                submittedAt,
                submissionReason,
                submitIdempotencyKey,
                totalItemCount,
                answeredItemCount,
                flaggedItemCount,
                omittedItemCount,
                scratchpadAttachmentCount,
                skewMs,
                f28VerificationBatchManifest,
                false,
                createdAt,
                serverNow,
                items
        );
    }

    public ExamSession withReplacedItem(ExamSessionItem updatedItem, Instant serverNow, long skewMs) {
        List<ExamSessionItem> nextItems = new ArrayList<>(items.size());
        boolean replaced = false;
        for (ExamSessionItem item : items) {
            if (item.id().equals(updatedItem.id())) {
                nextItems.add(updatedItem);
                replaced = true;
            } else {
                nextItems.add(item);
            }
        }
        if (!replaced) {
            throw new IllegalArgumentException("Updated item does not belong to session " + id);
        }
        int answered = (int) nextItems.stream().filter(i -> i.responseState().isAnswered()).count();
        int flagged = (int) nextItems.stream().filter(i -> i.responseState().isFlagged()).count();
        int omitted = nextItems.size() - answered;
        int scratchpadCount = nextItems.stream().mapToInt(i -> i.scratchpadAssets().size()).sum();

        return new ExamSession(
                id,
                userId,
                assemblyId,
                blueprintId,
                examCode,
                examVersionNumber,
                status,
                navigationPolicy,
                true,
                accommodationsSnapshot,
                baseDurationSeconds,
                extraTimeMultiplier,
                effectiveDurationSeconds,
                offlineGraceBufferSeconds,
                startedAt,
                serverDeadlineAt,
                graceDeadlineAt,
                serverNow,
                offlineDetectedAt,
                submittedAt,
                submissionReason,
                submitIdempotencyKey,
                totalItemCount,
                answered,
                flagged,
                omitted,
                scratchpadCount,
                skewMs,
                f28VerificationBatchManifest,
                false,
                createdAt,
                serverNow,
                nextItems
        );
    }

    public ExamSession submit(
            ExamSessionStatus finalStatus,
            String reason,
            String idempotencyKey,
            Instant serverNow,
            long skewMs
    ) {
        Objects.requireNonNull(finalStatus, "finalStatus is required");
        Objects.requireNonNull(serverNow, "serverNow is required");
        if (!finalStatus.isTerminal()) {
            throw new IllegalArgumentException("finalStatus must be terminal");
        }
        if (reason == null || !ALLOWED_SUBMISSION_REASONS.contains(reason)) {
            throw new IllegalArgumentException("Invalid submission reason: " + reason);
        }
        if (this.status.isTerminal()) {
            throw new ApiProblemException(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.EXAM_SESSION_ALREADY_TERMINATED,
                    "Exam session is already in terminal state: " + this.status,
                    false,
                    "Submission already finalized."
            );
        }

        F28VerificationBatchManifest manifest = buildF28Manifest(serverNow);
        int answered = (int) items.stream().filter(i -> i.responseState().isAnswered()).count();
        int flagged = (int) items.stream().filter(i -> i.responseState().isFlagged()).count();
        int omitted = items.size() - answered;
        int scratchpadCount = items.stream().mapToInt(i -> i.scratchpadAssets().size()).sum();

        return new ExamSession(
                id,
                userId,
                assemblyId,
                blueprintId,
                examCode,
                examVersionNumber,
                finalStatus,
                navigationPolicy,
                true,
                accommodationsSnapshot,
                baseDurationSeconds,
                extraTimeMultiplier,
                effectiveDurationSeconds,
                offlineGraceBufferSeconds,
                startedAt,
                serverDeadlineAt,
                graceDeadlineAt,
                serverNow,
                offlineDetectedAt,
                serverNow,
                reason,
                idempotencyKey,
                totalItemCount,
                answered,
                flagged,
                omitted,
                scratchpadCount,
                skewMs,
                manifest,
                false,
                createdAt,
                serverNow,
                items
        );
    }

    public F28VerificationBatchManifest buildF28Manifest(Instant generatedAt) {
        List<F28VerificationBatchEntry> entries = items.stream()
                .flatMap(item -> item.scratchpadAssets().stream()
                        .map(asset -> new F28VerificationBatchEntry(
                                asset.id(),
                                item.id(),
                                item.itemPosition(),
                                asset.pageNumber(),
                                item.questionCode(),
                                item.topicCode(),
                                asset.storageKey(),
                                asset.sha256Checksum(),
                                asset.captureSource(),
                                asset.bookletPublisher(),
                                asset.f28VerificationStatus()
                        )))
                .sorted(Comparator.comparingInt(F28VerificationBatchEntry::itemPosition)
                        .thenComparingInt(F28VerificationBatchEntry::pageNumber))
                .toList();

        String batchStatus = entries.isEmpty() ? "EMPTY_NO_SCRATCHPADS" : "QUEUED_FOR_F28_BATCH";
        return new F28VerificationBatchManifest(
                "f28-batch-manifest-v1",
                batchStatus,
                id,
                userId,
                examCode,
                entries.size(),
                0,
                false,
                generatedAt,
                entries
        );
    }
}
