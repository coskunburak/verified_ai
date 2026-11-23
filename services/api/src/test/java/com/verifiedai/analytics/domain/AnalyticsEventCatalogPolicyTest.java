package com.verifiedai.analytics.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.analytics.domain.AnalyticsPayloadSanitizer.RawEventInput;
import com.verifiedai.analytics.domain.AnalyticsPayloadSanitizer.SanitizedAnalyticsEvent;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class AnalyticsEventCatalogPolicyTest {

    private final AnalyticsPayloadSanitizer sanitizer = new AnalyticsPayloadSanitizer(16, 2048);
    private final Instant occurredAt = Instant.parse("2026-11-23T10:15:30Z");

    @Test
    void closedCatalogCoversAllSevenDomainsAndSplitsClientVsServerAuthority() {
        List<AnalyticsEventSpec> all = AnalyticsEventCatalog.allSpecs();
        assertThat(all).hasSize(56);
        assertThat(all.stream().map(AnalyticsEventSpec::domain).distinct().toList())
            .containsExactlyInAnyOrder(AnalyticsEventDomain.values());
        assertThat(all.stream().filter(AnalyticsEventSpec::clientAllowed).count()).isEqualTo(26);
        assertThat(all.stream().filter(s -> !s.clientAllowed() && s.serverAllowed()).count()).isEqualTo(30);
    }

    @Test
    void rejectsUnknownAndGenericUiEventNamesWith422AnalyticsEventUnknown() {
        for (String generic : List.of("button_clicked", "tap", "click", "screen_viewed", "generic_event", "")) {
            RawEventInput input = baseClientInput("evt-generic-001", generic, Map.of());
            assertThatThrownBy(() -> sanitizer.sanitize(input, AnalyticsEventSource.CLIENT, "FREE"))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).code()).isEqualTo(ApiErrorCode.ANALYTICS_EVENT_UNKNOWN));
        }
    }

    @Test
    void rejectsClientForgingServerAuthoritativeEvents() {
        for (String serverOnly : List.of(
            "purchase_completed",
            "entitlement_changed",
            "paywall.trial_started",
            "solve_completed",
            "first_solution_completed",
            "twin_drill.completed",
            "retrieval_check.completed",
            "mistake_passport.state_transitioned",
            "problem_parse_completed"
        )) {
            RawEventInput input = baseClientInput("evt-server-forge-01", serverOnly, Map.of());
            assertThatThrownBy(() -> sanitizer.sanitize(input, AnalyticsEventSource.CLIENT, "FREE"))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                    .isEqualTo(ApiErrorCode.ANALYTICS_EVENT_VALIDATION_FAILED));
        }
    }

    @Test
    void rejectsAllForbiddenPiiAndRawContentKeysWith422() {
        for (String forbiddenKey : List.of(
            "email",
            "studentEmail",
            "phone",
            "studentName",
            "deviceToken",
            "jwt",
            "signedPayload",
            "rawProblemText",
            "ocrText",
            "recognizedText",
            "latex",
            "rawLatex",
            "solutionSteps",
            "prompt",
            "rawProviderOutput",
            "imageBase64",
            "presignedUrl",
            "objectKey",
            "exactMasteryFloat",
            "masteryScore"
        )) {
            RawEventInput input = baseClientInput(
                "evt-pii-key-0001",
                "verification_details_viewed",
                Map.of(forbiddenKey, "redacted")
            );
            assertThatThrownBy(() -> sanitizer.sanitize(input, AnalyticsEventSource.CLIENT, "FREE"))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                    .isEqualTo(ApiErrorCode.ANALYTICS_PII_OR_RAW_CONTENT_REJECTED));
        }
    }

    @Test
    void rejectsForbiddenValuePatternsInAllowlistedPropertyKeys() {
        List<String> badValues = List.of(
            "learner@example.com",
            "eyJhbGciOiJIUzI1NiJ9.payload",
            "https://s3.example.com/presigned/asset.jpg",
            "\\frac{2x+1}{3}",
            "x".repeat(81),
            "A".repeat(130)
        );
        for (String badValue : badValues) {
            RawEventInput input = baseClientInput(
                "evt-pii-val-0001",
                "verification_details_viewed",
                Map.of("primarySkillCode", badValue)
            );
            assertThatThrownBy(() -> sanitizer.sanitize(input, AnalyticsEventSource.CLIENT, "FREE"))
                .isInstanceOf(ApiProblemException.class)
                .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                    .isEqualTo(ApiErrorCode.ANALYTICS_PII_OR_RAW_CONTENT_REJECTED));
        }
    }

    @Test
    void enforcesPropertyCountByteLimitsAndTrainingIneligibleGuard() {
        AnalyticsPayloadSanitizer tightCountSanitizer = new AnalyticsPayloadSanitizer(2, 2048);
        RawEventInput tooManyProps = baseClientInput(
            "evt-too-many-0001",
            "verification_details_viewed",
            Map.of("problemType", "LINEAR", "primarySkillCode", "ALG_1", "difficultyBand", "EASY")
        );
        assertThatThrownBy(() -> tightCountSanitizer.sanitize(tooManyProps, AnalyticsEventSource.CLIENT, "FREE"))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.ANALYTICS_EVENT_VALIDATION_FAILED));

        AnalyticsPayloadSanitizer tightBytesSanitizer = new AnalyticsPayloadSanitizer(16, 40);
        RawEventInput tooManyBytes = baseClientInput(
            "evt-too-large-0001",
            "verification_details_viewed",
            Map.of("problemType", "LINEAR_EQUATION", "primarySkillCode", "MATH.ALG.LINEAR_EQ")
        );
        assertThatThrownBy(() -> tightBytesSanitizer.sanitize(tooManyBytes, AnalyticsEventSource.CLIENT, "FREE"))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.ANALYTICS_EVENT_VALIDATION_FAILED));

        RawEventInput trainingTrue = new RawEventInput(
            "evt-train-0001",
            "onboarding_started",
            "CLIENT",
            "ios_client",
            "FREE",
            "en",
            "1.0.0",
            null,
            null,
            0L,
            Map.of("locale", "en"),
            occurredAt,
            true
        );
        assertThatThrownBy(() -> sanitizer.sanitize(trainingTrue, AnalyticsEventSource.CLIENT, "FREE"))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.ANALYTICS_EVENT_VALIDATION_FAILED));
    }

    @Test
    void computesDeterministicSha256PayloadHashRegardlessOfMapOrder() {
        Map<String, Object> propsA = new LinkedHashMap<>();
        propsA.put("verificationStatus", "VERIFIED");
        propsA.put("problemType", "QUADRATIC");

        Map<String, Object> propsB = new LinkedHashMap<>();
        propsB.put("problemType", "QUADRATIC");
        propsB.put("verificationStatus", "VERIFIED");

        SanitizedAnalyticsEvent first = sanitizer.sanitize(
            baseClientInput("evt-hash-0001", "verification_details_viewed", propsA),
            AnalyticsEventSource.CLIENT,
            "PRO"
        );
        SanitizedAnalyticsEvent second = sanitizer.sanitize(
            baseClientInput("evt-hash-0001", "verification_details_viewed", propsB),
            AnalyticsEventSource.CLIENT,
            "PRO"
        );
        SanitizedAnalyticsEvent modified = sanitizer.sanitize(
            baseClientInput("evt-hash-0001", "verification_details_viewed", Map.of("problemType", "LINEAR")),
            AnalyticsEventSource.CLIENT,
            "PRO"
        );

        assertThat(first.payloadHash()).hasSize(64).isEqualTo(second.payloadHash());
        assertThat(first.payloadHash()).isNotEqualTo(modified.payloadHash());
        assertThat(first.aiCostMicros()).isZero();
    }

    private RawEventInput baseClientInput(String clientEventId, String eventName, Map<String, Object> props) {
        return new RawEventInput(
            clientEventId,
            eventName,
            "CLIENT",
            "ios_client",
            "FREE",
            "en",
            "1.6.0",
            null,
            120,
            5_000L,
            props,
            occurredAt,
            false
        );
    }
}
