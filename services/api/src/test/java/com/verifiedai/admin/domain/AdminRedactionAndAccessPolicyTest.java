package com.verifiedai.admin.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

final class AdminRedactionAndAccessPolicyTest {

    @Test
    void redactsForbiddenRawFieldsTokensAndEmailsDeterministically() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("objectKey", "users/u1/sessions/s1/original.jpg");
        raw.put("rawOutputJsonb", "{\"text\":\"secret\"}");
        raw.put("normalizedProblemJsonb", "{\"latex\":\"x+1=2\"}");
        raw.put("raw_jws", "header.payload.sig");
        raw.put("tokenHash", "abc123");
        raw.put("safeSummary", "Contact student@example.com or Bearer eyJhbGciOiJIUzI1NiJ9.payload123.sig12");

        Map<String, Object> redacted = AdminRedactionPolicy.redactMap(raw);
        assertThat(redacted).doesNotContainKeys(
            "objectKey",
            "rawOutputJsonb",
            "normalizedProblemJsonb",
            "raw_jws",
            "tokenHash"
        );
        assertThat(redacted.get("safeSummary").toString())
            .doesNotContain("student@example.com")
            .doesNotContain("eyJhbGciOiJIUzI1NiJ9")
            .contains(AdminRedactionPolicy.REDACTED_PLACEHOLDER);
    }

    @Test
    void validatesTicketReferenceReasonTtlIdempotencyAndSha256() {
        assertThat(AdminRedactionPolicy.validateTicketReference("SUP-2026.108_1"))
            .isEqualTo("SUP-2026.108_1");
        assertThat(AdminRedactionPolicy.validateGrantTtlMinutes(null, 15)).isEqualTo(15);
        assertThat(AdminRedactionPolicy.validateGrantTtlMinutes(60, 15)).isEqualTo(60);
        assertThat(AdminRedactionPolicy.validateIdempotencyKey("idem-key:108-valid"))
            .isEqualTo("idem-key:108-valid");

        assertThatThrownBy(() -> AdminRedactionPolicy.validateTicketReference("bad ticket!"))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.ADMIN_VALIDATION_FAILED));

        assertThatThrownBy(() -> AdminRedactionPolicy.validateGrantTtlMinutes(61, 15))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.ADMIN_VALIDATION_FAILED));

        assertThatThrownBy(() -> AdminRedactionPolicy.validateStandardReason("ok"))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.ADMIN_VALIDATION_FAILED));

        assertThatThrownBy(() -> AdminRedactionPolicy.validateElevatedGrantReason("short"))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.ADMIN_VALIDATION_FAILED));

        assertThatThrownBy(() -> AdminRedactionPolicy.validateIdempotencyKey("short"))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.ADMIN_VALIDATION_FAILED));

        assertThatThrownBy(() -> AdminRedactionPolicy.validateResolutionNote("Contact learner@example.com for details"))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.ADMIN_VALIDATION_FAILED));

        String hash1 = AdminRedactionPolicy.sha256Hex("canonical-payload");
        String hash2 = AdminRedactionPolicy.sha256Hex("canonical-payload");
        assertThat(hash1).hasSize(64).matches("^[0-9a-f]{64}$").isEqualTo(hash2);
    }

    @Test
    void checksElevatedRawContentAuthorities() {
        assertThat(AdminRedactionPolicy.hasRawContentAccess(List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))))
            .isFalse();
        assertThat(AdminRedactionPolicy.hasRawContentAccess(List.of(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("ADMIN_RAW_CONTENT_READ")
        ))).isTrue();
        assertThat(AdminRedactionPolicy.hasRawContentAccess(List.of(
            new SimpleGrantedAuthority("ROLE_ADMIN"),
            new SimpleGrantedAuthority("ROLE_ADMIN_ENGINEERING")
        ))).isTrue();
    }
}
