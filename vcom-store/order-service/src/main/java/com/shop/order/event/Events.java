package com.shop.order.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class Events {
  private Events() {}

  public static final String ORDER_CREATED = "order.created";
  public static final String ORDER_CONFIRMED = "order.confirmed";
  public static final String ORDER_FULFILLMENT = "order.fulfillment";
  public static final String INVENTORY_RESERVED = "inventory.reserved";
  public static final String INVENTORY_FAILED = "inventory.failed";
  public static final String PAYMENT_COMPLETED = "payment.completed";
  public static final String PAYMENT_FAILED = "payment.failed";

  // published
  public record OrderCreated(UUID orderId, UUID customerId, UUID merchantId, List<Item> items, BigDecimal total, String paymentMethod) {
    public record Item(UUID productId, int quantity, BigDecimal price) {}
  }
  public record OrderConfirmed(UUID orderId, UUID customerId, BigDecimal total) {}
  /** For a future notification service: tell the customer when their order is packed, shipped or delivered. */
  public record OrderFulfillment(UUID orderId, UUID customerId, String status, String courier, String trackingNumber) {}

  // consumed
  public record InventoryReserved(UUID orderId) {}
  public record InventoryFailed(UUID orderId, String reason) {}
  public record PaymentCompleted(UUID orderId, String paymentId, String txnId) {}
  public record PaymentFailed(UUID orderId, String reason) {}
}
