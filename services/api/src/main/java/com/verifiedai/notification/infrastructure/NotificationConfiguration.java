package com.verifiedai.notification.infrastructure;

import com.verifiedai.notification.domain.NotificationPolicyEvaluator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
class NotificationConfiguration {

    @Bean
    NotificationPolicyEvaluator notificationPolicyEvaluator() {
        return new NotificationPolicyEvaluator();
    }
}
