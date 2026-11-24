package com.shop.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {
  public enum Status { PENDING, SUCCEEDED, FAILED }

  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(unique = true, nullable = false)   // one payment per order = idempotency
  private UUID orderId;
  @Column(nullable = false)
  private UUID customerId;
  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;
  private UUID merchantId;                 // the tenant who made the sale
  @Column(precision = 5, scale = 2)
  private BigDecimal commissionPercent;    // rate in force when the payment was taken
  @Column(precision = 12, scale = 2)
  private BigDecimal platformFee;          // owner's share
  @Column(precision = 12, scale = 2)
  private BigDecimal merchantAmount;       // tenant's share
  private String method;
  @Enumerated(EnumType.STRING)
  private Status status;
  private String transactionId;
  @Column(unique = true)
  private String razorpayOrderId;      // set while paying online through Razorpay
  private String razorpayPaymentId;
  private Instant expiresAt;
  private Instant paidAt;              // when the sale was completed; reports group by this           // an unpaid online order is cancelled after this
  private String failureReason;
  private Instant createdAt = Instant.now();

  public UUID getId() { return id; }
  public UUID getOrderId() { return orderId; }
  public void setOrderId(UUID orderId) { this.orderId = orderId; }
  public UUID getCustomerId() { return customerId; }
  public void setCustomerId(UUID customerId) { this.customerId = customerId; }
  public BigDecimal getAmount() { return amount; }
  public void setAmount(BigDecimal amount) { this.amount = amount; }
  public UUID getMerchantId() { return merchantId; }
  public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
  public BigDecimal getCommissionPercent() { return commissionPercent; }
  public void setCommissionPercent(BigDecimal commissionPercent) { this.commissionPercent = commissionPercent; }
  public BigDecimal getPlatformFee() { return platformFee; }
  public void setPlatformFee(BigDecimal platformFee) { this.platformFee = platformFee; }
  public BigDecimal getMerchantAmount() { return merchantAmount; }
  public void setMerchantAmount(BigDecimal merchantAmount) { this.merchantAmount = merchantAmount; }
  public String getMethod() { return method; }
  public void setMethod(String method) { this.method = method; }
  public Status getStatus() { return status; }
  public void setStatus(Status status) { this.status = status; }
  public String getTransactionId() { return transactionId; }
  public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
  public String getRazorpayOrderId() { return razorpayOrderId; }
  public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
  public String getRazorpayPaymentId() { return razorpayPaymentId; }
  public void setRazorpayPaymentId(String razorpayPaymentId) { this.razorpayPaymentId = razorpayPaymentId; }
  public Instant getPaidAt() { return paidAt; }
  public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
  public Instant getExpiresAt() { return expiresAt; }
  public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
  public String getFailureReason() { return failureReason; }
  public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
  public Instant getCreatedAt() { return createdAt; }
}
