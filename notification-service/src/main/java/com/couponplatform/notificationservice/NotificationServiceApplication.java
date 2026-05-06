package com.couponplatform.notificationservice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Notification Service — Kafka Consumer.
 *
 * FR7: System sends expiry reminders via email/SMS.
 * Listens to:
 *   - coupon.redeemed  → send confirmation to user
 *   - coupon.created   → notify marketing team
 */
@SpringBootApplication
@EnableDiscoveryClient
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}

@Component
@Slf4j
class NotificationConsumer {

    @KafkaListener(topics = "coupon.redeemed", groupId = "notification-group")
    public void onCouponRedeemed(String message) {
        log.info("[Notification] Coupon redeemed event received: {}", message);
        // TODO: Integrate SendGrid / Twilio to send email/SMS
        // Parse message → extract userId → lookup email → send notification
    }

    @KafkaListener(topics = "coupon.created", groupId = "notification-group")
    public void onCouponCreated(String message) {
        log.info("[Notification] Coupon created event received: {}", message);
        // TODO: Notify marketing admins of new coupon
    }
}
