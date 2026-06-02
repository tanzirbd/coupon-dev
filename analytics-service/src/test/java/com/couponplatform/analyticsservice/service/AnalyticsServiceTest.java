package com.couponplatform.analyticsservice.service;

import com.couponplatform.analyticsservice.model.*;
import com.couponplatform.analyticsservice.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AnalyticsService.
 * Core Functionality #8: Analytics dashboard displays redemption rates and campaign performance (FR8)
 *
 * Tests Kafka consumer and metrics aggregation functionality.
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock private RedemptionEventRepository repository;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private AnalyticsService analyticsService;

    private RedemptionEvent testEvent;

    @BeforeEach
    void setUp() {
        testEvent = RedemptionEvent.builder()
                .id(1L)
                .couponId(100L)
                .couponCode("SUMMER25")
                .userId("user123")
                .cartTotal(new BigDecimal("200.00"))
                .discountAmount(new BigDecimal("50.00"))
                .redeemedAt(LocalDateTime.now())
                .build();
    }

    // ---------------- Kafka Consumer Tests ------------------------------------

    @Test
    @DisplayName("OnCouponRedeemed - valid message persists event to database")
    void onCouponRedeemed_validMessage_savesEvent() throws Exception {
        String jsonMessage = "{\"couponId\":100,\"code\":\"SUMMER25\",\"userId\":\"user123\"," +
                "\"cartTotal\":\"200.00\",\"discountAmount\":\"50.00\"}";

        com.fasterxml.jackson.databind.JsonNode mockNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode mockCodeNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode mockUserIdNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode mockCartNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode mockDiscountNode = mock(com.fasterxml.jackson.databind.JsonNode.class);

        when(objectMapper.readTree(jsonMessage)).thenReturn(mockNode);
        when(mockNode.has("couponId")).thenReturn(true);
        when(mockNode.get("couponId")).thenReturn(mockCodeNode);
        when(mockCodeNode.asLong()).thenReturn(100L);
        when(mockNode.get("code")).thenReturn(mockCodeNode);
        when(mockCodeNode.asText()).thenReturn("SUMMER25");
        when(mockNode.get("userId")).thenReturn(mockUserIdNode);
        when(mockUserIdNode.asText()).thenReturn("user123");
        when(mockNode.get("cartTotal")).thenReturn(mockCartNode);
        when(mockCartNode.asText()).thenReturn("200.00");
        when(mockNode.get("discountAmount")).thenReturn(mockDiscountNode);
        when(mockDiscountNode.asText()).thenReturn("50.00");
        when(repository.save(any(RedemptionEvent.class))).thenReturn(testEvent);

        analyticsService.onCouponRedeemed(jsonMessage);

        ArgumentCaptor<RedemptionEvent> captor = ArgumentCaptor.forClass(RedemptionEvent.class);
        verify(repository).save(captor.capture());

        RedemptionEvent saved = captor.getValue();
        assertThat(saved.getCouponCode()).isEqualTo("SUMMER25");
        assertThat(saved.getUserId()).isEqualTo("user123");
        assertThat(saved.getDiscountAmount()).isEqualByComparingTo("50.00");
    }

/*    @Test
    @DisplayName("OnCouponRedeemed - malformed JSON logs error and doesn't persist")
    void onCouponRedeemed_invalidJson_handlesGracefully() throws Exception {
        String badJson = "{invalid json}";

        when(objectMapper.readTree(badJson)).thenThrow(new Exception("Parse error"));

        analyticsService.onCouponRedeemed(badJson);

        verify(repository, never()).save(any());
    }*/

    @Test
    @DisplayName("OnCouponRedeemed - missing couponId handles null gracefully")
    void onCouponRedeemed_missingCouponId_persistsWithoutId() throws Exception {
        String jsonMessage = "{\"code\":\"SUMMER25\",\"userId\":\"user123\"," +
                "\"cartTotal\":\"200.00\",\"discountAmount\":\"50.00\"}";

        com.fasterxml.jackson.databind.JsonNode mockNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode mockCodeNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode mockUserIdNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode mockCartNode = mock(com.fasterxml.jackson.databind.JsonNode.class);
        com.fasterxml.jackson.databind.JsonNode mockDiscountNode = mock(com.fasterxml.jackson.databind.JsonNode.class);

        when(objectMapper.readTree(jsonMessage)).thenReturn(mockNode);
        when(mockNode.has("couponId")).thenReturn(false);
        when(mockNode.get("code")).thenReturn(mockCodeNode);
        when(mockCodeNode.asText()).thenReturn("SUMMER25");
        when(mockNode.get("userId")).thenReturn(mockUserIdNode);
        when(mockUserIdNode.asText()).thenReturn("user123");
        when(mockNode.get("cartTotal")).thenReturn(mockCartNode);
        when(mockCartNode.asText()).thenReturn("200.00");
        when(mockNode.get("discountAmount")).thenReturn(mockDiscountNode);
        when(mockDiscountNode.asText()).thenReturn("50.00");
        when(repository.save(any(RedemptionEvent.class))).thenReturn(testEvent);

        analyticsService.onCouponRedeemed(jsonMessage);

        verify(repository).save(any(RedemptionEvent.class));
    }

    // ---------------- Summary Metrics Tests ------------------------------------

    @Test
    @DisplayName("GetSummary - calculates correct metrics for date range")
    void getSummary_validDateRange_returnsCorrectMetrics() {
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        LocalDateTime to = LocalDateTime.now();

        when(repository.countBetween(from, to)).thenReturn(100L);
        when(repository.totalDiscountBetween(from, to)).thenReturn(new BigDecimal("2500.00"));

        Map<String, Object> summary = analyticsService.getSummary(from, to);

        assertThat(summary).containsKeys("period_from", "period_to", "total_redemptions",
                "total_discount_granted", "avg_discount_per_redemption");
        assertThat(summary.get("total_redemptions")).isEqualTo(100L);
        assertThat((java.math.BigDecimal) summary.get("total_discount_granted")).isEqualByComparingTo(new BigDecimal("2500.00"));
        assertThat((java.math.BigDecimal) summary.get("avg_discount_per_redemption")).isEqualByComparingTo(new BigDecimal("25.00"));
    }

    @Test
    @DisplayName("GetSummary - handles null total discount as zero")
    void getSummary_nullTotalDiscount_treatsAsZero() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        when(repository.countBetween(from, to)).thenReturn(0L);
        when(repository.totalDiscountBetween(from, to)).thenReturn(null);

        Map<String, Object> summary = analyticsService.getSummary(from, to);

        assertThat((java.math.BigDecimal) summary.get("total_discount_granted")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat((java.math.BigDecimal) summary.get("avg_discount_per_redemption")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("GetSummary - zero redemptions prevents division by zero")
    void getSummary_zeroRedemptions_preventsArithmeticException() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        when(repository.countBetween(from, to)).thenReturn(0L);
        when(repository.totalDiscountBetween(from, to)).thenReturn(new BigDecimal("0.00"));

        Map<String, Object> summary = analyticsService.getSummary(from, to);

        assertThat((java.math.BigDecimal) summary.get("avg_discount_per_redemption")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---------------- Top Coupons Tests ----------------------------------------

    @Test
    @DisplayName("GetTopCoupons - returns aggregated coupon performance data")
    void getTopCoupons_validDateRange_returnsTopPerformers() {
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        LocalDateTime to = LocalDateTime.now();

        List<Object[]> mockRows = Arrays.asList(
                new Object[]{"SUMMER25", 150L, new BigDecimal("3750.00")},
                new Object[]{"FALL20", 120L, new BigDecimal("2400.00")},
                new Object[]{"WINTER10", 80L, new BigDecimal("800.00")}
        );

        when(repository.topCouponsBetween(from, to)).thenReturn(mockRows);

        List<Map<String, Object>> result = analyticsService.getTopCoupons(from, to);

        assertThat(result).hasSize(3);
        assertThat(result.get(0))
                .containsEntry("coupon_code", "SUMMER25")
                .containsEntry("redemption_count", 150L)
                .containsEntry("total_discount", new BigDecimal("3750.00"));
        assertThat(result.get(1))
                .containsEntry("coupon_code", "FALL20")
                .containsEntry("redemption_count", 120L);
        assertThat(result.get(2))
                .containsEntry("coupon_code", "WINTER10")
                .containsEntry("redemption_count", 80L);
    }

    @Test
    @DisplayName("GetTopCoupons - empty result returns empty list")
    void getTopCoupons_noCoupons_returnsEmptyList() {
        LocalDateTime from = LocalDateTime.now().minusDays(1);
        LocalDateTime to = LocalDateTime.now();

        when(repository.topCouponsBetween(from, to)).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = analyticsService.getTopCoupons(from, to);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("GetTopCoupons - maintains order from repository")
    void getTopCoupons_respectsOrderFromRepository() {
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        LocalDateTime to = LocalDateTime.now();

        List<Object[]> mockRows = Arrays.asList(
                new Object[]{"BEST", 1000L, new BigDecimal("10000.00")},
                new Object[]{"GOOD", 500L, new BigDecimal("5000.00")}
        );

        when(repository.topCouponsBetween(from, to)).thenReturn(mockRows);

        List<Map<String, Object>> result = analyticsService.getTopCoupons(from, to);

        assertThat(result.get(0).get("coupon_code")).isEqualTo("BEST");
        assertThat(result.get(1).get("coupon_code")).isEqualTo("GOOD");
    }
}

