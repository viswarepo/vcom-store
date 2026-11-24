package com.shop.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Single row (id = 1) holding the platform owner's pricing. */
@Entity
@Table(name = "platform_settings")
public class PlatformSettings {
  @Id
  private Long id = 1L;
  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal commissionPercent = new BigDecimal("10.00");
  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal registrationFee = new BigDecimal("0.00");

  public Long getId() { return id; }
  public BigDecimal getCommissionPercent() { return commissionPercent; }
  public void setCommissionPercent(BigDecimal commissionPercent) { this.commissionPercent = commissionPercent; }
  public BigDecimal getRegistrationFee() { return registrationFee; }
  public void setRegistrationFee(BigDecimal registrationFee) { this.registrationFee = registrationFee; }
}
