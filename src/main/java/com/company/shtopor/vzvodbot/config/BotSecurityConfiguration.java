package com.company.shtopor.vzvodbot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class BotSecurityConfiguration {

    @Bean
    @Order(0)
    SecurityFilterChain internalApiFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/internal/**")
                .authorizeHttpRequests(registry -> registry.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
