package com.verifiedai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.modulith.Modulithic;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@Modulithic
public class VerifiedAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(VerifiedAiApplication.class, args);
    }
}
