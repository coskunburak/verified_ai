package com.verifiedai.studyplan.gamification;

import com.verifiedai.studyplan.privacy.GamificationPrivacyContributor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * Verifies Sprint 8.8 Spring Boot 4 / Jackson 3 wiring compatibility inside an isolated
 * {@link AnnotationConfigApplicationContext} without registering a Jackson 2 {@code ObjectMapper} bean.
 */
class GamificationSpringWiringTest {

    @Test
    void contextBootsWithoutJackson2ObjectMapperBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(NamedParameterJdbcTemplate.class, () -> mock(NamedParameterJdbcTemplate.class));
            context.register(
                    JdbcGamificationRepository.class,
                    GamificationPolicyService.class,
                    GamificationController.class,
                    GamificationPrivacyContributor.class
            );
            context.refresh();

            GamificationPolicyService policyService = context.getBean(GamificationPolicyService.class);
            GamificationController controller = context.getBean(GamificationController.class);
            GamificationPrivacyContributor privacyContributor = context.getBean(GamificationPrivacyContributor.class);

            assertNotNull(policyService);
            assertNotNull(controller);
            assertNotNull(privacyContributor);
            assertEquals("studyplan.gamification", privacyContributor.category());
        }
    }
}
