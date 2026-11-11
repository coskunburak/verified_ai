package com.verifiedai.mastery;

import com.verifiedai.curriculum.api.CurriculumDagSnapshot;
import com.verifiedai.curriculum.api.CurriculumGraphQueryPort;
import com.verifiedai.mastery.api.dto.SkillGraphProjectionResponse;
import com.verifiedai.mastery.application.KnowledgeGraphProjectionService;
import com.verifiedai.mastery.domain.LearnerSkillMasteryEntity;
import com.verifiedai.mastery.domain.LearnerSkillMasteryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeGraphProjectionServiceTest {

    @Mock
    private CurriculumGraphQueryPort curriculumGraphPort;

    @Mock
    private LearnerSkillMasteryRepository masteryRepository;

    private KnowledgeGraphProjectionService service;
    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-11-11T12:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new KnowledgeGraphProjectionService(curriculumGraphPort, masteryRepository, fixedClock);
    }

    @Test
    void enforcesConservativeColdStartBoundAnd065InferredAncestorCapAndBottleneckDetection() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String curriculum = "MATH_GRADE_09";

        CurriculumDagSnapshot dag = new CurriculumDagSnapshot(
                curriculum,
                List.of("SKILL_ROOT", "SKILL_MID", "SKILL_LEAF"),
                Map.of(
                        "SKILL_ROOT", "Root Skill",
                        "SKILL_MID", "Middle Skill",
                        "SKILL_LEAF", "Leaf Skill"
                ),
                Map.of(
                        "SKILL_ROOT", List.of(),
                        "SKILL_MID", List.of("SKILL_ROOT"),
                        "SKILL_LEAF", List.of("SKILL_MID")
                ),
                List.of(
                        new CurriculumDagSnapshot.Edge("SKILL_ROOT", "SKILL_MID", new BigDecimal("1.0000")),
                        new CurriculumDagSnapshot.Edge("SKILL_MID", "SKILL_LEAF", new BigDecimal("1.0000"))
                )
        );

        // SKILL_ROOT has inferred score 0.8000 but directPracticeCount == 0 -> must be capped at 0.6500 (PRACTICING)
        LearnerSkillMasteryEntity rootRec = LearnerSkillMasteryEntity.rehydrateForTest(
                tenantId, userId, curriculum, "SKILL_ROOT",
                new BigDecimal("0.8000"), "PRACTICING", 2, 0, 0
        );

        // SKILL_MID has low mastery 0.3500 (< 0.5000)
        LearnerSkillMasteryEntity midRec = LearnerSkillMasteryEntity.rehydrateForTest(
                tenantId, userId, curriculum, "SKILL_MID",
                new BigDecimal("0.3500"), "EMERGING", 3, 0, 3
        );

        // SKILL_LEAF has 2 consecutive failures -> edge SKILL_MID -> SKILL_LEAF must trigger bottleneck
        LearnerSkillMasteryEntity leafRec = LearnerSkillMasteryEntity.rehydrateForTest(
                tenantId, userId, curriculum, "SKILL_LEAF",
                new BigDecimal("0.2000"), "EMERGING", 2, 2, 2
        );

        when(curriculumGraphPort.findDagByCurriculumCode(curriculum)).thenReturn(Optional.of(dag));
        when(masteryRepository.findAllByTenantIdAndUserIdAndCurriculumCode(tenantId, userId, curriculum))
                .thenReturn(List.of(rootRec, midRec, leafRec));

        SkillGraphProjectionResponse response = service.projectGraph(tenantId, userId, curriculum);

        assertThat(response.schemaVersion()).isEqualTo("skill-graph-v1");

        var rootNode = response.nodes().stream().filter(n -> n.skillCode().equals("SKILL_ROOT")).findFirst().orElseThrow();
        assertThat(rootNode.masteryScore()).isEqualByComparingTo("0.6500");
        assertThat(rootNode.masteryState()).isEqualTo("PRACTICING");

        var bottleneckEdge = response.edges().stream()
                .filter(e -> e.fromSkillCode().equals("SKILL_MID") && e.toSkillCode().equals("SKILL_LEAF"))
                .findFirst().orElseThrow();
        assertThat(bottleneckEdge.prerequisiteBottleneck()).isTrue();

        var leafNode = response.nodes().stream().filter(n -> n.skillCode().equals("SKILL_LEAF")).findFirst().orElseThrow();
        assertThat(leafNode.blockedByPrerequisiteBottleneck()).isTrue();
        assertThat(leafNode.masteryState()).isEqualTo("AT_RISK_REMEDIATION_REQUIRED");

        // Read-purity check: repository must never receive save/delete calls
        verifyNoMoreInteractions(masteryRepository);
    }
}
