package com.couponplatform.couponservice.service;

import com.couponplatform.couponservice.dto.CouponDtos.*;
import com.couponplatform.couponservice.model.Coupon;
import com.couponplatform.couponservice.repository.CouponRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CouponService.
 * Core Functionality #2: Coupon CRUD and template management (FR1, FR6)
 */
@ExtendWith(MockitoExtension.class)
class CouponServiceTest {

    @Mock private CouponRepository couponRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    @InjectMocks private CouponService couponService;

    private CreateCouponRequest createRequest;
    private Coupon activeCoupon;

    @BeforeEach
    void setUp() {
        createRequest = CreateCouponRequest.builder()
                .code("SUMMER25")
                .description("25% off summer sale")
                .type(Coupon.CouponType.PERCENTAGE)
                .discountValue(new BigDecimal("25.00"))
                .minCartValue(new BigDecimal("100.00"))
                .maxDiscountCap(new BigDecimal("50.00"))
                .usageLimit(1000)
                .perUserLimit(1)
                .expiryDate(LocalDateTime.now().plusDays(30))
                .build();

        activeCoupon = Coupon.builder()
                .id(1L)
                .code("SUMMER25")
                .description("25% off summer sale")
                .type(Coupon.CouponType.PERCENTAGE)
                .discountValue(new BigDecimal("25.00"))
                .minCartValue(new BigDecimal("100.00"))
                .maxDiscountCap(new BigDecimal("50.00"))
                .usageLimit(1000)
                .usageCount(5)
                .perUserLimit(1)
                .status(Coupon.CouponStatus.ACTIVE)
                .createdBy("admin")
                .build();
    }

    // ──────────────── Create Tests ────────────────────────────────────────────

    @Test
    @DisplayName("Create coupon - success publishes Kafka event")
    void createCoupon_success_publishesKafkaEvent() {
        when(couponRepository.existsByCode("SUMMER25")).thenReturn(false);
        when(couponRepository.save(any(Coupon.class))).thenReturn(activeCoupon);

        CouponResponse response = couponService.createCoupon(createRequest, "admin");

        assertThat(response.getCode()).isEqualTo("SUMMER25");
        assertThat(response.getType()).isEqualTo(Coupon.CouponType.PERCENTAGE);
        assertThat(response.getStatus()).isEqualTo(Coupon.CouponStatus.ACTIVE);
        assertThat(response.getCreatedBy()).isEqualTo("admin");

        verify(kafkaTemplate).send(eq("coupon.created"), anyString());
    }

    @Test
    @DisplayName("Create coupon - duplicate code throws IllegalArgumentException")
    void createCoupon_duplicateCode_throwsException() {
        when(couponRepository.existsByCode("SUMMER25")).thenReturn(true);

        assertThatThrownBy(() -> couponService.createCoupon(createRequest, "admin"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUMMER25")
                .hasMessageContaining("already exists");

        verify(couponRepository, never()).save(any());
    }

    // ──────────────── Read Tests ──────────────────────────────────────────────

    @Test
    @DisplayName("Get coupon by code - existing code returns response")
    void getCouponByCode_existing_returnsResponse() {
        when(couponRepository.findByCode("SUMMER25")).thenReturn(Optional.of(activeCoupon));

        CouponResponse response = couponService.getCouponByCode("SUMMER25");

        assertThat(response.getCode()).isEqualTo("SUMMER25");
        assertThat(response.getDiscountValue()).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("Get coupon by code - normalises to uppercase")
    void getCouponByCode_lowercaseInput_normalisedToUppercase() {
        when(couponRepository.findByCode("SUMMER25")).thenReturn(Optional.of(activeCoupon));

        couponService.getCouponByCode("summer25");

        verify(couponRepository).findByCode("SUMMER25");
    }

    @Test
    @DisplayName("Get coupon by id - not found throws EntityNotFoundException")
    void getCouponById_notFound_throwsException() {
        when(couponRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> couponService.getCouponById(999L))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    @DisplayName("Get active coupons - returns paginated list")
    void getActiveCoupons_returnsPaginatedResult() {
        var page = new PageImpl<>(List.of(activeCoupon));
        when(couponRepository.findActiveCoupons(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(page);

        PagedCouponResponse result = couponService.getActiveCoupons(0, 10);

        assertThat(result.getCoupons()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getCoupons().get(0).getStatus()).isEqualTo(Coupon.CouponStatus.ACTIVE);
    }

    // ──────────────── Update Tests ────────────────────────────────────────────

    @Test
    @DisplayName("Update coupon - partial update applies only non-null fields")
    void updateCoupon_partialUpdate_onlyChangesSpecifiedFields() {
        UpdateCouponRequest updateReq = UpdateCouponRequest.builder()
                .status(Coupon.CouponStatus.INACTIVE)
                .build();   // only status changed

        when(couponRepository.findById(1L)).thenReturn(Optional.of(activeCoupon));
        when(couponRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CouponResponse response = couponService.updateCoupon(1L, updateReq);

        assertThat(response.getStatus()).isEqualTo(Coupon.CouponStatus.INACTIVE);
        assertThat(response.getDiscountValue()).isEqualByComparingTo("25.00"); // unchanged
        verify(kafkaTemplate).send(eq("coupon.updated"), anyString());
    }

    // ──────────────── Delete Tests ────────────────────────────────────────────

    @Test
    @DisplayName("Delete coupon - publishes Kafka deleted event")
    void deleteCoupon_success_publishesEvent() {
        when(couponRepository.findById(1L)).thenReturn(Optional.of(activeCoupon));

        couponService.deleteCoupon(1L);

        verify(couponRepository).delete(activeCoupon);
        verify(kafkaTemplate).send(eq("coupon.deleted"), anyString());
    }
}
