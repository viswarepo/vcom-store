package com.shop.inventory.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "stock")
public class Stock {
  @Id
  private UUID productId;
  @Column(nullable = false)
  private int quantity;

  protected Stock() {}
  public Stock(UUID productId, int quantity) { this.productId = productId; this.quantity = quantity; }

  public UUID getProductId() { return productId; }
  public int getQuantity() { return quantity; }
  public void setQuantity(int quantity) { this.quantity = quantity; }
}
