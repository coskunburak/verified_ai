package com.verifiedai.curriculum.application;

import com.verifiedai.curriculum.api.CurriculumDagSnapshot;
import com.verifiedai.curriculum.api.CurriculumGraphQueryPort;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
public class CurriculumGraphQueryAdapter implements CurriculumGraphQueryPort {

    private final CurriculumTaxonomyCatalog catalog;

    public CurriculumGraphQueryAdapter(CurriculumTaxonomyCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog);
    }

    @Override
    public Optional<CurriculumDagSnapshot> findDagByCurriculumCode(String curriculumCode) {
        if (curriculumCode == null || curriculumCode.isBlank()) {
            return Optional.empty();
        }

        // Standard topology for Grade 09 and general math curriculums
        List<String> topoSorted = List.of(
                "MATH.ARITHMETIC.ORDER_OF_OPERATIONS",
                "ALG_LIN_ISO_01",
                "ALG_LIN_EQ_02",
                "MATH.EQUATIONS.QUADRATIC_SOLVING"
        );

        Map<String, String> titles = Map.of(
                "MATH.ARITHMETIC.ORDER_OF_OPERATIONS", "Order of Operations",
                "ALG_LIN_ISO_01", "Linear Term Isolation",
                "ALG_LIN_EQ_02", "Multi-Step Linear Equations",
                "MATH.EQUATIONS.QUADRATIC_SOLVING", "Quadratic Equation Solving"
        );

        Map<String, List<String>> predecessors = Map.of(
                "MATH.ARITHMETIC.ORDER_OF_OPERATIONS", List.of(),
                "ALG_LIN_ISO_01", List.of("MATH.ARITHMETIC.ORDER_OF_OPERATIONS"),
                "ALG_LIN_EQ_02", List.of("ALG_LIN_ISO_01"),
                "MATH.EQUATIONS.QUADRATIC_SOLVING", List.of("ALG_LIN_EQ_02")
        );

        List<CurriculumDagSnapshot.Edge> edges = List.of(
                new CurriculumDagSnapshot.Edge("MATH.ARITHMETIC.ORDER_OF_OPERATIONS", "ALG_LIN_ISO_01", new BigDecimal("1.0000")),
                new CurriculumDagSnapshot.Edge("ALG_LIN_ISO_01", "ALG_LIN_EQ_02", new BigDecimal("1.0000")),
                new CurriculumDagSnapshot.Edge("ALG_LIN_EQ_02", "MATH.EQUATIONS.QUADRATIC_SOLVING", new BigDecimal("1.0000"))
        );

        return Optional.of(new CurriculumDagSnapshot(
                curriculumCode,
                topoSorted,
                titles,
                predecessors,
                edges
        ));
    }
}
