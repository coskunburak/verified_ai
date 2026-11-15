package com.verifiedai.exam.api;

import com.verifiedai.exam.application.CandidateProfileService;
import com.verifiedai.exam.application.CandidateProfileService.UpsertResult;
import com.verifiedai.exam.application.SyllabusIngestionService;
import com.verifiedai.exam.application.SyllabusIngestionService.IngestResult;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileHistoryResponse;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileResponse;
import com.verifiedai.exam.domain.CandidateProfileModels.CandidateProfileUpsertRequest;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusConfirmRequest;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusIngestJsonRequest;
import com.verifiedai.exam.domain.CandidateProfileModels.SyllabusIngestionResponse;
import com.verifiedai.security.VerifiedJwtPrincipal;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import com.verifiedai.sharedkernel.error.ProblemDetailsResponse;
import com.verifiedai.sharedkernel.observability.CorrelationIds;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestController
@RequestMapping({"/v1/candidate", "/api/v1/candidate"})
public class CandidateProfileController {

    private final CandidateProfileService candidateProfileService;
    private final SyllabusIngestionService syllabusIngestionService;

    public CandidateProfileController(
            CandidateProfileService candidateProfileService,
            SyllabusIngestionService syllabusIngestionService
    ) {
        this.candidateProfileService = Objects.requireNonNull(candidateProfileService);
        this.syllabusIngestionService = Objects.requireNonNull(syllabusIngestionService);
    }

    @PutMapping("/profile")
    public ResponseEntity<CandidateProfileResponse> putProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CandidateProfileUpsertRequest request
    ) {
        UUID learnerId = resolveLearnerId(jwt, requestedUserIdHeader);
        UpsertResult result = candidateProfileService.upsertProfile(learnerId, request, idempotencyKey);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.response());
    }

    @PostMapping("/profile")
    public ResponseEntity<CandidateProfileResponse> postProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CandidateProfileUpsertRequest request
    ) {
        UUID learnerId = resolveLearnerId(jwt, requestedUserIdHeader);
        UpsertResult result = candidateProfileService.upsertProfile(learnerId, request, idempotencyKey);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.response());
    }

    @GetMapping("/profile")
    public ResponseEntity<CandidateProfileResponse> getProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader
    ) {
        UUID learnerId = resolveLearnerId(jwt, requestedUserIdHeader);
        return ResponseEntity.ok(candidateProfileService.getProfile(learnerId));
    }

    @GetMapping("/profile/history")
    public ResponseEntity<CandidateProfileHistoryResponse> getProfileHistory(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader
    ) {
        UUID learnerId = resolveLearnerId(jwt, requestedUserIdHeader);
        return ResponseEntity.ok(candidateProfileService.getProfileHistory(learnerId));
    }

    @PostMapping(value = "/syllabus-ingest", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<SyllabusIngestionResponse> ingestSyllabusMultipart(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String headerIdempotencyKey,
            @RequestPart("file") MultipartFile file,
            @RequestParam("examCode") String examCode,
            @RequestParam(value = "examVersion", required = false) String examVersion,
            @RequestParam(value = "idempotencyKey", required = false) String paramIdempotencyKey
    ) throws IOException {
        UUID learnerId = resolveLearnerId(jwt, requestedUserIdHeader);
        String effectiveKey = headerIdempotencyKey != null && !headerIdempotencyKey.isBlank()
                ? headerIdempotencyKey
                : paramIdempotencyKey;
        byte[] rawBytes = file == null ? new byte[0] : file.getBytes();
        IngestResult result = syllabusIngestionService.ingestSyllabus(
                learnerId,
                examCode,
                examVersion,
                rawBytes,
                file == null ? null : file.getContentType(),
                file == null ? null : file.getOriginalFilename(),
                effectiveKey
        );
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.response());
    }

    @PostMapping(value = "/syllabus-ingest", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SyllabusIngestionResponse> ingestSyllabusJson(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader,
            @RequestHeader(value = "Idempotency-Key", required = false) String headerIdempotencyKey,
            @RequestBody SyllabusIngestJsonRequest request
    ) {
        UUID learnerId = resolveLearnerId(jwt, requestedUserIdHeader);
        if (request == null || request.base64Payload() == null || request.base64Payload().isBlank()) {
            throw new ApiProblemException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    ApiErrorCode.SYLLABUS_MEDIA_UNSUPPORTED,
                    "Missing base64 syllabus media payload",
                    true,
                    "Provide a valid base64-encoded JPEG, PNG, WEBP, HEIC, or PDF document."
            );
        }
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(request.base64Payload().trim());
        } catch (IllegalArgumentException ex) {
            throw new ApiProblemException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    ApiErrorCode.SYLLABUS_MEDIA_UNSUPPORTED,
                    "Invalid base64 syllabus payload",
                    true,
                    "Upload valid base64-encoded image or PDF bytes."
            );
        }
        String effectiveKey = headerIdempotencyKey != null && !headerIdempotencyKey.isBlank()
                ? headerIdempotencyKey
                : request.idempotencyKey();
        IngestResult result = syllabusIngestionService.ingestSyllabus(
                learnerId,
                request.examCode(),
                request.examVersion(),
                decoded,
                request.mimeType(),
                request.filenameHint(),
                effectiveKey
        );
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.response());
    }

    @GetMapping("/syllabus-uploads/{uploadId}")
    public ResponseEntity<SyllabusIngestionResponse> getSyllabusUpload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader,
            @PathVariable("uploadId") UUID uploadId
    ) {
        UUID learnerId = resolveLearnerId(jwt, requestedUserIdHeader);
        return ResponseEntity.ok(syllabusIngestionService.getUpload(learnerId, uploadId));
    }

    @PostMapping("/syllabus-uploads/{uploadId}/confirm")
    public ResponseEntity<SyllabusIngestionResponse> confirmSyllabusUpload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "X-User-Id", required = false) String requestedUserIdHeader,
            @PathVariable("uploadId") UUID uploadId,
            @RequestBody SyllabusConfirmRequest request
    ) {
        UUID learnerId = resolveLearnerId(jwt, requestedUserIdHeader);
        return ResponseEntity.ok(syllabusIngestionService.confirmDisambiguation(learnerId, uploadId, request));
    }

    @ExceptionHandler(ApiProblemException.class)
    public ResponseEntity<ProblemDetailsResponse> handleProblem(ApiProblemException ex) {
        return buildProblemDetails(ex.status(), ex.code(), ex.getMessage(), ex.details());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ProblemDetailsResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return buildProblemDetails(
                HttpStatus.PAYLOAD_TOO_LARGE,
                ApiErrorCode.SYLLABUS_MEDIA_UNSUPPORTED,
                "Uploaded syllabus file exceeds maximum allowed size",
                Map.of("recoverable", true, "userAction", "Upload a syllabus image or PDF smaller than 10MB.")
        );
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ProblemDetailsResponse> handleMissingPart(MissingServletRequestPartException ex) {
        return buildProblemDetails(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.SYLLABUS_MEDIA_UNSUPPORTED,
                "Missing required multipart field: " + ex.getRequestPartName(),
                Map.of("recoverable", true, "userAction", "Attach the syllabus image/PDF under the 'file' form part.")
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetailsResponse> handleUnreadableJson(HttpMessageNotReadableException ex) {
        return buildProblemDetails(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.CANDIDATE_STUDY_BUDGET_INVALID,
                "Malformed candidate request payload",
                Map.of("recoverable", true, "userAction", "Check field types and formats in the JSON payload.")
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetailsResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return buildProblemDetails(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.CANDIDATE_STUDY_BUDGET_INVALID,
                ex.getMessage(),
                Map.of("recoverable", true, "userAction", "Check request parameters and retry.")
        );
    }

    private UUID resolveLearnerId(Jwt jwt, String requestedUserIdHeader) {
        if (jwt == null) {
            throw new ApiProblemException(
                    HttpStatus.UNAUTHORIZED,
                    ApiErrorCode.CANDIDATE_PROFILE_ACCESS_DENIED,
                    "Authentication is required",
                    true,
                    "Sign in before accessing candidate profile endpoints."
            );
        }
        UUID authenticatedUserId = VerifiedJwtPrincipal.from(jwt).learnerId();
        if (requestedUserIdHeader != null && !requestedUserIdHeader.isBlank()) {
            UUID requestedUserId;
            try {
                requestedUserId = UUID.fromString(requestedUserIdHeader.trim());
            } catch (IllegalArgumentException ex) {
                throw new ApiProblemException(
                        HttpStatus.BAD_REQUEST,
                        ApiErrorCode.CANDIDATE_PROFILE_ACCESS_DENIED,
                        "Invalid X-User-Id header format",
                        true,
                        "Provide a valid UUID or omit X-User-Id."
                );
            }
            if (!authenticatedUserId.equals(requestedUserId)) {
                throw new ApiProblemException(
                        HttpStatus.FORBIDDEN,
                        ApiErrorCode.CANDIDATE_PROFILE_ACCESS_DENIED,
                        "Access to another learner's candidate profile is forbidden",
                        false,
                        "Access only your own candidate profile and syllabus uploads."
                );
            }
        }
        return authenticatedUserId;
    }

    private ResponseEntity<ProblemDetailsResponse> buildProblemDetails(
            HttpStatus status,
            ApiErrorCode code,
            String title,
            Map<String, Object> details
    ) {
        ProblemDetailsResponse body = new ProblemDetailsResponse(
                "https://api.verifiedai.app/problems/" + code.name().toLowerCase().replace('_', '-'),
                title,
                status.value(),
                code,
                CorrelationIds.current(),
                details
        );
        return ResponseEntity.status(status).body(body);
    }
}
