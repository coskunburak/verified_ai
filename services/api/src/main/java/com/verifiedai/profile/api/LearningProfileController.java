package com.verifiedai.profile.api;

import com.verifiedai.profile.application.LearningProfileApplicationService;
import com.verifiedai.profile.application.UpdateLearningProfileCommand;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/learning-profile")
public class LearningProfileController {
    private final LearningProfileApplicationService learningProfileApplicationService;

    LearningProfileController(LearningProfileApplicationService learningProfileApplicationService) {
        this.learningProfileApplicationService = learningProfileApplicationService;
    }

    @GetMapping
    LearningProfileResponse current(@AuthenticationPrincipal Jwt jwt) {
        return LearningProfileResponse.from(learningProfileApplicationService.getCurrent(AuthenticatedUser.from(jwt).userId()));
    }

    @PatchMapping
    @ResponseStatus(HttpStatus.OK)
    LearningProfileResponse update(@AuthenticationPrincipal Jwt jwt, @RequestBody UpdateLearningProfileRequest request) {
        return LearningProfileResponse.from(learningProfileApplicationService.updateCurrent(
            AuthenticatedUser.from(jwt).userId(),
            new UpdateLearningProfileCommand(
                request.educationLevel(),
                request.preferredLanguage(),
                request.explanationDepth(),
                request.dailyStudyMinutes(),
                request.timezone(),
                request.goalContext(),
                Boolean.TRUE.equals(request.completeOnboarding()),
                request.expectedVersion()
            )
        ));
    }
}
