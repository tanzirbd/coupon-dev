package com.couponplatform.validationservice.client;

import com.couponplatform.validationservice.dto.ValidationDtos.CouponData;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for synchronous REST calls to Coupon Service.
 * NFR: Circuit breaker (Resilience4j) for reliability.
 *
 * The `fallback` attribute on @FeignClient handles failures — when the
 * Coupon Service is down or times out, Spring calls CouponServiceClientFallback
 * instead. Do NOT also use @CircuitBreaker on Feign interface methods;
 * they conflict and the method-level annotation is ignored on interfaces.
 */
@FeignClient(name = "coupon-service", fallback = CouponServiceClientFallback.class)
public interface CouponServiceClient {

    @GetMapping("/api/coupons/code/{code}")
    CouponDataWrapper getCouponByCode(@PathVariable String code);

    /**
     * Wrapper to match the ApiResponse<CouponResponse> envelope returned by coupon-service.
     */
    record CouponDataWrapper(boolean success, String message, CouponData data) {}
}
