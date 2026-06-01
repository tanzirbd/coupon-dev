package com.couponplatform.validationservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ValidationDtos {

    // ---------------- Shared CouponData (mirrored from Coupon Service) ----------------
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CouponData {
        private Long id;
        private String code;
        private String type;
        private BigDecimal discountValue;
        private BigDecimal minCartValue;
        private BigDecimal maxDiscountCap;
        private Integer usageLimit;
        private Integer usageCount;
        private Integer perUserLimit;
        private LocalDateTime startDate;
        private LocalDateTime expiryDate;
        private String status;
    }

    // ---------------- Request ------------------------------------------------
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidateRequest {
        @NotBlank(message = "Coupon code is required")
        private String couponCode;

        @NotNull(message = "Cart total is required")
        @DecimalMin(value = "0.01", message = "Cart total must be positive")
        private BigDecimal cartTotal;

        @NotBlank(message = "User ID is required")
        private String userId;
    }

    // ---------------- Response ------------------------------------------------
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidateResponse {
        private boolean valid;
        private String couponCode;
        private String message;
        private BigDecimal discountAmount;
        private BigDecimal finalCartTotal;
        private String couponType;
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
