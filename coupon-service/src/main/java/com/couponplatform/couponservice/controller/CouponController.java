package com.couponplatform.couponservice.controller;

import com.couponplatform.couponservice.dto.CouponDtos.*;
import com.couponplatform.couponservice.model.Coupon;
import com.couponplatform.couponservice.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Coupon CRUD operations.
 * Core Functionality #2: Coupon Template Management (FR1, FR2, FR6).
 *
 * All endpoints receive X-User-Name and X-User-Role headers injected
 * by the API Gateway after JWT validation.
 */
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
@Tag(name = "Coupon Management", description = "Create, read, update, and delete coupon templates")
@SecurityRequirement(name = "bearerAuth")
public class CouponController {

    private final CouponService couponService;

    /**
     * POST /api/coupons
     * FR1: Admin creates a coupon template.
     */
    @PostMapping
    @Operation(summary = "Create coupon", description = "Admin creates a new coupon template")
    public ResponseEntity<ApiResponse<CouponResponse>> createCoupon(
            @Valid @RequestBody CreateCouponRequest request,
            @RequestHeader("X-User-Name") String createdBy,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {

        if (!role.equals("ADMIN") && !role.equals("MARKETING")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only ADMIN or MARKETING users can create coupons"));
        }

        CouponResponse response = couponService.createCoupon(request, createdBy);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Coupon created successfully", response));
    }

    /**
     * GET /api/coupons/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get coupon by ID")
    public ResponseEntity<ApiResponse<CouponResponse>> getCouponById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("Success", couponService.getCouponById(id)));
    }

    /**
     * GET /api/coupons/code/{code}
     * Used by Validation Service to look up coupon rules by code.
     */
    @GetMapping("/code/{code}")
    @Operation(summary = "Get coupon by code", description = "Look up coupon rules by code (used by Validation Service)")
    public ResponseEntity<ApiResponse<CouponResponse>> getCouponByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok("Success", couponService.getCouponByCode(code)));
    }

    /**
     * GET /api/coupons?page=0&size=10&sortBy=createdAt
     */
    @GetMapping
    @Operation(summary = "List all coupons (paginated)")
    public ResponseEntity<ApiResponse<PagedCouponResponse>> getAllCoupons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy) {
        return ResponseEntity.ok(ApiResponse.ok("Success",
                couponService.getAllCoupons(page, size, sortBy)));
    }

    /**
     * GET /api/coupons/active
     * FR6: Users can view their available (active) coupons.
     */
    @GetMapping("/active")
    @Operation(summary = "List active coupons", description = "Returns non-expired, non-exhausted coupons")
    public ResponseEntity<ApiResponse<PagedCouponResponse>> getActiveCoupons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Success",
                couponService.getActiveCoupons(page, size)));
    }

    /**
     * GET /api/coupons/status/{status}
     */
    @GetMapping("/status/{status}")
    @Operation(summary = "List coupons by status")
    public ResponseEntity<ApiResponse<PagedCouponResponse>> getCouponsByStatus(
            @PathVariable Coupon.CouponStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok("Success",
                couponService.getCouponsByStatus(status, page, size)));
    }

    /**
     * PATCH /api/coupons/{id}
     * Partial update — only send fields you want to change.
     */
    @PatchMapping("/{id}")
    @Operation(summary = "Update coupon", description = "Partial update — only non-null fields are modified")
    public ResponseEntity<ApiResponse<CouponResponse>> updateCoupon(
            @PathVariable Long id,
            @RequestBody UpdateCouponRequest request,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {

        if (!role.equals("ADMIN") && !role.equals("MARKETING")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only ADMIN or MARKETING users can update coupons"));
        }

        return ResponseEntity.ok(ApiResponse.ok("Coupon updated", couponService.updateCoupon(id, request)));
    }

    /**
     * DELETE /api/coupons/{id}
     * Admin only.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete coupon", description = "Hard delete — admin only")
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", defaultValue = "USER") String role) {

        if (!role.equals("ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Only ADMIN can delete coupons"));
        }

        couponService.deleteCoupon(id);
        return ResponseEntity.ok(ApiResponse.ok("Coupon deleted successfully", null));
    }
}
