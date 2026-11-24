package com.shop.order.domain;

import jakarta.persistence.Embeddable;
import java.math.BigDecimal;
import java.util.UUID;

@Embeddable
public class OrderItem {
  private UUID productId;
  private String name;
  private BigDecimal price;
  private int quantity;
  private UUID merchantId;

  protected OrderItem() {}
  public OrderItem(UUID productId, String name, BigDecimal price, int quantity, UUID merchantId) {
    this.productId = productId; this.name = name; this.price = price; this.quantity = quantity; this.merchantId = merchantId;
  }

  public UUID getProductId() { return productId; }
  public String getName() { return name; }
  public BigDecimal getPrice() { return price; }
  public int getQuantity() { return quantity; }
  public UUID getMerchantId() { return merchantId; }
}
