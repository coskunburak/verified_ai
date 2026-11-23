package com.verifiedai.featureflags.api;

import com.verifiedai.featureflags.api.dto.RemoteConfigResponse;
import com.verifiedai.featureflags.application.ClientRemoteConfigView;
import com.verifiedai.featureflags.application.FeatureFlagEvaluationPort;
import com.verifiedai.sharedkernel.security.AuthenticatedUser;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authenticated learner remote-config endpoint delivering strictly client_safe=true
 * flags, experiments, and kill-switch state with strong ETag / 304 Not Modified support.
 */
@RestController
@RequestMapping({"/api/v1/config", "/v1/config"})
public class RemoteConfigController {

    private final FeatureFlagEvaluationPort evaluationPort;

    public RemoteConfigController(FeatureFlagEvaluationPort evaluationPort) {
        this.evaluationPort = Objects.requireNonNull(evaluationPort, "evaluationPort must not be null");
    }

    @GetMapping(path = "/remote", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<RemoteConfigResponse> getRemoteConfig(
        @AuthenticationPrincipal Jwt jwt,
        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch
    ) {
        UUID userId = AuthenticatedUser.from(jwt).userId();
        ClientRemoteConfigView bundle = evaluationPort.evaluateClientBundle(userId);
        if (matchesIfNoneMatch(ifNoneMatch, bundle.etag())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .eTag(bundle.etag())
                .cacheControl(CacheControl.noStore().cachePrivate())
                .build();
        }
        return ResponseEntity.ok()
            .eTag(bundle.etag())
            .cacheControl(CacheControl.noStore().cachePrivate())
            .body(RemoteConfigResponse.from(bundle));
    }

    private static boolean matchesIfNoneMatch(String headerValue, String currentEtag) {
        if (headerValue == null || headerValue.isBlank() || currentEtag == null || currentEtag.isBlank()) {
            return false;
        }
        if (headerValue.contains("\r") || headerValue.contains("\n")) {
            return false;
        }
        String normalizedCurrent = stripQuotesAndWeakPrefix(currentEtag);
        String[] candidates = headerValue.split(",");
        for (String candidate : candidates) {
            String trimmed = candidate.trim();
            if ("*".equals(trimmed)) {
                return true;
            }
            if (trimmed.equals(currentEtag) || stripQuotesAndWeakPrefix(trimmed).equals(normalizedCurrent)) {
                return true;
            }
        }
        return false;
    }

    private static String stripQuotesAndWeakPrefix(String tag) {
        String value = tag.trim();
        if (value.startsWith("W/")) {
            value = value.substring(2).trim();
        }
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
