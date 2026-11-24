package com.shop.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A subscription plan a merchant can pick when opening a store. Created and priced by the platform owner. */
@Entity
@Table(name = "plans")
public class Plan {
  public enum BillingPeriod { MONTHLY, YEARLY }

  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(nullable = false)
  private String name;
  @Column(length = 500)
  private String description;
  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price = BigDecimal.ZERO;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private BillingPeriod billingPeriod = BillingPeriod.MONTHLY;
  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal commissionPercent = new BigDecimal("10.00");
  private Integer maxProducts;          // null = unlimited
  private boolean active = true;        // inactive plans are hidden from new sign-ups
  private int sortOrder;
  private Instant createdAt = Instant.now();

  public UUID getId() { return id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public BigDecimal getPrice() { return price; }
  public void setPrice(BigDecimal price) { this.price = price; }
  public BillingPeriod getBillingPeriod() { return billingPeriod; }
  public void setBillingPeriod(BillingPeriod billingPeriod) { this.billingPeriod = billingPeriod; }
  public BigDecimal getCommissionPercent() { return commissionPercent; }
  public void setCommissionPercent(BigDecimal commissionPercent) { this.commissionPercent = commissionPercent; }
  public Integer getMaxProducts() { return maxProducts; }
  public void setMaxProducts(Integer maxProducts) { this.maxProducts = maxProducts; }
  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
  public int getSortOrder() { return sortOrder; }
  public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
  public Instant getCreatedAt() { return createdAt; }
}
