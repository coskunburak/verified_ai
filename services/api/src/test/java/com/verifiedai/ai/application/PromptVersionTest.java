package com.verifiedai.ai.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class PromptVersionTest {

    @ParameterizedTest
    @ValueSource(strings = {"v001", "v002"})
    void acceptsImmutableVersionFormat(
        String version
    ) {
        assertThatCode(() ->
            PromptVersion.requireValid(version)
        ).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"latest", "1", "v1", "prod"})
    void rejectsMutableOrAmbiguousVersions(
        String version
    ) {
        assertThatThrownBy(() ->
            PromptVersion.requireValid(version)
        )
            .isInstanceOf(
                IllegalArgumentException.class
            )
            .hasMessageContaining(
                "vNNN"
            );
    }
}
