package com.shop.store.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stores")
public class Store {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(unique = true, nullable = false)
  private UUID ownerId;
  @Column(nullable = false)
  private String name;
  @Column(unique = true, nullable = false)
  private String slug;
  @Column(length = 2000)
  private String description;
  private String logoUrl;
  @Column(length = 1000)
  private String address;     // shown on invoices
  private String gstin;       // tax id shown on invoices
  private String themeColor = "#1f6f5c";
  /** false until the merchant has completed registration with the platform owner */
  private boolean active = false;
  private Instant createdAt = Instant.now();

  public UUID getId() { return id; }
  public UUID getOwnerId() { return ownerId; }
  public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getSlug() { return slug; }
  public void setSlug(String slug) { this.slug = slug; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public String getLogoUrl() { return logoUrl; }
  public void setLogoUrl(String logoUrl) { this.logoUrl = logoUrl; }
  public String getAddress() { return address; }
  public void setAddress(String address) { this.address = address; }
  public String getGstin() { return gstin; }
  public void setGstin(String gstin) { this.gstin = gstin; }
  public String getThemeColor() { return themeColor; }
  public void setThemeColor(String themeColor) { this.themeColor = themeColor; }
  public boolean isActive() { return active; }
  public void setActive(boolean active) { this.active = active; }
  public Instant getCreatedAt() { return createdAt; }
}
