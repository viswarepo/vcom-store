package com.shop.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Merchants/admins live in the platform pseudo-tenant. A customer belongs to exactly one merchant
 * (tenantId = that merchant's user id), so the same email can have separate accounts in different stores.
 */
@Entity
@Table(name = "users", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"email", "tenant_id"}),
    @UniqueConstraint(columnNames = {"phone", "tenant_id"})})
public class User {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(nullable = false)
  private String email;
  @Column(nullable = false)
  private String passwordHash;
  private String phone;          // +91XXXXXXXXXX; unique within a tenant
  @Column(nullable = false)
  private String name;
  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role = Role.CUSTOMER;
  @Column(name = "tenant_id")
  private UUID tenantId;
  /** For STORE_ADMIN: the merchant (store owner) this staff member works for. */
  @Column(name = "merchant_id")
  private UUID merchantId;
  private Instant createdAt = Instant.now();

  /** The merchant this identity acts within: a customer's store owner, or the merchant themself. */
  public UUID tenantForToken() {
    return switch (role) {
      case CUSTOMER -> tenantId;
      case STORE_ADMIN -> merchantId;
      default -> id;
    };
  }

  public UUID getId() { return id; }
  public String getEmail() { return email; }
  public void setEmail(String email) { this.email = email; }
  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }
  public String getPasswordHash() { return passwordHash; }
  public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public Role getRole() { return role; }
  public void setRole(Role role) { this.role = role; }
  public UUID getTenantId() { return tenantId; }
  public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
  public UUID getMerchantId() { return merchantId; }
  public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
  public Instant getCreatedAt() { return createdAt; }
}
