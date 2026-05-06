package com.couponplatform.couponservice.repository;

import com.couponplatform.couponservice.model.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    Page<Coupon> findByStatus(Coupon.CouponStatus status, Pageable pageable);

    Page<Coupon> findByType(Coupon.CouponType type, Pageable pageable);

    Page<Coupon> findByCreatedBy(String createdBy, Pageable pageable);

    @Query("SELECT c FROM Coupon c WHERE c.status = 'ACTIVE' AND " +
           "(c.expiryDate IS NULL OR c.expiryDate > :now) AND " +
           "(c.usageLimit IS NULL OR c.usageCount < c.usageLimit)")
    Page<Coupon> findActiveCoupons(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT c FROM Coupon c WHERE c.expiryDate < :now AND c.status = 'ACTIVE'")
    List<Coupon> findExpiredActiveCoupons(@Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Coupon c SET c.usageCount = c.usageCount + 1 WHERE c.id = :id")
    int incrementUsageCount(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Coupon c SET c.status = 'EXHAUSTED' WHERE c.id = :id AND c.usageCount >= c.usageLimit")
    int markAsExhaustedIfLimitReached(@Param("id") Long id);
}
