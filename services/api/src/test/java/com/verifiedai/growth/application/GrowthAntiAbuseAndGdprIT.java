package com.verifiedai.growth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.growth.domain.GrowthContentSanitizer;
import com.verifiedai.growth.infrastructure.GrowthWriteJdbcRepository;
import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(properties = {
    "verifiedai.growth.sharing-enabled=true",
    "verifiedai.growth.referral-enabled=true",
    "verifiedai.growth.store-review-enabled=true"
})
final class GrowthAntiAbuseAndGdprIT extends PostgresIntegrationTestSupport {

    private static final String DEVICE_HASH_1 = "1111111111111111111111111111111111111111111111111111111111111111";
    private static final String DEVICE_HASH_2 = "2222222222222222222222222222222222222222222222222222222222222222";

    @Autowired
    ShareableArtifactService shareableArtifactService;

    @Autowired
    ReferralAttributionService referralAttributionService;

    @Autowired
    StoreReviewPromptService storeReviewPromptService;

    @Autowired
    GrowthPrivacyLifecycleContributor privacyContributor;

    @Autowired
    GrowthWriteJdbcRepository writeRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetState() {
        jdbcTemplate.execute("""
            truncate table
                growth_store_review_events,
                growth_shareable_artifacts,
                growth_referral_attributions,
                growth_referral_codes,
                learning_profiles,
                problem_sessions,
                entitlements,
                users
            cascade
            """);
    }

    @Test
    void enforcesConcurrencyIdempotencyAntiAbuseQualificationAndGdprLifecycle() throws Exception {
        UUID referrerId = insertUser("00000000-0000-0000-0000-00000000A001");
        UUID referredId = insertUser("00000000-0000-0000-0000-00000000A002");
        UUID thirdUserId = insertUser("00000000-0000-0000-0000-00000000A003");
        UUID fourthUserId = insertUser("00000000-0000-0000-0000-00000000A004");

        ReferralSummaryView referrerSummary = referralAttributionService.getOrCreateSummary(referrerId);
        ReferralSummaryView referredSummary = referralAttributionService.getOrCreateSummary(referredId);
        ReferralSummaryView thirdSummary = referralAttributionService.getOrCreateSummary(thirdUserId);

        // 1. 10 concurrent attribution calls with same (referredId, idempotencyKey) -> 1 row
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch ready = new CountDownLatch(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<ReferralAttributionResultView>> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> {
                ready.countDown();
                start.await();
                return referralAttributionService.attributeReferral(
                    referredId,
                    referrerSummary.referralCode(),
                    "DEEP_LINK",
                    DEVICE_HASH_1,
                    "attr-concurrent-key-1"
                );
            });
        }
        List<Future<ReferralAttributionResultView>> futures = new ArrayList<>();
        for (var task : tasks) {
            futures.add(pool.submit(task));
        }
        ready.await();
        start.countDown();
        List<ReferralAttributionResultView> results = new ArrayList<>();
        for (var future : futures) {
            results.add(future.get());
        }
        pool.shutdown();

        assertThat(results).hasSize(10);
        assertThat(results.stream().map(ReferralAttributionResultView::attributionId).distinct()).hasSize(1);
        assertThat(results.stream().filter(r -> !r.replayed()).count()).isEqualTo(1);
        assertThat(results.stream().filter(ReferralAttributionResultView::replayed).count()).isEqualTo(9);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM growth_referral_attributions WHERE referred_user_id = ?",
            Integer.class,
            referredId
        )).isEqualTo(1);

        // 2. Payload drift on same idempotencyKey -> 409 GROWTH_IDEMPOTENCY_CONFLICT
        assertThatThrownBy(() -> referralAttributionService.attributeReferral(
            referredId,
            referrerSummary.referralCode(),
            "MANUAL_CODE",
            DEVICE_HASH_1,
            "attr-concurrent-key-1"
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                ApiProblemException ape = (ApiProblemException) ex;
                assertThat(ape.status()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(ape.code()).isEqualTo(ApiErrorCode.GROWTH_IDEMPOTENCY_CONFLICT);
            });

        // 3. Self-referral -> 409 GROWTH_SELF_REFERRAL_FORBIDDEN
        assertThatThrownBy(() -> referralAttributionService.attributeReferral(
            referrerId,
            referrerSummary.referralCode(),
            "MANUAL_CODE",
            DEVICE_HASH_2,
            "attr-self-key-1"
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.GROWTH_SELF_REFERRAL_FORBIDDEN));

        // 4. Duplicate attribution for same referred user -> 409 GROWTH_REFERRAL_ALREADY_ATTRIBUTED
        assertThatThrownBy(() -> referralAttributionService.attributeReferral(
            referredId,
            thirdSummary.referralCode(),
            "MANUAL_CODE",
            DEVICE_HASH_2,
            "attr-second-code-key"
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.GROWTH_REFERRAL_ALREADY_ATTRIBUTED));

        // 5. Circular referral (referredId -> referrerId) -> 409 GROWTH_CIRCULAR_REFERRAL_FORBIDDEN
        assertThatThrownBy(() -> referralAttributionService.attributeReferral(
            referrerId,
            referredSummary.referralCode(),
            "DEEP_LINK",
            DEVICE_HASH_2,
            "attr-circular-key-1"
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.GROWTH_CIRCULAR_REFERRAL_FORBIDDEN));

        // 6. Device fingerprint replay for same referrer -> 409 GROWTH_ABUSE_LIMIT_EXCEEDED
        assertThatThrownBy(() -> referralAttributionService.attributeReferral(
            thirdUserId,
            referrerSummary.referralCode(),
            "DEEP_LINK",
            DEVICE_HASH_1,
            "attr-device-replay-1"
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.GROWTH_ABUSE_LIMIT_EXCEEDED));

        // 7. Monthly referrer cap exceeded (10 total allowed; 1 already exists -> add 9 more, 11th rejected)
        for (int i = 0; i < 9; i++) {
            UUID u = insertUser(String.format("00000000-0000-0000-0000-00000000B%03d", i));
            referralAttributionService.attributeReferral(
                u,
                referrerSummary.referralCode(),
                "SHARE_CARD",
                null,
                "attr-cap-key-" + i
            );
        }
        assertThatThrownBy(() -> referralAttributionService.attributeReferral(
            fourthUserId,
            referrerSummary.referralCode(),
            "SHARE_CARD",
            null,
            "attr-cap-overflow-1"
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.GROWTH_ABUSE_LIMIT_EXCEEDED));

        // 8. Qualification requires server-verified milestone and never mutates entitlements
        assertThatThrownBy(() -> referralAttributionService.qualifyAndGrantReward(referredId, "qualify-key-1"))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.GROWTH_INVALID_TRANSITION));

        jdbcTemplate.update(
            "INSERT INTO problem_sessions (id, user_id, status, input_mode, created_at, updated_at, version) "
                + "VALUES (?, ?, 'CREATED', 'CAMERA', NOW(), NOW(), 0)",
            UUID.randomUUID(),
            referredId
        );
        ReferralAttributionResultView rewarded = referralAttributionService.qualifyAndGrantReward(
            referredId,
            "qualify-key-1"
        );
        assertThat(rewarded.status()).isEqualTo("REWARD_GRANTED");
        assertThat(rewarded.rewardType()).isEqualTo("BONUS_VERIFIED_SOLVES_CREDIT");
        assertThat(rewarded.rewardUnits()).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM entitlements WHERE user_id IN (?, ?)",
            Integer.class,
            referrerId,
            referredId
        )).isZero();

        // 9. GDPR Export & Delete isolation
        shareableArtifactService.createShare(
            referrerId,
            "MASTERY_MILESTONE",
            "en",
            "Top Student",
            referrerSummary.referralCode(),
            Map.of(
                "skillCode", "MATH.ALG.LINEAR_EQ",
                "skillDomain", "ALGEBRA",
                "milestoneBadge", "TRAP_CONQUERED",
                "conqueredTrapCount", 2
            ),
            30,
            "share-gdpr-key-1"
        );
        storeReviewPromptService.recordOutcome(
            referrerId,
            "MASTERY_MILESTONE_UNLOCKED",
            "PROMPT_SHOWN",
            "1.0.0",
            "en",
            "rev-gdpr-key-1"
        );
        storeReviewPromptService.recordOutcome(
            thirdUserId,
            "WEEKLY_PROGRESS_WIN",
            "PROMPT_DEFERRED",
            "1.0.0",
            "tr",
            "rev-gdpr-other-1"
        );

        Map<String, Object> exportedReferrer = privacyContributor.exportUserData(referrerId);
        String exportedJson = GrowthContentSanitizer.toCanonicalJson(exportedReferrer);
        assertThat(exportedReferrer).containsEntry("category", "growth");
        assertThat(exportedReferrer).containsEntry("trainingEligible", false);
        assertThat(exportedJson)
            .doesNotContain("payloadHash")
            .doesNotContain("deviceFingerprintHash")
            .doesNotContain(DEVICE_HASH_1)
            .doesNotContain(referredId.toString());

        privacyContributor.deleteUserData(referrerId, Instant.now());

        assertThat(writeRepository.findReferralCodeByUserId(referrerId)).isEmpty();
        assertThat(writeRepository.findShareableArtifactsForPrivacyExport(referrerId)).isEmpty();
        assertThat(writeRepository.findReviewEventsForPrivacyExport(referrerId)).isEmpty();
        // Other user's data remains intact
        assertThat(writeRepository.findReferralCodeByUserId(thirdUserId)).isPresent();
        assertThat(writeRepository.findReviewEventsForPrivacyExport(thirdUserId)).hasSize(1);
    }

    private UUID insertUser(String rawUuid) {
        UUID userId = UUID.fromString(rawUuid);
        jdbcTemplate.update(
            "INSERT INTO users (id, status, created_at, updated_at) VALUES (?, 'ACTIVE', NOW() - INTERVAL '10 days', NOW())",
            userId
        );
        return userId;
    }
}
