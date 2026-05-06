package com.couponplatform.validationservice.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Feign fallback for CouponServiceClient.
 *
 * Called automatically by Spring Cloud OpenFeign when the Coupon Service:
 *   - is unreachable (connection refused / timeout)
 *   - returns a 5xx error
 *   - trips the Resilience4j circuit breaker
 *
 * Must implement CouponServiceClient so every method has a safe default.
 * NFR: Resilience — validation service degrades gracefully instead of throwing.
 */
@Component
@Slf4j
public class CouponServiceClientFallback implements CouponServiceClient {

    @Override
    public CouponServiceClient.CouponDataWrapper getCouponByCode(String code) {
        log.warn("[Fallback] Coupon Service unavailable when fetching code: {}. " +
                "Returning failure wrapper so ValidationEngine rejects the request gracefully.", code);

        // Return a failure wrapper — ValidationEngine treats null data as "coupon not found"
        // and returns a clean rejection response to the user rather than a 500 error.
        return new CouponServiceClient.CouponDataWrapper(
                false,
                "Coupon Service is temporarily unavailable. Please try again shortly.",
                null
        );
    }
}
