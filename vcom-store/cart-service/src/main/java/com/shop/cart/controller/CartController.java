package com.shop.cart.controller;

import com.shop.cart.dto.CartItem;
import com.shop.cart.service.CartService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cart")
public class CartController {

  public record AddRequest(@NotNull UUID productId, @NotBlank String name, @NotNull @DecimalMin("0") BigDecimal price,
                           String imageUrl, @Min(1) @Max(99) int quantity) {}
  public record QuantityRequest(@Min(0) @Max(99) int quantity) {}

  private final CartService cart;

  public CartController(CartService cart) { this.cart = cart; }

  @GetMapping
  public List<CartItem> get(@RequestHeader("X-User-Id") UUID userId) { return cart.list(userId); }

  @PostMapping("/items")
  public List<CartItem> add(@RequestHeader("X-User-Id") UUID userId, @Valid @RequestBody AddRequest r) {
    return cart.add(userId, new CartItem(r.productId(), r.name(), r.price(), r.imageUrl(), r.quantity(), 0));
  }

  @PutMapping("/items/{productId}")
  public List<CartItem> setQuantity(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID productId,
                                    @Valid @RequestBody QuantityRequest r) {
    return cart.setQuantity(userId, productId, r.quantity());
  }

  @DeleteMapping("/items/{productId}")
  public List<CartItem> remove(@RequestHeader("X-User-Id") UUID userId, @PathVariable UUID productId) {
    return cart.remove(userId, productId);
  }

  @DeleteMapping
  public List<CartItem> clear(@RequestHeader("X-User-Id") UUID userId) {
    cart.clear(userId);
    return List.of();
  }
}
