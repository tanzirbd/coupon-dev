package com.couponplatform.couponservice.dto;

import com.couponplatform.couponservice.model.Coupon;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CouponDtos {

    // ──────────────── Request DTOs ────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateCouponRequest {

        @NotBlank(message = "Coupon code is required")
        @Size(min = 3, max = 50, message = "Code must be 3–50 characters")
        @Pattern(regexp = "^[A-Z0-9_-]+$", message = "Code must be uppercase alphanumeric with - or _")
        private String code;

        @NotBlank(message = "Description is required")
        @Size(max = 200)
        private String description;

        @NotNull(message = "Coupon type is required")
        private Coupon.CouponType type;

        @NotNull(message = "Discount value is required")
        @DecimalMin(value = "0.01", message = "Discount value must be positive")
        @DecimalMax(value = "100.00", message = "Percentage discount cannot exceed 100")
        private BigDecimal discountValue;

        @DecimalMin(value = "0.00")
        @Builder.Default
        private BigDecimal minCartValue = BigDecimal.ZERO;

        private BigDecimal maxDiscountCap;

        @Min(value = 1, message = "Usage limit must be at least 1")
        private Integer usageLimit;        // null = unlimited

        @Min(value = 1)
        @Builder.Default
        private Integer perUserLimit = 1;

        private LocalDateTime startDate;

        @Future(message = "Expiry date must be in the future")
        private LocalDateTime expiryDate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateCouponRequest {
        @Size(max = 200)
        private String description;

        private Coupon.CouponStatus status;

        @DecimalMin("0.01")
        private BigDecimal discountValue;

        private BigDecimal minCartValue;
        private BigDecimal maxDiscountCap;
        private Integer usageLimit;
        private Integer perUserLimit;
        private LocalDateTime expiryDate;
    }

    // ──────────────── Response DTOs ────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CouponResponse {
        private Long id;
        private String code;
        private String description;
        private Coupon.CouponType type;
        private BigDecimal discountValue;
        private BigDecimal minCartValue;
        private BigDecimal maxDiscountCap;
        private Integer usageLimit;
        private Integer usageCount;
        private Integer perUserLimit;
        private LocalDateTime startDate;
        private LocalDateTime expiryDate;
        private Coupon.CouponStatus status;
        private String createdBy;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PagedCouponResponse {
        private java.util.List<CouponResponse> coupons;
        private long totalElements;
        private int totalPages;
        private int currentPage;
        private int pageSize;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        private T data;

        public static <T> ApiResponse<T> ok(String message, T data) {
            return ApiResponse.<T>builder().success(true).message(message).data(data).build();
        }

        public static <T> ApiResponse<T> error(String message) {
            return ApiResponse.<T>builder().success(false).message(message).build();
        }
    }
}
