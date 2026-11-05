package com.verifiedai.attempt.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verifiedai.attempt.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AttemptSubmissionRequestTest {
    final ObjectMapper mapper=new ObjectMapper();
    final String source="\"source\":{\"canonicalId\":\"AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA\",\"canonicalRevision\":1,\"parseId\":\"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb\",\"parseRevision\":1}";
    AttemptSubmissionRequest decode(String body) throws Exception { return AttemptSubmissionRequest.parse(mapper.readTree(body)); }
    @Test void explicitAnswerAndLegacyStepsRemainDifferent() throws Exception {
        var answer=decode("{\"schemaVersion\":\"attempt-submission-v3\","+source+",\"steps\":[],\"finalAnswer\":\"  x = 4  \"}");
        var transport=tools.jackson.databind.json.JsonMapper.builder().build();
        assertThat(transport.readValue("{\"schemaVersion\":\"attempt-submission-v3\","+source+",\"steps\":[],\"finalAnswer\":\"  x = 4  \"}",AttemptSubmissionRequest.class)).isEqualTo(answer);
        assertThat(answer.finalAnswer()).isEqualTo("  x = 4  "); assertThat(answer.steps()).isEmpty();
        var legacy=decode("{"+source+",\"steps\":[\"x=4\"]}");
        assertThat(legacy.finalAnswer()).isNull(); assertThat(legacy.steps()).containsExactly("x=4");
    }
    @Test void rejectsUnknownNullCoercedAndAmbiguousAuthority() {
        String good="{\"schemaVersion\":\"attempt-submission-v3\","+source+",\"steps\":[],\"finalAnswer\":\"x=4\"}";
        for(String body:List.of(good.replace("v3","v4"),good.replace("\"x=4\"","null"),
            good.replace("\"x=4\"","4"),good.replace("\"x=4\"","\" \""),good.replace("\"x=4\"","\"\\u0000\""),
            good.replace("\"canonicalRevision\":1","\"canonicalRevision\":\"1\""),
            good.replace("\"canonicalRevision\":1","\"canonicalRevision\":1.5"),
            good.replace("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA","1-1-1-1-1"),
            good.replace("\"steps\":[]","\"steps\":[],\"trainingEligible\":true"),
            good.replace("\"steps\":[]","\"steps\":[],\"realDomainConfirmed\":true"),
            good.replace("\"steps\":[]","\"steps\":[null]"),
            good.replace("\"steps\":[]","\"steps\":[1]"),
            "{"+source+",\"steps\":[\"x=4\"],\"realDomainConfirmed\":null}")) {
            assertThatThrownBy(()->decode(body)).isInstanceOf(Exception.class);
        }
    }
    @Test void textBoundsRejectSupplementaryOverflowWithoutRewritingNotation() {
        assertThat(AttemptAnalysisPolicy.validText("😀".repeat(128))).isTrue();
        for(String text:List.of("😀".repeat(129),"a".repeat(257),"\u0000","\uD800"))
            assertThat(AttemptAnalysisPolicy.validText(text)).isFalse();
        AttemptAnalysisPolicy.answerSubmission("answer-key",Collections.nCopies(11," x = 4 "),"{−2, 2}");
        assertThatThrownBy(()->AttemptAnalysisPolicy.answerSubmission("answer-key",Collections.nCopies(12,"x=4"),"4")).isInstanceOf(RuntimeException.class);
    }
}
