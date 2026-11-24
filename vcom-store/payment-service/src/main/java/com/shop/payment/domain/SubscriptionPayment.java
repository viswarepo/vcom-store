package com.shop.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** One payment for one plan period: the Razorpay order while unpaid, and the revenue record once paid. */
@Entity
@Table(name = "subscription_payments")
public class SubscriptionPayment {
  @Id
  private String razorpayOrderId;
  @Column(nullable = false)
  private UUID merchantId;
  @Column(nullable = false)
  private UUID planId;
  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;
  @Column(nullable = false)
  private String status = "CREATED";   // CREATED -> PAID
  private String razorpayPaymentId;
  private Instant createdAt = Instant.now();
  private Instant paidAt;

  protected SubscriptionPayment() {}
  public SubscriptionPayment(String razorpayOrderId, UUID merchantId, UUID planId, BigDecimal amount) {
    this.razorpayOrderId = razorpayOrderId; this.merchantId = merchantId; this.planId = planId; this.amount = amount;
  }

  public String getRazorpayOrderId() { return razorpayOrderId; }
  public UUID getMerchantId() { return merchantId; }
  public UUID getPlanId() { return planId; }
  public BigDecimal getAmount() { return amount; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getPaidAt() { return paidAt; }
  public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
  public Instant getCreatedAt() { return createdAt; }
  public String getRazorpayPaymentId() { return razorpayPaymentId; }
  public void setRazorpayPaymentId(String id) { this.razorpayPaymentId = id; }
}
