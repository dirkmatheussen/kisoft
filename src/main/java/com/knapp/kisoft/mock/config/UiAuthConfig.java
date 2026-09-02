package com.knapp.kisoft.mock.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.util.StringUtils;

/**
 * In-memory UI user for HTTP Basic Auth on the homepage and Swagger UI.
 */
@Configuration
public class UiAuthConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @ConditionalOnProperty(name = "knapp.mock.ui-auth-enabled", havingValue = "true", matchIfMissing = true)
    UserDetailsService uiUserDetailsService(KnappMockProperties properties, PasswordEncoder passwordEncoder) {
        return new InMemoryUserDetailsManager(
                User.builder()
                        .username(properties.getUiUsername())
                        .password(passwordEncoder.encode(properties.getUiPassword()))
                        .roles("UI")
                        .build());
    }

    @Bean
    @ConditionalOnProperty(name = "knapp.mock.ui-auth-enabled", havingValue = "true", matchIfMissing = true)
    ApplicationRunner uiPasswordStartupCheck(KnappMockProperties properties) {
        return (ApplicationArguments args) -> {
            if (!StringUtils.hasText(properties.getUiPassword())) {
                throw new IllegalStateException(
                        "UI login is enabled but no password is set. "
                                + "Set knapp.mock.ui-password or the MOCK_UI_PASSWORD environment variable.");
            }
        };
    }
}
