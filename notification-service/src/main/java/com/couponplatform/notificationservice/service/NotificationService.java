package com.couponplatform.notificationservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Notification Service.
 *
 * FR7: System sends expiry reminders and redemption confirmations via email/SMS.
 *
 * Listens to:
 *   - coupon.redeemed  → send redemption confirmation email to user
 *   - coupon.created   → optionally notify marketing admins
 *
 * Production: Replace stub email with SendGrid or Twilio SDK.
 * Configure SMTP in application.yml (environment variables MAIL_HOST etc.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "coupon.redeemed", groupId = "notification-group")
    public void onCouponRedeemed(String message) {
        log.info("[Notification] Coupon redeemed event: {}", message);
        try {
            JsonNode node = objectMapper.readTree(message);
            String userId    = node.get("userId").asText();
            String code      = node.get("code").asText();
            String discount  = node.get("discountAmount").asText();

            sendEmail(
                userId + "@example.com",   // Replace with real user email lookup
                "Coupon Applied — " + code,
                String.format(
                    "Hi %s,\n\n" +
                    "Your coupon %s has been applied successfully.\n" +
                    "Discount: $%s\n\n" +
                    "Thank you for shopping with us!\n\n" +
                    "— Coupon Platform Team",
                    userId, code, discount
                )
            );
        } catch (Exception e) {
            log.error("[Notification] Failed to process redeemed event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "coupon.created", groupId = "notification-group")
    public void onCouponCreated(String message) {
        log.info("[Notification] New coupon created: {}", message);
        // Optionally notify marketing admin team
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(to);
            mail.setSubject(subject);
            mail.setText(body);
            mail.setFrom("noreply@couponplatform.com");
            mailSender.send(mail);
            log.info("[Notification] Email sent to {}", to);
        } catch (Exception e) {
            // Non-critical: log and continue (don't break the event pipeline)
            log.warn("[Notification] Email send failed for {}: {}", to, e.getMessage());
        }
    }
}
