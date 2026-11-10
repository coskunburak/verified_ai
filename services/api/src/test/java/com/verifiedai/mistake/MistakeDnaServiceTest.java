package com.verifiedai.mistake;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class MistakeDnaServiceTest {
    final Instant now=Instant.parse("2026-11-09T00:00:00Z");
    final EmpiricalBayesMistakeRateCalculator calculator=new EmpiricalBayesMistakeRateCalculator();
    @Test void productionAuthorizationAndParentCapabilityAreRequired() {
        assertThatThrownBy(()->new LearnerMistakeDnaService(null,null,Clock.systemUTC(),null,null,true,true,"production",false))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(new LearnerMistakeDnaService(null,null,Clock.systemUTC(),null,null,true,false,"test",false).enabled()).isFalse();
        assertThat(new LearnerMistakeDnaService(null,null,Clock.systemUTC(),null,null,false,true,"production",false).enabled()).isFalse();
    }
    List<EmpiricalBayesMistakeRateCalculator.Observation> observations(int errors,int total,Instant at) {
        return java.util.stream.IntStream.range(0,total).mapToObj(i->new EmpiricalBayesMistakeRateCalculator.Observation(new UUID(0,i),at,i<errors)).toList();
    }
    @Test void twoErrorsInThreeExposuresExceedThreeInForty() {
        assertThat(calculator.calculate(observations(2,3,now),now).propensity())
            .isGreaterThan(calculator.calculate(observations(3,40,now),now).propensity());
        assertThat(calculator.calculate(observations(2,3,now),now).propensity()).isEqualByComparingTo("0.34285714");
    }
    @Test void priorDecayFutureEventsAndReplayAreDeterministic() {
        assertThat(calculator.calculate(List.of(),now).propensity()).isEqualByComparingTo("0.10");
        var old=observations(1,2,now.minus(Duration.ofDays(21)));
        var rate=calculator.calculate(old,now);
        assertThat(rate.errors()).isEqualByComparingTo("0.5"); assertThat(rate.exposures()).isEqualByComparingTo("1");
        assertThat(rate.propensity()).isEqualByComparingTo("0.18"); assertThat(rate.recentErrors()).isEqualTo(1);
        assertThat(calculator.calculate(observations(1,1,now.plusSeconds(1)),now).exposures()).isZero();
        var reversed=new ArrayList<>(old); Collections.reverse(reversed);
        assertThat(calculator.calculate(reversed,now)).isEqualTo(rate);
        assertThat(calculator.calculate(observations(1,1,now.minus(Duration.ofDays(100000))),now).propensity()).isEqualByComparingTo("0.10");
    }
    @Test void recurrenceRequiresBothThresholdsAndCannotClaimRemediation() {
        assertThat(MistakeRecurrenceState.project(calculator.calculate(observations(1,1,now),now))).isEqualTo(MistakeRecurrenceState.OBSERVED_ONCE);
        assertThat(MistakeRecurrenceState.project(calculator.calculate(observations(2,40,now),now))).isEqualTo(MistakeRecurrenceState.OBSERVED_ONCE);
        assertThat(MistakeRecurrenceState.project(calculator.calculate(observations(2,3,now),now))).isEqualTo(MistakeRecurrenceState.RECURRING_PATTERN);
        var threshold=new EmpiricalBayesMistakeRateCalculator.Rate(BigDecimal.ONE,BigDecimal.TEN,new BigDecimal("0.22"),2);
        assertThat(MistakeRecurrenceState.project(threshold)).isEqualTo(MistakeRecurrenceState.RECURRING_PATTERN);
        for(var state:List.of(MistakeRecurrenceState.IN_REMEDIATION,MistakeRecurrenceState.RESOLVED_PENDING_RETENTION,
            MistakeRecurrenceState.STABLE_RESOLVED,MistakeRecurrenceState.RELAPSED))
            assertThatThrownBy(()->MistakeRecurrenceState.RECURRING_PATTERN.requireObservationTransition(state)).isInstanceOf(IllegalArgumentException.class);
        MistakeRecurrenceState.RECURRING_PATTERN.requireObservationTransition(MistakeRecurrenceState.OBSERVED_ONCE);
    }
    @Test void duplicateExposureIsRejectedAndWindowUsesUtcSeconds() {
        var one=observations(1,1,now);
        assertThatThrownBy(()->calculator.calculate(List.of(one.getFirst(),one.getFirst()),now)).isInstanceOf(IllegalArgumentException.class);
        assertThat(calculator.calculate(observations(2,2,now.minus(Duration.ofDays(21)).minusNanos(1)),now).recentErrors()).isZero();
        assertThat(MistakeTaxonomy.SKILLS).doesNotContainKey("CALC_PRODUCT_QUOTIENT_NAIVE");
    }
    @Test void sharedContractsRejectMissingAuthorityAndMalformedRequests() throws Exception {
        for(String name:List.of("mistake-dna-view-v1","mistake-dispute-request-v1","mistake-dispute-result-v1",
            "mistake-effective-state-v1","attempt-ocr-correction-request-v1","attempt-ocr-correction-result-v1")) {
            try(var in=getClass().getResourceAsStream("/schemas/fixtures/"+name+".json")) {
                String json=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                var value=com.verifiedai.sharedkernel.contracts.VersionedJsonContract.validate(name,json);
                var bad=(com.fasterxml.jackson.databind.node.ObjectNode)value.deepCopy(); bad.put("confidence",0.99);
                assertThatThrownBy(()->com.verifiedai.sharedkernel.contracts.VersionedJsonContract.validate(name,bad.toString())).isInstanceOf(IllegalArgumentException.class);
            }
        }
    }
}
