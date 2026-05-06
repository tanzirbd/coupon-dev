package com.couponplatform.analyticsservice.service;

import com.couponplatform.analyticsservice.model.RedemptionEvent;
import com.couponplatform.analyticsservice.repository.RedemptionEventRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Analytics Service.
 *
 * FR8: Analytics dashboard displays redemption rates and campaign performance.
 *
 * Consumes coupon.redeemed events from Kafka and persists them for querying.
 * Exposes aggregated metrics via AnalyticsController.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsService {

    private final RedemptionEventRepository repository;
    private final ObjectMapper objectMapper;

    // ──────────────── Kafka Consumer ─────────────────────────────────────────

    @KafkaListener(topics = "coupon.redeemed", groupId = "analytics-group")
    @Transactional
    public void onCouponRedeemed(String message) {
        log.info("[Analytics] Received redemption event: {}", message);
        try {
            JsonNode node = objectMapper.readTree(message);

            RedemptionEvent event = RedemptionEvent.builder()
                    .couponId(node.has("couponId") ? node.get("couponId").asLong() : null)
                    .couponCode(node.get("code").asText())
                    .userId(node.get("userId").asText())
                    .cartTotal(new BigDecimal(node.get("cartTotal").asText()))
                    .discountAmount(new BigDecimal(node.get("discountAmount").asText()))
                    .redeemedAt(LocalDateTime.now())
                    .build();

            repository.save(event);
            log.info("[Analytics] Saved redemption for coupon: {}", event.getCouponCode());

        } catch (Exception e) {
            log.error("[Analytics] Failed to parse redemption event: {} — {}", message, e.getMessage());
        }
    }

    // ──────────────── Dashboard Metrics ──────────────────────────────────────

    /**
     * Summary metrics for a given date range.
     * Used by GET /api/analytics/summary
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(LocalDateTime from, LocalDateTime to) {
        long totalRedemptions = repository.countBetween(from, to);
        BigDecimal totalDiscount = repository.totalDiscountBetween(from, to);
        if (totalDiscount == null) totalDiscount = BigDecimal.ZERO;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("period_from", from.toString());
        summary.put("period_to", to.toString());
        summary.put("total_redemptions", totalRedemptions);
        summary.put("total_discount_granted", totalDiscount);
        summary.put("avg_discount_per_redemption",
                totalRedemptions > 0
                        ? totalDiscount.divide(BigDecimal.valueOf(totalRedemptions), 2, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO);
        return summary;
    }

    /**
     * Top performing coupons by redemption count.
     * Used by GET /api/analytics/top-coupons
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopCoupons(LocalDateTime from, LocalDateTime to) {
        List<Object[]> rows = repository.topCouponsBetween(from, to);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("coupon_code", row[0]);
            entry.put("redemption_count", row[1]);
            entry.put("total_discount", row[2]);
            result.add(entry);
        }
        return result;
    }
}
