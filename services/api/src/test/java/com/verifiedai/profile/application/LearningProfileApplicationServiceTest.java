package com.verifiedai.profile.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.integration.PostgresIntegrationTestSupport;
import com.verifiedai.profile.domain.model.OnboardingStatus;
import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

final class LearningProfileApplicationServiceTest extends PostgresIntegrationTestSupport {

    @Autowired
    LearningProfileApplicationService learningProfileApplicationService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanTables() {
        jdbcTemplate.execute("truncate table entitlements, learning_profiles, auth_security_events, refresh_tokens, sessions, user_identities, users cascade");
    }

    @Test
    void getCurrentReturnsNotStartedWhenNoProfileExists() {
        UUID userId = insertUser();

        LearningProfileResult result = learningProfileApplicationService.getCurrent(userId);

        assertThat(result.exists()).isFalse();
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.onboardingStatus()).isEqualTo(OnboardingStatus.NOT_STARTED);
    }

    @Test
    void updateCreatesCompletedLearningProfileWithoutMutatingIdentity() {
        UUID userId = insertUser();

        LearningProfileResult result = learningProfileApplicationService.updateCurrent(userId, completeCommand(null, "HIGH_SCHOOL", 30));

        assertThat(result.exists()).isTrue();
        assertThat(result.educationLevel()).isEqualTo("HIGH_SCHOOL");
        assertThat(result.preferredLanguage()).isEqualTo("en");
        assertThat(result.explanationDepth()).isEqualTo("STANDARD");
        assertThat(result.onboardingStatus()).isEqualTo(OnboardingStatus.COMPLETED);
        assertThat(result.version()).isEqualTo(0L);
        assertThat(count("users")).isEqualTo(1);
        assertThat(count("learning_profiles")).isEqualTo(1);
    }

    @Test
    void profileUpdateUsesOptimisticVersion() {
        UUID userId = insertUser();
        LearningProfileResult created = learningProfileApplicationService.updateCurrent(userId, completeCommand(null, "HIGH_SCHOOL", 25));

        LearningProfileResult updated = learningProfileApplicationService.updateCurrent(
            userId,
            completeCommand(created.version(), "UNIVERSITY", 45)
        );

        assertThat(updated.version()).isGreaterThan(created.version());
        assertThatThrownBy(() -> learningProfileApplicationService.updateCurrent(
            userId,
            completeCommand(created.version(), "OTHER", 20)
        ))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.OPTIMISTIC_CONFLICT);
    }

    @Test
    void validationRejectsUnsupportedProfileValues() {
        UUID userId = insertUser();

        assertThatThrownBy(() -> learningProfileApplicationService.updateCurrent(
            userId,
            new UpdateLearningProfileCommand("HIGH_SCHOOL", "xx", "STANDARD", 30, "Europe/Istanbul", null, true, null)
        ))
            .isInstanceOf(ApiProblemException.class)
            .extracting(exception -> ((ApiProblemException) exception).code())
            .isEqualTo(ApiErrorCode.PROFILE_VALIDATION_FAILED);

        assertThat(count("learning_profiles")).isEqualTo(0);
    }

    @Test
    void concurrentProfileCreatesAreCollapsedToOneRow() throws Exception {
        UUID userId = insertUser();
        var executor = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        try {
            var futures = new ArrayList<Future<LearningProfileResult>>();
            for (int index = 0; index < 2; index++) {
                int minutes = 20 + index;
                futures.add(executor.submit(() -> {
                    barrier.await();
                    return learningProfileApplicationService.updateCurrent(userId, completeCommand(null, "HIGH_SCHOOL", minutes));
                }));
            }

            futures.get(0).get();
            futures.get(1).get();

            assertThat(count("learning_profiles")).isEqualTo(1);
            assertThat(countWhere("learning_profiles", "user_id = '" + userId + "'")).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private UpdateLearningProfileCommand completeCommand(Long expectedVersion, String educationLevel, int dailyStudyMinutes) {
        return new UpdateLearningProfileCommand(
            educationLevel,
            "en",
            "STANDARD",
            dailyStudyMinutes,
            "Europe/Istanbul",
            "Prepare for calculus",
            true,
            expectedVersion
        );
    }

    private UUID insertUser() {
        UUID userId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-08T00:00:00Z");
        jdbcTemplate.update(
            "insert into users (id, status, created_at, updated_at) values (?, 'ACTIVE', ?, ?)",
            userId,
            Timestamp.from(now),
            Timestamp.from(now)
        );
        return userId;
    }

    private Integer count(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }

    private Integer countWhere(String table, String predicate) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where " + predicate, Integer.class);
    }
}
