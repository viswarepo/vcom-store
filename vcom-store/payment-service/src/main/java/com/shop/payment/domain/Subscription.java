package com.shop.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A merchant's current plan. The plan's price, commission and limits are copied here when the merchant
 * pays, so later edits to the plan never change what an existing merchant agreed to.
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {
  @Id
  private UUID merchantId;
  private UUID planId;
  private String planName;
  @Column(precision = 12, scale = 2)
  private BigDecimal price;
  @Enumerated(EnumType.STRING)
  private Plan.BillingPeriod billingPeriod;
  @Column(precision = 5, scale = 2)
  private BigDecimal commissionPercent;
  private Integer maxProducts;
  @Column(nullable = false)
  private String status = "ACTIVE";      // ACTIVE | EXPIRED
  private Instant currentPeriodEnd;      // null = no expiry (free plan)
  private Instant startedAt = Instant.now();

  protected Subscription() {}
  public Subscription(UUID merchantId) { this.merchantId = merchantId; }

  public void apply(Plan plan, Instant periodEnd) {
    this.planId = plan.getId();
    this.planName = plan.getName();
    this.price = plan.getPrice();
    this.billingPeriod = plan.getBillingPeriod();
    this.commissionPercent = plan.getCommissionPercent();
    this.maxProducts = plan.getMaxProducts();
    this.currentPeriodEnd = periodEnd;
    this.status = "ACTIVE";
  }

  public UUID getMerchantId() { return merchantId; }
  public UUID getPlanId() { return planId; }
  public String getPlanName() { return planName; }
  public BigDecimal getPrice() { return price; }
  public Plan.BillingPeriod getBillingPeriod() { return billingPeriod; }
  public BigDecimal getCommissionPercent() { return commissionPercent; }
  public Integer getMaxProducts() { return maxProducts; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
}
