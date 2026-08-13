package com.verifiedai.problem.application.session;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProblemSessionCursorCodecTest {
    private final ProblemSessionCursorCodec codec = new ProblemSessionCursorCodec();

    @Test
    void roundTripsKeysetCursorWithoutExposingRawOrderingFields() {
        ProblemSessionCursor cursor = new ProblemSessionCursor(
            Instant.parse("2026-08-11T09:30:00Z"),
            UUID.fromString("00000000-0000-0000-0000-000000004901")
        );

        String encoded = codec.encode(cursor);

        assertThat(encoded).doesNotContain("2026-08-11T09:30:00Z");
        assertThat(codec.decode(encoded)).isEqualTo(cursor);
    }

    @Test
    void rejectsInvalidCursorAsPublicProblemSessionError() {
        assertThatThrownBy(() -> codec.decode("not-a-valid-cursor"))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.PROBLEM_SESSION_CURSOR_INVALID);
    }
}
