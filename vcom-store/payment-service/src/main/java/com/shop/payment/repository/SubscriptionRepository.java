package com.shop.payment.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.shop.payment.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
  List<Subscription> findByStatusAndCurrentPeriodEndBefore(String status, Instant before);
}
