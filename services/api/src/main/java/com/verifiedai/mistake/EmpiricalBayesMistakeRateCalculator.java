package com.verifiedai.mistake;

import java.math.*;
import java.time.*;
import java.util.*;

/** UTC elapsed seconds; deterministic ordering and HALF_EVEN scale 8 at the public boundary. */
public final class EmpiricalBayesMistakeRateCalculator {
    public static final String POLICY = "mistake-dna-v1";
    public record Observation(UUID id, Instant at, boolean causalError) { }
    public record Rate(BigDecimal errors, BigDecimal exposures, BigDecimal propensity, int recentErrors) { }
    public Rate calculate(Collection<Observation> observations, Instant asOf) {
        Objects.requireNonNull(asOf);
        var ordered = observations.stream().sorted(Comparator.comparing(Observation::at).thenComparing(Observation::id)).toList();
        var seen = new HashSet<UUID>(); double errors=0, exposures=0; int recent=0;
        for(var item:ordered) {
            if(item.id()==null || item.at()==null || !seen.add(item.id())) throw new IllegalArgumentException("Duplicate exposure");
            // Future/delayed deliveries cannot become evidence before their event time.
            if(item.at().isAfter(asOf)) continue;
            var elapsed=Duration.between(item.at(),asOf);
            double days=(elapsed.getSeconds()+elapsed.getNano()/1_000_000_000.0)/86400.0;
            double weight=StrictMath.pow(2.0,-days/21.0);
            exposures+=weight;
            if(item.causalError()) { errors+=weight; if(!item.at().isBefore(asOf.minus(Duration.ofDays(21)))) recent++; }
        }
        double theta=(errors+0.4)/(exposures+4.0);
        if(!Double.isFinite(theta) || !Double.isFinite(exposures) || errors>exposures || theta<0 || theta>1)
            throw new IllegalArgumentException("Invalid exposure evidence");
        return new Rate(decimal(errors),decimal(exposures),decimal(theta),recent);
    }
    private static BigDecimal decimal(double value) { return BigDecimal.valueOf(value).setScale(8,RoundingMode.HALF_EVEN); }
}
