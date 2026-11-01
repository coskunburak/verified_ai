package com.verifiedai.tutoring.domain;

import com.verifiedai.sharedkernel.error.ApiProblemException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class TutorPolicyTest {
    @Test void activeCurrentSessionCanAppend() { assertThatCode(()->TutorPolicy.append(TutorPolicy.Status.ACTIVE,true,0,100)).doesNotThrowAnyException(); }
    @Test void closedSessionNeverReopens() { assertThatThrownBy(()->TutorPolicy.append(TutorPolicy.Status.CLOSED,true,0,100)).isInstanceOf(ApiProblemException.class); }
    @Test void staleSourceCannotAppend() { assertThatThrownBy(()->TutorPolicy.append(TutorPolicy.Status.ACTIVE,false,0,100)).isInstanceOf(ApiProblemException.class); }
    @Test void capacityIsBounded() { assertThatThrownBy(()->TutorPolicy.append(TutorPolicy.Status.ACTIVE,true,100,100)).isInstanceOf(ApiProblemException.class); }
    @Test void unicodeCodePointsAndUtf8HaveExplicitBounds() {
        TutorPolicy.content("😀".repeat(2000));
        assertThatThrownBy(()->TutorPolicy.content("😀".repeat(2001))).isInstanceOf(ApiProblemException.class);
    }
    @Test void emptyControlAndUnpairedSurrogatesAreRejected() {
        for(String value:new String[]{""," \n\t","\u00a0","a\u0000","a\u001b","a\u202e","\ud800"})
            assertThatThrownBy(()->TutorPolicy.content(value)).isInstanceOf(ApiProblemException.class);
        TutorPolicy.content("x + 1 = 2\nMy next step\t?");
    }
    @Test void keysAndPaginationAreBounded() {
        TutorPolicy.key("request-123"); TutorPolicy.page(0,50);
        for(String key:new String[]{"", "short", "a".repeat(129), "bad key request"})
            assertThatThrownBy(()->TutorPolicy.key(key)).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->TutorPolicy.page(-1,50)).isInstanceOf(ApiProblemException.class);
        assertThatThrownBy(()->TutorPolicy.page(0,51)).isInstanceOf(ApiProblemException.class);
    }
}
