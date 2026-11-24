package com.shop.cart.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CartItem(UUID productId, String name, BigDecimal price, String imageUrl, int quantity, long addedAt) {
  public CartItem withQuantity(int q) { return new CartItem(productId, name, price, imageUrl, q, addedAt); }
}
