package com.shop.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A Razorpay order created for a merchant's registration fee. */
@Entity
@Table(name = "registration_orders")
public class RegistrationOrder {
  @Id
  private String razorpayOrderId;
  @Column(nullable = false)
  private UUID merchantId;
  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;
  @Column(nullable = false)
  private String status = "CREATED";   // CREATED -> PAID
  private Instant createdAt = Instant.now();

  protected RegistrationOrder() {}
  public RegistrationOrder(String razorpayOrderId, UUID merchantId, BigDecimal amount) {
    this.razorpayOrderId = razorpayOrderId; this.merchantId = merchantId; this.amount = amount;
  }

  public String getRazorpayOrderId() { return razorpayOrderId; }
  public UUID getMerchantId() { return merchantId; }
  public BigDecimal getAmount() { return amount; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
}
