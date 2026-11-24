package com.shop.payment.repository;

import java.util.List;

import com.shop.payment.domain.Payment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
  Optional<Payment> findByOrderId(UUID orderId);
  List<Payment> findByStatus(Payment.Status status);
  Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

  @Query("select p from Payment p where p.status = :status and p.merchantId = :merchantId "
      + "and coalesce(p.paidAt, p.createdAt) >= :from and coalesce(p.paidAt, p.createdAt) < :to")
  List<Payment> findPaidForMerchant(@Param("status") Payment.Status status, @Param("merchantId") UUID merchantId,
                                    @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

  @Query("select p from Payment p where p.status = :status "
      + "and coalesce(p.paidAt, p.createdAt) >= :from and coalesce(p.paidAt, p.createdAt) < :to")
  List<Payment> findPaidBetween(@Param("status") Payment.Status status,
                                @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);
  List<Payment> findByStatusAndExpiresAtBefore(Payment.Status status, java.time.Instant before);
  List<Payment> findByMerchantIdAndStatus(UUID merchantId, Payment.Status status);
}
