package com.couponplatform.validationservice.service;

import com.couponplatform.validationservice.client.CouponServiceClient;
import com.couponplatform.validationservice.dto.ValidationDtos.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ValidationEngine.
 * NFR: < 200ms latency via Redis cache.
 * FR4: Validates code, applies rules, returns discount.
 * FR5: Prevents duplicate redemptions per user.
 */
@ExtendWith(MockitoExtension.class)
class ValidationEngineTest {

    @Mock private CouponServiceClient couponClient;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks private ValidationEngine validationEngine;

    private CouponData validCoupon;
    private ValidateRequest validRequest;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);

        validCoupon = CouponData.builder()
                .id(1L)
                .code("SUMMER25")
                .type("PERCENTAGE")
                .discountValue(new BigDecimal("25.00"))
                .minCartValue(new BigDecimal("100.00"))
                .maxDiscountCap(new BigDecimal("50.00"))
                .usageLimit(1000)
                .usageCount(5)
                .perUserLimit(1)
                .status("ACTIVE")
                .expiryDate(LocalDateTime.now().plusDays(30))
                .build();

        validRequest = ValidateRequest.builder()
                .couponCode("SUMMER25")
                .cartTotal(new BigDecimal("200.00"))
                .userId("user123")
                .build();
    }

    // ──────────────── Cache HIT path ──────────────────────────────────────────

    @Test
    @DisplayName("Validate - cache hit returns correct discount")
    void validate_cacheHit_returnsDiscount() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        String cachedJson = mapper.writeValueAsString(validCoupon);

        when(valueOps.get("coupon:SUMMER25")).thenReturn(cachedJson);
        when(valueOps.get("redeemed:SUMMER25:user123")).thenReturn(null);
        when(valueOps.increment("redeemed:SUMMER25:user123")).thenReturn(1L);
        when(redisTemplate.expire(any(), any())).thenReturn(true);

        // Inject real ObjectMapper
        var field = ValidationEngine.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(validationEngine, mapper);

        ValidateResponse response = validationEngine.validate(validRequest);

        assertThat(response.isValid()).isTrue();
        // 25% of 200 = 50, capped at 50 → discount = 50
        assertThat(response.getDiscountAmount()).isEqualByComparingTo("50.00");
        assertThat(response.getFinalCartTotal()).isEqualByComparingTo("150.00");
        assertThat(response.getCouponType()).isEqualTo("PERCENTAGE");
    }

    // ──────────────── Rejection scenarios ────────────────────────────────────

    @Test
    @DisplayName("Validate - inactive coupon is rejected")
    void validate_inactiveCoupon_isRejected() throws Exception {
        validCoupon.setStatus("INACTIVE");

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        String cachedJson = mapper.writeValueAsString(validCoupon);
        when(valueOps.get("coupon:SUMMER25")).thenReturn(cachedJson);

        var field = ValidationEngine.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(validationEngine, mapper);

        ValidateResponse response = validationEngine.validate(validRequest);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("inactive");
    }

    @Test
    @DisplayName("Validate - cart below minimum is rejected")
    void validate_belowMinCart_isRejected() throws Exception {
        validRequest.setCartTotal(new BigDecimal("50.00")); // min is 100

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        String cachedJson = mapper.writeValueAsString(validCoupon);
        when(valueOps.get("coupon:SUMMER25")).thenReturn(cachedJson);

        var field = ValidationEngine.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(validationEngine, mapper);

        ValidateResponse response = validationEngine.validate(validRequest);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("Minimum cart value");
    }

    @Test
    @DisplayName("Validate - expired coupon is rejected")
    void validate_expiredCoupon_isRejected() throws Exception {
        validCoupon.setExpiryDate(LocalDateTime.now().minusDays(1));

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        String cachedJson = mapper.writeValueAsString(validCoupon);
        when(valueOps.get("coupon:SUMMER25")).thenReturn(cachedJson);

        var field = ValidationEngine.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(validationEngine, mapper);

        ValidateResponse response = validationEngine.validate(validRequest);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("expired");
    }

    @Test
    @DisplayName("Validate - user exceeded per-user limit is rejected (FR5)")
    void validate_perUserLimitExceeded_isRejected() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        String cachedJson = mapper.writeValueAsString(validCoupon);
        when(valueOps.get("coupon:SUMMER25")).thenReturn(cachedJson);
        when(valueOps.get("redeemed:SUMMER25:user123")).thenReturn("1"); // already used once

        var field = ValidationEngine.class.getDeclaredField("objectMapper");
        field.setAccessible(true);
        field.set(validationEngine, mapper);

        ValidateResponse response = validationEngine.validate(validRequest);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("maximum number of times");
    }

    @Test
    @DisplayName("Validate - unknown coupon returns not found rejection")
    void validate_unknownCoupon_isRejected() {
        when(valueOps.get(anyString())).thenReturn(null);
        when(couponClient.getCouponByCode(any())).thenReturn(null);

        ValidateResponse response = validationEngine.validate(validRequest);

        assertThat(response.isValid()).isFalse();
        assertThat(response.getMessage()).contains("not found");
    }
}
