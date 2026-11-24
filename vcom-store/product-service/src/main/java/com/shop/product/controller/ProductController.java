package com.shop.product.controller;

import com.shop.product.config.KafkaConfig;
import com.shop.product.domain.PlanLimitClient;
import com.shop.product.domain.Product;
import com.shop.product.dto.ProductRequest;
import com.shop.product.event.ProductEvent;
import com.shop.product.repository.ProductRepository;
import jakarta.validation.Valid;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Identity comes from X-User-Id / X-User-Role headers, which only the API gateway
 * sets after verifying the JWT. Do not expose this service directly to the internet.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

  private static final Sort NEWEST = Sort.by(Sort.Direction.DESC, "createdAt");

  private final ProductRepository repo;
  private final KafkaTemplate<String, Object> kafka;

  private final PlanLimitClient limits;

  public ProductController(ProductRepository repo, KafkaTemplate<String, Object> kafka, PlanLimitClient limits) {
    this.repo = repo;
    this.kafka = kafka;
    this.limits = limits;
  }

  /**
   * Storefront search. sort: latest (default), price_asc, price_desc.
   * Unknown sort values fall back to latest.
   */
  @GetMapping
  public List<Product> list(@RequestParam(defaultValue = "") String q,
                            @RequestParam(required = false) String category,
                            @RequestParam(required = false) UUID merchantId,
                            @RequestParam(required = false) BigDecimal minPrice,
                            @RequestParam(required = false) BigDecimal maxPrice,
                            @RequestParam(required = false) String sort) {
    checkPrices(minPrice, maxPrice);
    return repo.findAll(spec(q, category, merchantId, minPrice, maxPrice), sortFor(sort));
  }

  /** The merchant team's own products, with the same search, category filter and sorting. */
  @GetMapping("/mine")
  public List<Product> mine(@RequestParam(defaultValue = "") String q,
                            @RequestParam(required = false) String category,
                            @RequestParam(required = false) BigDecimal minPrice,
                            @RequestParam(required = false) BigDecimal maxPrice,
                            @RequestParam(required = false) String sort,
                            @RequestHeader("X-Tenant-Id") UUID userId,
                            @RequestHeader("X-User-Role") String role) {
    requireMerchant(role);
    checkPrices(minPrice, maxPrice);
    return repo.findAll(spec(q, category, userId, minPrice, maxPrice), sortFor(sort));
  }

  /** The categories one merchant uses, for the filter drop-down (case-insensitive, sorted). */
  @GetMapping("/categories")
  public List<String> categories(@RequestParam UUID merchantId) {
    return repo.findCategories(merchantId).stream()
        .map(String::trim).filter(c -> !c.isEmpty())
        .collect(Collectors.toMap(String::toLowerCase, c -> c, (a, b) -> a, TreeMap::new))
        .values().stream().toList();
  }

  public record PriceRange(BigDecimal min, BigDecimal max) {}

  /** Lowest and highest price in one store, to hint at sensible values in the price filter (both null if it has no products). */
  @GetMapping("/price-range")
  public PriceRange priceRange(@RequestParam UUID merchantId) {
    var b = repo.findPriceBounds(merchantId);
    return new PriceRange(b == null ? null : b.getLowest(), b == null ? null : b.getHighest());
  }

  @GetMapping("/{id}")
  public Product get(@PathVariable UUID id) {
    return find(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Product create(@Valid @RequestBody ProductRequest req,
                        @RequestHeader("X-Tenant-Id") UUID userId,
                        @RequestHeader("X-User-Role") String role) {
    requireMerchant(role);
    Integer max = limits.maxProducts(userId);
    if (max != null && repo.countByMerchantId(userId) >= max)
      throw new ResponseStatusException(HttpStatus.FORBIDDEN,
          "Your plan allows " + max + " products. Upgrade your plan to add more.");
    Product p = new Product();
    p.setMerchantId(userId);
    apply(p, req);
    p = repo.save(p);
    publish(p.getId(), "CREATED");
    return p;
  }

  @PutMapping("/{id}")
  public Product update(@PathVariable UUID id, @Valid @RequestBody ProductRequest req,
                        @RequestHeader("X-Tenant-Id") UUID userId,
                        @RequestHeader("X-User-Role") String role) {
    requireMerchant(role);
    Product p = find(id);
    requireOwner(p, userId, role);
    apply(p, req);
    p = repo.save(p);
    publish(p.getId(), "UPDATED");
    return p;
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable UUID id,
                     @RequestHeader("X-Tenant-Id") UUID userId,
                     @RequestHeader("X-User-Role") String role) {
    requireMerchant(role);
    Product p = find(id);
    requireOwner(p, userId, role);
    repo.delete(p);
    publish(id, "DELETED");
  }

  private Product find(UUID id) {
    return repo.findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
  }

  private void apply(Product p, ProductRequest r) {
    p.setName(r.name());
    p.setDescription(r.description());
    p.setPrice(r.price());
    p.setImageUrl(r.imageUrl());
    p.setCategory(r.category());
  }

  private void requireMerchant(String role) {
    if (!"MERCHANT".equals(role) && !"STORE_ADMIN".equals(role) && !"ADMIN".equals(role)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant access required");
    }
  }

  private void requireOwner(Product p, UUID userId, String role) {
    if (!"ADMIN".equals(role) && !p.getMerchantId().equals(userId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your product");
    }
  }

  private void publish(UUID id, String type) {
    kafka.send(KafkaConfig.PRODUCT_UPDATED, id.toString(), new ProductEvent(id, type, Instant.now()));
  }

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, String>> status(ResponseStatusException e) {
    return ResponseEntity.status(e.getStatusCode()).body(Map.of("message", String.valueOf(e.getReason())));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<Map<String, String>> invalid(MethodArgumentNotValidException e) {
    String msg = e.getBindingResult().getFieldErrors().stream()
        .map(f -> f.getField() + " " + f.getDefaultMessage()).findFirst().orElse("Invalid request");
    return ResponseEntity.badRequest().body(Map.of("message", msg));
  }

  private static Specification<Product> spec(String q, String category, UUID merchantId, BigDecimal minPrice, BigDecimal maxPrice) {
    return (root, cq, cb) -> {
      List<Predicate> ps = new ArrayList<>();
      if (q != null && !q.isBlank()) ps.add(cb.like(cb.lower(root.get("name")), "%" + q.trim().toLowerCase() + "%"));
      if (category != null && !category.isBlank()) ps.add(cb.equal(cb.lower(root.get("category")), category.trim().toLowerCase()));
      if (merchantId != null) ps.add(cb.equal(root.get("merchantId"), merchantId));
      if (minPrice != null) ps.add(cb.greaterThanOrEqualTo(root.<BigDecimal>get("price"), minPrice));
      if (maxPrice != null) ps.add(cb.lessThanOrEqualTo(root.<BigDecimal>get("price"), maxPrice));
      return cb.and(ps.toArray(new Predicate[0]));
    };
  }

  private static void checkPrices(BigDecimal min, BigDecimal max) {
    if ((min != null && min.signum() < 0) || (max != null && max.signum() < 0))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prices cannot be negative");
    if (min != null && max != null && min.compareTo(max) > 0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The minimum price is higher than the maximum price");
  }

  /** Price sorts break ties by newest first so the order is stable. */
  private static Sort sortFor(String sort) {
    return switch (sort == null ? "latest" : sort.toLowerCase()) {
      case "price_asc" -> Sort.by(Sort.Direction.ASC, "price").and(NEWEST);
      case "price_desc" -> Sort.by(Sort.Direction.DESC, "price").and(NEWEST);
      default -> NEWEST;
    };
  }
}
