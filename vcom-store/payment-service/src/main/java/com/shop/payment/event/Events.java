package com.shop.payment.event;

import java.math.BigDecimal;
import java.util.UUID;

public final class Events {
  private Events() {}

  public static final String INVENTORY_RESERVED = "inventory.reserved";
  public static final String PAYMENT_COMPLETED = "payment.completed";
  public static final String PAYMENT_FAILED = "payment.failed";

  public record InventoryReserved(UUID orderId, UUID customerId, UUID merchantId, BigDecimal total, String paymentMethod) {}

  public record MerchantActivated(UUID merchantId) {}
  public record MerchantDeactivated(UUID merchantId) {}
  public static final String MERCHANT_DEACTIVATED = "merchant.deactivated";
  public static final String MERCHANT_ACTIVATED = "merchant.activated";

  public sealed interface Outcome permits PaymentCompleted, PaymentFailed {}
  public record PaymentCompleted(UUID orderId, String paymentId, String txnId) implements Outcome {}
  public record PaymentFailed(UUID orderId, String reason) implements Outcome {}
}
