package com.couponplatform.userservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Exclude UserDetailsServiceAutoConfiguration to prevent Spring Boot from
 * auto-configuring a default "user" account and adding Basic Auth credentials
 * to outgoing HTTP calls (including Eureka client registration RestTemplate).
 * We provide our own UserDetailsServiceImpl, so this auto-config is redundant
 * and causes the Eureka registration to return 401.
 */
@SpringBootApplication(exclude = {UserDetailsServiceAutoConfiguration.class})
@EnableDiscoveryClient
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}