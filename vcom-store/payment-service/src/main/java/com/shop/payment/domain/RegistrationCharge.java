package com.shop.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** What a merchant paid the platform owner to open their store. One per merchant. */
@Entity
@Table(name = "registration_charges")
public class RegistrationCharge {
  @Id
  private UUID merchantId;
  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;
  private String transactionId;
  private Instant paidAt = Instant.now();

  protected RegistrationCharge() {}
  public RegistrationCharge(UUID merchantId, BigDecimal amount, String transactionId) {
    this.merchantId = merchantId; this.amount = amount; this.transactionId = transactionId;
  }

  public UUID getMerchantId() { return merchantId; }
  public BigDecimal getAmount() { return amount; }
  public String getTransactionId() { return transactionId; }
  public Instant getPaidAt() { return paidAt; }
}
