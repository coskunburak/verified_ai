package com.verifiedai.exam.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PreExamEntityTagsTest {
    private final String digest = "a".repeat(64);
    private final String tag = PreExamEntityTags.quote(digest);

    @Test void acceptsStrongWeakListsAndWildcard() {
        assertTrue(PreExamEntityTags.matches(tag, digest));
        assertTrue(PreExamEntityTags.matches("W/" + tag, digest));
        assertTrue(PreExamEntityTags.matches("\"another,opaque,tag\", W/" + tag, digest));
        assertTrue(PreExamEntityTags.matches(" \t* \t", digest));
        assertTrue(PreExamEntityTags.matches(tag + ", \"other\"", digest));
    }
    @Test void malformedOrNonmatchingValuesCannotReturn304() {
        for (String input : new String[]{"", digest, "w/" + tag, tag + ",", tag + ", *",
                "*, " + tag, tag + "junk", tag + ", \"unterminated", "\"other\"", "x".repeat(8193)}) {
            assertFalse(PreExamEntityTags.matches(input, digest), input);
        }
        assertFalse(PreExamEntityTags.matches(null, digest));
    }
    @Test void refusesUntrustedDigestInResponseHeader() {
        assertThrows(IllegalArgumentException.class, () -> PreExamEntityTags.quote("\r\nInjected: true"));
        assertThrows(IllegalArgumentException.class, () -> PreExamEntityTags.quote(null));
    }
}
