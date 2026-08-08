package com.verifiedai.problem.api;

import com.verifiedai.problem.application.ProblemAssetUploadApplicationService;
import com.verifiedai.problem.application.ProblemAssetUploadCommand;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/uploads")
public class ProblemAssetUploadController {
    private final ProblemAssetUploadApplicationService uploadApplicationService;

    ProblemAssetUploadController(ProblemAssetUploadApplicationService uploadApplicationService) {
        this.uploadApplicationService = uploadApplicationService;
    }

    @PostMapping("/presign")
    @ResponseStatus(HttpStatus.CREATED)
    PresignProblemAssetUploadResponse presign(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        @RequestBody PresignProblemAssetUploadRequest request
    ) {
        return PresignProblemAssetUploadResponse.from(uploadApplicationService.reserve(
            AuthenticatedUser.from(jwt).userId(),
            idempotencyKey,
            new ProblemAssetUploadCommand(
                request.source(),
                request.assetKind(),
                request.contentType(),
                request.safeSizeBytes(),
                request.checksumSha256(),
                request.imageWidth(),
                request.imageHeight(),
                request.pageCount(),
                request.cropX(),
                request.cropY(),
                request.cropWidth(),
                request.cropHeight()
            )
        ));
    }

    @PostMapping("/{uploadId}/complete")
    @ResponseStatus(HttpStatus.OK)
    CompleteProblemAssetUploadResponse complete(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
        @PathVariable UUID uploadId
    ) {
        return CompleteProblemAssetUploadResponse.from(uploadApplicationService.complete(
            AuthenticatedUser.from(jwt).userId(),
            uploadId,
            idempotencyKey
        ));
    }
}
