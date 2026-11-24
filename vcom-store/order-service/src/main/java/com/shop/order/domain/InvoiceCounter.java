package com.shop.order.domain;

import jakarta.persistence.*;
import java.util.UUID;

/** One running invoice number per merchant. */
@Entity
@Table(name = "invoice_counters")
public class InvoiceCounter {
  @Id
  private UUID merchantId;
  private long lastNumber;

  protected InvoiceCounter() {}
  public InvoiceCounter(UUID merchantId) { this.merchantId = merchantId; }

  public long getLastNumber() { return lastNumber; }
  public void setLastNumber(long lastNumber) { this.lastNumber = lastNumber; }
}
