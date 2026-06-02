package com.couponplatform.analyticsservice.controller;

import com.couponplatform.analyticsservice.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
//import org.springframework.boot.test.mock.MockBean;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for AnalyticsController.
 * Tests HTTP request/response handling for analytics endpoints.
 */
@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false",
    "spring.kafka.bootstrap-servers="
})
class AnalyticsControllerTest {

    @Autowired private MockMvc mockMvc;
    @org.springframework.boot.test.mock.mockito.MockBean
    private AnalyticsService analyticsService;

    private LocalDateTime testFromDate;
    private LocalDateTime testToDate;
    private Map<String, Object> summaryData;
    private List<Map<String, Object>> topCouponsData;

    @BeforeEach
    void setUp() {
        testFromDate = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        testToDate = LocalDateTime.of(2026, 1, 31, 23, 59, 59);

        summaryData = new LinkedHashMap<>();
        summaryData.put("period_from", testFromDate.toString());
        summaryData.put("period_to", testToDate.toString());
        summaryData.put("total_redemptions", 150L);
        summaryData.put("total_discount_granted", new BigDecimal("3750.00"));
        summaryData.put("avg_discount_per_redemption", new BigDecimal("25.00"));

        topCouponsData = Arrays.asList(
                Map.of("coupon_code", "SUMMER25", "redemption_count", 100L, "total_discount", new BigDecimal("2500.00")),
                Map.of("coupon_code", "FALL20", "redemption_count", 50L, "total_discount", new BigDecimal("1000.00"))
        );
    }

    // ---------------- Summary Endpoint Tests -----------------------------

    @Test
    @DisplayName("GET /api/analytics/summary - returns summary with valid dates")
    void getSummary_validDateRange_returns200() throws Exception {
        when(analyticsService.getSummary(any(), any()))
                .thenReturn(summaryData);

        mockMvc.perform(get("/api/analytics/summary")
                .param("from", "2026-01-01T00:00:00")
                .param("to", "2026-01-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_redemptions").value(150))
                .andExpect(jsonPath("$.total_discount_granted").exists())
                .andExpect(jsonPath("$.avg_discount_per_redemption").exists());

        verify(analyticsService).getSummary(any(), any());
    }

    @Test
    @DisplayName("GET /api/analytics/summary - uses default dates when not provided")
    void getSummary_noDateParams_usesDefaults() throws Exception {
        when(analyticsService.getSummary(any(), any()))
                .thenReturn(summaryData);

        mockMvc.perform(get("/api/analytics/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_redemptions").value(150));

        verify(analyticsService).getSummary(any(), any());
    }

    @Test
    @DisplayName("GET /api/analytics/summary - returns correct field structure")
    void getSummary_returnsCorrectStructure() throws Exception {
        when(analyticsService.getSummary(any(), any()))
                .thenReturn(summaryData);

        mockMvc.perform(get("/api/analytics/summary")
                .param("from", "2026-01-01T00:00:00")
                .param("to", "2026-01-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period_from").exists())
                .andExpect(jsonPath("$.period_to").exists())
                .andExpect(jsonPath("$.total_redemptions").exists())
                .andExpect(jsonPath("$.total_discount_granted").exists())
                .andExpect(jsonPath("$.avg_discount_per_redemption").exists());
    }

    @Test
    @DisplayName("GET /api/analytics/summary - handles zero redemptions")
    void getSummary_zeroRedemptions_returns200() throws Exception {
        Map<String, Object> emptyData = new LinkedHashMap<>();
        emptyData.put("period_from", testFromDate.toString());
        emptyData.put("period_to", testToDate.toString());
        emptyData.put("total_redemptions", 0L);
        emptyData.put("total_discount_granted", BigDecimal.ZERO);
        emptyData.put("avg_discount_per_redemption", BigDecimal.ZERO);

        when(analyticsService.getSummary(any(), any()))
                .thenReturn(emptyData);

        mockMvc.perform(get("/api/analytics/summary")
                .param("from", testFromDate.toString())
                .param("to", testToDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_redemptions").value(0));
    }

    // ---------------- Top Coupons Endpoint Tests -------------------------

    @Test
    @DisplayName("GET /api/analytics/top-coupons - returns ranked list of coupons")
    void getTopCoupons_validDateRange_returns200() throws Exception {
        when(analyticsService.getTopCoupons(any(), any()))
                .thenReturn(topCouponsData);

        mockMvc.perform(get("/api/analytics/top-coupons")
                .param("from", "2026-01-01T00:00:00")
                .param("to", "2026-01-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].coupon_code").value("SUMMER25"))
                .andExpect(jsonPath("$[0].redemption_count").value(100))
                .andExpect(jsonPath("$[1].coupon_code").value("FALL20"));

        verify(analyticsService).getTopCoupons(any(), any());
    }

    @Test
    @DisplayName("GET /api/analytics/top-coupons - returns empty list when no data")
    void getTopCoupons_noCoupons_returnsEmptyList() throws Exception {
        when(analyticsService.getTopCoupons(any(), any()))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/analytics/top-coupons")
                .param("from", "2026-01-01T00:00:00")
                .param("to", "2026-01-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        verify(analyticsService).getTopCoupons(any(), any());
    }

    @Test
    @DisplayName("GET /api/analytics/top-coupons - maintains ranking order")
    void getTopCoupons_maintainsRankingOrder() throws Exception {
        List<Map<String, Object>> rankedData = Arrays.asList(
                Map.of("coupon_code", "BEST", "redemption_count", 1000L, "total_discount", new BigDecimal("10000.00")),
                Map.of("coupon_code", "GOOD", "redemption_count", 500L, "total_discount", new BigDecimal("5000.00")),
                Map.of("coupon_code", "OK", "redemption_count", 100L, "total_discount", new BigDecimal("1000.00"))
        );

        when(analyticsService.getTopCoupons(any(), any()))
                .thenReturn(rankedData);

        mockMvc.perform(get("/api/analytics/top-coupons")
                .param("from", "2026-01-01T00:00:00")
                .param("to", "2026-01-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].redemption_count").value(1000L))
                .andExpect(jsonPath("$[1].redemption_count").value(500L))
                .andExpect(jsonPath("$[2].redemption_count").value(100L));
    }

    @Test
    @DisplayName("GET /api/analytics/top-coupons - includes required fields")
    void getTopCoupons_includesAllRequiredFields() throws Exception {
        when(analyticsService.getTopCoupons(any(), any()))
                .thenReturn(topCouponsData);

        mockMvc.perform(get("/api/analytics/top-coupons")
                .param("from", "2026-01-01T00:00:00")
                .param("to", "2026-01-31T23:59:59"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].coupon_code").exists())
                .andExpect(jsonPath("$[0].redemption_count").exists())
                .andExpect(jsonPath("$[0].total_discount").exists());
    }

    // ---------------- Health Check Endpoint Tests -----------------------

    @Test
    @DisplayName("GET /api/analytics/health - returns UP status")
    void health_returns200WithUpStatus() throws Exception {
        mockMvc.perform(get("/api/analytics/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("analytics-service"));
    }

    // ---------------- Date Parameter Handling Tests -----------------------

    @Test
    @DisplayName("GET /api/analytics/summary - accepts ISO 8601 datetime format")
    void getSummary_acceptsISO8601Format() throws Exception {
        when(analyticsService.getSummary(any(), any()))
                .thenReturn(summaryData);

        mockMvc.perform(get("/api/analytics/summary")
                .param("from", "2026-01-01T10:30:45")
                .param("to", "2026-01-31T20:15:30"))
                .andExpect(status().isOk());

        verify(analyticsService).getSummary(any(), any());
    }

    @Test
    @DisplayName("GET /api/analytics/top-coupons - uses default dates when parameters missing")
    void getTopCoupons_noDateParams_usesDefaults() throws Exception {
        when(analyticsService.getTopCoupons(any(), any()))
                .thenReturn(topCouponsData);

        mockMvc.perform(get("/api/analytics/top-coupons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));

        verify(analyticsService).getTopCoupons(any(), any());
    }

    @Test
    @DisplayName("GET /api/analytics/summary - single day range returns data")
    void getSummary_singleDayRange_returns200() throws Exception {
        LocalDateTime sameDate = LocalDateTime.of(2026, 1, 15, 12, 0, 0);

        when(analyticsService.getSummary(any(), any()))
                .thenReturn(summaryData);

        mockMvc.perform(get("/api/analytics/summary")
                .param("from", "2026-01-15T12:00:00")
                .param("to", "2026-01-15T12:00:00"))
                .andExpect(status().isOk());

        verify(analyticsService).getSummary(any(), any());
    }
}

