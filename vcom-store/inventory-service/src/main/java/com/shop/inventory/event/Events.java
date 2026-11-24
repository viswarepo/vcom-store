package com.shop.inventory.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class Events {
  private Events() {}

  public static final String ORDER_CREATED = "order.created";
  public static final String INVENTORY_RESERVED = "inventory.reserved";
  public static final String INVENTORY_FAILED = "inventory.failed";
  public static final String PAYMENT_COMPLETED = "payment.completed";
  public static final String PAYMENT_FAILED = "payment.failed";

  public record OrderCreated(UUID orderId, UUID customerId, UUID merchantId, List<Item> items, BigDecimal total, String paymentMethod) {
    public record Item(UUID productId, int quantity, BigDecimal price) {}
  }

  public record PaymentRef(UUID orderId) {}

  /** Result of trying to reserve stock; the compiler checks every case is handled. */
  public sealed interface Outcome permits InventoryReserved, InventoryFailed {}
  public record InventoryReserved(UUID orderId, UUID customerId, UUID merchantId, BigDecimal total, String paymentMethod) implements Outcome {}
  public record InventoryFailed(UUID orderId, String reason) implements Outcome {}
}
