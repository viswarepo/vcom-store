package com.shop.auth.config;

import com.shop.auth.controller.AuthController;
import com.shop.auth.domain.Role;
import com.shop.auth.domain.User;
import com.shop.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates the platform owner account on startup when ADMIN_EMAIL and ADMIN_PASSWORD are set. */
@Component
public class AdminBootstrap implements ApplicationRunner {

  private final UserRepository users;
  private final String email;
  private final String password;

  public AdminBootstrap(UserRepository users,
                        @Value("${app.admin.email:}") String email,
                        @Value("${app.admin.password:}") String password) {
    this.users = users;
    this.email = email;
    this.password = password;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (email.isBlank() || password.isBlank()) return;
    if (users.existsByEmailIgnoreCaseAndTenantId(email, AuthController.PLATFORM)) return;
    User admin = new User();
    admin.setName("Platform owner");
    admin.setEmail(email.toLowerCase());
    admin.setPasswordHash(new BCryptPasswordEncoder().encode(password));
    admin.setRole(Role.ADMIN);
    admin.setTenantId(AuthController.PLATFORM);
    users.save(admin);
  }
}
