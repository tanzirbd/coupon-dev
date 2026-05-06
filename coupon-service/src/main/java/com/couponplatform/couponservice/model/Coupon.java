package com.couponplatform.couponservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Core domain entity for coupon templates.
 * Each coupon defines its type, discount rules, constraints, and lifecycle.
 * Aligns with FR1: Admin can create a coupon template with type, discount, and constraints.
 */
@Entity
@Table(name = "coupons", indexes = {
    @Index(name = "idx_coupon_code", columnList = "code", unique = true),
    @Index(name = "idx_coupon_status", columnList = "status"),
    @Index(name = "idx_coupon_expiry", columnList = "expiry_date")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponType type;

    /**
     * Discount value:
     * - For PERCENTAGE: 0–100 (e.g., 20.00 = 20% off)
     * - For FIXED_AMOUNT: absolute currency amount
     * - For FREE_SHIPPING: ignored / 0
     */
    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "min_cart_value", precision = 10, scale = 2)
    @Builder.Default
    private BigDecimal minCartValue = BigDecimal.ZERO;

    @Column(name = "max_discount_cap", precision = 10, scale = 2)
    private BigDecimal maxDiscountCap;  // cap for PERCENTAGE coupons

    @Column(name = "usage_limit")
    private Integer usageLimit;         // null = unlimited

    @Column(name = "usage_count", nullable = false)
    @Builder.Default
    private Integer usageCount = 0;

    @Column(name = "per_user_limit")
    @Builder.Default
    private Integer perUserLimit = 1;

    @Column(name = "start_date")
    private LocalDateTime startDate;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CouponStatus status = CouponStatus.ACTIVE;

    @Column(name = "created_by", nullable = false, length = 50)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ─── Enums ────────────────────────────────────────────────────────────────

    public enum CouponType {
        PERCENTAGE,       // % off the cart total
        FIXED_AMOUNT,     // flat currency discount
        FREE_SHIPPING,    // waive shipping charges
        BUY_X_GET_Y       // complex promotional rule
    }

    public enum CouponStatus {
        ACTIVE,
        INACTIVE,
        EXPIRED,
        EXHAUSTED         // usage_limit reached
    }
}
