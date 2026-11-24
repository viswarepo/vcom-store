package com.shop.order.event;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;

/** One entry in an order's timeline: placed, confirmed, packed, shipped, in-transit update, delivered, cancelled. */
@Embeddable
public class OrderEvent {
  private Instant occurredAt = Instant.now();
  private String status;
  @Column(length = 300)
  private String message;

  protected OrderEvent() {}
  public OrderEvent(String status, String message) { this.status = status; this.message = message; }

  public Instant getOccurredAt() { return occurredAt; }
  public String getStatus() { return status; }
  public String getMessage() { return message; }
}
