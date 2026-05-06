package com.couponplatform.eurekaserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * REQUIRED: spring-cloud-starter-netflix-eureka-server pulls Spring Security
 * in transitively. Without this class, Spring Boot's DEFAULT security applies:
 *   - random password generated on startup
 *   - ALL endpoints require HTTP Basic Auth
 *   - Eureka client registrations (POST /eureka/apps/**) get 401
 *
 * This config disables all auth and CSRF so clients can register freely.
 */
@Configuration
@EnableWebSecurity
public class EurekaSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}