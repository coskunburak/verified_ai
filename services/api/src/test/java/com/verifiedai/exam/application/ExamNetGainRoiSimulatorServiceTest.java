package com.verifiedai.exam.application;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExamNetGainRoiSimulatorServiceTest {
    private final ExamNetGainRoiSimulatorService service = new ExamNetGainRoiSimulatorService();
    private static BigDecimal d(String s) { return new BigDecimal(s); }
    private ExamNetGainRoiSimulatorService.SkillInput input(String skill,String loss) {
        return new ExamNetGainRoiSimulatorService.SkillInput(skill,d("0.5"),d("0.4"),1,2,d("2"),d(loss));
    }
    @Test void formulaAndRoundingMatchIndependentHandCalculation() {
        var a=service.calculate(input("s","1.25"));
        assertEquals(d("0.4600"),a.examSuccessProbability());
        assertEquals(d("2.96"),a.estimatedRemediationHours());
        assertEquals(d("0.329"),a.expectedNetGainPerHour());
        assertEquals(d("0.98"),a.estimatedNetRecoveryTotal());
    }
    @Test void masteredSkillsHaveZeroRoiAndAreExcluded() {
        var in=new ExamNetGainRoiSimulatorService.SkillInput("s",d("1"),d("0"),1,1,d("2"),d("0"));
        assertEquals(d("0.000"),service.calculate(in).expectedNetGainPerHour());
        assertTrue(service.topThree(List.of(in)).isEmpty());
    }
    @Test void hoursAreProtectedEvenForExtrapolatedInputs() {
        assertEquals(d("0.50"),service.remediationHours(d("2"),d("0")));
    }
    @Test void tiesUseLostNetThenSkillIdAndTopThreeLimit() {
        var ranked=service.topThree(List.of(input("z","2.5"),input("a","2.5"),input("b","1.25"),input("c","1.25")));
        assertEquals(List.of("a","z","b"),ranked.stream().map(ExamNetGainRoiSimulatorService.Allocation::skillId).toList());
    }
    @Test void impossibleCountsAreRejected() {
        assertThrows(IllegalArgumentException.class,()->service.calculate(new ExamNetGainRoiSimulatorService.SkillInput("s",d("0.5"),d("0"),1,0,d("1"),d("0"))));
    }
}
