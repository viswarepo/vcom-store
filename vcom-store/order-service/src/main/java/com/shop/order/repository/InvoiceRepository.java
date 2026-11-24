package com.shop.order.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shop.order.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
  Optional<Invoice> findByOrderId(UUID orderId);
  List<Invoice> findByMerchantIdOrderByIssuedAtDesc(UUID merchantId);
}
