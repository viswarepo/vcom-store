package com.shop.auth.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.shop.auth.domain.Role;
import com.shop.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByEmailIgnoreCaseAndTenantId(String email, UUID tenantId);
  boolean existsByEmailIgnoreCaseAndTenantId(String email, UUID tenantId);
  Optional<User> findByPhoneAndTenantId(String phone, UUID tenantId);
  boolean existsByPhoneAndTenantId(String phone, UUID tenantId);
  List<User> findByMerchantIdAndRoleOrderByCreatedAtDesc(UUID merchantId, Role role);
  List<User> findByTenantIdAndRoleOrderByCreatedAtDesc(UUID tenantId, Role role);
}
