package com.verifiedai.ai.application;

import java.util.Objects;
import java.util.regex.Pattern;

public record PromptVersion(String value) {

    private static final Pattern FORMAT =
        Pattern.compile("^v\\d{3}$");

    public PromptVersion {
        Objects.requireNonNull(value, "prompt version is required");

        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                "Prompt version must match vNNN"
            );
        }
    }

    public static void requireValid(
        String value
    ) {
        new PromptVersion(value);
    }
}
