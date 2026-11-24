package com.shop.store.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

import com.shop.store.repository.StoreRepository;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** payment-service opens a store when a plan is paid and closes it when the paid period ends. */
@Component
public class ActivationListener {

  record MerchantEvent(UUID merchantId) {}

  private final ObjectMapper mapper;
  private final StoreRepository stores;

  public ActivationListener(ObjectMapper mapper, StoreRepository stores) {
    this.mapper = mapper;
    this.stores = stores;
  }

  @KafkaListener(topics = "merchant.activated", groupId = "store-service")
  void onActivated(String payload) throws Exception {
    setActive(mapper.readValue(payload, MerchantEvent.class).merchantId(), true);
  }

  @KafkaListener(topics = "merchant.deactivated", groupId = "store-service")
  void onDeactivated(String payload) throws Exception {
    setActive(mapper.readValue(payload, MerchantEvent.class).merchantId(), false);
  }

  private void setActive(UUID merchantId, boolean active) {
    stores.findByOwnerId(merchantId).ifPresent(s -> {
      s.setActive(active);
      stores.save(s);
    });
  }
}
