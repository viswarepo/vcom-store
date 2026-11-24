package com.shop.inventory.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.inventory.event.Events;
import com.shop.inventory.event.Events.*;
import com.shop.inventory.service.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class InventoryListener {

  private final ObjectMapper mapper;
  private final InventoryService service;
  private final KafkaTemplate<String, Object> kafka;

  public InventoryListener(ObjectMapper mapper, InventoryService service, KafkaTemplate<String, Object> kafka) {
    this.mapper = mapper;
    this.service = service;
    this.kafka = kafka;
  }

  @KafkaListener(topics = Events.ORDER_CREATED)
  void onOrderCreated(String payload) throws Exception {
    OrderCreated order = mapper.readValue(payload, OrderCreated.class);
    Outcome outcome = service.reserve(order);
    if (outcome == null) return;
    String key = order.orderId().toString();
    switch (outcome) {
      case InventoryReserved r -> kafka.send(Events.INVENTORY_RESERVED, key, r);
      case InventoryFailed f -> kafka.send(Events.INVENTORY_FAILED, key, f);
    }
  }

  @KafkaListener(topics = Events.PAYMENT_FAILED)
  void onPaymentFailed(String payload) throws Exception {
    service.release(mapper.readValue(payload, PaymentRef.class).orderId());
  }

  @KafkaListener(topics = Events.PAYMENT_COMPLETED)
  void onPaymentCompleted(String payload) throws Exception {
    service.commit(mapper.readValue(payload, PaymentRef.class).orderId());
  }
}
