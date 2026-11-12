package com.verifiedai.studyplan.service;

import com.verifiedai.studyplan.domain.*;
import com.verifiedai.studyplan.engine.NextBestActionRecommendationEngine;
import com.verifiedai.studyplan.engine.SkillCandidateSignal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StudyPlanServiceIntegrationTest {

    private LearnerRecommendationRepository repository;
    private NextBestActionRecommendationEngine engine;
    private NextBestActionSignalProvider signalProvider;
    private StudyPlanService service;

    private final Instant baseTime = Instant.parse("2026-11-12T10:00:00Z");
    private final Clock fixedClock = Clock.fixed(baseTime, ZoneId.of("UTC"));

    private final UUID userA = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private final UUID userB = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        repository = mock(LearnerRecommendationRepository.class);
        engine = new NextBestActionRecommendationEngine();
        signalProvider = mock(NextBestActionSignalProvider.class);
        service = new StudyPlanService(repository, engine, signalProvider, fixedClock);
    }

    @Test
    @DisplayName("getOrGenerateRecommendations creates and persists recommendations when cache is empty")
    void testGenerateRecommendationsWhenCacheEmpty() {
        when(repository.findByUserIdAndStatusAndExpiresAtAfterOrderByPriorityScoreDescTargetSkillCodeAsc(
                eq(userA), eq(RecommendationStatus.ACTIVE), eq(baseTime), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        when(signalProvider.fetchSignalsForLearner(userA)).thenReturn(List.of(
                SkillCandidateSignal.builder("MATH.CALC.LIMITS")
                        .retention(new BigDecimal("0.5000"), new BigDecimal("14.00"))
                        .independentMastery(new BigDecimal("0.4000"))
                        .exam("AYT", new BigDecimal("0.8000"))
                        .build()
        ));

        when(repository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        List<LearnerRecommendationEntity> result = service.getOrGenerateRecommendations(userA, 5, false);

        assertThat(result).hasSize(1);
        LearnerRecommendationEntity rec = result.get(0);
        assertThat(rec.getUserId()).isEqualTo(userA);
        assertThat(rec.getTargetSkillCode()).isEqualTo("MATH.CALC.LIMITS");
        assertThat(rec.getStatus()).isEqualTo(RecommendationStatus.ACTIVE);
        assertThat(rec.getExpiresAt()).isEqualTo(baseTime.plus(Duration.ofHours(24)));

        verify(repository, times(1)).expireOutdatedByUserId(userA, baseTime);
        verify(repository, times(1)).saveAll(any());
    }

    @Test
    @DisplayName("getOrGenerateRecommendations returns cached active recommendations when refresh=false")
    void testReturnsCachedRecommendations() {
        LearnerRecommendationEntity cached = new LearnerRecommendationEntity(
                UUID.randomUUID(),
                userA,
                RecommendationType.HIGH_ROI_TOPIC,
                "MATH.ALG.QUADRATICS",
                null,
                new BigDecimal("65.0000"),
                RecommendationRationaleCode.HIGH_EXAM_NET_ROI,
                "{}",
                RecommendationStatus.ACTIVE,
                baseTime.plus(Duration.ofHours(12)),
                baseTime.minus(Duration.ofHours(12))
        );

        when(repository.findByUserIdAndStatusAndExpiresAtAfterOrderByPriorityScoreDescTargetSkillCodeAsc(
                eq(userA), eq(RecommendationStatus.ACTIVE), eq(baseTime), any(Pageable.class)))
                .thenReturn(List.of(cached));

        List<LearnerRecommendationEntity> result = service.getOrGenerateRecommendations(userA, 5, false);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTargetSkillCode()).isEqualTo("MATH.ALG.QUADRATICS");
        verify(signalProvider, never()).fetchSignalsForLearner(any());
        verify(repository, never()).saveAll(any());
    }

    @Test
    @DisplayName("BOLA Protection: User B cannot accept User A's recommendation")
    void testBolaProtectionOnAccept() {
        UUID recId = UUID.randomUUID();
        LearnerRecommendationEntity userARec = new LearnerRecommendationEntity(
                recId,
                userA,
                RecommendationType.MISTAKE_REPAIR,
                "MATH.ALG.FACTORING",
                "TRAP_1",
                new BigDecimal("70.0000"),
                RecommendationRationaleCode.ACTIVE_RECURRING_TRAP,
                "{}",
                RecommendationStatus.ACTIVE,
                baseTime.plus(Duration.ofHours(10)),
                baseTime
        );

        when(repository.findById(recId)).thenReturn(Optional.of(userARec));

        assertThatThrownBy(() -> service.acceptRecommendation(userB, recId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("does not own recommendation");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("BOLA Protection: User B cannot dismiss User A's recommendation")
    void testBolaProtectionOnDismiss() {
        UUID recId = UUID.randomUUID();
        LearnerRecommendationEntity userARec = new LearnerRecommendationEntity(
                recId,
                userA,
                RecommendationType.SPACED_RETENTION,
                "MATH.TRIG.IDENTITIES",
                null,
                new BigDecimal("50.0000"),
                RecommendationRationaleCode.RETENTION_HALF_LIFE_DUE,
                "{}",
                RecommendationStatus.ACTIVE,
                baseTime.plus(Duration.ofHours(10)),
                baseTime
        );

        when(repository.findById(recId)).thenReturn(Optional.of(userARec));

        assertThatThrownBy(() -> service.dismissRecommendation(userB, recId, "NOT_NOW"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("does not own recommendation");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("acceptRecommendation transitions status to ACCEPTED and produces valid deep link")
    void testAcceptRecommendationLifecycle() {
        UUID recId = UUID.randomUUID();
        LearnerRecommendationEntity entity = new LearnerRecommendationEntity(
                recId,
                userA,
                RecommendationType.MISTAKE_REPAIR,
                "MATH.ALG.FACTORING",
                "SIGN_TRAP",
                new BigDecimal("60.0000"),
                RecommendationRationaleCode.ACTIVE_RECURRING_TRAP,
                "{}",
                RecommendationStatus.ACTIVE,
                baseTime.plus(Duration.ofHours(10)),
                baseTime
        );

        when(repository.findById(recId)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LearnerRecommendationEntity accepted = service.acceptRecommendation(userA, recId);

        assertThat(accepted.getStatus()).isEqualTo(RecommendationStatus.ACCEPTED);
        String sessionType = StudyPlanService.resolveNextSessionType(accepted.getRecommendationType());
        assertThat(sessionType).isEqualTo("TWIN_REMEDIATION");
        String uri = StudyPlanService.buildDeepLinkUri(sessionType, accepted.getTargetSkillCode(), accepted.getTargetMistakeCode());
        assertThat(uri).isEqualTo("verifiedai://coach/session?type=TWIN_REMEDIATION&skill=MATH.ALG.FACTORING&mistake=SIGN_TRAP");

        // Accepting again throws conflict
        assertThatThrownBy(() -> service.acceptRecommendation(userA, recId))
                .isInstanceOf(RecommendationStateConflictException.class);
    }

    @Test
    @DisplayName("dismissRecommendation transitions status to DISMISSED")
    void testDismissRecommendationLifecycle() {
        UUID recId = UUID.randomUUID();
        LearnerRecommendationEntity entity = new LearnerRecommendationEntity(
                recId,
                userA,
                RecommendationType.SPACED_RETENTION,
                "MATH.TRIG.IDENTITIES",
                null,
                new BigDecimal("45.0000"),
                RecommendationRationaleCode.RETENTION_HALF_LIFE_DUE,
                "{}",
                RecommendationStatus.ACTIVE,
                baseTime.plus(Duration.ofHours(10)),
                baseTime
        );

        when(repository.findById(recId)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LearnerRecommendationEntity dismissed = service.dismissRecommendation(userA, recId, "NOT_NOW");

        assertThat(dismissed.getStatus()).isEqualTo(RecommendationStatus.DISMISSED);

        // Dismissing again throws conflict
        assertThatThrownBy(() -> service.dismissRecommendation(userA, recId, "NOT_NOW"))
                .isInstanceOf(RecommendationStateConflictException.class);
    }

    @Test
    @DisplayName("RecommendationNotFoundException is thrown when recommendation ID does not exist")
    void testNotFoundException() {
        UUID nonExistent = UUID.randomUUID();
        when(repository.findById(nonExistent)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.acceptRecommendation(userA, nonExistent))
                .isInstanceOf(RecommendationNotFoundException.class);

        assertThatThrownBy(() -> service.dismissRecommendation(userA, nonExistent, "NOT_NOW"))
                .isInstanceOf(RecommendationNotFoundException.class);
    }
}
