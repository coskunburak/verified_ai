package com.verifiedai.identity.api;

import com.verifiedai.identity.application.AccountPrivacyApplicationService;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
class AccountPrivacyController {
    private final AccountPrivacyApplicationService accountPrivacyApplicationService;

    AccountPrivacyController(AccountPrivacyApplicationService accountPrivacyApplicationService) {
        this.accountPrivacyApplicationService = accountPrivacyApplicationService;
    }

    @GetMapping("/account")
    AccountStateResponse account(@AuthenticationPrincipal Jwt jwt) {
        return AccountStateResponse.from(accountPrivacyApplicationService.currentAccount(AuthenticatedUser.from(jwt).userId()));
    }

    @PostMapping("/data-exports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    DataExportResponse requestExport(@AuthenticationPrincipal Jwt jwt) {
        return DataExportResponse.from(accountPrivacyApplicationService.requestExport(AuthenticatedUser.from(jwt).userId()));
    }

    @GetMapping("/data-exports/{exportId}")
    DataExportResponse exportStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID exportId) {
        return DataExportResponse.from(accountPrivacyApplicationService.exportStatus(AuthenticatedUser.from(jwt).userId(), exportId));
    }

    @GetMapping("/data-exports/{exportId}/content")
    Map<String, Object> exportContent(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID exportId) {
        return accountPrivacyApplicationService.downloadExport(AuthenticatedUser.from(jwt).userId(), exportId);
    }

    @PostMapping("/deletion-request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    DeletionRequestResponse requestDeletion(@AuthenticationPrincipal Jwt jwt) {
        return DeletionRequestResponse.from(accountPrivacyApplicationService.requestDeletion(AuthenticatedUser.from(jwt).userId()));
    }

    @GetMapping("/deletion-request")
    DeletionRequestResponse deletionRequest(@AuthenticationPrincipal Jwt jwt) {
        return DeletionRequestResponse.from(accountPrivacyApplicationService.deletionRequest(AuthenticatedUser.from(jwt).userId()));
    }

    @PostMapping("/deletion-request/confirm")
    DeletionRequestResponse confirmDeletion(
        @AuthenticationPrincipal Jwt jwt,
        @RequestBody DeletionConfirmationRequest request
    ) {
        return DeletionRequestResponse.from(accountPrivacyApplicationService.confirmDeletion(
            AuthenticatedUser.from(jwt).userId(),
            request.confirmationText()
        ));
    }
}
