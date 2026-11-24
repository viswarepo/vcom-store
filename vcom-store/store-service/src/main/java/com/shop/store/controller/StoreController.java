package com.shop.store.controller;

import com.shop.store.domain.Store;
import com.shop.store.dto.StoreRequest;
import com.shop.store.repository.StoreRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** One store per merchant. The store's ownerId is the merchantId used on products. */
@RestController
@RequestMapping("/api/stores")
public class StoreController {

  private final StoreRepository repo;

  public StoreController(StoreRepository repo) { this.repo = repo; }

  @GetMapping
  public List<Store> list() { return repo.findByActiveTrue(Sort.by("name")); }

  @GetMapping("/slug/{slug}")
  public Store bySlug(@PathVariable String slug) {
    return repo.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found"));
  }

  @GetMapping("/owner/{ownerId}")
  public Store byOwner(@PathVariable UUID ownerId) {
    return repo.findByOwnerId(ownerId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found"));
  }

  @GetMapping("/mine")
  public Store mine(@RequestHeader("X-Tenant-Id") UUID userId, @RequestHeader("X-User-Role") String role) {
    if (!"STORE_ADMIN".equals(role)) requireMerchant(role);
    return repo.findByOwnerId(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "You have not created a store yet"));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Store create(@Valid @RequestBody StoreRequest req,
                      @RequestHeader("X-User-Id") UUID userId, @RequestHeader("X-User-Role") String role) {
    requireMerchant(role);
    if (repo.existsByOwnerId(userId)) throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a store");
    Store s = new Store();
    s.setOwnerId(userId);
    s.setSlug(uniqueSlug(req.name()));
    apply(s, req);
    return repo.save(s);
  }

  @PutMapping("/mine")
  public Store update(@Valid @RequestBody StoreRequest req,
                      @RequestHeader("X-User-Id") UUID userId, @RequestHeader("X-User-Role") String role) {
    requireMerchant(role);
    Store s = repo.findByOwnerId(userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "You have not created a store yet"));
    apply(s, req);
    return repo.save(s);
  }

  private void apply(Store s, StoreRequest r) {
    s.setName(r.name());
    s.setDescription(r.description());
    s.setLogoUrl(r.logoUrl());
    s.setAddress(r.address());
    s.setGstin(r.gstin());
    if (r.themeColor() != null) s.setThemeColor(r.themeColor());
  }

  private String uniqueSlug(String name) {
    String base = name.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    if (base.isEmpty()) base = "store";
    String slug = base;
    for (int i = 2; repo.existsBySlug(slug); i++) slug = base + "-" + i;
    return slug;
  }

  private void requireMerchant(String role) {
    if (!"MERCHANT".equals(role) && !"ADMIN".equals(role))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Merchant access required");
  }
}
