package com.verifiedai.mastery.application;

import com.verifiedai.curriculum.api.CurriculumDagSnapshot;
import com.verifiedai.curriculum.api.CurriculumGraphQueryPort;
import com.verifiedai.mastery.api.dto.SkillGraphProjectionResponse;
import com.verifiedai.mastery.domain.LearnerSkillMasteryEntity;
import com.verifiedai.mastery.domain.LearnerSkillMasteryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class KnowledgeGraphProjectionService {

    private static final BigDecimal COLD_START_PRIOR = new BigDecimal("0.2500");
    private static final BigDecimal BOTTLENECK_THRESHOLD = new BigDecimal("0.5000");
    private static final BigDecimal ANCESTOR_INFERRED_CAP = new BigDecimal("0.6500");
    private static final int BOTTLENECK_FAILURE_COUNT = 2;

    private final CurriculumGraphQueryPort curriculumGraphPort;
    private final LearnerSkillMasteryRepository masteryRepository;
    private final Clock clock;

    public KnowledgeGraphProjectionService(
            CurriculumGraphQueryPort curriculumGraphPort,
            LearnerSkillMasteryRepository masteryRepository,
            Clock clock) {
        this.curriculumGraphPort = curriculumGraphPort;
        this.masteryRepository = masteryRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SkillGraphProjectionResponse projectGraph(UUID tenantId, UUID userId, String curriculumCode) {
        CurriculumDagSnapshot dag = curriculumGraphPort.findDagByCurriculumCode(curriculumCode)
                .orElseThrow(() -> new ResourceNotFoundOrForbiddenException("CURRICULUM_NOT_FOUND"));

        Map<String, LearnerSkillMasteryEntity> recordedMap = masteryRepository
                .findAllByTenantIdAndUserIdAndCurriculumCode(tenantId, userId, curriculumCode)
                .stream()
                .collect(Collectors.toMap(LearnerSkillMasteryEntity::getSkillCode, e -> e));

        List<String> topoSortedSkills = dag.topologicallySortedSkillCodes();
        Map<String, BigDecimal> effectiveScores = new HashMap<>();
        List<SkillGraphProjectionResponse.SkillNodeDto> nodes = new ArrayList<>();
        List<SkillGraphProjectionResponse.SkillEdgeDto> edges = new ArrayList<>();
        Set<String> bottleneckTargetSkills = new HashSet<>();

        // 1. Forward Pass: Conservative Prerequisite Propagation & 0.65 Ancestor Cap
        for (String skillCode : topoSortedSkills) {
            LearnerSkillMasteryEntity rec = recordedMap.get(skillCode);
            List<String> parents = dag.predecessorsOf(skillCode);

            BigDecimal minParentScore = parents.stream()
                    .map(p -> effectiveScores.getOrDefault(p, COLD_START_PRIOR))
                    .min(BigDecimal::compareTo)
                    .orElse(COLD_START_PRIOR);

            BigDecimal effectiveScore;
            boolean isColdStart = (rec == null || rec.getAttemptCount() == 0);

            if (isColdStart) {
                effectiveScore = parents.isEmpty()
                        ? COLD_START_PRIOR
                        : COLD_START_PRIOR.min(minParentScore).setScale(4, RoundingMode.HALF_UP);
            } else {
                effectiveScore = rec.getMasteryScore().setScale(4, RoundingMode.HALF_UP);
                if (rec.getDirectPracticeCount() == 0 && effectiveScore.compareTo(ANCESTOR_INFERRED_CAP) > 0) {
                    effectiveScore = ANCESTOR_INFERRED_CAP;
                }
            }
            effectiveScores.put(skillCode, effectiveScore);
        }

        // 2. Edge Evaluation & Prerequisite Bottleneck Detection
        for (CurriculumDagSnapshot.Edge edge : dag.edges()) {
            BigDecimal preScore = effectiveScores.get(edge.fromSkillCode());
            LearnerSkillMasteryEntity postRec = recordedMap.get(edge.toSkillCode());
            int postConsecutiveFailures = (postRec != null) ? postRec.getConsecutiveFailures() : 0;

            boolean isBottleneck = preScore.compareTo(BOTTLENECK_THRESHOLD) < 0
                    && postConsecutiveFailures >= BOTTLENECK_FAILURE_COUNT;

            if (isBottleneck) {
                bottleneckTargetSkills.add(edge.toSkillCode());
            }

            edges.add(new SkillGraphProjectionResponse.SkillEdgeDto(
                    edge.fromSkillCode(),
                    edge.toSkillCode(),
                    edge.weight(),
                    isBottleneck
            ));
        }

        // 3. Node Materialization
        for (String skillCode : topoSortedSkills) {
            LearnerSkillMasteryEntity rec = recordedMap.get(skillCode);
            BigDecimal score = effectiveScores.get(skillCode);
            boolean hasDirectEvidence = (rec != null && rec.getDirectPracticeCount() > 0);
            boolean blockedByBottleneck = bottleneckTargetSkills.contains(skillCode);

            String state = resolveMasteryState(score, hasDirectEvidence, rec, blockedByBottleneck);

            nodes.add(new SkillGraphProjectionResponse.SkillNodeDto(
                    skillCode,
                    dag.titleOf(skillCode),
                    score,
                    state,
                    rec != null ? rec.getAttemptCount() : 0,
                    rec != null ? rec.getConsecutiveFailures() : 0,
                    blockedByBottleneck
            ));
        }

        return new SkillGraphProjectionResponse(
                "skill-graph-v1",
                curriculumCode,
                Instant.now(clock),
                nodes,
                edges
        );
    }

    private String resolveMasteryState(
            BigDecimal score,
            boolean hasDirectEvidence,
            LearnerSkillMasteryEntity rec,
            boolean blockedByBottleneck) {
        if (blockedByBottleneck || (rec != null && "AT_RISK_REMEDIATION_REQUIRED".equals(rec.getMasteryState()))) {
            return "AT_RISK_REMEDIATION_REQUIRED";
        }
        if (rec == null || rec.getAttemptCount() == 0) {
            return "UNSEEN";
        }
        if (!hasDirectEvidence) {
            return "PRACTICING";
        }
        if (score.compareTo(new BigDecimal("0.8500")) >= 0) {
            return "MASTERED";
        }
        if (score.compareTo(new BigDecimal("0.4000")) >= 0) {
            return "PRACTICING";
        }
        return "EMERGING";
    }
}
