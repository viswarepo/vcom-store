package com.shop.payment.repository;

import java.util.Optional;
import java.util.UUID;

import com.shop.payment.domain.SubscriptionPayment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, String> {
  java.util.List<SubscriptionPayment> findByStatus(String status);
  Optional<SubscriptionPayment> findFirstByMerchantIdAndPlanIdAndStatusOrderByCreatedAtDesc(UUID merchantId, UUID planId, String status);
}
