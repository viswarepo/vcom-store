package com.shop.inventory.controller;

import com.shop.inventory.dto.ProductClient;
import com.shop.inventory.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

  public record SetStockRequest(@Min(0) int quantity) {}

  private final InventoryService service;
  private final ProductClient products;

  public InventoryController(InventoryService service, ProductClient products) {
    this.service = service;
    this.products = products;
  }

  /** GET /api/inventory?ids=a,b,c  ->  { "a": 5, "b": 0 } (unknown products report 0) */
  @GetMapping
  public Map<UUID, Integer> levels(@RequestParam String ids) {
    var list = Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty()).map(UUID::fromString).limit(100).toList();
    return service.levels(list);
  }

  @GetMapping("/{productId}")
  public Map<String, Object> one(@PathVariable UUID productId) {
    return Map.of("productId", productId, "quantity", service.levels(java.util.List.of(productId)).get(productId));
  }

  @PutMapping("/{productId}")
  public Map<String, Object> set(@PathVariable UUID productId, @Valid @RequestBody SetStockRequest req,
                                 @RequestHeader("X-Tenant-Id") UUID userId, @RequestHeader("X-User-Role") String role) {
    if (!"MERCHANT".equals(role) && !"STORE_ADMIN".equals(role) && !"ADMIN".equals(role))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant access required");
    var product = products.get(productId);
    if (!"ADMIN".equals(role) && !userId.equals(product.merchantId()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your product");
    service.setStock(productId, req.quantity());
    return Map.of("productId", productId, "quantity", req.quantity());
  }
}
