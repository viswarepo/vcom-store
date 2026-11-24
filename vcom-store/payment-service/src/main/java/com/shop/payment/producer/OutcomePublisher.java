package com.shop.payment.producer;

import com.shop.payment.event.Events;
import com.shop.payment.event.Events.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Sends a payment result to Kafka so order and inventory services can react. */
@Component
public class OutcomePublisher {

  private final KafkaTemplate<String, Object> kafka;

  public OutcomePublisher(KafkaTemplate<String, Object> kafka) { this.kafka = kafka; }

  public void publish(Outcome outcome) {
    if (outcome == null) return;
    switch (outcome) {
      case PaymentCompleted c -> kafka.send(Events.PAYMENT_COMPLETED, c.orderId().toString(), c);
      case PaymentFailed f -> kafka.send(Events.PAYMENT_FAILED, f.orderId().toString(), f);
    }
  }
}
