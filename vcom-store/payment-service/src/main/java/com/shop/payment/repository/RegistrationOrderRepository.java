package com.shop.payment.repository;

import java.util.Optional;

import com.shop.payment.domain.RegistrationOrder;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationOrderRepository extends JpaRepository<RegistrationOrder, String> {
  Optional<RegistrationOrder> findFirstByMerchantIdAndStatusOrderByCreatedAtDesc(java.util.UUID merchantId, String status);
}
