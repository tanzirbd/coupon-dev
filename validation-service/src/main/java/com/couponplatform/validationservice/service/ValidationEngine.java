package com.couponplatform.validationservice.service;

import com.couponplatform.validationservice.client.CouponServiceClient;
import com.couponplatform.validationservice.dto.ValidationDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Core Validation Engine.
 *
 * Implements the sequence diagram from the capstone report:
 *   1. Check Redis cache for coupon data
 *   2. On miss → call Coupon Service via Feign (REST)
 *   3. Apply business rules
 *   4. Return discount or rejection
 *   5. Publish redemption event to Kafka → Analytics + Notification
 *
 * NFR: < 200 ms latency via Redis cache + stateless service.
 * FR4: Validation service verifies code, applies rules, returns discount.
 * FR5: Logs usage and prevents duplicate redemptions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationEngine {

    private final CouponServiceClient couponClient;
    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX      = "coupon:";
    private static final String REDEMPTION_PREFIX = "redeemed:";
    private static final String TOPIC_REDEEMED    = "coupon.redeemed";
    private static final Duration CACHE_TTL       = Duration.ofMinutes(10);

    /**
     * Main validation entry point.
     * Returns discount amount on success, or rejection reason on failure.
     */
    public ValidateResponse validate(ValidateRequest request) {
        long start = System.currentTimeMillis();
        String code = request.getCouponCode().toUpperCase();

        // Step 1: Load coupon data (cache-first)
        CouponData coupon = loadCoupon(code);
        if (coupon == null) {
            return reject(code, "Coupon code not found");
        }

        // Step 2: Status check
        if (!"ACTIVE".equals(coupon.getStatus())) {
            return reject(code, "Coupon is " + coupon.getStatus().toLowerCase());
        }

        // Step 3: Date range check
        LocalDateTime now = LocalDateTime.now();
        if (coupon.getStartDate() != null && now.isBefore(coupon.getStartDate())) {
            return reject(code, "Coupon is not yet active");
        }
        if (coupon.getExpiryDate() != null && now.isAfter(coupon.getExpiryDate())) {
            return reject(code, "Coupon has expired");
        }

        // Step 4: Usage limit check
        if (coupon.getUsageLimit() != null && coupon.getUsageCount() >= coupon.getUsageLimit()) {
            return reject(code, "Coupon usage limit has been reached");
        }

        // Step 5: Minimum cart value check
        if (coupon.getMinCartValue() != null &&
                request.getCartTotal().compareTo(coupon.getMinCartValue()) < 0) {
            return reject(code, String.format(
                    "Minimum cart value of %.2f required (current: %.2f)",
                    coupon.getMinCartValue(), request.getCartTotal()));
        }

        // Step 6: Per-user redemption check
        String redemptionKey = REDEMPTION_PREFIX + code + ":" + request.getUserId();
        String existingCount = redisTemplate.opsForValue().get(redemptionKey);
        int userRedemptions = existingCount != null ? Integer.parseInt(existingCount) : 0;
        int perUserLimit = coupon.getPerUserLimit() != null ? coupon.getPerUserLimit() : 1;
        if (userRedemptions >= perUserLimit) {
            return reject(code, "You have already used this coupon the maximum number of times");
        }

        // Step 7: Calculate discount
        BigDecimal discountAmount = calculateDiscount(coupon, request.getCartTotal());
        BigDecimal finalTotal = request.getCartTotal().subtract(discountAmount).max(BigDecimal.ZERO);

        // Step 8: Record redemption in Redis (atomic increment)
        redisTemplate.opsForValue().increment(redemptionKey);
        redisTemplate.expire(redemptionKey, Duration.ofDays(365));

        // Step 9: Publish redemption event (async, non-blocking)
        publishRedemptionEvent(coupon, request, discountAmount);

        long elapsed = System.currentTimeMillis() - start;
        log.info("Coupon {} validated for user {} in {}ms — discount: {}",
                code, request.getUserId(), elapsed, discountAmount);

        return ValidateResponse.builder()
                .valid(true)
                .couponCode(code)
                .message("Coupon applied successfully")
                .discountAmount(discountAmount.setScale(2, RoundingMode.HALF_UP))
                .finalCartTotal(finalTotal.setScale(2, RoundingMode.HALF_UP))
                .couponType(coupon.getType())
                .build();
    }

    // ──────────────── Discount Calculator ────────────────────────────────────

    private BigDecimal calculateDiscount(CouponData coupon, BigDecimal cartTotal) {
        return switch (coupon.getType()) {
            case "PERCENTAGE" -> {
                BigDecimal pct = coupon.getDiscountValue().divide(BigDecimal.valueOf(100));
                BigDecimal discount = cartTotal.multiply(pct);
                if (coupon.getMaxDiscountCap() != null) {
                    discount = discount.min(coupon.getMaxDiscountCap());
                }
                yield discount;
            }
            case "FIXED_AMOUNT" -> coupon.getDiscountValue().min(cartTotal);
            case "FREE_SHIPPING" -> BigDecimal.ZERO; // handled by shipping service
            default -> BigDecimal.ZERO;
        };
    }

    // ──────────────── Cache (Redis) ────────────────────────────────────────

    private CouponData loadCoupon(String code) {
        String cacheKey = CACHE_PREFIX + code;
        String cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached != null) {
            try {
                log.debug("Cache HIT for coupon: {}", code);
                return objectMapper.readValue(cached, CouponData.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize cached coupon: {}", e.getMessage());
            }
        }

        log.debug("Cache MISS for coupon: {} — calling Coupon Service", code);
        try {
            var wrapper = couponClient.getCouponByCode(code);
            if (wrapper != null && wrapper.success() && wrapper.data() != null) {
                CouponData data = wrapper.data();
                // Cache it
                String json = objectMapper.writeValueAsString(data);
                redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
                return data;
            }
        } catch (Exception e) {
            log.error("Error calling Coupon Service for code {}: {}", code, e.getMessage());
        }

        return null;
    }

    // ──────────────── Kafka Event ────────────────────────────────────────────

    private void publishRedemptionEvent(CouponData coupon, ValidateRequest request, BigDecimal discount) {
        try {
            String payload = String.format(
                "{\"couponId\":%d,\"code\":\"%s\",\"userId\":\"%s\"," +
                "\"cartTotal\":%.2f,\"discountAmount\":%.2f,\"timestamp\":\"%s\"}",
                coupon.getId(), coupon.getCode(), request.getUserId(),
                request.getCartTotal(), discount, LocalDateTime.now()
            );
            kafkaTemplate.send(TOPIC_REDEEMED, payload);
        } catch (Exception e) {
            log.warn("Failed to publish redemption event: {}", e.getMessage());
        }
    }

    private ValidateResponse reject(String code, String reason) {
        log.info("Coupon {} rejected: {}", code, reason);
        return ValidateResponse.builder()
                .valid(false)
                .couponCode(code)
                .message(reason)
                .discountAmount(BigDecimal.ZERO)
                .build();
    }
}
