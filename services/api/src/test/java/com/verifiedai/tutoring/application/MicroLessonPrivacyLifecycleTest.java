package com.verifiedai.tutoring.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.verifiedai.studyplan.rebalance.RebalancePrivacyPurgeContributor;
import com.verifiedai.tutoring.domain.MicroLessonCompletionRequest;
import com.verifiedai.tutoring.domain.MicroLessonHashVerifier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;

class MicroLessonPrivacyLifecycleTest {

    private static final UUID USER_A = UUID.fromString("00000000-0000-4000-8630-000000000001");
    private static final UUID USER_B = UUID.fromString("00000000-0000-4000-8630-000000000002");

    @Test
    void exportAndPurgeAreUserScopedTrainingIneligibleFastAndPreserveCatalog() {
        InMemoryMicroLessonRepository repository = new InMemoryMicroLessonRepository();
        MicroLessonApplicationService service = new MicroLessonApplicationService(
                repository,
                new MicroLessonHashVerifier(),
                new SimpleMeterRegistry(),
                Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC),
                true,
                1000,
                600000
        );
        MicroLessonDataLifecycleContributor contributor = new MicroLessonDataLifecycleContributor(repository);

        service.completeMicroLesson(
                USER_A,
                "ALG_SIGN_FLIP_DISTRIBUTE",
                new MicroLessonCompletionRequest("tr-TR", "COACH_REPAIR_DRAWER", null, "B", 22_000, "priv-user-a-001")
        );
        service.completeMicroLesson(
                USER_B,
                "ALG_FRESHMAN_DREAM_POWER",
                new MicroLessonCompletionRequest("en-US", "COACH_REPAIR_DRAWER", null, "C", 25_000, "priv-user-b-001")
        );

        Map<String, Object> exportedA = contributor.exportUserData(USER_A);
        assertThat(contributor.category()).isEqualTo("micro_lessons");
        assertThat(exportedA.get("trainingEligible")).isEqualTo(false);
        assertThat((List<?>) exportedA.get("completions")).hasSize(1);

        Instant start = Instant.now();
        contributor.deleteUserData(USER_A, start);
        long elapsedMs = Duration.between(start, Instant.now()).toMillis();

        assertThat(elapsedMs).isLessThan(500L);
        assertThat(repository.findCompletionsByUserId(USER_A)).isEmpty();
        assertThat(repository.findCompletionsByUserId(USER_B)).hasSize(1);
        assertThat(repository.catalogCount()).isEqualTo(34);
    }

    @Test
    void privacyOrder28IsDistinctFromRebalanceContributorOrder27() {
        Order microOrder = MicroLessonDataLifecycleContributor.class.getAnnotation(Order.class);
        Order rebalanceOrder = RebalancePrivacyPurgeContributor.class.getAnnotation(Order.class);
        assertThat(microOrder).isNotNull();
        assertThat(rebalanceOrder).isNotNull();
        assertThat(microOrder.value()).isEqualTo(28);
        assertThat(rebalanceOrder.value()).isEqualTo(27);
        assertThat(microOrder.value()).isNotEqualTo(rebalanceOrder.value());
    }
}
