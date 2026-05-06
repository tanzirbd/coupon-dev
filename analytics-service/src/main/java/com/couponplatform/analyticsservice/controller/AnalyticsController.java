package com.couponplatform.analyticsservice.controller;

import com.couponplatform.analyticsservice.service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Analytics REST endpoints.
 * FR8: Analytics dashboard displaying redemption rates and campaign performance.
 */
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Redemption metrics and campaign performance dashboard")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * GET /api/analytics/summary?from=2026-01-01T00:00:00&to=2026-12-31T23:59:59
     * Returns overall redemption stats for the period.
     */
    @GetMapping("/summary")
    @Operation(summary = "Redemption summary", description = "Returns total redemptions and discount granted for a date range")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestParam(defaultValue = "#{T(java.time.LocalDateTime).now().minusDays(30)}")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(defaultValue = "#{T(java.time.LocalDateTime).now()}")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(analyticsService.getSummary(from, to));
    }

    /**
     * GET /api/analytics/top-coupons?from=...&to=...
     * Returns top-performing coupons ranked by redemption count.
     */
    @GetMapping("/top-coupons")
    @Operation(summary = "Top coupons", description = "Ranks coupons by redemption count for a given period")
    public ResponseEntity<List<Map<String, Object>>> getTopCoupons(
            @RequestParam(defaultValue = "#{T(java.time.LocalDateTime).now().minusDays(30)}")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(defaultValue = "#{T(java.time.LocalDateTime).now()}")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(analyticsService.getTopCoupons(from, to));
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "analytics-service"));
    }
}
