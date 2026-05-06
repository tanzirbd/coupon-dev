package com.couponplatform.analyticsservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Analytics Service Application.
 *
 * FR8: Analytics dashboard displaying redemption rates and campaign performance.
 * Consumes Kafka events from:
 *   - coupon.redeemed  → persisted to analytics_db via AnalyticsService
 *   - coupon.created   → logged for campaign tracking
 *
 * Exposes REST API via AnalyticsController for dashboard queries.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class AnalyticsServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalyticsServiceApplication.class, args);
    }
}
