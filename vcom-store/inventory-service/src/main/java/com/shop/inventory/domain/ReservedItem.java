package com.shop.inventory.domain;

import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public class ReservedItem {
  private UUID productId;
  private int quantity;

  protected ReservedItem() {}
  public ReservedItem(UUID productId, int quantity) { this.productId = productId; this.quantity = quantity; }

  public UUID getProductId() { return productId; }
  public int getQuantity() { return quantity; }
}
