package com.verifiedai.problem.api.preprocessing;

import com.verifiedai.problem.application.asset.ProblemAssetPreprocessingApplicationService;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/problem-assets")
public class ProblemAssetPreprocessingController {
    private final ProblemAssetPreprocessingApplicationService preprocessingApplicationService;

    ProblemAssetPreprocessingController(ProblemAssetPreprocessingApplicationService preprocessingApplicationService) {
        this.preprocessingApplicationService = preprocessingApplicationService;
    }

    @PostMapping("/{assetId}/preprocess")
    @ResponseStatus(HttpStatus.OK)
    ProblemAssetPreprocessingResponse preprocess(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID assetId
    ) {
        return ProblemAssetPreprocessingResponse.from(preprocessingApplicationService.preprocess(
            AuthenticatedUser.from(jwt).userId(),
            assetId
        ));
    }

    @GetMapping("/{assetId}/preprocessing")
    @ResponseStatus(HttpStatus.OK)
    ProblemAssetPreprocessingResponse getPreprocessing(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID assetId
    ) {
        return ProblemAssetPreprocessingResponse.from(preprocessingApplicationService.getPreprocessing(
            AuthenticatedUser.from(jwt).userId(),
            assetId
        ));
    }
}
