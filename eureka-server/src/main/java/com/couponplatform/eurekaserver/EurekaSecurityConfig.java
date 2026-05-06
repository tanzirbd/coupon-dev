package com.couponplatform.eurekaserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Eureka Server Security Configuration.
 *
 * Problem without this class:
 *   Spring Boot auto-configures CSRF protection on ALL POST endpoints.
 *   Eureka clients register/heartbeat via POST → CSRF filter blocks them → 401/403.
 *   Services cannot register even with correct credentials in the defaultZone URL.
 *
 * Fix:
 *   Disable CSRF for /eureka/** endpoints only so Eureka clients can POST freely,
 *   while still requiring HTTP Basic auth for the Eureka dashboard UI.
 */
@Configuration
@EnableWebSecurity
public class EurekaSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for Eureka endpoints — clients cannot send CSRF tokens
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                    "/eureka/**",
                    "/actuator/**"
            ))
            // Allow Eureka dashboard and actuator without auth,
            // everything else requires Basic auth
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/**").permitAll()
                    .anyRequest().authenticated()
            )
            // Use HTTP Basic so the username:password in defaultZone URL works
            .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
