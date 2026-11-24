package com.verifiedai.admin.infrastructure;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AdminSupportProperties.class)
public class AdminSupportConfiguration {
}
