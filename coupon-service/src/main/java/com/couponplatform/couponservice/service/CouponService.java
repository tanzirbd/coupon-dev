package com.couponplatform.couponservice.service;

import com.couponplatform.couponservice.dto.CouponDtos.*;
import com.couponplatform.couponservice.model.Coupon;
import com.couponplatform.couponservice.repository.CouponRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponService {

    private final CouponRepository couponRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    private static final String TOPIC_COUPON_CREATED  = "coupon.created";
    private static final String TOPIC_COUPON_UPDATED  = "coupon.updated";
    private static final String TOPIC_COUPON_DELETED  = "coupon.deleted";

    // ──────────────── Create ────────────────────────────────────────────────

    /**
     * FR1: Admin can create a coupon template with type, discount value, and constraints.
     */
    @Transactional
    public CouponResponse createCoupon(CreateCouponRequest request, String createdBy) {
        if (couponRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("Coupon code '" + request.getCode() + "' already exists");
        }

        Coupon coupon = Coupon.builder()
                .code(request.getCode().toUpperCase())
                .description(request.getDescription())
                .type(request.getType())
                .discountValue(request.getDiscountValue())
                .minCartValue(request.getMinCartValue())
                .maxDiscountCap(request.getMaxDiscountCap())
                .usageLimit(request.getUsageLimit())
                .perUserLimit(request.getPerUserLimit())
                .startDate(request.getStartDate())
                .expiryDate(request.getExpiryDate())
                .createdBy(createdBy)
                .status(Coupon.CouponStatus.ACTIVE)
                .build();

        Coupon saved = couponRepository.save(coupon);
        log.info("Coupon created: {} by {}", saved.getCode(), createdBy);

        // Publish event to Kafka → Distribution and Analytics services
        publishEvent(TOPIC_COUPON_CREATED,
                String.format("{\"id\":%d,\"code\":\"%s\",\"type\":\"%s\",\"createdBy\":\"%s\"}",
                        saved.getId(), saved.getCode(), saved.getType(), createdBy));

        return toResponse(saved);
    }

    // ──────────────── Read ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CouponResponse getCouponById(Long id) {
        return couponRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Coupon not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public CouponResponse getCouponByCode(String code) {
        return couponRepository.findByCode(code.toUpperCase())
                .map(this::toResponse)
                .orElseThrow(() -> new EntityNotFoundException("Coupon not found with code: " + code));
    }

    @Transactional(readOnly = true)
    public PagedCouponResponse getAllCoupons(int page, int size, String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));
        Page<Coupon> couponPage = couponRepository.findAll(pageable);
        return toPagedResponse(couponPage);
    }

    @Transactional(readOnly = true)
    public PagedCouponResponse getActiveCoupons(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "expiryDate"));
        Page<Coupon> couponPage = couponRepository.findActiveCoupons(LocalDateTime.now(), pageable);
        return toPagedResponse(couponPage);
    }

    @Transactional(readOnly = true)
    public PagedCouponResponse getCouponsByStatus(Coupon.CouponStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return toPagedResponse(couponRepository.findByStatus(status, pageable));
    }

    // ──────────────── Update ────────────────────────────────────────────────

    /**
     * Partial update — only non-null fields are applied.
     */
    @Transactional
    public CouponResponse updateCoupon(Long id, UpdateCouponRequest request) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Coupon not found with id: " + id));

        if (request.getDescription() != null)   coupon.setDescription(request.getDescription());
        if (request.getStatus() != null)         coupon.setStatus(request.getStatus());
        if (request.getDiscountValue() != null)  coupon.setDiscountValue(request.getDiscountValue());
        if (request.getMinCartValue() != null)   coupon.setMinCartValue(request.getMinCartValue());
        if (request.getMaxDiscountCap() != null) coupon.setMaxDiscountCap(request.getMaxDiscountCap());
        if (request.getUsageLimit() != null)     coupon.setUsageLimit(request.getUsageLimit());
        if (request.getPerUserLimit() != null)   coupon.setPerUserLimit(request.getPerUserLimit());
        if (request.getExpiryDate() != null)     coupon.setExpiryDate(request.getExpiryDate());

        Coupon updated = couponRepository.save(coupon);
        log.info("Coupon updated: {}", updated.getCode());

        publishEvent(TOPIC_COUPON_UPDATED,
                String.format("{\"id\":%d,\"code\":\"%s\",\"status\":\"%s\"}",
                        updated.getId(), updated.getCode(), updated.getStatus()));

        return toResponse(updated);
    }

    // ──────────────── Delete ────────────────────────────────────────────────

    @Transactional
    public void deleteCoupon(Long id) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Coupon not found with id: " + id));
        couponRepository.delete(coupon);
        log.info("Coupon deleted: {}", coupon.getCode());

        publishEvent(TOPIC_COUPON_DELETED,
                String.format("{\"id\":%d,\"code\":\"%s\"}", id, coupon.getCode()));
    }

    // ──────────────── Scheduled Jobs ────────────────────────────────────────

    /**
     * Every hour: auto-expire coupons whose expiry date has passed.
     */
    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void expireOverdueCoupons() {
        List<Coupon> expiredCoupons = couponRepository.findExpiredActiveCoupons(LocalDateTime.now());
        if (!expiredCoupons.isEmpty()) {
            expiredCoupons.forEach(c -> c.setStatus(Coupon.CouponStatus.EXPIRED));
            couponRepository.saveAll(expiredCoupons);
            log.info("Auto-expired {} coupon(s)", expiredCoupons.size());
        }
    }

    // ──────────────── Helpers ────────────────────────────────────────────────

    private void publishEvent(String topic, String payload) {
        try {
            kafkaTemplate.send(topic, payload);
        } catch (Exception e) {
            log.warn("Failed to publish event to {}: {}", topic, e.getMessage());
            // Non-critical: don't fail the main transaction
        }
    }

    public CouponResponse toResponse(Coupon c) {
        return CouponResponse.builder()
                .id(c.getId())
                .code(c.getCode())
                .description(c.getDescription())
                .type(c.getType())
                .discountValue(c.getDiscountValue())
                .minCartValue(c.getMinCartValue())
                .maxDiscountCap(c.getMaxDiscountCap())
                .usageLimit(c.getUsageLimit())
                .usageCount(c.getUsageCount())
                .perUserLimit(c.getPerUserLimit())
                .startDate(c.getStartDate())
                .expiryDate(c.getExpiryDate())
                .status(c.getStatus())
                .createdBy(c.getCreatedBy())
                .createdAt(c.getCreatedAt())
                .build();
    }

    private PagedCouponResponse toPagedResponse(Page<Coupon> page) {
        return PagedCouponResponse.builder()
                .coupons(page.getContent().stream().map(this::toResponse).toList())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .build();
    }
}
