package com.shop.order.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** Issued once, when an order is paid. Seller details are a snapshot so old invoices never change. */
@Entity
@Table(name = "invoices")
public class Invoice {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(unique = true, nullable = false)
  private UUID orderId;
  @Column(nullable = false)
  private UUID merchantId;
  @Column(nullable = false)
  private String number;
  private Instant issuedAt = Instant.now();
  private String sellerName;
  @Column(length = 1000)
  private String sellerAddress;
  private String sellerGstin;

  public UUID getId() { return id; }
  public UUID getOrderId() { return orderId; }
  public void setOrderId(UUID orderId) { this.orderId = orderId; }
  public UUID getMerchantId() { return merchantId; }
  public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
  public String getNumber() { return number; }
  public void setNumber(String number) { this.number = number; }
  public Instant getIssuedAt() { return issuedAt; }
  public String getSellerName() { return sellerName; }
  public void setSellerName(String sellerName) { this.sellerName = sellerName; }
  public String getSellerAddress() { return sellerAddress; }
  public void setSellerAddress(String sellerAddress) { this.sellerAddress = sellerAddress; }
  public String getSellerGstin() { return sellerGstin; }
  public void setSellerGstin(String sellerGstin) { this.sellerGstin = sellerGstin; }
}
