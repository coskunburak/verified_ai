package com.verifiedai.featureflags.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.verifiedai.sharedkernel.error.ApiErrorCode;
import com.verifiedai.sharedkernel.error.ApiProblemException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

final class DeterministicBucketingPolicyTest {

    @Test
    void bucketingIsDeterministicAndMonotoneAcrossRolloutThresholds() {
        UUID userA = UUID.fromString("11111111-1111-1111-1111-111111111111");
        int bucketFirst = DeterministicBucketingPolicy.computeBucket("tutor_v2_enabled", "v1", userA);
        int bucketSecond = DeterministicBucketingPolicy.computeBucket(" tutor_v2_enabled ", " v1 ", userA);
        assertThat(bucketFirst)
            .isEqualTo(bucketSecond)
            .isBetween(0, 9999);

        // Salt change isolates re-randomization cleanly
        int bucketSalted = DeterministicBucketingPolicy.computeBucket("tutor_v2_enabled", "v2", userA);
        assertThat(bucketSalted).isBetween(0, 9999).isNotEqualTo(bucketFirst);

        // Boundary gates & monotone nesting across 0% -> 1% -> 5% -> 25% -> 50% -> 100%
        int[] thresholds = {0, 100, 500, 2500, 5000, 10_000};
        for (int i = 0; i < 500; i++) {
            UUID user = UUID.nameUUIDFromBytes(("monotone-user-" + i).getBytes());
            assertThat(DeterministicBucketingPolicy.isInRollout("EXP-LOS-01", "v1", user, 0)).isFalse();
            assertThat(DeterministicBucketingPolicy.isInRollout("EXP-LOS-01", "v1", user, 10_000)).isTrue();
            for (int t = 0; t < thresholds.length - 1; t++) {
                boolean lower = DeterministicBucketingPolicy.isInRollout("EXP-LOS-01", "v1", user, thresholds[t]);
                boolean higher = DeterministicBucketingPolicy.isInRollout("EXP-LOS-01", "v1", user, thresholds[t + 1]);
                if (lower) {
                    assertThat(higher).isTrue();
                }
            }
        }
    }

    @Test
    void tenThousandUsersYieldUniformDistributionWithinTolerance() {
        int totalUsers = 10_000;
        int matchedHalf = 0;
        int matchedFivePct = 0;
        int[] deciles = new int[10];
        List<ExperimentVariantSpec> split = List.of(
            new ExperimentVariantSpec("control", 5000),
            new ExperimentVariantSpec("treatment", 5000)
        );
        int treatmentCount = 0;

        for (int i = 0; i < totalUsers; i++) {
            UUID userId = UUID.nameUUIDFromBytes(("dist-user-" + i).getBytes());
            int bucket = DeterministicBucketingPolicy.computeBucket("EXP-LOS-01", "v1", userId);
            deciles[bucket / 1000]++;
            if (bucket < 5000) {
                matchedHalf++;
            }
            if (bucket < 500) {
                matchedFivePct++;
            }
            if ("treatment".equals(DeterministicBucketingPolicy.selectVariant(bucket, split))) {
                treatmentCount++;
            }
        }

        // 50% target: allow <= +/- 2.5% band over 10k SHA-256 samples
        assertThat(matchedHalf).isBetween(4750, 5250);
        assertThat(treatmentCount).isBetween(4750, 5250);
        // 5% target (~500): allow wide statistical envelope [400, 600]
        assertThat(matchedFivePct).isBetween(400, 600);
        // Every decile (~1000) within +/- 15%
        for (int decileCount : deciles) {
            assertThat(decileCount).isBetween(850, 1150);
        }
    }

    @Test
    void rejectsInvalidRolloutsVariantWeightsDuplicatesAndMissingControl() {
        assertThatThrownBy(() -> DeterministicBucketingPolicy.validateRolloutBasisPoints(-1))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                ApiProblemException problem = (ApiProblemException) ex;
                assertThat(problem.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(problem.code()).isEqualTo(ApiErrorCode.FEATURE_FLAG_VALIDATION_FAILED);
            });
        assertThatThrownBy(() -> DeterministicBucketingPolicy.validateRolloutBasisPoints(10_001))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.FEATURE_FLAG_VALIDATION_FAILED));

        assertThatThrownBy(() -> DeterministicBucketingPolicy.validateVariants(
            List.of(
                new ExperimentVariantSpec("control", 4000),
                new ExperimentVariantSpec("treatment", 5999)
            ),
            "control"
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> {
                ApiProblemException problem = (ApiProblemException) ex;
                assertThat(problem.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                assertThat(problem.code()).isEqualTo(ApiErrorCode.EXPERIMENT_VALIDATION_FAILED);
            });

        assertThatThrownBy(() -> DeterministicBucketingPolicy.validateVariants(
            List.of(
                new ExperimentVariantSpec("control", 5000),
                new ExperimentVariantSpec("control", 5000)
            ),
            "control"
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.EXPERIMENT_VALIDATION_FAILED));

        assertThatThrownBy(() -> DeterministicBucketingPolicy.validateVariants(
            List.of(
                new ExperimentVariantSpec("variant_a", 5000),
                new ExperimentVariantSpec("variant_b", 5000)
            ),
            "control"
        ))
            .isInstanceOf(ApiProblemException.class)
            .satisfies(ex -> assertThat(((ApiProblemException) ex).code())
                .isEqualTo(ApiErrorCode.EXPERIMENT_VALIDATION_FAILED));

        assertThatThrownBy(() -> DeterministicBucketingPolicy.validateVariants(
            List.of(new ExperimentVariantSpec("control", 10_000)),
            "control"
        )).isInstanceOf(ApiProblemException.class);
    }
}
