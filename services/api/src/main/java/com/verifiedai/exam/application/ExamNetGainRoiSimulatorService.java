package com.verifiedai.exam.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

/** H-03 arithmetic uses decimal inputs throughout, with rounding only at specified boundaries. */
@Service
public class ExamNetGainRoiSimulatorService {
    public record SkillInput(String skillId, BigDecimal mastery, BigDecimal trapRate,
            int correctCount, int totalCount, BigDecimal questionWeight, BigDecimal sessionLostNet) { }
    public record Allocation(String skillId, BigDecimal sessionLostNet, BigDecimal examSuccessProbability,
            BigDecimal expectedNetGainPerHour, BigDecimal estimatedRemediationHours,
            BigDecimal estimatedNetRecoveryTotal) { }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
    public BigDecimal remediationHours(BigDecimal mastery, BigDecimal trapRate) {
        return d("1.20").add(d("2.80").multiply(BigDecimal.ONE.subtract(mastery)))
                .add(d("0.90").multiply(trapRate)).setScale(2, RoundingMode.HALF_UP).max(d("0.50"));
    }
    public Allocation calculate(SkillInput in) {
        if (in.totalCount() < 1 || in.correctCount() < 0 || in.correctCount() > in.totalCount()
                || in.mastery().signum() < 0 || in.mastery().compareTo(BigDecimal.ONE) > 0
                || in.trapRate().signum() < 0 || in.trapRate().compareTo(BigDecimal.ONE) > 0
                || in.questionWeight().signum() < 0 || in.sessionLostNet().signum() < 0)
            throw new IllegalArgumentException("Invalid ROI signals");
        BigDecimal rate = BigDecimal.valueOf(in.correctCount()).divide(BigDecimal.valueOf(in.totalCount()), 16, RoundingMode.HALF_UP);
        BigDecimal probability = d("0.60").multiply(in.mastery()).add(d("0.40").multiply(rate))
                .subtract(d("0.10").multiply(in.trapRate())).max(d("0.05")).min(d("0.95"));
        BigDecimal hours = remediationHours(in.mastery(), in.trapRate());
        BigDecimal recovery = d("1.25").multiply(in.questionWeight().max(BigDecimal.ONE))
                .multiply(d("0.85").subtract(probability).max(BigDecimal.ZERO));
        return new Allocation(in.skillId(), in.sessionLostNet(), probability.setScale(4, RoundingMode.HALF_UP),
                recovery.divide(hours, 3, RoundingMode.HALF_UP), hours, recovery.setScale(2, RoundingMode.HALF_UP));
    }
    public List<Allocation> rank(List<SkillInput> skills) {
        return skills.stream().map(this::calculate)
                .filter(a -> a.expectedNetGainPerHour().signum() > 0)
                .sorted(Comparator.comparing(Allocation::expectedNetGainPerHour).reversed()
                        .thenComparing(Allocation::sessionLostNet, Comparator.reverseOrder())
                        .thenComparing(Allocation::skillId)).toList();
    }
    public List<Allocation> topThree(List<SkillInput> skills) { return rank(skills).stream().limit(3).toList(); }
}
