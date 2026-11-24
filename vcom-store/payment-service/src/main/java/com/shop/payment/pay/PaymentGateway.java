package com.shop.payment;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Seam for a real provider. Implement this with Razorpay or Stripe (create a charge/order,
 * verify the signature or webhook) and replace MockPaymentGateway.
 */
public interface PaymentGateway {
  record Result(boolean success, String transactionId, String failureReason) {}

  Result charge(UUID orderId, BigDecimal amount, String method);
}
