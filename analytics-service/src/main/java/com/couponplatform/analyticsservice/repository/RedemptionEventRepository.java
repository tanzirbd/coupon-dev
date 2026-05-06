package com.couponplatform.analyticsservice.repository;

import com.couponplatform.analyticsservice.model.RedemptionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RedemptionEventRepository extends JpaRepository<RedemptionEvent, Long> {

    long countByCouponCode(String couponCode);

    @Query("SELECT SUM(r.discountAmount) FROM RedemptionEvent r WHERE r.couponCode = :code")
    BigDecimal totalDiscountByCoupon(@Param("code") String couponCode);

    @Query("SELECT r.couponCode, COUNT(r), SUM(r.discountAmount) " +
           "FROM RedemptionEvent r " +
           "WHERE r.redeemedAt BETWEEN :from AND :to " +
           "GROUP BY r.couponCode ORDER BY COUNT(r) DESC")
    List<Object[]> topCouponsBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(r) FROM RedemptionEvent r WHERE r.redeemedAt BETWEEN :from AND :to")
    long countBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT SUM(r.discountAmount) FROM RedemptionEvent r WHERE r.redeemedAt BETWEEN :from AND :to")
    BigDecimal totalDiscountBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
