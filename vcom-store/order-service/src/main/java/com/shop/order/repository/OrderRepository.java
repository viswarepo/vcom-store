package com.shop.order.repository;

import java.util.List;
import java.util.UUID;

import com.shop.order.domain.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {
  List<Order> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);
  List<Order> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);

  @Query("select distinct o from Order o join o.items i where i.merchantId = :merchantId order by o.createdAt desc")
  List<Order> findForMerchant(@Param("merchantId") UUID merchantId);
}
