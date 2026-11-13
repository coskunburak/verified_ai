package com.verifiedai.studyplan.session;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StudySessionOrchestrationProperties.class)
class StudySessionConfiguration {

    @Bean
    StudySessionStateMachine studySessionStateMachine() {
        return new StudySessionStateMachine();
    }
}
