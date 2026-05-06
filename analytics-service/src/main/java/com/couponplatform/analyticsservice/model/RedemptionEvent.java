package com.couponplatform.analyticsservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persisted record of every coupon redemption.
 * Powers FR8: analytics dashboard with redemption rates and campaign ROI.
 */
@Entity
@Table(name = "redemption_events", indexes = {
    @Index(name = "idx_redemption_coupon_code", columnList = "coupon_code"),
    @Index(name = "idx_redemption_timestamp",   columnList = "redeemed_at"),
    @Index(name = "idx_redemption_coupon_id",   columnList = "coupon_id")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedemptionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id")
    private Long couponId;

    @Column(name = "coupon_code", nullable = false, length = 50)
    private String couponCode;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "cart_total", precision = 12, scale = 2)
    private BigDecimal cartTotal;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "redeemed_at", nullable = false)
    @Builder.Default
    private LocalDateTime redeemedAt = LocalDateTime.now();
}
