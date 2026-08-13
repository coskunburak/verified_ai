package com.verifiedai.problem.application.session;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ProblemSessionCursorCodec {
    private static final String VERSION = "v1";
    private static final int MAX_CURSOR_LENGTH = 256;

    public String encode(ProblemSessionCursor cursor) {
        String value = VERSION + "|" + cursor.updatedAt() + "|" + cursor.sessionId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    public ProblemSessionCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        if (cursor.length() > MAX_CURSOR_LENGTH) {
            throw invalidCursor();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            String value = new String(decoded, StandardCharsets.UTF_8);
            String[] parts = value.split("\\|", -1);
            if (parts.length != 3 || !VERSION.equals(parts[0])) {
                throw invalidCursor();
            }
            return new ProblemSessionCursor(Instant.parse(parts[1]), UUID.fromString(parts[2]));
        } catch (ApiProblemException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidCursor();
        }
    }

    private static ApiProblemException invalidCursor() {
        return new ApiProblemException(
            HttpStatus.BAD_REQUEST,
            ApiErrorCode.PROBLEM_SESSION_CURSOR_INVALID,
            "Problem session history cursor is invalid",
            false,
            "NONE"
        );
    }
}
