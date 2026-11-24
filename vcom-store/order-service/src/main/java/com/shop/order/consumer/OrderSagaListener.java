package com.shop.order.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.order.service.OrderService;
import com.shop.order.event.Events;
import com.shop.order.event.Events.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Moves the order through its states as the other services report back. */
@Component
public class OrderSagaListener {

  private final ObjectMapper mapper;
  private final OrderService service;
  private final KafkaTemplate<String, Object> kafka;

  public OrderSagaListener(ObjectMapper mapper, OrderService service, KafkaTemplate<String, Object> kafka) {
    this.mapper = mapper;
    this.service = service;
    this.kafka = kafka;
  }

  @KafkaListener(topics = Events.INVENTORY_RESERVED)
  void onReserved(String payload) throws Exception {
    service.markReserved(mapper.readValue(payload, InventoryReserved.class).orderId());
  }

  @KafkaListener(topics = Events.INVENTORY_FAILED)
  void onInventoryFailed(String payload) throws Exception {
    InventoryFailed e = mapper.readValue(payload, InventoryFailed.class);
    service.cancel(e.orderId(), e.reason());
  }

  @KafkaListener(topics = Events.PAYMENT_COMPLETED)
  void onPaid(String payload) throws Exception {
    PaymentCompleted e = mapper.readValue(payload, PaymentCompleted.class);
    OrderConfirmed confirmed = service.confirm(e.orderId());
    if (confirmed != null) kafka.send(Events.ORDER_CONFIRMED, e.orderId().toString(), confirmed);
  }

  @KafkaListener(topics = Events.PAYMENT_FAILED)
  void onPaymentFailed(String payload) throws Exception {
    PaymentFailed e = mapper.readValue(payload, PaymentFailed.class);
    service.cancel(e.orderId(), e.reason());
  }
}
