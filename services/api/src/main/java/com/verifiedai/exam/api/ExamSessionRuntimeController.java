package com.verifiedai.exam.api;

import com.verifiedai.exam.application.ActiveExamNoHintGuard.HintGuardResult;
import com.verifiedai.exam.application.ExamSessionRuntimeService;
import com.verifiedai.exam.application.ExamSessionRuntimeService.AttachScratchpadCommand;
import com.verifiedai.exam.application.ExamSessionRuntimeService.AutosaveItemCommand;
import com.verifiedai.exam.application.ExamSessionRuntimeService.AutosaveResult;
import com.verifiedai.exam.application.ExamSessionRuntimeService.ScratchpadAttachResult;
import com.verifiedai.exam.application.ExamSessionRuntimeService.SessionStateResult;
import com.verifiedai.exam.application.ExamSessionRuntimeService.StartOrResumeResult;
import com.verifiedai.exam.application.ExamSessionRuntimeService.StartSessionCommand;
import com.verifiedai.exam.application.ExamSessionRuntimeService.SubmitSessionCommand;
import com.verifiedai.exam.application.ExamSessionRuntimeService.SubmitSessionResult;
import com.verifiedai.exam.domain.AppliedAccommodationSnapshot;
import com.verifiedai.exam.domain.ExamItemResponseState;
import com.verifiedai.exam.domain.ExamScratchpadAsset;
import com.verifiedai.exam.domain.ExamSession;
import com.verifiedai.exam.domain.ExamSession.F28VerificationBatchManifest;
import com.verifiedai.exam.domain.ExamSession.SectionProgressSummary;
import com.verifiedai.exam.domain.ExamSession.ServerClockSync;
import com.verifiedai.exam.domain.ExamSessionItem;
import com.verifiedai.exam.domain.ExamSessionItem.SessionItemOptionSnapshot;
import com.verifiedai.exam.domain.ExamSessionStatus;
import com.verifiedai.exam.domain.QuestionDifficultyBand;
import com.verifiedai.security.AuthenticatedLearnerPrincipal;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/v1/exams/sessions", "/api/v1/exams/sessions"})
public class ExamSessionRuntimeController {

    private final ExamSessionRuntimeService runtimeService;

    public ExamSessionRuntimeController(ExamSessionRuntimeService runtimeService) {
        this.runtimeService = Objects.requireNonNull(runtimeService, "runtimeService cannot be null");
    }

    // --- Request / Response DTOs matching Draft 2020-12 JSON Schemas ---

    public record ExamSessionStartRequestDto(
            UUID userId,
            UUID assemblyId,
            Integer offlineGraceBufferSeconds,
            Instant clientTimestamp,
            Boolean trainingEligible
    ) {
    }

    public record ExamScratchpadAssetDto(
            UUID id,
            UUID sessionId,
            UUID sessionItemId,
            int itemPosition,
            int pageNumber,
            String storageKey,
            String mimeType,
            long byteSize,
            String sha256Checksum,
            Integer widthPx,
            Integer heightPx,
            String captureSource,
            String bookletPublisher,
            String f28VerificationStatus,
            Instant capturedAt,
            boolean trainingEligible
    ) {
    }

    public record ExamSessionItemResponseDto(
            UUID id,
            int itemPosition,
            UUID questionItemId,
            String questionCode,
            String sectionCode,
            int sectionOrder,
            String topicCode,
            String itemFormat,
            QuestionDifficultyBand difficultyBand,
            String stemMarkdown,
            List<SessionItemOptionSnapshot> optionsSnapshot,
            String figureAltText,
            BigDecimal maxPoints,
            BigDecimal negativeMarkingWeight,
            ExamItemResponseState responseState,
            String selectedOptionKey,
            BigDecimal numericResponseValue,
            boolean flaggedForReview,
            List<String> eliminatedOptionKeys,
            int timeSpentSeconds,
            int autosaveVersion,
            long clientAutosaveSeq,
            Instant lastAutosavedAt,
            boolean scratchpadRequired,
            List<ExamScratchpadAssetDto> scratchpadAssets,
            boolean trainingEligible
    ) {
    }

    public record ExamSessionResponseDto(
            String schemaVersion,
            UUID sessionId,
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
            Instant submittedAt,
            String submissionReason,
            int totalItemCount,
            int answeredItemCount,
            int flaggedItemCount,
            int omittedItemCount,
            int scratchpadAttachmentCount,
            ServerClockSync clockSync,
            List<SectionProgressSummary> sectionProgress,
            List<ExamSessionItemResponseDto> items,
            F28VerificationBatchManifest f28VerificationBatchManifest,
            int llmTokensUsed,
            boolean trainingEligible
    ) {
    }

    public record ExamSessionItemAutosaveRequestDto(
            String action,
            String selectedOptionKey,
            BigDecimal numericResponseValue,
            Boolean flaggedForReview,
            List<String> eliminatedOptionKeys,
            int deltaTimeSpentSeconds,
            int expectedAutosaveVersion,
            long clientAutosaveSeq,
            Boolean offlineReplay,
            Instant clientTimestamp,
            Boolean trainingEligible
    ) {
    }

    public record ExamSessionItemAutosaveResponseDto(
            String schemaVersion,
            UUID sessionId,
            ExamSessionStatus status,
            int itemPosition,
            String questionCode,
            ExamItemResponseState responseState,
            String selectedOptionKey,
            BigDecimal numericResponseValue,
            boolean flaggedForReview,
            List<String> eliminatedOptionKeys,
            int timeSpentSeconds,
            int autosaveVersion,
            long clientAutosaveSeq,
            int answeredItemCount,
            int flaggedItemCount,
            int omittedItemCount,
            ServerClockSync clockSync,
            int llmTokensUsed,
            boolean trainingEligible
    ) {
    }

    public record ExamScratchpadUploadRequestDto(
            int pageNumber,
            String storageKey,
            String mimeType,
            long byteSize,
            String sha256Checksum,
            Integer widthPx,
            Integer heightPx,
            String captureSource,
            String bookletPublisher,
            Instant capturedAt,
            Boolean trainingEligible
    ) {
    }

    public record ExamScratchpadUploadResponseDto(
            String schemaVersion,
            UUID sessionId,
            int itemPosition,
            int scratchpadAttachmentCount,
            int itemScratchpadCount,
            ExamScratchpadAssetDto asset,
            ServerClockSync clockSync,
            int llmTokensUsed,
            boolean trainingEligible
    ) {
    }

    public record ExamSessionSubmitRequestDto(
            String submissionReason,
            Instant clientTimestamp,
            Boolean trainingEligible
    ) {
    }

    public record ExamSessionSubmitResponseDto(
            String schemaVersion,
            UUID sessionId,
            UUID assemblyId,
            UUID blueprintId,
            String examCode,
            ExamSessionStatus status,
            String submissionReason,
            Instant submittedAt,
            String submitIdempotencyKey,
            int totalItemCount,
            int answeredItemCount,
            int flaggedItemCount,
            int omittedItemCount,
            int scratchpadAttachmentCount,
            F28VerificationBatchManifest f28VerificationBatchManifest,
            ServerClockSync clockSync,
            int llmTokensUsed,
            boolean trainingEligible
    ) {
    }

    public record NoHintCheckRequestDto(
            String requestedSurface
    ) {
    }

    // --- Endpoints (Dual-mapped /v1/exams/sessions/** and /api/v1/exams/sessions/**) ---

    @PostMapping
    public ResponseEntity<ExamSessionResponseDto> startOrResumeSession(
            @RequestBody ExamSessionStartRequestDto request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader
    ) {
        UUID authenticatedUserId = resolveAuthenticatedUserId(jwt, authentication, requestedUserIdHeader);
        StartOrResumeResult result = runtimeService.startOrResumeSession(
                authenticatedUserId,
                new StartSessionCommand(
                        request.userId(),
                        request.assemblyId(),
                        request.offlineGraceBufferSeconds(),
                        request.clientTimestamp(),
                        request.trainingEligible()
                )
        );
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(toSessionResponse(result.session(), result.clockSync()));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ExamSessionResponseDto> getSessionState(
            @PathVariable("sessionId") UUID sessionId,
            @RequestParam(value = "clientTimestamp", required = false) Instant clientTimestamp,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader
    ) {
        UUID authenticatedUserId = resolveAuthenticatedUserId(jwt, authentication, requestedUserIdHeader);
        SessionStateResult result = runtimeService.getSessionState(authenticatedUserId, sessionId, clientTimestamp);
        return ResponseEntity.ok(toSessionResponse(result.session(), result.clockSync()));
    }

    @PatchMapping("/{sessionId}/items/{itemPosition}")
    public ResponseEntity<Object> autosaveItem(
            @PathVariable("sessionId") UUID sessionId,
            @PathVariable("itemPosition") int itemPosition,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ExamSessionItemAutosaveRequestDto request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader
    ) {
        UUID authenticatedUserId = resolveAuthenticatedUserId(jwt, authentication, requestedUserIdHeader);
        AutosaveResult result = runtimeService.autosaveItem(
                authenticatedUserId,
                sessionId,
                itemPosition,
                idempotencyKey,
                new AutosaveItemCommand(
                        request.action(),
                        request.selectedOptionKey(),
                        request.numericResponseValue(),
                        request.flaggedForReview(),
                        request.eliminatedOptionKeys(),
                        request.deltaTimeSpentSeconds(),
                        request.expectedAutosaveVersion(),
                        request.clientAutosaveSeq(),
                        request.offlineReplay(),
                        request.clientTimestamp(),
                        request.trainingEligible()
                )
        );
        if (result.idempotentReplay() && result.cachedResponse() != null) {
            return ResponseEntity.ok(result.cachedResponse());
        }
        return ResponseEntity.ok(toAutosaveResponse(result.session(), result.item(), result.clockSync()));
    }

    @PostMapping("/{sessionId}/items/{itemPosition}/scratchpad")
    public ResponseEntity<Object> attachScratchpad(
            @PathVariable("sessionId") UUID sessionId,
            @PathVariable("itemPosition") int itemPosition,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody ExamScratchpadUploadRequestDto request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader
    ) {
        UUID authenticatedUserId = resolveAuthenticatedUserId(jwt, authentication, requestedUserIdHeader);
        ScratchpadAttachResult result = runtimeService.attachScratchpad(
                authenticatedUserId,
                sessionId,
                itemPosition,
                idempotencyKey,
                new AttachScratchpadCommand(
                        request.pageNumber(),
                        request.storageKey(),
                        request.mimeType(),
                        request.byteSize(),
                        request.sha256Checksum(),
                        request.widthPx(),
                        request.heightPx(),
                        request.captureSource(),
                        request.bookletPublisher(),
                        request.capturedAt(),
                        request.trainingEligible()
                )
        );
        if (result.idempotentReplay() && result.cachedResponse() != null) {
            return ResponseEntity.ok(result.cachedResponse());
        }
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(toScratchpadUploadResponse(result.session(), result.item(), result.asset(), result.clockSync()));
    }

    @PostMapping("/{sessionId}/submit")
    public ResponseEntity<Object> submitSession(
            @PathVariable("sessionId") UUID sessionId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody(required = false) ExamSessionSubmitRequestDto request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader
    ) {
        UUID authenticatedUserId = resolveAuthenticatedUserId(jwt, authentication, requestedUserIdHeader);
        String reason = request != null ? request.submissionReason() : "USER_INITIATED";
        Instant clientTimestamp = request != null ? request.clientTimestamp() : null;
        Boolean trainingEligible = request != null ? request.trainingEligible() : Boolean.FALSE;

        SubmitSessionResult result = runtimeService.submitSession(
                authenticatedUserId,
                sessionId,
                idempotencyKey,
                new SubmitSessionCommand(reason, clientTimestamp, trainingEligible)
        );
        if (result.idempotentReplay() && result.cachedResponse() != null) {
            return ResponseEntity.ok(result.cachedResponse());
        }
        return ResponseEntity.ok(toSubmitResponse(result.session(), result.f28Manifest(), result.clockSync()));
    }

    @PostMapping({"/{sessionId}/hint-guard-check", "/{sessionId}/hint-check"})
    public ResponseEntity<HintGuardResult> checkNoHintGuard(
            @PathVariable("sessionId") UUID sessionId,
            @RequestBody(required = false) NoHintCheckRequestDto request,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader
    ) {
        UUID authenticatedUserId = resolveAuthenticatedUserId(jwt, authentication, requestedUserIdHeader);
        String requestedSurface = request != null ? request.requestedSurface() : "SOCRATIC_HINT_DRAWER";
        HintGuardResult result = runtimeService.checkNoHintGuard(authenticatedUserId, sessionId, requestedSurface);
        HttpStatus status = result.hintAllowed() ? HttpStatus.OK : HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(result);
    }

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ProblemDetailsResponse> handleProblem(ApiProblemException ex) {
        ProblemDetailsResponse body = new ProblemDetailsResponse(
                "https://api.verifiedai.app/problems/" + ex.code().name().toLowerCase().replace('_', '-'),
                ex.getMessage(),
                ex.status().value(),
                ex.code(),
                CorrelationIds.current(),
                ex.details()
        );
        return ResponseEntity.status(ex.status()).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetailsResponse> handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetailsResponse body = new ProblemDetailsResponse(
                "https://api.verifiedai.app/problems/exam-item-invalid-answer-payload",
                ex.getMessage(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                ApiErrorCode.EXAM_ITEM_INVALID_ANSWER_PAYLOAD,
                CorrelationIds.current(),
                Map.of("recoverable", true, "userAction", "Check request parameters and retry.")
        );
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    // --- Mapping Helpers ---

    private static UUID resolveAuthenticatedUserId(
            Jwt jwt,
            Authentication authentication,
            String requestedUserIdHeader
    ) {
        UUID principalId = null;
        if (jwt != null && jwt.getSubject() != null && !jwt.getSubject().isBlank()) {
            principalId = parseUuidOrForbidden(jwt.getSubject());
        } else if (authentication != null) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof AuthenticatedLearnerPrincipal alp) {
                principalId = alp.learnerId();
            } else if (principal instanceof VerifiedJwtPrincipal vjp) {
                principalId = vjp.userId();
            } else if (principal instanceof UUID uuid) {
                principalId = uuid;
            } else if (authentication.getName() != null && !authentication.getName().isBlank()) {
                principalId = parseUuidOrForbidden(authentication.getName());
            }
        }

        UUID headerId = null;
        if (requestedUserIdHeader != null && !requestedUserIdHeader.isBlank()) {
            headerId = parseUuidOrForbidden(requestedUserIdHeader.trim());
        }

        if (principalId != null && headerId != null && !principalId.equals(headerId)) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_CANDIDATE_ACCESS_DENIED,
                    "X-User-Id header does not match authenticated JWT subject",
                    false,
                    "Ensure X-User-Id matches the authenticated user session."
            );
        }

        return principalId != null ? principalId : headerId;
    }

    private static UUID parseUuidOrForbidden(String raw) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.EXAM_CANDIDATE_ACCESS_DENIED,
                    "Invalid learner UUID identifier",
                    false,
                    "Authenticate with a valid learner UUID."
            );
        }
    }

    private static ExamScratchpadAssetDto toScratchpadAssetDto(ExamScratchpadAsset asset) {
        return new ExamScratchpadAssetDto(
                asset.id(),
                asset.sessionId(),
                asset.sessionItemId(),
                asset.itemPosition(),
                asset.pageNumber(),
                asset.storageKey(),
                asset.mimeType(),
                asset.byteSize(),
                asset.sha256Checksum(),
                asset.widthPx(),
                asset.heightPx(),
                asset.captureSource(),
                asset.bookletPublisher(),
                asset.f28VerificationStatus(),
                asset.capturedAt(),
                false
        );
    }

    private static ExamSessionItemResponseDto toSessionItemDto(ExamSessionItem item) {
        List<ExamScratchpadAssetDto> assets = item.scratchpadAssets().stream()
                .map(ExamSessionRuntimeController::toScratchpadAssetDto)
                .toList();
        return new ExamSessionItemResponseDto(
                item.id(),
                item.itemPosition(),
                item.questionItemId(),
                item.questionCode(),
                item.sectionCode(),
                item.sectionOrder(),
                item.topicCode(),
                item.itemFormat(),
                item.difficultyBand(),
                item.stemMarkdown(),
                item.optionsSnapshot(),
                item.figureAltText(),
                item.maxPoints(),
                item.negativeMarkingWeight(),
                item.responseState(),
                item.selectedOptionKey(),
                item.numericResponseValue(),
                item.flaggedForReview(),
                item.eliminatedOptionKeys(),
                item.timeSpentSeconds(),
                item.autosaveVersion(),
                item.clientAutosaveSeq(),
                item.lastAutosavedAt(),
                item.scratchpadRequired(),
                assets,
                false
        );
    }

    private static ExamSessionResponseDto toSessionResponse(ExamSession session, ServerClockSync clockSync) {
        List<ExamSessionItemResponseDto> items = session.items().stream()
                .map(ExamSessionRuntimeController::toSessionItemDto)
                .toList();
        return new ExamSessionResponseDto(
                "exam-session-response-v1",
                session.id(),
                session.userId(),
                session.assemblyId(),
                session.blueprintId(),
                session.examCode(),
                session.examVersionNumber(),
                session.status(),
                session.navigationPolicy(),
                session.noHintPolicyEnforced(),
                session.accommodationsSnapshot(),
                session.baseDurationSeconds(),
                session.extraTimeMultiplier(),
                session.effectiveDurationSeconds(),
                session.offlineGraceBufferSeconds(),
                session.startedAt(),
                session.serverDeadlineAt(),
                session.graceDeadlineAt(),
                session.lastHeartbeatAt(),
                session.submittedAt(),
                session.submissionReason(),
                session.totalItemCount(),
                session.answeredItemCount(),
                session.flaggedItemCount(),
                session.omittedItemCount(),
                session.scratchpadAttachmentCount(),
                clockSync,
                session.computeSectionProgress(),
                items,
                session.f28VerificationBatchManifest(),
                0,
                false
        );
    }

    private static ExamSessionItemAutosaveResponseDto toAutosaveResponse(
            ExamSession session,
            ExamSessionItem item,
            ServerClockSync clockSync
    ) {
        return new ExamSessionItemAutosaveResponseDto(
                "exam-session-item-autosave-response-v1",
                session.id(),
                session.status(),
                item.itemPosition(),
                item.questionCode(),
                item.responseState(),
                item.selectedOptionKey(),
                item.numericResponseValue(),
                item.flaggedForReview(),
                item.eliminatedOptionKeys(),
                item.timeSpentSeconds(),
                item.autosaveVersion(),
                item.clientAutosaveSeq(),
                session.answeredItemCount(),
                session.flaggedItemCount(),
                session.omittedItemCount(),
                clockSync,
                0,
                false
        );
    }

    private static ExamScratchpadUploadResponseDto toScratchpadUploadResponse(
            ExamSession session,
            ExamSessionItem item,
            ExamScratchpadAsset asset,
            ServerClockSync clockSync
    ) {
        return new ExamScratchpadUploadResponseDto(
                "exam-scratchpad-upload-response-v1",
                session.id(),
                item.itemPosition(),
                session.scratchpadAttachmentCount(),
                item.scratchpadAssets().size(),
                toScratchpadAssetDto(asset),
                clockSync,
                0,
                false
        );
    }

    private static ExamSessionSubmitResponseDto toSubmitResponse(
            ExamSession session,
            F28VerificationBatchManifest manifest,
            ServerClockSync clockSync
    ) {
        return new ExamSessionSubmitResponseDto(
                "exam-session-submit-response-v1",
                session.id(),
                session.assemblyId(),
                session.blueprintId(),
                session.examCode(),
                session.status(),
                session.submissionReason(),
                session.submittedAt(),
                session.submitIdempotencyKey(),
                session.totalItemCount(),
                session.answeredItemCount(),
                session.flaggedItemCount(),
                session.omittedItemCount(),
                session.scratchpadAttachmentCount(),
                manifest,
                clockSync,
                0,
                false
        );
    }
}
