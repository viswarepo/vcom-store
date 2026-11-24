package com.shop.auth.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shop.auth.domain.PasswordReset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, UUID> {
  Optional<PasswordReset> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);
  Optional<PasswordReset> findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(UUID userId);
  List<PasswordReset> findByUserIdAndUsedFalse(UUID userId);
  long countByUserIdAndCreatedAtAfter(UUID userId, Instant after);
}
