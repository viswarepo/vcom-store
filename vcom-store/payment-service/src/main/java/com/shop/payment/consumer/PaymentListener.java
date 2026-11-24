package com.shop.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.payment.service.PaymentService;
import com.shop.payment.event.Events;
import com.shop.payment.event.Events.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentListener {

  private final ObjectMapper mapper;
  private final PaymentService service;
  private final KafkaTemplate<String, Object> kafka;

  public PaymentListener(ObjectMapper mapper, PaymentService service, KafkaTemplate<String, Object> kafka) {
    this.mapper = mapper;
    this.service = service;
    this.kafka = kafka;
  }

  @KafkaListener(topics = Events.INVENTORY_RESERVED)
  void onStockReserved(String payload) throws Exception {
    InventoryReserved e = mapper.readValue(payload, InventoryReserved.class);
    Outcome outcome = service.process(e);
    if (outcome == null) return;
    String key = e.orderId().toString();
    switch (outcome) {
      case PaymentCompleted c -> kafka.send(Events.PAYMENT_COMPLETED, key, c);
      case PaymentFailed f -> kafka.send(Events.PAYMENT_FAILED, key, f);
    }
  }
}
