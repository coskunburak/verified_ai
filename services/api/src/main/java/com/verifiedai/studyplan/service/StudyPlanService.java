package com.verifiedai.studyplan.service;

import com.verifiedai.studyplan.domain.*;
import com.verifiedai.studyplan.engine.NextBestActionRecommendationEngine;
import com.verifiedai.studyplan.engine.ScoredRecommendation;
import com.verifiedai.studyplan.engine.SkillCandidateSignal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class StudyPlanService {

    public static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final int DEFAULT_LIMIT = 5;

    private final LearnerRecommendationRepository repository;
    private final NextBestActionRecommendationEngine engine;
    private final NextBestActionSignalProvider signalProvider;
    private final Clock clock;

    @Autowired
    public StudyPlanService(
            LearnerRecommendationRepository repository,
            NextBestActionRecommendationEngine engine,
            NextBestActionSignalProvider signalProvider,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository cannot be null");
        this.engine = Objects.requireNonNull(engine, "engine cannot be null");
        this.signalProvider = Objects.requireNonNull(signalProvider, "signalProvider cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    public StudyPlanService(
            LearnerRecommendationRepository repository,
            NextBestActionRecommendationEngine engine,
            NextBestActionSignalProvider signalProvider
    ) {
        this(repository, engine, signalProvider, Clock.systemUTC());
    }

    public List<LearnerRecommendationEntity> getOrGenerateRecommendations(UUID userId, int limit, boolean forceRefresh) {
        Objects.requireNonNull(userId, "userId cannot be null");
        int effectiveLimit = limit > 0 ? Math.min(limit, 20) : DEFAULT_LIMIT;
        Instant now = clock.instant();

        // 1. Mark expired recommendations
        repository.expireOutdatedByUserId(userId, now);

        // 2. Return existing active recommendations if not forcing refresh
        if (!forceRefresh) {
            List<LearnerRecommendationEntity> existing = repository
                    .findByUserIdAndStatusAndExpiresAtAfterOrderByPriorityScoreDescTargetSkillCodeAsc(
                            userId,
                            RecommendationStatus.ACTIVE,
                            now,
                            PageRequest.of(0, effectiveLimit)
                    );
            if (!existing.isEmpty()) {
                return existing;
            }
        }

        // 3. Collect learner candidate signals
        List<SkillCandidateSignal> signals = signalProvider.fetchSignalsForLearner(userId);

        // 4. Rank candidates deterministically ($0.00 token cost)
        List<ScoredRecommendation> ranked = engine.rankCandidates(signals, effectiveLimit);

        // 5. Persist recommendations with 24-hour TTL
        Instant expiresAt = now.plus(DEFAULT_TTL);
        List<LearnerRecommendationEntity> entitiesToSave = new ArrayList<>();

        for (ScoredRecommendation scored : ranked) {
            LearnerRecommendationEntity entity = new LearnerRecommendationEntity(
                    UUID.randomUUID(),
                    userId,
                    scored.recommendationType(),
                    scored.targetSkillCode(),
                    scored.targetMistakeCode(),
                    scored.priorityScore(),
                    scored.rationaleCode(),
                    scored.rationaleParamsJson(),
                    RecommendationStatus.ACTIVE,
                    expiresAt,
                    now
            );
            entitiesToSave.add(entity);
        }

        return repository.saveAll(entitiesToSave);
    }

    public LearnerRecommendationEntity acceptRecommendation(UUID userId, UUID recommendationId) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(recommendationId, "recommendationId cannot be null");
        Instant now = clock.instant();

        LearnerRecommendationEntity entity = repository.findById(recommendationId)
                .orElseThrow(() -> new RecommendationNotFoundException(recommendationId));

        // BOLA (Broken Object Level Authorization) validation
        if (!entity.getUserId().equals(userId)) {
            throw new AccessDeniedException("User " + userId + " does not own recommendation " + recommendationId);
        }

        entity.accept(now);
        return repository.save(entity);
    }

    public LearnerRecommendationEntity dismissRecommendation(UUID userId, UUID recommendationId, String reasonCode) {
        Objects.requireNonNull(userId, "userId cannot be null");
        Objects.requireNonNull(recommendationId, "recommendationId cannot be null");
        Instant now = clock.instant();

        LearnerRecommendationEntity entity = repository.findById(recommendationId)
                .orElseThrow(() -> new RecommendationNotFoundException(recommendationId));

        // BOLA validation
        if (!entity.getUserId().equals(userId)) {
            throw new AccessDeniedException("User " + userId + " does not own recommendation " + recommendationId);
        }

        entity.dismiss(now);
        return repository.save(entity);
    }

    public static String resolveNextSessionType(RecommendationType type) {
        if (type == null) {
            return "TOPIC_PRACTICE";
        }
        return switch (type) {
            case PREREQUISITE_REPAIR -> "PREREQUISITE_DRILL";
            case MISTAKE_REPAIR -> "TWIN_REMEDIATION";
            case SPACED_RETENTION -> "SPACED_REVIEW";
            case HIGH_ROI_TOPIC -> "TOPIC_PRACTICE";
        };
    }

    public static String buildDeepLinkUri(String sessionType, String skillCode, String mistakeCode) {
        StringBuilder uri = new StringBuilder("verifiedai://coach/session?type=")
                .append(sessionType)
                .append("&skill=")
                .append(skillCode);
        if (mistakeCode != null && !mistakeCode.isBlank()) {
            uri.append("&mistake=").append(mistakeCode);
        }
        return uri.toString();
    }
}
