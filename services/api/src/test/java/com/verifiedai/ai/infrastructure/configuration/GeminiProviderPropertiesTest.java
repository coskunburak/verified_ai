package com.verifiedai.ai.infrastructure.configuration;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GeminiProviderPropertiesTest {

    @Test
    void disabledPropertiesDoNotRequireCredentialsOrEndpoint() {
        var props = GeminiProviderProperties.disabled();
        assertThat(props.enabled()).isFalse();
        assertThat(props.apiKey()).isEmpty();
        assertThat(props.toString()).doesNotContain("secret");
        assertThat(props.toString()).contains("apiKey=REDACTED");
    }

    @Test
    void enabledPropertiesValidateOfficialModelIdentifiers() {
        var limits = GeminiProviderProperties.Limits.defaultLimits();
        var accounting = GeminiProviderProperties.Accounting.defaultAccounting();

        // Valid official Gemini API models
        for (String validModel : new String[]{"gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash", "gemini-1.5-flash"}) {
            var props = new GeminiProviderProperties(
                true,
                URI.create("https://generativelanguage.googleapis.com"),
                validModel,
                "not_a_secret_synthetic_key_12345",
                "approval-ref-1",
                limits,
                accounting
            );
            assertThat(props.model()).isEqualTo(validModel);
        }

        // Consumer-facing or invalid marketing names must be rejected
        for (String invalidModel : new String[]{"Gemini Advanced", "Gemini Ultra", "gemini-flash-thinking", "gpt-4o", "unknown-model"}) {
            assertThatThrownBy(() -> new GeminiProviderProperties(
                true,
                URI.create("https://generativelanguage.googleapis.com"),
                invalidModel,
                "not_a_secret_synthetic_key_12345",
                "approval-ref-1",
                limits,
                accounting
            )).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void enabledPropertiesRequireHttpsOrLoopbackHttp() {
        var limits = GeminiProviderProperties.Limits.defaultLimits();
        var accounting = GeminiProviderProperties.Accounting.defaultAccounting();

        // Non-loopback HTTP must be rejected
        assertThatThrownBy(() -> new GeminiProviderProperties(
            true,
            URI.create("http://insecure-remote.example.com"),
            "gemini-2.5-flash",
            "not_a_secret_synthetic_key_12345",
            "approval-ref-1",
            limits,
            accounting
        )).isInstanceOf(IllegalArgumentException.class);

        // Loopback HTTP is accepted for test doubles
        var loopback = new GeminiProviderProperties(
            true,
            URI.create("http://127.0.0.1:8080"),
            "gemini-2.5-flash",
            "not_a_secret_synthetic_key_12345",
            "approval-ref-1",
            limits,
            accounting
        );
        assertThat(loopback.endpoint().getHost()).isEqualTo("127.0.0.1");
    }

    @Test
    void toStringStrictlyMasksApiKey() {
        var limits = GeminiProviderProperties.Limits.defaultLimits();
        var accounting = GeminiProviderProperties.Accounting.defaultAccounting();
        String rawKey = "not_a_secret_confidential_token_xyz987";

        var props = new GeminiProviderProperties(
            true,
            URI.create("https://generativelanguage.googleapis.com"),
            "gemini-2.5-flash",
            rawKey,
            "approval-ref-1",
            limits,
            accounting
        );

        String representation = props.toString();
        assertThat(representation).doesNotContain(rawKey);
        assertThat(representation).contains("apiKey=REDACTED");
    }
}
