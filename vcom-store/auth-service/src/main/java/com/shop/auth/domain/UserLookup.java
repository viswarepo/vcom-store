package com.shop.auth.domain;

import java.util.Optional;
import java.util.UUID;

import com.shop.auth.repository.UserRepository;
import org.springframework.stereotype.Component;

/** Finds an account by email or by mobile number, inside one tenant (platform, or one merchant's store). */
@Component
public class UserLookup {

  private final UserRepository users;

  public UserLookup(UserRepository users) { this.users = users; }

  public Optional<User> find(String identifier, UUID tenantId) {
    if (identifier == null || identifier.isBlank()) return Optional.empty();
    String id = identifier.trim();
    if (id.contains("@")) return users.findByEmailIgnoreCaseAndTenantId(id, tenantId);
    return Phones.normalize(id).flatMap(p -> users.findByPhoneAndTenantId(p, tenantId));
  }
}
