package com.shop.product.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(nullable = false)
  private String name;
  @Column(length = 4000)
  private String description;
  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal price;
  private String imageUrl;
  private String category;
  @Column(nullable = false)
  private UUID merchantId;
  private Instant createdAt = Instant.now();

  public UUID getId() { return id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public BigDecimal getPrice() { return price; }
  public void setPrice(BigDecimal price) { this.price = price; }
  public String getImageUrl() { return imageUrl; }
  public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
  public String getCategory() { return category; }
  public void setCategory(String category) { this.category = category; }
  public UUID getMerchantId() { return merchantId; }
  public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
  public Instant getCreatedAt() { return createdAt; }
}
