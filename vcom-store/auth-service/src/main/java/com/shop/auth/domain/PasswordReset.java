package com.shop.auth.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/** A pending one-time code for resetting a password. Only a hash of the code is stored. */
@Entity
@Table(name = "password_resets")
public class PasswordReset {
  @Id @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;
  @Column(nullable = false)
  private UUID userId;
  @Column(nullable = false)
  private String otpHash;
  @Column(nullable = false)
  private Instant expiresAt;
  private int attempts;
  private boolean used;
  private Instant createdAt = Instant.now();

  protected PasswordReset() {}
  public PasswordReset(UUID userId, String otpHash, Instant expiresAt) {
    this.userId = userId; this.otpHash = otpHash; this.expiresAt = expiresAt;
  }

  public UUID getUserId() { return userId; }
  public String getOtpHash() { return otpHash; }
  public Instant getExpiresAt() { return expiresAt; }
  public int getAttempts() { return attempts; }
  public void setAttempts(int attempts) { this.attempts = attempts; }
  public boolean isUsed() { return used; }
  public void setUsed(boolean used) { this.used = used; }
  public Instant getCreatedAt() { return createdAt; }
}
