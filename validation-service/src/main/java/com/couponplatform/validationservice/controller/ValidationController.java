package com.couponplatform.validationservice.controller;

import com.couponplatform.validationservice.dto.ValidationDtos.*;
import com.couponplatform.validationservice.service.ValidationEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Validation REST endpoint.
 * FR4: Validation service verifies code, applies rules, returns discount.
 * NFR: < 200 ms via Redis cache + stateless service design.
 */
@RestController
@RequestMapping("/api/validate")
@RequiredArgsConstructor
@Tag(name = "Coupon Validation", description = "Real-time coupon validation and discount calculation")
public class ValidationController {

    private final ValidationEngine validationEngine;

    /**
     * POST /api/validate
     * Validates a coupon code against cart context.
     * Returns discount amount or rejection reason.
     */
    @PostMapping
    @Operation(
        summary = "Validate coupon",
        description = "Checks coupon rules, applies discount, logs redemption. " +
                      "Designed for < 200ms response via Redis cache."
    )
    public ResponseEntity<ApiResponse<ValidateResponse>> validate(
            @Valid @RequestBody ValidateRequest request,
            @RequestHeader(value = "X-User-Name", required = false) String userName) {

        // Use X-User-Name from Gateway if userId not set in body
        if (request.getUserId() == null && userName != null) {
            request.setUserId(userName);
        }

        ValidateResponse result = validationEngine.validate(request);

        if (result.isValid()) {
            return ResponseEntity.ok(ApiResponse.ok("Coupon applied", result));
        } else {
            return ResponseEntity.ok(ApiResponse.error(result.getMessage()));
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<ApiResponse<String>> health() {
        return ResponseEntity.ok(ApiResponse.ok("Validation Service is running", "OK"));
    }
}
